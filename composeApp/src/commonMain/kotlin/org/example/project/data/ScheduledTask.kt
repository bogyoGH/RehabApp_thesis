package org.example.project.data

sealed class ScheduledTask {
    abstract val duration: Int
    abstract val displayName: String
    abstract val exercise: Exercise // Új mező a teljes feladathoz

    data class Task(
        override val exercise: Exercise,
        override val duration: Int
    ) : ScheduledTask() {
        override val displayName: String = exercise.name
    }
}