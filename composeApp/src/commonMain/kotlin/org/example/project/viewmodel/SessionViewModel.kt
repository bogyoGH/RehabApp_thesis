package org.example.project.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.example.project.MockDataGenerator
import org.example.project.analysis.FullSessionResult
import org.example.project.bluetooth.SensoriaBluetoothManager
import org.example.project.bluetooth.SockSide
import org.example.project.data.Exercise
import org.example.project.data.ScheduledTask
import org.example.project.data.FileStorage
import org.example.project.data.SensorSample
import org.example.project.dto.MeasurementDocument
import org.example.project.network.ExerciseRepository
import org.example.project.network.SessionRepository
import org.example.project.network.SupabaseManager
import org.example.project.data.BiomechanicsResult

enum class ViewPreference {
    SELF, VIDEO, BOTH
}

class SessionViewModel {

    var uiState by mutableStateOf(SessionUiState())
        private set

    var sessionResult by mutableStateOf<FullSessionResult?>(null)
        private set

    var allBiomechanicsResults by mutableStateOf<List<org.example.project.data.BiomechanicsResult>>(emptyList())
        private set

    var sockAnalysisResult by mutableStateOf<org.example.project.analysis.SockAnalysisResult?>(null)
        private set

    // ÚJ: Két külön adatlista a két zokninak
    private val leftSessionData = mutableListOf<SensorSample>()
    private val rightSessionData = mutableListOf<SensorSample>()

    private var sessionStartTimeStr: String = ""
    private var sessionStartTimeMs: Long = 0
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var sessionJob: Job? = null
    private var bleJob: Job? = null
    private var bluetoothManager: SensoriaBluetoothManager? = null


    var mySessions by mutableStateOf<List<MeasurementDocument>>(emptyList())
        private set
    var biomechanicsResult by mutableStateOf<BiomechanicsResult?>(null)
        private set //
    var deletedSessions by mutableStateOf<List<MeasurementDocument>>(emptyList())
        private set

    var availableExercises by mutableStateOf<List<Exercise>>(emptyList())
        private set

    var exerciseToEdit by mutableStateOf<Exercise?>(null)
        private set

    var selectedTaskToRun by mutableStateOf<ScheduledTask?>(null)
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

    fun startSingleTaskSession(exercise: Exercise, duration: Int) {
        if (uiState.isRunning) return

        selectedTaskToRun = ScheduledTask.Task(exercise, duration)


        leftSessionData.clear()
        rightSessionData.clear()

        uiState = uiState.copy(
            sessionCompleted = false,
            showViewModeDialog = true,
            saveStatus = "",
            recordedVideoPath = null
        )
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
            currentInstruction = task.exercise.description ?: "Nincs megadva leírás.",
            currentFallback = ""
        )

        if (!task.displayName.contains("Pihenő", ignoreCase = true)) {
            uiState = uiState.copy(isPreviewPhase = true)
            runCountdown(5, "Felkészülés: ")
        }
        uiState = uiState.copy(isPreviewPhase = false)


        val now = Clock.System.now()
        val localDateTime =
            Instant.fromEpochMilliseconds(now.toEpochMilliseconds()).toLocalDateTime(TimeZone.currentSystemDefault())
        sessionStartTimeStr = localDateTime.toString()
        sessionStartTimeMs = Clock.System.now().toEpochMilliseconds()

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
                repeat(25) {
                    processNewData(
                        SockSide.LEFT,
                        MockDataGenerator.generatePacket(uiState.currentTaskName, SockSide.LEFT)
                    )
                    processNewData(
                        SockSide.RIGHT,
                        MockDataGenerator.generatePacket(uiState.currentTaskName, SockSide.RIGHT)
                    )
                }
            }

            delay(1000)
            sec--
        }
    }

    private fun processNewData(side: SockSide, data: SensorSample) {
        if (!uiState.isRunning || uiState.isPreviewPhase) return

        if (side == SockSide.LEFT) {
            leftSessionData.add(data)
        } else {
            rightSessionData.add(data)
        }
        uiState =
            uiState.copy(stabilityScore = "Adatok rögzítve (L: ${leftSessionData.size}, R: ${rightSessionData.size})")
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

        leftSessionData.clear()
        rightSessionData.clear()

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
            uiState = uiState.copy(
                isPaused = false,
                currentInstruction = selectedTaskToRun?.exercise?.description ?: "Nincs megadva leírás."
            )
        }
    }


    private suspend fun saveSessionToJson() {
        val now = Clock.System.now()
        val localDateTime =
            Instant.fromEpochMilliseconds(now.toEpochMilliseconds()).toLocalDateTime(TimeZone.currentSystemDefault())
        val endTimeStr = localDateTime.toString()
        val timeString = "${localDateTime.hour.toString().padStart(2, '0')}${
            localDateTime.minute.toString().padStart(2, '0')
        }${localDateTime.second.toString().padStart(2, '0')}"
        val sessionId =
            "session_${uiState.virtualPatientId}-${localDateTime.date.toString().replace("-", "")}_$timeString"

        val durationMs = (Clock.System.now().toEpochMilliseconds() - sessionStartTimeMs).toInt()
        val durationS = durationMs / 1000.0

        uiState = uiState.copy(saveStatus = "Mentés és feltöltés folyamatban...")

        var videoMetaData: org.example.project.dto.Video? = null

        if (uiState.recordedVideoPath != null) {
            try {
                val videoBytes = FileStorage.readBytes(uiState.recordedVideoPath!!)
                if (videoBytes != null) {
                    val uploadResult = SessionRepository.uploadMeasurementVideo(videoBytes, sessionId)
                    if (uploadResult.isSuccess) {
                        // Kinyerjük a visszatérési értékből a teljes http://... linket
                        val fullVideoUrl = uploadResult.getOrNull() ?: ""

                        // Ezt a teljes linket adjuk át a JSON export dokumentumnak
                        videoMetaData = org.example.project.dto.Video(file_name = fullVideoUrl)
                    }
                }
            } catch (e: Exception) {
                println("Videó beolvasási/feltöltési hiba: ${e.message}")
            }
        }


        val exportDocument = MeasurementDocument(
            measurement_id = sessionId,
            athlete_id = uiState.patientId,
            athlete_name = uiState.virtualPatientId,
            exercise_id = selectedTaskToRun?.exercise?.id,
            exercise_name = selectedTaskToRun?.displayName,
            start_time = sessionStartTimeStr,
            end_time = endTimeStr,
            duration_ms = durationMs,
            duration_s = durationS,
            sensors = org.example.project.dto.Sensors(
                left = org.example.project.dto.Side(
                    device_id = "Sensoria-C1-D348",
                    sample_count = leftSessionData.size,
                    effective_rate_samples_per_s = if (durationS > 0) leftSessionData.size / durationS else 0.0,
                    data = leftSessionData.toList()
                ),
                right = org.example.project.dto.Side(
                    device_id = "Sensoria-C1-9C1D",
                    sample_count = rightSessionData.size,
                    effective_rate_samples_per_s = if (durationS > 0) rightSessionData.size / durationS else 0.0,
                    data = rightSessionData.toList()
                )
            ),
            video = videoMetaData
        )

        // Mentés az adatbázisba
        val result = SessionRepository.saveSession(sessionId, uiState.patientId, exportDocument)

        result.onSuccess { fileName ->
            uiState = uiState.copy(saveStatus = "Sikeres mentés: $fileName")
        }.onFailure { e ->
            uiState = uiState.copy(saveStatus = "Mentési hiba: ${e.message}")
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
                    })?.collect { (side, data) -> // Pair (oldal, minta) fogadása
                    if (uiState.isBluetoothEnabled) {
                        val newCount = uiState.dataPacketCount + 1
                        uiState = uiState.copy(
                            dataPacketCount = newCount, bluetoothStatus = "📡 Adat fogadva ($newCount csomag)"
                        )
                        processNewData(side, data)
                    }
                }
            } catch (e: Exception) {
                println("Bluetooth hiba: ${e.message}")
            } finally {
                cleanupBluetoothConnection()
            }
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
        biomechanicsResult = null
        sockAnalysisResult = null
    }

    fun updatePatientIds(dbUuid: String, virtualId: String) {

        mySessions = emptyList()
        deletedSessions = emptyList()


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
                fetchAllBiomechanicsResults()
            }.onFailure { e ->
                uiState = uiState.copy(currentTaskName = "Hiba a letöltéskor: ${e.message}")
            }
        }
    }


    fun openCloudSession(document: MeasurementDocument) {
        uiState = uiState.copy(
            patientId = document.athlete_id ?: "",
            currentTaskName = "Adatok betöltése...",
            sessionCompleted = false
        )

        viewModelScope.launch {
            // Biomechanikai eredmények lekérése
            val result = SessionRepository.getBiomechanicsResult(document.measurement_id)
            if (result.isSuccess) {
                biomechanicsResult = result.getOrNull()
            }

            // Zokni eredmények lekérése
            val sockResult = SessionRepository.getSockAnalysis(document.measurement_id)
            if (sockResult.isSuccess) {
                sockAnalysisResult = sockResult.getOrNull()
            }

            // Üzenet beállítása
            if (result.isSuccess || sockResult.isSuccess) {
                sessionResult = FullSessionResult(message = "A mérés eredményei sikeresen betöltve.")
            } else {
                sessionResult = FullSessionResult(message = "Hiba történt az eredmények letöltésekor.")
            }

            uiState = uiState.copy(
                currentTaskName = document.exercise_name ?: "Mentett Mérés",
                sessionCompleted = true
            )
        }
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

    fun setRecordedVideoPath(path: String) {
        uiState = uiState.copy(recordedVideoPath = path)
    }

    fun fetchAllBiomechanicsResults() {
        viewModelScope.launch {
            try {
                // Mivel az adatbázisban beállítottuk az RLS (Row Level Security) policy-t,
                // a Supabase szervere automatikusan csak azokat a sorokat adja vissza,
                // amelyek a bejelentkezett felhasználó (auth.uid) méréseihez tartoznak!
                val results = SupabaseManager.supabaseClient
                    .from("biomechanics_results")
                    .select()
                    .decodeList<org.example.project.data.BiomechanicsResult>()

                // Rendezzük időrendbe a 'kiszamitva' alapján
                allBiomechanicsResults = results.sortedBy { it.kiszamitva }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}