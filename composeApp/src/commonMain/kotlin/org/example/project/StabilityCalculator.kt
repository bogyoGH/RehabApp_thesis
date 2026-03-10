package org.example.project

import kotlin.math.sqrt

object StabilityCalculator {
    /**
     * Kiszámítja a stabilitási pontszámot a gyorsulásmérő adatai alapján.
     */
    fun calculate(data: SensoriaData): Double {
        val accel = data.GetNormalizedAcceleration() // Normalizált adatok lekérése

        val x = accel["x"] ?: 0.0
        val y = accel["y"] ?: 0.0
        val z = accel["z"] ?: 0.0

        // Euklideszi magnitúdó számítása
        val magnitude = sqrt(x * x + y * y + z * z)

        // Pontszám skálázása és korlátozása 0-100 közé
        val score = (100 - (magnitude * 50)).coerceIn(0.0, 100.0)

        // Kerekítés egy tizedesjegyre
        return (score * 10).toInt() / 10.0
    }
}