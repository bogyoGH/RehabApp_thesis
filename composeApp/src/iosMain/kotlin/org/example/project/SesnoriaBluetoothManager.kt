package org.example.project

import com.benasher44.uuid.uuidFrom
import com.juul.kable.Characteristic
import com.juul.kable.Scanner
import com.juul.kable.peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.filterNotNull

actual class `SensoriaBluetoothManager` actual constructor(private val scope: CoroutineScope) {

    // A Python kódból származó Characteristic UUID
    private val targetCharUuidString = "1cac0003-656e-696c-4b5f-6e6572726157"

    // JAVÍTÁS 1: Bekerült az 'actual' kulcsszó
    actual suspend fun connectAndListen(): Flow<SensoriaData> {
        println("🔍 Keresés indítása iOS-en...")

        val scanner = Scanner()
        // JAVÍTÁS 2: Csak névre szűrünk, mert iOS-en nincs publikus MAC cím
        val advertisement = scanner.advertisements.first { ad ->
            ad.name == "Sensoria-C1-D348"
        }

        println("🟢 Zokni megtalálva! Csatlakozás...")
        val sock = scope.peripheral(advertisement)
        sock.connect()

        // 2. Dinamikusan megkeressük, hogy melyik Service-ben lakik a Characteristic
        val targetCharUuid = uuidFrom(targetCharUuidString)
        var sensorCharacteristic: Characteristic? = null

        val services = sock.services.filterNotNull().first() // Megvárjuk, amíg a lista elérhető lesz
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

        // 3. Feliratkozunk az adatfolyamra és dekódoljuk a biteket
        return sock.observe(sensorCharacteristic).mapNotNull { byteArray ->
            if (byteArray.size < 20) return@mapNotNull null // Biztonsági ellenőrzés

            val reader = BitReader(byteArray)

            // Fejléc adatok ugrása
            reader.readBits(4)  // msg_type
            reader.readBits(4)  // context
            reader.readBits(2)  // gs
            reader.readBits(2)  // s
            reader.readBits(2)  // g
            reader.readBits(2)  // hz
            reader.readBits(24) // tick

            // Nyomásadatok kiolvasása
            val ch0 = reader.readBits(10)
            val ch1 = reader.readBits(10)
            val ch2 = reader.readBits(10)

            // Mágneses adatok ugrása
            reader.readBits(10) // mag_x
            reader.readBits(10) // mag_y
            reader.readBits(10) // mag_z

            // Gyorsulásmérő adatok kiolvasása
            val accelX = reader.readBits(10)
            val accelY = reader.readBits(10)
            val accelZ = reader.readBits(10)

            // SensoriaData példányosítása
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