package org.example.project.data

import org.example.project.dto.ExportedSensorData
import kotlin.time.Clock
import org.example.project.dto.SessionExport

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

    fun SessionExport.toSensoriaDataList(): List<SensoriaData> {
        return this.sensorData.map { exported ->
            val x = exported.accelNormalized["x"] ?: 0.0
            val y = exported.accelNormalized["y"] ?: 0.0
            val z = exported.accelNormalized["z"] ?: 0.0

            val rawX = ((x / 2.0) * 512.0 + 512).toInt().coerceIn(0, 1023)
            val rawY = ((y / 2.0) * 512.0 + 512).toInt().coerceIn(0, 1023)
            val rawZ = ((z / 2.0) * 512.0 + 512).toInt().coerceIn(0, 1023)

            SensoriaData(
                ch0 = exported.pressure["ch0"] ?: 0,
                ch1 = exported.pressure["ch1"] ?: 0,
                ch2 = exported.pressure["ch2"] ?: 0,
                accelX = rawX,
                accelY = rawY,
                accelZ = rawZ,
                taskName = exported.task
            )
        }
    }

    fun List<SensoriaData>.toExportedData(): List<ExportedSensorData> {
        return this.map { d ->
            ExportedSensorData(
                timestamp = d.timestamp.toString(),
                task = d.taskName,
                pressure = mapOf("ch0" to d.ch0, "ch1" to d.ch1, "ch2" to d.ch2),
                accelNormalized = d.GetNormalizedAcceleration()
            )
        }
    }
}