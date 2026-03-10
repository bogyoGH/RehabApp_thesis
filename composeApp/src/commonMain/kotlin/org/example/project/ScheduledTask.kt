package org.example.project

sealed class ScheduledTask {
    abstract val duration: Int
    abstract val displayName: String // Új közös mező

    data class Preset(
        val type: RehabTaskType,
        override val duration: Int
    ) : ScheduledTask() {
        override val displayName: String = type.displayName
    }

    data class Custom(
        val name: String,
        override val duration: Int
    ) : ScheduledTask() {
        override val displayName: String = name
    }
}