package org.example.project.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SessionEntity(
    val session_id: String,
    val patient_id: String,
    val session_data: JsonObject,
    val is_deleted: Boolean = false,
    val video_url: String? = null,
    val analysis_status: String
)