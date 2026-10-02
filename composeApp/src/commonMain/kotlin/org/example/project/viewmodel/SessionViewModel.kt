package org.example.project.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.example.project.MockDataGenerator
import org.example.project.analysis.FullSessionResult
import org.example.project.bluetooth.SensoriaBluetoothManager
import org.example.project.data.RehabTaskType
import org.example.project.data.ScheduledTask
import org.example.project.data.SensoriaData
import org.example.project.data.SessionRepository
import org.example.project.dto.ExportedTasks
import org.example.project.dto.SessionExport
import org.example.project.dto.SessionInfo
import org.example.project.dto.toExportedData


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

    val standardRehabRoutine = mutableStateListOf(
        ScheduledTask.Preset(RehabTaskType.TANDEM_WALKING, duration = 1),
        ScheduledTask.Preset(RehabTaskType.REST, duration = 1),
        ScheduledTask.Preset(RehabTaskType.MARCHING_IN_PLACE, duration = 1),
        ScheduledTask.Preset(RehabTaskType.REST, duration = 1),
        ScheduledTask.Preset(RehabTaskType.TOE_WALKING, duration = 1)
    )

    fun startSession(tasks: List<ScheduledTask>, mode: String = "Custom") {
        if (uiState.isRunning) return

        uiState = uiState.copy(
            sessionCompleted = false, showViewModeDialog = true
        )
        sessionData.clear()
    }

    fun confirmViewModeAndStart(preference: ViewPreference) {
        uiState = uiState.copy(
            viewPreference = preference, showViewModeDialog = false, isRunning = true
        )

        sessionJob = viewModelScope.launch {
            try {
                standardRehabRoutine.forEach { runTask(it) }
                finishSession() // Ha végigmegy a mérés, ez hívja be a dialógust
            } catch (e: CancellationException) {
                // Ha rányomnak a Leállítás gombra, itt kapjuk el, és kérdezünk rá a mentésre
                uiState = uiState.copy(
                    isRunning = false,
                    currentTaskName = "Mérés megszakítva",
                    showSaveDialog = true
                )
            }
        }
    }

    private suspend fun runTask(task: ScheduledTask) {
        val isPreset = task is ScheduledTask.Preset
        uiState = uiState.copy(
            currentTaskName = task.displayName,
            currentVideoName = if (isPreset && task.type.videoName.isNotBlank()) task.type.videoName else "buffer",
            currentInstruction = if (isPreset) task.type.instruction else "Kövesse az utasításokat!",
            currentFallback = if (isPreset) task.type.fallback else ""
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

        // Amíg van hátra idő a mérésből...
        while (sec > 0) {

            // 1. SZÜNETELLENŐRZÉS: Itt várakozunk, amíg az isPaused igaz.
            // Ez a kis belső hurok felfüggeszti a másodpercek csökkenését.
            while (uiState.isPaused) {
                delay(500) // Fél másodpercenként ránéz, hogy folytatódott-e a mérés
            }

            // 2. HA NINCS SZÜNET, VAGY FOLYTATÓDIK A MÉRÉS:
            // Frissítjük a felületet a hátralévő idővel
            uiState = uiState.copy(
                timerText = "$prefix${sec.toString().padStart(2, '0')}",
                progressValue = (seconds - sec).toFloat() / seconds
            )

            // 3. (Opcionális) Mock adat generálás, ha nincs Bluetooth
            if (isMainTask && !uiState.isBluetoothEnabled) {
                repeat(25) { processNewData(MockDataGenerator.generatePacket(uiState.currentTaskName)) }
            }

            // Várakozunk 1 másodpercet a következő számláló-lépésig
            delay(1000)

            // 4. Csökkentjük az időt
            sec--
        }
    }

    fun updateStandardTaskDuration(index: Int, durationStr: String) {
        val currentTask = standardRehabRoutine[index]
        if (currentTask is ScheduledTask.Preset) {
            standardRehabRoutine[index] = currentTask.copy(duration = durationStr.toIntOrNull() ?: 0)
        }
    }

    private fun processNewData(data: SensoriaData) {
        if (!uiState.isRunning || uiState.isPreviewPhase) return

        sessionData.add(data.copy(taskName = uiState.currentTaskName))
        uiState = uiState.copy(stabilityScore = "Adatok rögzítése folyamatban...")
    }

    fun stopSession() {
        sessionJob?.cancel()
        uiState = uiState.copy(
            isRunning = false, sessionCompleted = false
        )
    }

    private fun finishSession() {
        // NEM mentünk azonnal, hanem jelezzük a UI-nak, hogy mutassa a kérdést!
        uiState = uiState.copy(
            isRunning = false,
            currentTaskName = "Mérés Befejeződött!",
            showSaveDialog = true
        )
        sessionResult = FullSessionResult()
    }
    fun confirmSaveAndFinish() {
        println("AUTH_DEBUG: confirmSaveAndFinish ELINDULT a ViewModel-ben!")

        // UI állapot azonnali frissítése a Coroutine-on kívül
        uiState = uiState.copy(
            showSaveDialog = false,
            saveStatus = "Mentés előkészítése..."
        )

        // Ha a viewModelScope rossz, próbáljuk meg Dispatchers.IO-val közvetlenül
        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("AUTH_DEBUG: Coroutine elindult a viewModelScope-ban!")
                saveSessionToJson()

                // Mivel UI-t frissítünk, vissza kell térnünk a Main szálra, vagy a Compose kezeli,
                // de biztosabb a Default/Main használata UI frissítéshez, ha az IO-n vagyunk.
                // A legegyszerűbb, ha a saveSessionToJson után frissítjük a statust.
                uiState = uiState.copy(sessionCompleted = true)
                println("AUTH_DEBUG: sessionCompleted true-ra állítva!")
            } catch (e: Exception) {
                uiState = uiState.copy(
                    saveStatus = "Hiba történt a mentés során: ${e.message}"
                )
                e.printStackTrace()
            } catch (e: Throwable) {
                uiState = uiState.copy(
                    saveStatus = "Kritikus hiba a mentés során: ${e.message}"
                )
                e.printStackTrace()
            }
        }
    }
    fun discardSession() {
        sessionData.clear() // Töröljük a memóriából az adatokat
        uiState = uiState.copy(
            showSaveDialog = false,
            sessionCompleted = true, // Kilépünk a mérésből mentés nélkül
            currentTaskName = "Mérés elvetve"
        )
    }

    fun requestStop(){
        //megállítja a feladatot és az időzítőt
        sessionJob?.cancel(CancellationException("USER_STOP"))

    }



    fun pauseSession() {
        if (uiState.isRunning && !uiState.isPaused) {
            uiState = uiState.copy(
                isPaused = true,
                // Opcionális: jelezhetjük a UI-on, hogy manuálisan megállítottuk
                currentInstruction = "Mérés szüneteltetve"
            )
        }
    }

    fun resumeSession() {
        if (uiState.isRunning && uiState.isPaused) {
            uiState = uiState.copy(
                isPaused = false,
                // Visszaállítjuk az eredeti instrukciót (vagy hagyjuk, hogy a futó feladat felülírja)
                currentInstruction = "Kövesse az utasításokat!"
            )
        }
    }

    fun loadAndShowSavedSession(fileName: String) {
        viewModelScope.launch {
            uiState = uiState.copy(currentTaskName = "Adatok betöltése...")
            val result = SessionRepository.loadLocalSession(fileName)
            result.onSuccess { sessionExport ->
                // Nincs helyi elemzés, rögtön átugrunk az eredmény képernyőre
                sessionResult =
                    FullSessionResult(message = "A betöltött munkamenet adatai készen állnak a szerver oldali elemzésre.")
                uiState = uiState.copy(
                    patientId = sessionExport.sessionInfo.patientId,
                    currentTaskName = "Betöltött munkamenet",
                    sessionCompleted = true
                )
            }.onFailure { e ->
                uiState = uiState.copy(currentTaskName = "Hiba a betöltéskor")
            }
        }
    }

    private suspend fun saveSessionToJson() {
        println("AUTH_DEBUG: Mentés indítása a saveSessionToJson-ban...")
        val now = Clock.System.now()
        val localDateTime =
            Instant.fromEpochMilliseconds(now.toEpochMilliseconds()).toLocalDateTime(TimeZone.currentSystemDefault())
        val timeString = "${localDateTime.hour.toString().padStart(2, '0')}" +
                "${localDateTime.minute.toString().padStart(2, '0')}" +
                "${localDateTime.second.toString().padStart(2, '0')}"
        val sessionId = "session_${uiState.patientId}-${localDateTime.date.toString().replace("-", "")}_$timeString"

        println("AUTH_DEBUG: Session ID generálva: $sessionId")

        val export = SessionExport(
            sessionInfo = SessionInfo(
                sessionId = sessionId,
                patientId = uiState.patientId,
                timeStamp = localDateTime.toString(),
                mode = if (uiState.isBluetoothEnabled) "Online" else "Offline",
                bluetoothEnabled = uiState.isBluetoothEnabled,
                tasks = standardRehabRoutine.map { ExportedTasks(it.displayName, it.duration) }),
            sensorData = sessionData.toExportedData())

        println("AUTH_DEBUG: Export objektum elkészítve, Repository hívása következik...")
        uiState = uiState.copy(saveStatus = "Mentés folyamatban...")
        val result = SessionRepository.saveSession(sessionId, uiState.patientId, export)

        result.onSuccess { fileName ->
            println("AUTH_DEBUG: Repository mentés SIKERES, fájlnév: $fileName")
            uiState = uiState.copy(saveStatus = "Sikeres mentés: $fileName")
        }.onFailure { e ->
            println("AUTH_DEBUG: Repository mentés SIKERTELEN, hiba: ${e.message}")
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
                //ha megszakadt a bluetooth connection akkor megáll a mérés
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

    fun updatePatientId(newId: String) {
        uiState = uiState.copy(patientId = newId)
    }

    fun dismissViewModeDialog() {
        uiState = uiState.copy(showViewModeDialog = false)
    }
}

