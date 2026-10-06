package org.example.project.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.example.project.MockDataGenerator
import org.example.project.analysis.FullSessionResult
import org.example.project.bluetooth.SensoriaBluetoothManager
import org.example.project.data.Exercise
import org.example.project.data.ScheduledTask
import org.example.project.data.SensoriaData
import org.example.project.dto.ExportedTasks
import org.example.project.dto.SessionExport
import org.example.project.dto.SessionInfo
import org.example.project.dto.toExportedData
import org.example.project.network.ExerciseRepository
import org.example.project.network.SessionRepository
import org.example.project.network.SupabaseManager

enum class ViewPreference {
    SELF, VIDEO, BOTH
}

class SessionViewModel {

    var uiState by mutableStateOf(SessionUiState())
        private set

    var sessionResult by mutableStateOf<FullSessionResult?>(null)
        private set

    private val sessionData = mutableListOf<SensoriaData>()
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var sessionJob: Job? = null
    private var bleJob: Job? = null
    private var bluetoothManager: SensoriaBluetoothManager? = null

    var mySessions by mutableStateOf<List<SessionExport>>(emptyList())
        private set

    var availableExercises by mutableStateOf<List<Exercise>>(emptyList())
        private set

    var exerciseToEdit by mutableStateOf<Exercise?>(null)
        private set

    // A kiválasztott feladat tárolása
    var selectedTaskToRun by mutableStateOf<ScheduledTask?>(null)
        private set

    var deletedSessions by mutableStateOf<List<SessionExport>>(emptyList())
        private set

    fun loadExercisesFromCloud() {
        viewModelScope.launch {
            val result = ExerciseRepository.getExercises()
            result.onSuccess { exercises ->
                availableExercises = exercises
            }.onFailure { e ->
                println("Hiba a gyakorlatok betöltésekor: ${e.message}")
            }
        }
    }

    // ÚJ: Egyetlen feladat indítása időtartammal
    fun startSingleTaskSession(exercise: Exercise, duration: Int) {
        if (uiState.isRunning) return

        selectedTaskToRun = ScheduledTask.Task(exercise, duration)
        uiState = uiState.copy(sessionCompleted = false)
        sessionData.clear()

        // Ha nincs videó, automatikusan a kameraképet használjuk (nincs dialógus)
        if (exercise.video_url.isNullOrBlank() || exercise.video_url == "buffer") {
            confirmViewModeAndStart(ViewPreference.SELF)
        } else {
            uiState = uiState.copy(showViewModeDialog = true)
        }
    }

    fun confirmViewModeAndStart(preference: ViewPreference) {
        uiState = uiState.copy(
            viewPreference = preference, showViewModeDialog = false, isRunning = true
        )

        sessionJob = viewModelScope.launch {
            try {
                selectedTaskToRun?.let { runTask(it) }
                finishSession()
            } catch (e: CancellationException) {
                uiState = uiState.copy(
                    isRunning = false,
                    currentTaskName = "Mérés megszakítva",
                    showSaveDialog = true
                )
            }
        }
    }

    private suspend fun runTask(task: ScheduledTask) {
        uiState = uiState.copy(
            currentTaskName = task.displayName,
            currentVideoName = task.exercise.video_url ?: "buffer",
            // JAVÍTÁS: Az Execution screen-en mostantól a DESCRIPTION (leírás) jelenik meg!
            currentInstruction = task.exercise.description ?: "Nincs megadva leírás.",
            currentFallback = ""
        )

        if (!task.displayName.contains("Pihenő", ignoreCase = true)) {
            uiState = uiState.copy(isPreviewPhase = true)
            runCountdown(1, "Felkészülés: ")
        }
        uiState = uiState.copy(isPreviewPhase = false)
        runCountdown(task.duration, "", isMainTask = true)
    }

    private suspend fun runCountdown(seconds: Int, prefix: String, isMainTask: Boolean = false) {
        var sec = seconds

        while (sec > 0) {
            while (uiState.isPaused) {
                delay(500)
            }

            uiState = uiState.copy(
                timerText = "$prefix${sec.toString().padStart(2, '0')}",
                progressValue = (seconds - sec).toFloat() / seconds
            )

            if (isMainTask && !uiState.isBluetoothEnabled) {
                repeat(25) { processNewData(MockDataGenerator.generatePacket(uiState.currentTaskName)) }
            }

            delay(1000)
            sec--
        }
    }

    private fun processNewData(data: SensoriaData) {
        if (!uiState.isRunning || uiState.isPreviewPhase) return
        sessionData.add(data.copy(taskName = uiState.currentTaskName))
        uiState = uiState.copy(stabilityScore = "Adatok rögzítése folyamatban...")
    }

    fun stopSession() {
        sessionJob?.cancel()
        uiState = uiState.copy(isRunning = false, sessionCompleted = false)
    }

    private fun finishSession() {
        uiState = uiState.copy(
            isRunning = false,
            currentTaskName = "Mérés Befejeződött!",
            showSaveDialog = true
        )
        sessionResult = FullSessionResult()
    }

    fun confirmSaveAndFinish() {
        uiState = uiState.copy(
            showSaveDialog = false,
            saveStatus = "Mentés előkészítése..."
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                saveSessionToJson()
                uiState = uiState.copy(sessionCompleted = true)
            } catch (e: Exception) {
                uiState = uiState.copy(saveStatus = "Hiba történt a mentés során: ${e.message}")
            }
        }
    }

    fun discardSession() {
        sessionData.clear()
        uiState = uiState.copy(
            showSaveDialog = false,
            sessionCompleted = true,
            currentTaskName = "Mérés elvetve"
        )
    }

    fun requestStop() {
        sessionJob?.cancel(CancellationException("USER_STOP"))
    }

    fun pauseSession() {
        if (uiState.isRunning && !uiState.isPaused) {
            uiState = uiState.copy(isPaused = true, currentInstruction = "Mérés szüneteltetve")
        }
    }

    fun resumeSession() {
        if (uiState.isRunning && uiState.isPaused) {
            // Szünet után visszatöltjük a description-t
            uiState = uiState.copy(isPaused = false, currentInstruction = selectedTaskToRun?.exercise?.description ?: "Nincs megadva leírás.")
        }
    }

    private suspend fun saveSessionToJson() {
        val now = Clock.System.now()
        val localDateTime =
            Instant.fromEpochMilliseconds(now.toEpochMilliseconds()).toLocalDateTime(TimeZone.currentSystemDefault())
        val timeString = "${localDateTime.hour.toString().padStart(2, '0')}" +
                "${localDateTime.minute.toString().padStart(2, '0')}" +
                "${localDateTime.second.toString().padStart(2, '0')}"
        val sessionId = "session_${uiState.virtualPatientId}-${localDateTime.date.toString().replace("-", "")}_$timeString"

        val exportTasks = listOfNotNull(selectedTaskToRun?.let { ExportedTasks(it.displayName, it.duration) })

        val export = SessionExport(
            sessionInfo = SessionInfo(
                sessionId = sessionId,
                patientId = uiState.virtualPatientId,
                timeStamp = localDateTime.toString(),
                mode = if (uiState.isBluetoothEnabled) "Online" else "Offline",
                bluetoothEnabled = uiState.isBluetoothEnabled,
                tasks = exportTasks
            ),
            sensorData = sessionData.toExportedData()
        )

        uiState = uiState.copy(saveStatus = "Mentés folyamatban...")
        val result = SessionRepository.saveSession(sessionId, uiState.patientId, export)

        result.onSuccess { fileName ->
            uiState = uiState.copy(saveStatus = "Sikeres mentés: $fileName")
        }.onFailure { e ->
            uiState = uiState.copy(saveStatus = "Mentési hiba: ${e.message}")
        }
    }

    fun toggleBluetooth(enabled: Boolean) {
        uiState = uiState.copy(isBluetoothEnabled = enabled)

        if (enabled) {
            startBluetoothListening()
        } else {
            uiState = uiState.copy(bluetoothStatus = "Leválasztva")
            bleJob?.cancel()
        }
    }

    private fun startBluetoothListening() {
        uiState = uiState.copy(dataPacketCount = 0, bluetoothStatus = "Keresés...")

        bleJob = viewModelScope.launch {
            try {
                bluetoothManager = SensoriaBluetoothManager(this)
                bluetoothManager?.connectAndListen(
                    onStatusChange = { status ->
                        if (!uiState.bluetoothStatus.startsWith("📡 Adat fogadva")) {
                            uiState = uiState.copy(bluetoothStatus = status)
                        }
                    })?.collect { data ->
                    if (uiState.isBluetoothEnabled) {
                        val newCount = uiState.dataPacketCount + 1
                        uiState = uiState.copy(
                            dataPacketCount = newCount, bluetoothStatus = "📡 Adat fogadva ($newCount csomag)"
                        )
                        processNewData(data)
                    }
                }
            } catch (e: Exception) {
                println("Bluetooth hiba: ${e.message}")
            } finally {
                cleanupBluetoothConnection()
            }
        }
    }

    private suspend fun cleanupBluetoothConnection() {
        if (uiState.isBluetoothEnabled && uiState.isRunning) {
            uiState = uiState.copy(
                isPaused = true,
                isBluetoothEnabled = false, bluetoothStatus = "❌ Kapcsolat megszakadt (Eszköz lecsatlakozott)"
            )
        }
        withContext(NonCancellable) {
            bluetoothManager?.disconnect()
        }
        bluetoothManager = null
    }

    fun resetSessionState() {
        uiState = uiState.copy(
            sessionCompleted = false, currentTaskName = "Ready to start"
        )
        sessionResult = null
    }

    fun updatePatientIds(dbUuid: String, virtualId: String) {
        uiState = uiState.copy(
            patientId = dbUuid,
            virtualPatientId = virtualId
        )
    }

    fun dismissViewModeDialog() {
        uiState = uiState.copy(showViewModeDialog = false)
    }

    fun initializeUser() {
        val currentUser = SupabaseManager.supabaseClient.auth.currentUserOrNull()
        if (currentUser != null) {
            uiState = uiState.copy(patientId = currentUser.id)
            refreshSessions()
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            uiState = uiState.copy(currentTaskName = "Adatok szinkronizálása...")

            val result = SessionRepository.getMySessions()

            result.onSuccess { sessions ->
                mySessions = sessions
                uiState = uiState.copy(currentTaskName = "Kész. ${sessions.size} mérés betöltve.")
            }.onFailure { e ->
                uiState = uiState.copy(currentTaskName = "Hiba a letöltéskor: ${e.message}")
            }
        }
    }

    fun openCloudSession(sessionExport: SessionExport) {
        sessionResult = FullSessionResult(message = "A betöltött munkamenet adatai készen állnak a szerver oldali elemzésre.")
        uiState = uiState.copy(
            patientId = sessionExport.sessionInfo.patientId,
            currentTaskName = "Betöltött munkamenet",
            sessionCompleted = true
        )
    }

    fun setEditingExercise(exercise: Exercise?) {
        exerciseToEdit = exercise
    }

    fun refreshDeletedSessions() {
        viewModelScope.launch {
            val result = SessionRepository.getDeletedSessions()
            result.onSuccess { sessions ->
                deletedSessions = sessions
            }
        }
    }

    fun restoreCloudSession(sessionId: String) {
        viewModelScope.launch {
            uiState = uiState.copy(currentTaskName = "Visszaállítás folyamatban...")
            val result = SessionRepository.restoreSession(sessionId)
            if (result.isSuccess) {
                refreshSessions()
                refreshDeletedSessions()
                uiState = uiState.copy(currentTaskName = "Mérés sikeresen visszaállítva.")
            } else {
                uiState = uiState.copy(currentTaskName = "Hiba a visszaállítás során.")
            }
        }
    }
}