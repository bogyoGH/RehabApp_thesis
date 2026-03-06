package org.example.project

import kotlin.time.Clock

// A class a zokni adatait tartalmazza
data class SensoriaData(
    //idő változó
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    //A szenzorok adatai
    val ch0: Int,
    val ch1: Int,
    val ch2: Int,
    //Az adatok normalizálásához szükséges konstansok
    val accelX: Int = 512,
    val accelY: Int = 512,
    val accelZ: Int = 512,
    // Az adott feladat neve, amelyet később megkap értékként
    val taskName: String = ""
) {
    //Normalizált adatokat kiszámoló függvény
    fun GetNormalizedAcceleration(): Map<String, Double> {
        return mapOf(
            "x" to (accelX - 512) / 512.0 * 2.0,
            "y" to (accelY - 512) / 512.0 * 2.0,
            "z" to (accelZ - 512) / 512.0 * 2.0
        )
    }
}