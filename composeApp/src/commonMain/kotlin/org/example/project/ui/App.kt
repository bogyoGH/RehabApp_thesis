package org.example.project.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.example.project.viewmodel.SessionViewModel
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

        LaunchedEffect(viewModel.uiState.sessionCompleted) {
            if (viewModel.uiState.sessionCompleted && viewModel.sessionResult != null) {
                currentScreen = Screen.Results(viewModel.sessionResult!!)
                viewModel.resetSessionState()
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val screen = currentScreen) {
                is Screen.Config -> ConfigScreen(
                    patientId = viewModel.uiState.patientId,
                    onPatientIdChange = { viewModel.updatePatientId(it) },

                    athleteTasks = viewModel.standardRehabRoutine,
                    onAthleteTaskDurationChange = { index, duration ->
                        viewModel.updateStandardTaskDuration(index, duration)
                    },
                    onStartSession = {
                        viewModel.startSession( viewModel.standardRehabRoutine)
                        currentScreen = Screen.Execution
                    },

                    isBluetoothEnabled = viewModel.uiState.isBluetoothEnabled,
                    bluetoothStatus = viewModel.uiState.bluetoothStatus,
                    onToggleBluetooth = { isChecked -> viewModel.toggleBluetooth(isChecked) },

                    onOpenSavedSessions = {
                        currentScreen = Screen.SavedSessions
                    }
                )

                is Screen.Execution -> ExecutionScreen(
                    viewModel = viewModel,
                    onStop = {
                        viewModel.stopSession()
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
                    onBack = { currentScreen = Screen.Config },
                    onOpenSession = { fileName ->
                        viewModel.loadAndShowSavedSession(fileName)
                    }
                )
            }
        }
    }
}