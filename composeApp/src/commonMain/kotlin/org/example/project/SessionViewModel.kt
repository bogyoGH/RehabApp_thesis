package org.example.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext


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

    var isBluetoothEnabled by mutableStateOf(false) // Ez vezérli a pipát
    var bluetoothStatus by mutableStateOf("Nincs csatlakoztatva")
    var dataPacketCount by mutableIntStateOf(0)

    private var bleJob: Job? = null
    private var bluetoothManager: SensoriaBluetoothManager? = null

    private var currentSessionMode = "Custom"

    var sessionResult by mutableStateOf<FullSessionResult?>(null)

    val standardRehabRoutine = mutableStateListOf(
        ScheduledTask.Preset(RehabTaskType.TANDEM_WALKING, duration = 30),
        ScheduledTask.Preset(RehabTaskType.REST, duration = 60),
        ScheduledTask.Preset(RehabTaskType.MARCHING_IN_PLACE, duration = 45),
        ScheduledTask.Preset(RehabTaskType.REST, duration = 60),
        ScheduledTask.Preset(RehabTaskType.TOE_WALKING, duration = 30)
    )

    fun startSession(tasks: List<ScheduledTask>, mode: String = "Custom") {
        if (isRunning) return // Megakadályozzuk a többszörös indítást
        currentSessionMode = mode
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

    fun startStandardRehab() {
        startSession(standardRehabRoutine, mode = "Standard")
    }

    private suspend fun runTask(task: ScheduledTask) {
        currentTaskName = task.displayName

        // Feladat instrukcióinak beállítása
        if (task is ScheduledTask.Preset) {
            currentVideoName = if (task.type.videoName.isNotBlank()) task.type.videoName else null
            currentInstruction = task.type.instruction
            currentFallback = task.type.fallback
        } else {
            currentVideoName = "buffer"
            currentInstruction = "Kövesse a terapeuta utasításait."
            currentFallback = ""
        }

        // Időzítő ciklus - CSAK EGYSZER SZEREPEL!
        for (second in task.duration downTo 0) {
            timerText = second.toString().padStart(2, '0')
            progressValue = (task.duration - second).toFloat() / task.duration

            // MOCK ADAT GENERÁLÁSA 25 Hz-en
            if (!isBluetoothEnabled) {
                for (i in 0 until 25) {
                    val mockPacket = MockDataGenerator.generatePacket(currentTaskName)
                    processNewData(mockPacket)
                }
            }

            delay(1000)
        }
    }

    fun updateStandardTaskDuration(index: Int, durationStr: String) {
        // Ha üres a string (mert a felhasználó kitörölte), 0-ként tároljuk el
        val duration = if (durationStr.isEmpty()) 0 else durationStr.toIntOrNull() ?: 0

        val currentTask = standardRehabRoutine[index]
        if (currentTask is ScheduledTask.Preset) {
            // Frissítjük a listát a másolattal
            standardRehabRoutine[index] = currentTask.copy(duration = duration)
        }
    }

     private fun processNewData(data: SensoriaData) {
        if (!isRunning) return

        // Az adatcsomagot lemásoljuk és felcímkézzük az aktuális feladat nevével
        val taggedData = data.copy(taskName = currentTaskName)
        sessionData.add(taggedData)

        // A stabilitást a felcímkézett adatok alapján számoljuk
        val score = StabilityCalculator.calculate(taggedData)
        stabilityScore = "Stability: $score/100"
    }

    fun stopSession() {
        sessionJob?.cancel() // Megállítja a futó mérést
        isRunning = false
        sessionCompleted = false
    }

    private fun finishSession() {
        isRunning = false
        currentTaskName = "All Tasks Completed!" //
        calculateFullAnalysis()
        saveSessionToJson() // Először kimentjük az adatokat

        // 1. Kiszámoljuk az összes eredményt és feltöltjük a sessionResult változót


        // 2. Csak a számolás UTÁN állítjuk true-ra, ami triggereli a képernyőváltást!
        sessionCompleted = true
    }
    private fun calculateFullAnalysis() {
        val segmented = SessionAnalyzer.segmentDataByTask(sessionData)
        val (activeTasks, restTasks) = SessionAnalyzer.separateTasks(segmented)

        val resultsMap = mutableMapOf<String, Any>()

        // Fáradási index kiszámítása
        val activeTaskNames = activeTasks.keys.toList()
        var fatigue = 0.0

        if (activeTaskNames.size >= 2) {
            val firstTaskData = activeTasks[activeTaskNames.first()]!!
            val lastTaskData = activeTasks[activeTaskNames.last()]!!

            val firstStability = firstTaskData.map { StabilityCalculator.calculate(it) }.average()
            val lastStability = lastTaskData.map { StabilityCalculator.calculate(it) }.average()

            if (firstStability > 0) {
                fatigue = ((firstStability - lastStability) / firstStability) * 100
            }
        }

        // Egyedi feladatok elemzése (Tandem, Lábujjhegyen, stb.)
        activeTasks.forEach { (name, data) ->
            resultsMap[name] = if (name.contains("walking", ignoreCase = true) || name.contains("járás", ignoreCase = true)) {
                SessionAnalyzer.analyzeDynamicTask(data)
            } else {
                SessionAnalyzer.analyzeStaticTask(data)
            }
        }

        // VÉGEREDMÉNY MENTÉSE: Ez a változó adja át az adatokat a ResultsScreen-nek!
        sessionResult = FullSessionResult(
            taskResults = resultsMap,
            rawTaskData = activeTasks, // Itt adjuk át a nyers adatokat a grafikonokhoz!
            fatigueIndex = fatigue.coerceAtLeast(0.0),
            globalFogEvents = SessionAnalyzer.detectFreezingOfGait(sessionData)
        )

        println("Elemzés kész! Képernyőváltás indítása...")
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
                mode = if (isBluetoothEnabled) "Online" else "Offline",
                bluetoothEnabled = isBluetoothEnabled,
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


    fun toggleBluetooth(enabled: Boolean) {
        isBluetoothEnabled = enabled

        if (enabled) {
            dataPacketCount = 0
            bluetoothStatus = "Keresés..."

            bleJob = viewModelScope.launch {
                try {
                    val manager = SensoriaBluetoothManager(this)
                    bluetoothManager = manager

                    manager.connectAndListen(
                        onStatusChange = { status ->
                            // Csak akkor írjuk felül a státuszt a háttér-üzenetekkel, ha még nem jön adat
                            if (!bluetoothStatus.startsWith("📡 Adat fogadva")) {
                                bluetoothStatus = status
                            }
                        }
                    ).collect { data ->
                        if (isBluetoothEnabled) {
                            dataPacketCount++
                            bluetoothStatus = "📡 Adat fogadva ($dataPacketCount csomag)"

                            // Ez küldi el a központi feldolgozónak, ami majd felcímkézi a task nevével
                            processNewData(data)
                        }
                    }
                } catch (e: Exception) {
                    println("Bluetooth hiba: ${e.message}")
                } finally {
                    // --- EZ A BLOKK MINDIG LEFUT A FOLYAMAT VÉGÉN ---

                    // Ha a folyamat úgy ért véget, hogy a pipa Még MINDIG bent van,
                    // az azt jelenti, hogy a zokni váratlanul lecsatlakozott!
                    if (isBluetoothEnabled) {
                        isBluetoothEnabled = false
                        bluetoothStatus = "❌ Kapcsolat megszakadt (Eszköz lecsatlakozott)"
                    }

                    // Fizikai kapcsolat takarítása.
                    // (A NonCancellable azért kell, mert egy már "lelőtt" szálon is muszáj lekapcsolnunk a Bluetooth-t)
                    withContext(NonCancellable) {
                        bluetoothManager?.disconnect()
                    }
                    bluetoothManager = null
                }
            }
        } else {
            // Ha TE kattintottál rá és vetted ki a pipát manuálisan:
            bluetoothStatus = "Leválasztva"
            // A job leállítása azonnal kiváltja a fenti 'finally' blokkot, ami elvégzi a fizikai leválasztást!
            bleJob?.cancel()
        }
    }
}