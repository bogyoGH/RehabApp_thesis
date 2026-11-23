package org.example.project.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import org.example.project.AppContextHolder
import org.example.project.data.SensorSample

actual class SensoriaBluetoothManager actual constructor(
    private val scope: CoroutineScope
) {
    private val context: Context
        get() = AppContextHolder.context
            ?: error("AppContextHolder.context nincs inicializálva")

    // UUID-k a Sensoria BLE szolgáltatáshoz és jellemzőkhöz
    private val sensoriaServiceUuid: UUID = UUID.fromString("1cac0001-656e-696c-4b5f-6e6572726157")
    private val dataCharacteristicUuid: UUID = UUID.fromString("1cac0003-656e-696c-4b5f-6e6572726157")
    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Swift kód alapján a hardcoded nevek a bal és jobb lábhoz
    private val preferredLeftSockName = "Sensoria-C1-D348"
    private val preferredRightSockName = "Sensoria-C1-9C1D"

    // Két külön kapcsolat fenntartása
    private var leftGatt: BluetoothGatt? = null
    private var rightGatt: BluetoothGatt? = null

    // Mérés kezdeti ideje a host_time_ms számításához
    private var measurementStartTimeMs: Long = 0

    @SuppressLint("MissingPermission")
    actual suspend fun connectAndListen(
        onStatusChange: (String) -> Unit
    ): Flow<Pair<SockSide, SensorSample>> = callbackFlow {
        ensureBluetoothPermissions()

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter ?: run {
            onStatusChange("❌ Bluetooth adapter nem elérhető")
            close()
            return@callbackFlow
        }

        if (!bluetoothAdapter.isEnabled) {
            onStatusChange("❌ Bluetooth nincs bekapcsolva")
            close()
            return@callbackFlow
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            onStatusChange("❌ BLE scanner nem elérhető")
            close()
            return@callbackFlow
        }

        onStatusChange("🔍 Sensoria zoknik keresése (Bal és Jobb)...")
        measurementStartTimeMs = System.currentTimeMillis()

        // GATT Callback gyár függvény, hogy mindkét lábnak saját eseménykezelője legyen
        fun createGattCallback(side: SockSide): BluetoothGattCallback {
            val sideName = if (side == SockSide.LEFT) "BAL" else "JOBB"
            return object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        onStatusChange("❌ $sideName kapcsolódási hiba: $status")
                        gatt.close()
                        if (side == SockSide.LEFT) leftGatt = null else rightGatt = null
                        return
                    }
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        if (side == SockSide.LEFT) leftGatt = gatt else rightGatt = gatt
                        onStatusChange("✅ $sideName csatlakozva. Service keresése...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        onStatusChange("🔌 $sideName lecsatlakozott")
                        gatt.close()
                        if (side == SockSide.LEFT) leftGatt = null else rightGatt = null
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt.getService(sensoriaServiceUuid)
                        val notifyCharacteristic = service?.getCharacteristic(dataCharacteristicUuid)

                        if (notifyCharacteristic != null) {
                            enableNotification(gatt, notifyCharacteristic)
                            onStatusChange("📡 $sideName feliratkozva az adatokra.")
                        } else {
                            onStatusChange("❌ $sideName Notify characteristic nem található")
                        }
                    }
                }

                @Deprecated("Deprecated in Android 13")
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    handleIncomingPacket(characteristic.value)
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    handleIncomingPacket(value)
                }

                private fun handleIncomingPacket(value: ByteArray) {
                    val rawSample = SensoriaDecoder.decodePacket(value)
                    if (rawSample != null) {
                        val hostTimeMs = (System.currentTimeMillis() - measurementStartTimeMs).toDouble()
                        val sampleWithHostTime = rawSample.copy(host_time_ms = hostTimeMs)

                        trySend(Pair(side, sampleWithHostTime))
                    }
                }
            }
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = result.scanRecord?.deviceName ?: safeDeviceName(device) ?: ""

                // Eszköz nevéből döntjük el a bal/jobb oldalt
                val side = when (deviceName) {
                    preferredLeftSockName -> SockSide.LEFT
                    preferredRightSockName -> SockSide.RIGHT
                    else -> null
                }

                if (side != null) {
                    val isLeftAndNeeded = side == SockSide.LEFT && leftGatt == null
                    val isRightAndNeeded = side == SockSide.RIGHT && rightGatt == null

                    if (isLeftAndNeeded || isRightAndNeeded) {
                        onStatusChange("⏳ Csatlakozás: $deviceName (${if(side == SockSide.LEFT) "BAL" else "JOBB"})")

                        val gattCallback = createGattCallback(side)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                        } else {
                            device.connectGatt(context, false, gattCallback)
                        }
                    }

                    // Ha mindkettő csatlakozási fázisban van, leállíthatjuk a szkennert
                    if (leftGatt != null && rightGatt != null) {
                        onStatusChange("✅ Mindkét zokni megvan, szkenner leállítása.")
                        runCatching { scanner.stopScan(this) }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                onStatusChange("❌ BLE scan hiba: $errorCode")
            }
        }

        val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(sensoriaServiceUuid)).build()
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)

        awaitClose {
            runCatching { scanner.stopScan(scanCallback) }
            runCatching { leftGatt?.disconnect(); leftGatt?.close() }
            runCatching { rightGatt?.disconnect(); rightGatt?.close() }
            leftGatt = null
            rightGatt = null
        }
    }

    @SuppressLint("MissingPermission")
    actual suspend fun disconnect() {
        leftGatt?.disconnect(); leftGatt?.close(); leftGatt = null
        rightGatt?.disconnect(); rightGatt?.close(); rightGatt = null
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        if (!gatt.setCharacteristicNotification(characteristic, true)) return false
        val descriptor = characteristic.getDescriptor(cccdUuid) ?: return false

        val enableValue = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, enableValue) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = enableValue
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? {
        return try { device.name } catch (_: SecurityException) { null }
    }

    private fun ensureBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!scanGranted || !connectGranted) throw SecurityException("Hiányzik a BLUETOOTH_SCAN vagy BLUETOOTH_CONNECT engedély")
        } else {
            val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!locationGranted) throw SecurityException("Android 11 vagy régebbi rendszeren BLE scanhez ACCESS_FINE_LOCATION kell")
        }
    }
}