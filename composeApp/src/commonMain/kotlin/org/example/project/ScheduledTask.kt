package org.example.project

sealed class ScheduledTask {
    abstract val duration: Int // Minden feladatnak van ideje

    // Gyári feladat (a listából választva)
    data class Preset(
        val type: RehabTaskType,
        override val duration: Int
    ) : ScheduledTask()

    // Egyedi feladat (kézzel beírva)
    data class Custom(
        val name: String,
        override val duration: Int
    ) : ScheduledTask()
}