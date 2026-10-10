package org.example.project.data

import kotlinx.serialization.Serializable

@Serializable
data class SensorSample(
    val t: Int,
    val host_time_ms: Double? = null,
    val pressure: Pressure,
    val imu: IMU,
    val magnetometer: Magnetometer,
    val msgType: Int,
    val context: Int,
    val gs: Int,
    val s: Int,
    val g: Int,
    val hz: Int
)

@Serializable
data class Pressure(
    val heel: Double,
    val met_1: Double,
    val met_5: Double
)

@Serializable
data class IMU(
    val ax: Double,
    val ay: Double,
    val az: Double,
    val gx: Double,
    val gy: Double,
    val gz: Double
)

@Serializable
data class Magnetometer(
    val mx: Double,
    val my: Double,
    val mz: Double
)