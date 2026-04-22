package org.example.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.example.project.dto.ExportedSensorData
import org.example.project.dto.SessionExport
import org.example.project.dto.SessionInfo
import org.example.project.dto.ExportedTasks
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class SessionViewModel {
    // UI Állapotok
    var currentTaskName by mutableStateOf("Ready to start")
    var timerText by mutableStateOf("00")
    var progressValue by mutableStateOf(0f)
    var isRunning by mutableStateOf(false)
    var sessionCompleted by mutableStateOf(false)

    var stabilityScore by mutableStateOf("Stability: --")

    var patientId by mutableStateOf("PT-001")

    private val sessionData = mutableListOf<SensoriaData>() //

    // ViewModel szintű Scope a biztonságos szálkezeléshez
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var sessionJob: Job? = null

    private var scheduledTasksForExport = listOf<ScheduledTask>()
    var saveStatus by mutableStateOf("")

    var currentVideoName by mutableStateOf<String?>("")
    var currentInstruction by mutableStateOf("")
    var currentFallback by mutableStateOf("")

    var bluetoothStatus by mutableStateOf("Nincs csatlakoztatva")
    var isBluetoothConnected by mutableStateOf(false)
    var isScanning by mutableStateOf(false)
    fun startSession(tasks: List<ScheduledTask>) {
        if (isRunning) return // Megakadályozzuk a többszörös indítást
        sessionCompleted = false
        scheduledTasksForExport = tasks
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
        if(task is ScheduledTask.Preset) {
            currentVideoName = when {
                task is ScheduledTask.Preset && task.type.videoName.isNotBlank() -> task.type.videoName
                else -> null  // Ha nincs videó, ne próbálja betölteni
            }
            currentInstruction = task.type.instruction
            currentFallback = task.type.fallback

        }else {
            currentVideoName = "buffer"
            currentInstruction = "Kövesse a terapeuta utasításait a gyakorlat végrehajtásához."
            currentFallback = ""
        }
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
        sessionCompleted = false
    }

    private fun finishSession() {
        isRunning = false
        sessionCompleted = true
        currentTaskName = "All Tasks Completed!" //
        saveSessionToJson()
    }

    private fun saveSessionToJson() {
        val now = Clock.System.now()
        val localDateTime = Instant.fromEpochMilliseconds(now.toEpochMilliseconds())
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val sessionId = "session_${localDateTime.date.toString().replace("-", "")}_" +
                "${localDateTime.hour}${localDateTime.minute}${localDateTime.second}"

        val export = SessionExport(
            sessionInfo = SessionInfo(
                sessionId = sessionId,
                patientId = patientId,
                timeStamp = localDateTime.toString(),
                mode = "Offline",
                bluetoothEnabled = false,
                tasks = scheduledTasksForExport.map { ExportedTasks(it.displayName, it.duration) }
            ),
            sensorData = sessionData.map { d ->
                ExportedSensorData(
                    timestamp = d.timestamp.toString(),
                    task = d.taskName,
                    pressure = mapOf("ch0" to d.ch0, "ch1" to d.ch1, "ch2" to d.ch2),
                    accelNormalized = d.GetNormalizedAcceleration()
                )
            }
        )

        val jsonString = Json { prettyPrint = true }.encodeToString(export)

        runCatching {
            FileStorage.saveText("$sessionId.json", jsonString)
        }.onSuccess { savedPath ->
            saveStatus = "Sikeres mentés: $savedPath"
        }.onFailure { e ->
            saveStatus = "Mentési hiba: ${e.message ?: "ismeretlen hiba"}"
        }
    }

    fun connectToBluetooth() {
        if (isBluetoothConnected || isScanning) return

        isScanning = true
        bluetoothStatus = "Keresés..."

        viewModelScope.launch {
            try {
                // A SensoriaBluetoothManager példányosítása
                val manager = `SensoriaBluetoothManager`(this)

                // A connectAndListen() elindítása
                manager.connectAndListen().collect { data ->
                    if (!isBluetoothConnected) {
                        isBluetoothConnected = true
                        isScanning = false
                        bluetoothStatus = "Csatlakozva"
                    }
                    // Itt akár frissítheted a stabilitási pontszámot is élőben
                    val score = StabilityCalculator.calculate(data)
                    stabilityScore = "Stability: $score/100"
                }
            } catch (e: Exception) {
                isScanning = false
                isBluetoothConnected = false
                bluetoothStatus = "Hiba: ${e.message ?: "Ismeretlen hiba"}"
            }
        }
    }
}

