package org.example.project.dto

import kotlinx.serialization.Serializable
import org.example.project.data.SensorSample

@Serializable
data class MeasurementDocument(
    val schema_version: String = "1.0",
    val measurement_id: String,
    val athlete_id: String?,
    val athlete_name: String?,
    val exercise_id: String?,
    val exercise_name: String?,
    val start_time: String,
    val end_time: String,
    val duration_ms: Int,
    val duration_s: Double,
    val sensor_value_mode: String = "raw_relative_unitless",
    val sensors: Sensors,
    val video: Video? = null
)

@Serializable
data class Sensors(
    val left: Side,
    val right: Side
)

@Serializable
data class Side(
    val device_id: String? = null,
    val sample_count: Int,
    val effective_rate_samples_per_s: Double,
    val data: List<SensorSample>
)

@Serializable
data class Video(
    val file_name: String,
    val camera_position: String = "back",
    val timing_reference: String = "android_host" // Androidra módosítva
)