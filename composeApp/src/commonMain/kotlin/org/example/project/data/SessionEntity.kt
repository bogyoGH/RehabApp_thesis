package org.example.project.data

import kotlinx.serialization.Serializable

@Serializable
data class SessionEntity(
    val sessionId: String,
    val patientId: String,
    val json_file_path: String
)