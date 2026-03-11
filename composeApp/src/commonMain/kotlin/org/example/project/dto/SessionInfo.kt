package org.example.project.dto
import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val sessionId : String,
    val patientId : String,
    val timeStamp : String,
    val mode : String,
    val bluetoothEnabled : Boolean,
    val tasks : List<ExportedTasks>
)
