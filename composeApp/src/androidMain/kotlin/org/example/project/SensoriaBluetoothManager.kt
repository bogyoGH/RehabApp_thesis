package org.example.project.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
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
import org.example.project.data.SensoriaData

actual class SensoriaBluetoothManager actual constructor(
    private val scope: CoroutineScope
) {
    private val context: Context
        get() = AppContextHolder.context
            ?: error("AppContextHolder.context nincs inicializálva")

    private val sensoriaServiceUuid: UUID =
        UUID.fromString("1cac0001-656e-696c-4b5f-6e6572726157")
    //1cac0003-656e-696c-4b5f-6e6572726157(?)
    private val cccdUuid: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothGatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    actual suspend fun connectAndListen(
        onStatusChange: (String) -> Unit
    ): Flow<SensoriaData> = callbackFlow {
        ensureBluetoothPermissions()

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val bluetoothAdapter: BluetoothAdapter =
            bluetoothManager.adapter ?: run {
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

        onStatusChange("🔍 Sensoria zokni keresése...")

        var scanCallback: ScanCallback? = null

        val gattCallback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onStatusChange("❌ Kapcsolódási hiba: $status")
                    gatt.close()
                    bluetoothGatt = null
                    close(IllegalStateException("GATT error: $status"))
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        bluetoothGatt = gatt
                        onStatusChange("✅ Csatlakozva. Service keresése...")

                        val started = gatt.discoverServices()

                        if (!started) {
                            onStatusChange("❌ Service discovery nem indult el")
                            gatt.close()
                            bluetoothGatt = null
                            close(IllegalStateException("Service discovery did not start"))
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        onStatusChange("🔌 Sensoria zokni lecsatlakozott")
                        gatt.close()
                        bluetoothGatt = null
                        close()
                    }
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onStatusChange("❌ Service discovery hiba: $status")
                    close(IllegalStateException("Service discovery error: $status"))
                    return
                }

                val service = gatt.getService(sensoriaServiceUuid)

                if (service == null) {
                    onStatusChange("❌ Sensoria service nem található")
                    close(IllegalStateException("Sensoria service not found"))
                    return
                }
                println("✅ Service megvan! Lássuk, milyen csatornák (Characteristics) vannak benne:")
                service.characteristics.forEach { char ->
                    println("👉 Talált csatorna: ${char.uuid} | Properties: ${char.properties}")
                }
                val targetUuid = UUID.fromString("1cac0003-656e-696c-4b5f-6e6572726157")

                val notifyCharacteristic = service.characteristics.firstOrNull { characteristic ->
                    characteristic.uuid == targetUuid
                }

                if (notifyCharacteristic == null) {
                    onStatusChange("❌ Notify characteristic nem található")
                    close(IllegalStateException("Notify characteristic not found"))
                    return
                }

                val subscribed = enableNotification(
                    gatt = gatt,
                    characteristic = notifyCharacteristic
                )

                if (subscribed) {
                    onStatusChange("📡 Listening for data...")
                } else {
                    onStatusChange("❌ Notify feliratkozás sikertelen")
                    close(IllegalStateException("Notification subscription failed"))
                }
            }

            @Deprecated("Deprecated in Android 13, but needed below API 33")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val value = characteristic.value ?: return
                handleIncomingPacket(value)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    println("🔔 SIKERES FELIRATKOZÁS! A zokni elfogadta a kérést. Jönnie kéne az adatnak.")
                } else {
                    println("❌ HIBA a feliratkozáskor. A zokni elutasította. Status kód: $status")
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleIncomingPacket(value)
            }

            private fun handleIncomingPacket(value: ByteArray) {
                // ÚJ: Minden beérkező jelnél kiírunk egy sort a Logcatbe!
                println("📡 NYERS ADAT ÉRKEZETT: ${value.size} byte")

                try {
                    val sensoriaData = decodeSensoriaPacket(value)

                    if (sensoriaData != null) {
                        trySend(sensoriaData)
                    } else {
                        println("❌ A csomagot nem sikerült dekódolni (null lett a végeredmény).")
                    }
                } catch (e: Exception) {
                    onStatusChange("❌ Adat dekódolási hiba: ${e.message}")
                }
            }
        }

        scanCallback = object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                val device = result.device

                val deviceName =
                    result.scanRecord?.deviceName
                        ?: safeDeviceName(device)
                        ?: ""

                val advertisedServiceUuids =
                    result.scanRecord?.serviceUuids.orEmpty()

                val hasSensoriaService =
                    advertisedServiceUuids.any { parcelUuid ->
                        parcelUuid.uuid == sensoriaServiceUuid
                    }

                val hasSensoriaName =
                    deviceName.contains("sensoria", ignoreCase = true) ||
                            deviceName.contains("smart sock", ignoreCase = true) ||
                            deviceName.contains("Sensoria-C1-D348", ignoreCase = true) ||
                            deviceName.contains("Sensoria-C1-9C1D", ignoreCase = true)

                if (!hasSensoriaService && !hasSensoriaName) {
                    return
                }

                val shownName = deviceName.ifBlank { "Sensoria Sock" }

                onStatusChange("✅ Talált eszköz: $shownName")

                runCatching {
                    scanner.stopScan(this)
                }

                onStatusChange("⏳ Csatlakozás: $shownName")

                bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(
                        context,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    device.connectGatt(
                        context,
                        false,
                        gattCallback
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                onStatusChange("❌ BLE scan hiba: $errorCode")
                close(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(sensoriaServiceUuid))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(
            listOf(scanFilter),
            scanSettings,
            scanCallback
        )

        awaitClose {
            scanCallback?.let {
                runCatching {
                    scanner.stopScan(it)
                }
            }

            bluetoothGatt?.let {
                runCatching {
                    it.disconnect()
                }

                runCatching {
                    it.close()
                }
            }

            bluetoothGatt = null
        }
    }

    @SuppressLint("MissingPermission")
    actual suspend fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        val notificationEnabled =
            gatt.setCharacteristicNotification(characteristic, true)

        if (!notificationEnabled) {
            return false
        }

        val descriptor = characteristic.getDescriptor(cccdUuid)
            ?: return false

        val enableValue =
            if (
                characteristic.properties and
                BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            ) {
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

    private fun decodeSensoriaPacket(byteArray: ByteArray): SensoriaData? {
        // JAVÍTÁS: A 130 bit olvasásához elég 17 byte! Ha ennél is kevesebb, akkor van baj.
        if (byteArray.size < 12) {
            println("⚠️ Csomag eldobva: Túl rövid (${byteArray.size} byte)")
            return null
        }

        return try {
            val reader = BitReader(byteArray)

            reader.readBits(4)  // msg_type
            reader.readBits(4)  // context
            reader.readBits(2)  // gs
            reader.readBits(2)  // s
            reader.readBits(2)  // g
            reader.readBits(2)  // hz
            reader.readBits(24) // tick

            val ch0 = reader.readBits(10)
            val ch1 = reader.readBits(10)
            val ch2 = reader.readBits(10)

            reader.readBits(10) // mag_x
            reader.readBits(10) // mag_y
            reader.readBits(10) // mag_z

            val accelX = reader.readBits(10)
            val accelY = reader.readBits(10)
            val accelZ = reader.readBits(10)

            SensoriaData(
                ch0 = ch0,
                ch1 = ch1,
                ch2 = ch2,
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ
            )
        } catch (e: Exception) {
            // ÚJ: Most már látni fogjuk a Logcatben, ha elszáll a BitReader!
            println("❌ Hiba a bitek olvasásakor: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? {
        return try {
            device.name
        } catch (_: SecurityException) {
            null
        }
    }

    private fun ensureBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

            val connectGranted =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

            if (!scanGranted || !connectGranted) {
                throw SecurityException(
                    "Hiányzik a BLUETOOTH_SCAN vagy BLUETOOTH_CONNECT engedély"
                )
            }
        } else {
            val locationGranted =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

            if (!locationGranted) {
                throw SecurityException(
                    "Android 11 vagy régebbi rendszeren BLE scanhez ACCESS_FINE_LOCATION kell"
                )
            }
        }
    }
}