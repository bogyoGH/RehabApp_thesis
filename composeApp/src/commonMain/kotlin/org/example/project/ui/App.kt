package org.example.project.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.example.project.viewmodel.SessionViewModel
import org.example.project.data.ScheduledTask
import org.example.project.analysis.FullSessionResult

sealed class Screen {
    object Config : Screen()
    object Execution : Screen()
    object SavedSessions : Screen()
    data class Results(val result: FullSessionResult) : Screen()
}

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9C27B0),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onBackground = Color.White
        )
    ) {
        val viewModel = remember { SessionViewModel() }
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Config) }
        val selectedTasks = remember { mutableStateListOf<ScheduledTask>() }

        // ÚJ LOGIKA: Központi navigáció-figyelő
        // Bármikor, ha a ViewModel jelzi, hogy kész az elemzés (akár élő mérés, akár fájlbetöltés után),
        // ez az Effect lefut, és átnavigál az Eredmények képernyőre.
        LaunchedEffect(viewModel.uiState.sessionCompleted) {
            if (viewModel.uiState.sessionCompleted && viewModel.sessionResult != null) {
                currentScreen = Screen.Results(viewModel.sessionResult!!)
                // Fontos: visszaállítjuk false-ra!
                // Így ha a felhasználó a Results képernyőről visszalép a Configra, nem ugrik azonnal vissza.
                viewModel.resetSessionState()
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

            when (val screen = currentScreen) {

                is Screen.Config -> ConfigScreen(
                    patientId = viewModel.uiState.patientId,
                    onPatientIdChange = { viewModel.updatePatientId(it) },
                    selectedTasks = selectedTasks,

                    standardTasks = viewModel.standardRehabRoutine,
                    onStandardTaskDurationChange = { index, duration ->
                        viewModel.updateStandardTaskDuration(index, duration)
                    },
                    onStartRehab = {
                        viewModel.startStandardRehab()
                        currentScreen = Screen.Execution
                    },

                    isBluetoothEnabled = viewModel.uiState.isBluetoothEnabled,
                    bluetoothStatus = viewModel.uiState.bluetoothStatus,
                    onToggleBluetooth = { isChecked -> viewModel.toggleBluetooth(isChecked) },

                    onStartCustom = {
                        if (selectedTasks.isNotEmpty()) {
                            viewModel.startSession(selectedTasks.toList(), mode = "Custom")
                            currentScreen = Screen.Execution
                        }
                    },
                    onClearSession = {
                        selectedTasks.clear()
                    },
                    onOpenSavedSessions = {
                        currentScreen = Screen.SavedSessions
                    }
                )

                is Screen.Execution -> ExecutionScreen(
                    viewModel = viewModel,
                    onStop = {
                        viewModel.stopSession()

                        // Mivel a sikeres mérést a LaunchedEffect kezeli feljebb,
                        // itt csak azt az esetet kell lekezelni, ha a usert MEGSZAKÍTOTTA a mérést (Stop).
                        // Ha nincs eredmény (megszakítás történt), menjünk vissza a menübe.
                        if (viewModel.sessionResult == null) {
                            currentScreen = Screen.Config
                        }
                    }
                )

                is Screen.Results -> ResultsScreen(
                    result = screen.result,
                    onBackToMenu = {
                        viewModel.resetSessionState()
                        currentScreen = Screen.Config
                    },
                    onGoToSavedSessions = {
                        viewModel.resetSessionState()
                        currentScreen = Screen.SavedSessions
                    }
                )

                is Screen.SavedSessions -> SavedSessionsScreen(
                    onBack = {
                        currentScreen = Screen.Config
                    },
                    onOpenSession = { fileName ->
                        // 1. Szólunk a ViewModelnek, hogy töltse be és elemezze a fájlt.
                        // Ez aszinkron elindul a háttérben.
                        viewModel.loadAndShowSavedSession(fileName)

                        // 2. A navigációt ráhagyjuk a fenti LaunchedEffect-re!
                        // Amint a ViewModel végez a számolással, automatikusan váltani fog a képernyő.
                    }
                )
            }
        }
    }
}