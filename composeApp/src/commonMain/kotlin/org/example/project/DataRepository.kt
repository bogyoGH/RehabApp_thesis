package org.example.project

import kotlin.math.sin
import kotlin.random.Random

object MockDataGenerator {
    // Egy belső számláló, ami az idő múlását szimulálja, így hullámokat (lépéseket) tudunk generálni
    private var tick = 0

    /**
     * Mesterséges adatcsomagot generál a feladat neve alapján.
     */
    fun generatePacket(taskName: String = ""): SensoriaData {
        tick++

        val nameLower = taskName.lowercase()
        val isRest = nameLower.contains("rest") || nameLower.contains("pihenő")
        val isTandem = nameLower.contains("tandem")
        val isMarching = nameLower.contains("march") || nameLower.contains("helyben")
        val isToeWalking = nameLower.contains("toe") || nameLower.contains("lábujj")

        var ch0 = 0
        var ch1 = 0
        var ch2 = 0
        var accelX = 512
        var accelY = 512
        var accelZ = 512

        when {
            isRest -> {
                // NYUGALOM: Nagyon stabil nyomás (enyhén a sarkon), apróbb Parkinsonos "mikro-remegés" a gyorsulásban
                ch0 = 200 + Random.nextInt(-5, 5)
                ch1 = 200 + Random.nextInt(-5, 5)
                ch2 = 450 + Random.nextInt(-10, 10)
                accelX = 512 + Random.nextInt(-15, 15)
                accelY = 512 + Random.nextInt(-15, 15)
                accelZ = 512 + Random.nextInt(-5, 5)
            }

            isTandem -> {
                // EGYENSÚLYOZÁS: A súlypont vándorol előre-hátra (szinusz hullám), nagyobb dőlések az X/Y tengelyen
                val sway = (sin(tick * 0.5) * 100).toInt()
                ch0 = 300 + sway
                ch1 = 300 - sway
                ch2 = 400 + (sway / 2)

                accelX = 512 + (sway / 3) + Random.nextInt(-20, 20)
                accelY = 512 + Random.nextInt(-30, 30)
                accelZ = 512 + Random.nextInt(-10, 10)
            }

            isToeWalking -> {
                // LÁBUJJHEGY: A súly szinte csak az előtalpon van (ch0, ch1).
                val stepPhase = sin(tick.toDouble())
                val isStep = stepPhase > 0 // Amikor épp "leér" a lábujjhegy

                ch0 = if (isStep) 700 + Random.nextInt(-50, 50) else 50
                ch1 = if (isStep) 700 + Random.nextInt(-50, 50) else 50
                ch2 = 20 + Random.nextInt(-10, 10) // A sarok alig ér a földhöz

                // Időnként egy véletlenszerű lefagyás (FoG) szimulálása (gyors remegés, nincs lépés)
                if (Random.nextInt(100) < 5) {
                    accelX = 512 + Random.nextInt(-50, 50)
                    accelY = 512 + Random.nextInt(-50, 50)
                } else {
                    accelX = 512 + Random.nextInt(-20, 20)
                    accelY = 512 + Random.nextInt(-20, 20)
                }

                // Lépéskor a Z gyorsulás megugrik
                accelZ = if (isStep) 650 + Random.nextInt(-20, 20) else 512 + Random.nextInt(-10, 10)
            }

            isMarching -> {
                val cycle = tick % 50

                when {
                    cycle in 0..5 -> { // 1. Heel Strike (Sarokleütés)
                        ch0 = Random.nextInt(0, 50)
                        ch1 = Random.nextInt(0, 50)
                        ch2 = 800 + Random.nextInt(-50, 50) // Magas saroknyomás
                        accelZ = 700 + Random.nextInt(-20, 20) // Ütődés a talajhoz
                    }

                    cycle in 6..25 -> { // 2. Flat foot (Gördülés, teljes talp lenn)
                        ch0 = 400 + Random.nextInt(-30, 30)
                        ch1 = 400 + Random.nextInt(-30, 30)
                        ch2 = 400 + Random.nextInt(-30, 30)
                        accelZ = 512 + Random.nextInt(-10, 10) // Nincs nagy függőleges gyorsulás
                    }

                    cycle in 26..35 -> { // 3. Toe off (Elrugaszkodás lábujjhegyről)
                        ch0 = 700 + Random.nextInt(-50, 50)
                        ch1 = 700 + Random.nextInt(-50, 50)
                        ch2 = Random.nextInt(0, 50) // Sarok már a levegőben
                        accelZ = 600 + Random.nextInt(-20, 20) // Elrugaszkodás gyorsulása
                    }

                    else -> { // 4. Swing phase (Láb a levegőben)
                        ch0 = Random.nextInt(0, 20)
                        ch1 = Random.nextInt(0, 20)
                        ch2 = Random.nextInt(0, 20)
                        accelZ = 400 + Random.nextInt(-20, 20) // Láb emelése (negatív G irány)
                    }
                }
                accelX = 512 + (sin(tick * 0.1) * 50).toInt() // Oldalirányú dőlés járás közben
                accelY = 512 + Random.nextInt(-20, 20)
            }
        }

            return SensoriaData(
                // A coerceIn biztosítja, hogy az értékek ne lépjék túl a Sensoria 10-bites (0-1023) hardveres limitjét
                ch0 = ch0.coerceIn(0, 1023),
                ch1 = ch1.coerceIn(0, 1023),
                ch2 = ch2.coerceIn(0, 1023),
                accelX = accelX.coerceIn(0, 1023),
                accelY = accelY.coerceIn(0, 1023),
                accelZ = accelZ.coerceIn(0, 1023),
                taskName = taskName
            )
        }
    }