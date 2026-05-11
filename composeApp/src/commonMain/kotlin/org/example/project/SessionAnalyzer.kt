package org.example.project

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object SessionAnalyzer {
    /**
     * 1. LÉPÉS: SZEGMENTÁLÁS
     * A bejövő, teljes adathalmazt szétbontja feladatok szerint.
     */
    fun segmentDataByTask(data: List<SensoriaData>): Map<String, List<SensoriaData>> {
        return data.groupBy { it.taskName }
    }

    /**
     * Segédfüggvény: Különválasztja az aktív feladatokat a pihenőktől.
     */
    fun separateTasks(segmentedData: Map<String, List<SensoriaData>>): Pair<Map<String, List<SensoriaData>>, Map<String, List<SensoriaData>>> {
        val activeTasks = mutableMapOf<String, List<SensoriaData>>()
        val restTasks = mutableMapOf<String, List<SensoriaData>>()

        for ((taskName, data) in segmentedData) {
            if (taskName == "Ready to start" || taskName == "Session Stopped" || taskName == "All Tasks Completed!") {
                continue
            }

            if (taskName == RehabTaskType.REST.displayName) {
                restTasks[taskName] = data
            } else {
                activeTasks[taskName] = data
            }
        }

        return Pair(activeTasks, restTasks)
    }

    fun analyzeStaticTask(dataPoints: List<SensoriaData>): StaticAnalysisResult {
        if (dataPoints.isEmpty()) return StaticAnalysisResult(0.0, false, 0.0, 0.0)

        val apSwayList = mutableListOf<Double>() // Előre-hátra dőlés
        val mlSwayList = mutableListOf<Double>() // Jobbra-balra dőlés
        val accelerations = mutableListOf<Double>()
        var totalHeelRatio = 0.0

        for (data in dataPoints) {
            val forefoot = data.ch0 + data.ch1
            val heel = data.ch2
            val totalPressure = forefoot + heel

            if (totalPressure > 0) {
                val apRatio = heel.toDouble() / totalPressure
                apSwayList.add(apRatio)
                totalHeelRatio += apRatio

                // Jobb-bal egyensúly (ch1 vs ch0)
                if (forefoot > 0) {
                    mlSwayList.add((data.ch1 - data.ch0).toDouble() / forefoot)
                }
            }

            // Gyorsulás magnitúdója (G-erő vektor hossza)
            val accelMag = sqrt((data.accelX * data.accelX + data.accelY * data.accelY + data.accelZ * data.accelZ).toDouble())
            accelerations.add(accelMag)
        }

        val avgAp = apSwayList.average()
        val avgMl = if (mlSwayList.isNotEmpty()) mlSwayList.average() else 0.0

        // Kombinált 2D kilengés (Sway Area / Variancia)
        val apVariance = apSwayList.map { (it - avgAp).pow(2.0) }.average()
        val mlVariance = mlSwayList.map { (it - avgMl).pow(2.0) }.average()
        val combinedSwayVariability = (apVariance + mlVariance) * 1000

        // Remegés: A variancia jobb mérőszám, ha kivonjuk belőle a gravitációt
        val avgAccel = accelerations.average()
        val tremorIndex = accelerations.map { (it - avgAccel).pow(2.0) }.average()

        val backwardFallRisk = (totalHeelRatio / dataPoints.size) > 0.65

        return StaticAnalysisResult(combinedSwayVariability, backwardFallRisk, (totalHeelRatio / dataPoints.size) * 100, tremorIndex)
    }

    /**
     * B. DINAMIKUS FELADATOK ELEMZÉSE (Helyben járás, Lábujjhegyen járás)
     */
    fun analyzeDynamicTask(dataPoints: List<SensoriaData>): DynamicAnalysisResult {
        if (dataPoints.size < 10) return DynamicAnalysisResult(0, 0.0, 0.0, 0.0)

        var steps = 0
        var totalHeelStrikePressure = 0.0
        var totalStepPressure = 0.0

        val zThreshold = 550.0 // Küszöbérték a sarokleütéshez
        var isAboveThreshold = false
        var samplesSinceLastStep = 0
        val minSamplesBetweenSteps = 15 // Holt idő zaj ellen (Debounce)

        val energyFirstHalf = mutableListOf<Double>()
        val energySecondHalf = mutableListOf<Double>()
        val midPoint = dataPoints.size / 2

        dataPoints.forEachIndexed { index, data ->
            val zAccel = data.accelZ.toDouble()
            samplesSinceLastStep++

            // Lépés detektálása
            if (zAccel > zThreshold && !isAboveThreshold && samplesSinceLastStep > minSamplesBetweenSteps) {
                isAboveThreshold = true
                steps++
                samplesSinceLastStep = 0 // Számláló nullázása

                // Sarokleütés minősége a lépés pillanatában
                val forefoot = data.ch0 + data.ch1
                val heel = data.ch2
                totalHeelStrikePressure += heel
                totalStepPressure += (forefoot + heel)
            } else if (zAccel < zThreshold) {
                isAboveThreshold = false
            }

            // Energiaszámítás (gyorsulás magnitúdója, a 3D vektor hossza)
            val mag = sqrt((data.accelX * data.accelX + data.accelY * data.accelY + data.accelZ * data.accelZ).toDouble())
            if (index < midPoint) {
                energyFirstHalf.add(mag)
            } else {
                energySecondHalf.add(mag)
            }
        }

        val heelStrikeQuality = if (totalStepPressure > 0) (totalHeelStrikePressure / totalStepPressure) * 100 else 0.0

        // Védekezés az üres listák vagy nullával osztás ellen
        val avgEnergyFirst = if (energyFirstHalf.isNotEmpty()) energyFirstHalf.average() else 0.0
        val avgEnergySecond = if (energySecondHalf.isNotEmpty()) energySecondHalf.average() else 0.0
        val amplitudeDropoff = if (avgEnergyFirst > 0) ((avgEnergyFirst - avgEnergySecond) / avgEnergyFirst) * 100 else 0.0

        // Megjegyzés: A kadencia paramétert itt most a lépésszámmal töltjük fel,
        // de ha a jövőben kiszámolod az időtartamot percekben, ide kerülhet a valódi Lépés/Perc!
        return DynamicAnalysisResult(steps, steps.toDouble(), heelStrikeQuality, amplitudeDropoff)
    }

    /**
     * C. KRITIKUS ESEMÉNYEK: Lefagyás (Freezing of Gait) detektálása
     */
    fun detectFreezingOfGait(dataPoints: List<SensoriaData>): Int {
        // Ha nincs elég adat egy ablakhoz, nem elemzünk
        if (dataPoints.size < 50) return 0

        var fogEvents = 0
        val windowSize = 50 // Ablak mérete (kb. 1-2 mp adat a mintavételezéstől függően)
        var i = 0

        while (i <= dataPoints.size - windowSize) {
            val window = dataPoints.subList(i, i + windowSize)

            // Gyorsulások és nyomások kigyűjtése az aktuális ablakban
            val accelsX = window.map { it.accelX.toDouble() }
            val accelsY = window.map { it.accelY.toDouble() }
            val totalPressures = window.map { (it.ch0 + it.ch1 + it.ch2).toDouble() }

            // Átlagok számítása a varianciához
            val avgAccelX = accelsX.average()
            val avgAccelY = accelsY.average()

            // Varianciák (mennyire "ugrál" a láb vízszintes síkban - remegés)
            val varianceX = accelsX.map { (it - avgAccelX).pow(2.0) }.average()
            val varianceY = accelsY.map { (it - avgAccelY).pow(2.0) }.average()

            // Nyomáskülönbség (felemeli-e a lábát, vagy a földhöz van "ragadva")
            val maxPressure = totalPressures.maxOrNull() ?: 0.0
            val minPressure = totalPressures.minOrNull() ?: 0.0
            val pressureDelta = maxPressure - minPressure

            // Feltételrendszer:
            // 1. Magas X és Y variancia = Remeg a láb (Parkinson tremor)
            // 2. Alacsony delta nyomás = Nem mozdul a súlypont, nincs lépés
            if (varianceX > 1500.0 && varianceY > 1500.0 && pressureDelta < 100) {
                fogEvents++
                // Ha találtunk egy lefagyást, átugorjuk a teljes ablakot,
                // hogy ne számoljuk a másodperc töredékével később ugyanazt az eseményt duplán.
                i += windowSize
            } else {
                // Ha nincs esemény, csúszunk tovább egy picit (átfedéssel vizsgálunk, így pontosabb)
                i += 10
            }
        }

        return fogEvents
    }
}