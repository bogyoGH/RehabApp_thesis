package org.example.project.data

import kotlinx.serialization.Serializable

@Serializable
data class SessionEntity(
    val session_id: String,
    val patient_id: String,
    val json_file_path: String,
    val is_deleted: Boolean = false
)