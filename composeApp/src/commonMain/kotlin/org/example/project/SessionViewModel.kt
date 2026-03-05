package org.example.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*

class SessionViewModel {
    // UI Állapotok (A Python Property-k megfelelői)
    var currentTaskName by mutableStateOf("Ready to start")
    var timerText by mutableStateOf("00")
    var progressValue by mutableStateOf(0f)
    var isRunning by mutableStateOf(false)
    var stabilityScore by mutableStateOf("Stability: --")

    // A mérés alatt gyűjtött adatok listája [cite: 21]
    private val sessionData = mutableListOf<SensoriaData>()

    // A mérésért felelős Coroutine (a Python threading helyett) [cite: 11]
    private var sessionJob: Job? = null

    /**
     * Elindítja a mérési folyamatot a megadott feladatlistával.
     * Ez felel meg a Python start_session metódusának.
     */
    fun startSession(tasks: List<ScheduledTask>) {
        isRunning = true
        sessionJob = CoroutineScope(Dispatchers.Default).launch {
            tasks.forEachIndexed { index, task ->
                runTask(task, index + 1, tasks.size)
            }
            finishSession()
        }
    }

    private suspend fun runTask(task: ScheduledTask, currentIdx: Int, totalTasks: Int) {
        // Feladat nevének beállítása (Preset vs Custom hibrid kezelés)
        currentTaskName = when (task) {
            is ScheduledTask.Preset -> task.type.displayName
            is ScheduledTask.Custom -> task.name
        }

        // Visszaszámlálás logikája (Python _task_timer_tick megfelelője) [cite: 11]
        for (second in task.duration downTo 0) {
            timerText = second.toString().padStart(2, '0')
            progressValue = (task.duration - second).toFloat() / task.duration

            // Adatgyűjtés szimulálása (Mock Data) [cite: 11]
            val mockPacket = MockDataGenerator.generatePacket(currentTaskName)
            processNewData(mockPacket)

            delay(1000) // 1 másodperces várakozás
        }
    }

    private fun processNewData(data: SensoriaData) {
        sessionData.add(data)
        // Stabilitás számítása a Python logikája alapján [cite: 11]
        val accel = data.GetNormalizedAcceleration()
        val magnitude = kotlin.math.sqrt(accel["x"]!! * accel["x"]!! + accel["y"]!! * accel["y"]!! + accel["z"]!! * accel["z"]!!)
        val score = (100 - (magnitude * 50)).coerceIn(0.0, 100.0)
    // Matematikai kerekítés 1 tizedesjegyre (KMP kompatibilis):
        val roundedScore = (score * 10).toInt() / 10.0
        stabilityScore = "Stability: $roundedScore/100"    }

    fun stopSession() {
        sessionJob?.cancel()
        isRunning = false
        currentTaskName = "Session Stopped"
    }

    private fun finishSession() {
        isRunning = false
        currentTaskName = "All Tasks Completed!"
        // Itt hívhatod majd meg a JSON mentést, mint a manager.py-ban [cite: 21]
    }
}
