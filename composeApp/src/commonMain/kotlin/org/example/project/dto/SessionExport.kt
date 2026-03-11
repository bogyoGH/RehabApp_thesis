package org.example.project.dto

import kotlinx.serialization.Serializable


@Serializable
data class SessionExport (
    val sessionInfo : SessionInfo,
    val sensorData : List<ExportedSensorData>

)