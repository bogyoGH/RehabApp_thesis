package org.example.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*

class SessionViewModel {
    // UI Állapotok
    var currentTaskName by mutableStateOf("Ready to start")
    var timerText by mutableStateOf("00")
    var progressValue by mutableStateOf(0f)
    var isRunning by mutableStateOf(false)
    var stabilityScore by mutableStateOf("Stability: --")

    private val sessionData = mutableListOf<SensoriaData>() //

    // ViewModel szintű Scope a biztonságos szálkezeléshez
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var sessionJob: Job? = null

    fun startSession(tasks: List<ScheduledTask>) {
        if (isRunning) return // Megakadályozzuk a többszörös indítást

        isRunning = true
        sessionData.clear()

        sessionJob = viewModelScope.launch {
            try {
                tasks.forEach { task ->
                    runTask(task)
                }
                finishSession()
            } catch (e: CancellationException) {
                // Ha leállítják a coroutine-t, itt kezeljük
                currentTaskName = "Session Stopped"
            }
        }
    }

    private suspend fun runTask(task: ScheduledTask) {
        currentTaskName = task.displayName // A 1. lépésben bevezetett közös nevet használjuk

        for (second in task.duration downTo 0) {
            timerText = second.toString().padStart(2, '0') //
            progressValue = (task.duration - second).toFloat() / task.duration //

            // Adatgenerálás és feldolgozás
            val mockPacket = MockDataGenerator.generatePacket(currentTaskName)
            processNewData(mockPacket)

            delay(1000)
        }
    }

    private fun processNewData(data: SensoriaData) {
        sessionData.add(data) //
        // A számítást az új kalkulátor végzi
        val score = StabilityCalculator.calculate(data)
        stabilityScore = "Stability: $score/100"
    }

    fun stopSession() {
        sessionJob?.cancel() // Megállítja a futó mérést
        isRunning = false
    }

    private fun finishSession() {
        isRunning = false
        currentTaskName = "All Tasks Completed!" //
    }
}