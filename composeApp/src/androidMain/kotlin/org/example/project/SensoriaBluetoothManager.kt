package org.example.project

import com.benasher44.uuid.uuidFrom
import com.juul.kable.Advertisement
import com.juul.kable.Characteristic
import com.juul.kable.Scanner
import com.juul.kable.peripheral // Ezt az importot hozzá kell adni!
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

actual class SensoriaBluetoothManager actual constructor(private val scope: CoroutineScope) {

    private val targetCharUuidString = "1cac0003-656e-696c-4b5f-6e6572726157"

    // Visszatettük a 'suspend' kulcsszót!
    actual suspend fun connectAndListen(): Flow<SensoriaData> {
        println("🔍 Keresés indítása...")

        // 1. Scanner inicializálása Kable módra
        val scanner = Scanner()
        val advertisement: Advertisement = scanner.advertisements.first { ad ->
            ad.name == "Sensoria-C1-D348" || ad.identifier == "D6:1B:51:53:EF:E1" // Figyelem: címe helyett identifier a biztosabb iOS/Android multiplatform esetén
        }

        println("🟢 Zokni megtalálva! Csatlakozás...")

        // 2. Peripheral létrehozása a scope segítségével
        val sock = scope.peripheral(advertisement)
        sock.connect()

        val targetCharUuid = uuidFrom(targetCharUuidString)
        var sensorCharacteristic: Characteristic? = null

        val services = sock.services.filterNotNull().first()
        for (service in services) {
            for (char in service.characteristics) {
                if (char.characteristicUuid == targetCharUuid) {
                    sensorCharacteristic = char
                    break
                }
            }
            if (sensorCharacteristic != null) break
        }

        if (sensorCharacteristic == null) {
            throw Exception("Nem találtam meg a megfelelő Bluetooth karakterisztikát a zoknin!")
        }

        println("📡 Csatlakoztatva! Adatok fogadása...")

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
}