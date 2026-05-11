package org.example.project.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.example.project.SessionViewModel
import org.example.project.ScheduledTask
import org.example.project.FullSessionResult // ÚJ: importáljuk az eredmény modellt

// ÚJ: Kiegészítettük a Screen osztályt a Results képernyővel
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

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

            // ÚJ: Itt definiáljuk a 'screen' változót, hogy hozzáférjünk a paraméterekhez
            when (val screen = currentScreen) {

                is Screen.Config -> ConfigScreen(
                    patientId = viewModel.patientId,
                    onPatientIdChange = { viewModel.patientId = it },
                    selectedTasks = selectedTasks,

                    standardTasks = viewModel.standardRehabRoutine,
                    onStandardTaskDurationChange = { index, duration ->
                        viewModel.updateStandardTaskDuration(index, duration)
                    },
                    onStartRehab = {
                        viewModel.startStandardRehab()
                        currentScreen = Screen.Execution
                    },

                    isBluetoothEnabled = viewModel.isBluetoothEnabled,
                    bluetoothStatus = viewModel.bluetoothStatus,
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
                        // ÚJ LOGIKA: Ha a mérés végén van eredmény, mutassuk meg.
                        // Ha csak "megszakították", menjünk vissza a főmenübe.
                        if (viewModel.sessionResult != null) {
                            currentScreen = Screen.Results(viewModel.sessionResult!!)
                        } else {
                            currentScreen = Screen.Config
                        }
                    }
                )

                // ÚJ KÉPERNYŐ BEKÖTÉSE:
                is Screen.Results -> ResultsScreen(
                    result = screen.result, // Itt adjuk át a kiszámolt adatokat
                    onBack = { currentScreen = Screen.Config } // Visszagomb a Configra
                )

                is Screen.SavedSessions -> SavedSessionsScreen(
                    onBack = { currentScreen = Screen.Config }
                )
            }
        }
    }
}