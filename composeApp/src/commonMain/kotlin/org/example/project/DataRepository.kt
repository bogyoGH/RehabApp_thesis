package org.example.project

import kotlin.random.Random

object MockDataGenerator {
    /**
     * Mesterséges adatcsomagot generál alapján.
     */
    fun generatePacket(taskName: String = ""): SensoriaData {
        // Megnézzük, hogy járó feladat-e
        val isWalking = taskName.lowercase().contains("walking")

        // Alapértékek beállítása
        val baseVal = if (isWalking) 520 else 512
        val noiseRange = if (isWalking) 50 else 20

        return SensoriaData(
            // Nyers nyomásértékek generálása
            ch0 = Random.nextInt(300, 700),
            ch1 = Random.nextInt(200, 600),
            ch2 = Random.nextInt(100, 500),

            // Gyorsulásmérő adatok zajjal
            accelX = baseVal + Random.nextInt(-noiseRange, noiseRange),
            accelY = baseVal + Random.nextInt(-noiseRange, noiseRange),
            accelZ = baseVal + Random.nextInt(-noiseRange, noiseRange),

            taskName = taskName
        )
    }
}