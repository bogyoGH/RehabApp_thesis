package org.example.project.dto
import kotlinx.serialization.Serializable

@Serializable
data class ExportedTasks(
    val taskName : String,
    val taskDuration : Int
)
