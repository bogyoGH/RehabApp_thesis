package org.example.project.data

sealed class ScheduledTask {
    abstract val duration: Int
    abstract val displayName: String // Új közös mező

    data class Preset(
        val type: RehabTaskType,
        override val duration: Int
    ) : ScheduledTask() {
        override val displayName: String = type.displayName
    }


}