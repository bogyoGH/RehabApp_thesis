//TODO:
/*:Ha működik a bluetooth törölhető
package org.example.project.dto

import kotlinx.serialization.Serializable
import org.example.project.data.SensoriaData


@Serializable
data class SessionExport (
    val sessionInfo : SessionInfo,
    val sensorData : List<ExportedSensorData>

)
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

    */
/**
     * Átalakítja a belső SensoriaData listát a felhőbe/JSON-be szánt ExportedSensorData listává.
     *//*

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
*/
