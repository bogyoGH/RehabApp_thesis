package org.example.project.bluetooth // A te csomagneved

import com.juul.kable.Scanner
import com.juul.kable.peripheral
import com.juul.kable.characteristicOf // ÚJ IMPORT: Ezt hozzá kell adni!
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.example.project.data.SensoriaData

actual class SensoriaBluetoothManager actual constructor(private val scope: CoroutineScope) {

    // Mindkét UUID-t stringként tároljuk
    private val sensoriaServiceUuidString = "1cac0001-656e-696c-4b5f-6e6572726157" // Ezt az Android kódból vettük
    private val targetCharUuidString = "1cac0003-656e-696c-4b5f-6e6572726157"

    actual suspend fun connectAndListen(
        onStatusChange: (String) -> Unit
    ): Flow<SensoriaData> {
        onStatusChange("🔍 Keresés indítása iOS-en...")

        val scanner = Scanner()
        val advertisement = scanner.advertisements.first { ad ->
            ad.name == "Sensoria-C1-D348"
        }

        onStatusChange("🟢 Zokni megtalálva! Csatlakozás...")
        val sock = scope.peripheral(advertisement)
        sock.connect()

        onStatusChange("📡 Csatlakoztatva! Adatok fogadása...")

        // JAVÍTÁS: Nincs több ciklus! Közvetlenül példányosítjuk a Kable-nek a csatornát.
        val sensorCharacteristic = characteristicOf(
            service = sensoriaServiceUuidString,
            characteristic = targetCharUuidString
        )

        // Feliratkozunk és dekódolunk
        return sock.observe(sensorCharacteristic).mapNotNull { byteArray ->
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

    actual suspend fun disconnect() {
        // Stub, ahogy korábban is
        println("🔌 iOS Bluetooth kapcsolat bontása (Stub)")
    }
}