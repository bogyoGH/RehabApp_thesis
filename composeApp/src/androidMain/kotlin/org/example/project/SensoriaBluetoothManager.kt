package org.example.project

import com.benasher44.uuid.uuidFrom
import com.juul.kable.Advertisement
import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

actual class SensoriaBluetoothManager actual constructor(private val scope: CoroutineScope) {

    private val targetCharUuidString = "1cac0003-656e-696c-4b5f-6e6572726157"
    private var sock: Peripheral? = null // Itt tároljuk el a kapcsolatot a leválasztáshoz

    actual suspend fun connectAndListen(onStatusChange: (String) -> Unit): Flow<SensoriaData> {
        onStatusChange("🔍 Keresés indítása...")

        val scanner = Scanner()
        val advertisement: Advertisement = scanner.advertisements.first { ad ->
            ad.name == "Sensoria-C1-D348"
        }

        sock = scope.peripheral(advertisement)

        // HÁTTÉRBELI ÁLLAPOTFIGYELŐ
        scope.launch {
            var hasConnected = false
            sock?.state?.collect { state ->
                when (state) {
                    is State.Connecting -> onStatusChange("⏳ Csatlakozás...")
                    is State.Connected -> {
                        hasConnected = true
                        onStatusChange("✅ Csatlakoztatva (Várakozás adatra...)")
                    }
                    is State.Disconnecting -> onStatusChange("🔌 Leválasztás folyamatban...")
                    is State.Disconnected -> {
                        if (hasConnected) {
                            onStatusChange("❌ Megszakadt a kapcsolat!")
                        }
                    }
                }
            }
        }

        sock?.connect()

        val targetCharUuid = uuidFrom(targetCharUuidString)
        var sensorCharacteristic: Characteristic? = null

        val services = sock?.services?.filterNotNull()?.first()
        if (services != null) {
            for (service in services) {
                for (char in service.characteristics) {
                    if (char.characteristicUuid == targetCharUuid) {
                        sensorCharacteristic = char
                        break
                    }
                }
                if (sensorCharacteristic != null) break
            }
        }

        if (sensorCharacteristic == null) {
            throw Exception("Nem találtam meg a megfelelő karakterisztikát!")
        }

        return sock!!.observe(sensorCharacteristic).mapNotNull { byteArray ->
            if (byteArray.size < 20) return@mapNotNull null

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
        }
    }

    // A LEVÁLASZTÁS FIZIKAI IMPLEMENTÁCIÓJA
    actual suspend fun disconnect() {
        sock?.disconnect()
        sock = null
    }
}