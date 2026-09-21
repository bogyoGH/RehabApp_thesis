package org.example.project

import kotlinx.datetime.Instant
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock
import org.example.project.data.SensoriaData

object MockDataGenerator {
    // Egy belső számláló, ami az idő múlását szimulálja, így hullámokat (lépéseket) tudunk generálni
    private var tick = 0

    /**
     * Mesterséges adatcsomagot generál a feladat neve alapján.
     */
    fun generatePacket(taskName: String = ""): SensoriaData {
        tick++

        val nameLower = taskName.lowercase()
        val isRest = nameLower.contains("rest") || nameLower.contains("pihenő") || nameLower.contains("állás") && !nameLower.contains("tandem")
        val isTandem = nameLower.contains("tandem")
        val isMarching = nameLower.contains("march") || nameLower.contains("helyben")
        val isToeWalking = nameLower.contains("toe") || nameLower.contains("lábujj")
        val isHeelWalking = nameLower.contains("heel") || nameLower.contains("sarkon")

        var ch0 = 0
        var ch1 = 0
        var ch2 = 0
        var accelX = 512
        var accelY = 512
        var accelZ = 512

        when {
            isRest -> {
                // NYUGALOM: A sarok (ch2) hordozza a súly nagy részét, a belső talp (ch0) közepeset, a külső (ch1) alig.
                val swayX = sin(tick * 0.05)
                val swayY = cos(tick * 0.03)

                ch0 = (400 + swayY * 100 + swayX * 50).toInt() + Random.nextInt(-10, 10) // Sárgás lesz
                ch1 = (200 + swayY * 50 - swayX * 30).toInt() + Random.nextInt(-10, 10)  // Zöldes lesz
                ch2 = (700 - swayY * 150).toInt() + Random.nextInt(-15, 15)              // Pirosas lesz
            }

            isToeWalking -> {
                // LÁBUJJHEGY: A belső talppárna (ch0) viszi a fő terhet, a külső (ch1) kevesebbet, sarok (ch2) levegőben.
                val stepCycle = sin(tick * 0.2)

                ch0 = (600 + stepCycle * 350).toInt().coerceAtLeast(50) // Eléri a 950-et (Piros)
                ch1 = (300 + stepCycle * 150).toInt().coerceAtLeast(50) // Eléri a 450-et (Zöld/Sárga)
                ch2 = Random.nextInt(0, 30) // Fekete/Sötétzöld

                accelZ = 512 + (stepCycle * 100).toInt()
            }

            isHeelWalking -> {
                // SARKON JÁRÁS: Extrém sarokterhelés, előtalp a levegőben.
                val stepCycle = sin(tick * 0.2)

                ch0 = Random.nextInt(0, 40) // Sötétzöld
                ch1 = Random.nextInt(0, 20) // Sötétzöld
                ch2 = (600 + stepCycle * 350).toInt().coerceAtLeast(50) // Eléri a 950-et (Piros)

                accelZ = 512 + (stepCycle * 150).toInt()
            }

            isTandem -> {
                // TANDEM ÁLLÁS: Erős egyensúlyozás aszimmetriával
                val wobble = sin(tick * 0.4) * cos(tick * 0.15)

                ch0 = (750 + wobble * 200).toInt() // Nagyon magas (Piros)
                ch1 = (400 - wobble * 100).toInt() // Közepes (Sárga)
                ch2 = (550 + sin(tick * 0.1) * 150).toInt() // Közepes-magas (Narancs)
            }

            isMarching -> {
                // HELYBEN JÁRÁS: Jól elkülönülő fázisok, csökkentett ch1 értékkel, hogy ne legyen minden piros!
                val cycle = tick % 40
                when (cycle) {
                    in 0..9 -> { // 1. Sarokleütés: ch2 piros
                        ch0 = Random.nextInt(30, 60)
                        ch1 = Random.nextInt(10, 30)
                        ch2 = 950 + Random.nextInt(-30, 30)
                        accelZ = 800
                    }
                    in 10..19 -> { // 2. Teljes talp: közepes terhelések
                        ch0 = 450 + Random.nextInt(-30, 30)
                        ch1 = 250 + Random.nextInt(-30, 30)
                        ch2 = 500 + Random.nextInt(-30, 30)
                        accelZ = 512
                    }
                    in 20..29 -> { // 3. Elrugaszkodás: ch0 piros, ch1 csak sárgászöld!
                        ch0 = 850 + Random.nextInt(-30, 30)
                        ch1 = 350 + Random.nextInt(-30, 30)
                        ch2 = Random.nextInt(10, 40)
                        accelZ = 300
                    }
                    else -> { // 4. Lengési fázis: minden zöld
                        ch0 = Random.nextInt(0, 20)
                        ch1 = Random.nextInt(0, 20)
                        ch2 = Random.nextInt(0, 20)
                        accelZ = 450
                    }
                }
            }

            else -> {
                ch0 = 600 + Random.nextInt(-50, 50)
                ch1 = 300 + Random.nextInt(-50, 50)
                ch2 = 800 + Random.nextInt(-50, 50)
            }
        }

        // Alapzaj hozzáadása
        if (accelX == 512) accelX = 512 + Random.nextInt(-20, 20)
        if (accelY == 512) accelY = 512 + Random.nextInt(-20, 20)
        if (accelZ == 512) accelZ = 512 + Random.nextInt(-20, 20)

        val baseTime = 1778224500000L

        return SensoriaData(
            ch0 = ch0.coerceIn(0, 1023),
            ch1 = ch1.coerceIn(0, 1023),
            ch2 = ch2.coerceIn(0, 1023),
            accelX = accelX.coerceIn(0, 1023),
            accelY = accelY.coerceIn(0, 1023),
            accelZ = accelZ.coerceIn(0, 1023),
            timestamp = baseTime + (tick * 50L)
        )
    }
}