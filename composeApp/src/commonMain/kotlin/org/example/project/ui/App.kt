package org.example.project.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.example.project.SessionViewModel
import org.example.project.ScheduledTask

sealed class Screen {
    object Config : Screen()
    object Execution : Screen()
    object SavedSessions : Screen()
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



        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (currentScreen) {
                is Screen.Config -> ConfigScreen(

                    patientId = viewModel.patientId,
                    onPatientIdChange = { viewModel.patientId = it },
                    selectedTasks = selectedTasks,

                    // --- FRISSÍTETT BLUETOOTH BEKÖTÉS ---
                    isBluetoothEnabled = viewModel.isBluetoothEnabled,
                    bluetoothStatus = viewModel.bluetoothStatus,
                    onToggleBluetooth = { isChecked -> viewModel.toggleBluetooth(isChecked) },
                    // -----------------------------------

                    onStart = {
                        if (selectedTasks.isNotEmpty()) {
                            viewModel.startSession(selectedTasks)
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
                        currentScreen = Screen.Config
                    }
                )

                is Screen.SavedSessions -> SavedSessionsScreen(
                    onBack = { currentScreen = Screen.Config }
                )
            }
        }
    }
}