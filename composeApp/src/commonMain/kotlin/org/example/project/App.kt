package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem

// Képernyők közötti navigáció állapota
sealed class Screen {
    object Config : Screen()
    object Execution : Screen()
}

@Composable
fun App() {
    // Sötét lila téma beállítása a Pythonos main.py alapján
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9C27B0), // Deep Purple
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onBackground = Color.White
        )
    ) {
        val viewModel = remember { SessionViewModel() }
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Config) }
        val selectedTasks = remember { mutableStateListOf<ScheduledTask>() }
        var patientId by remember { mutableStateOf("PT-001") }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (currentScreen) {
                is Screen.Config -> ConfigScreen(
                    patientId = patientId,
                    onPatientIdChange = { patientId = it },
                    selectedTasks = selectedTasks,
                    onStart = {
                        if (selectedTasks.isNotEmpty()) {
                            viewModel.startSession(selectedTasks)
                            currentScreen = Screen.Execution
                        }
                    }
                )

                is Screen.Execution -> ExecutionScreen(
                    viewModel = viewModel,
                    onStop = {
                        viewModel.stopSession()
                        currentScreen = Screen.Config
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    patientId: String,
    onPatientIdChange: (String) -> Unit,
    selectedTasks: MutableList<ScheduledTask>,
    onStart: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedTaskType by remember { mutableStateOf<RehabTaskType?>(null) }
    var taskDuration by remember { mutableStateOf("10") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Session Configuration", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        // Beteg azonosító (Python TextField megfelelője)
        OutlinedTextField(
            value = patientId,
            onValueChange = onPatientIdChange,
            label = { Text("Patient ID") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text("Choose tasks", style = MaterialTheme.typography.titleMedium)

        // Gyors feladat gombok az Enum alapján
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dropdown menu
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedTaskType?.displayName ?: "Select task...",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    label = { Text("Task Type") }
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    RehabTaskType.values().forEach { taskType ->
                        DropdownMenuItem(
                            text = { Text(taskType.displayName) }, // Teljes név!
                            onClick = {
                                selectedTaskType = taskType
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Időtartam választó
            OutlinedTextField(
                value = taskDuration,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        taskDuration = newValue
                    }
                },
                label = { Text("Duration (s)") },
                modifier = Modifier.width(100.dp)
            )
            // Hozzáadás gomb
            Button(
                onClick = {
                    selectedTaskType?.let { task ->
                        val duration = taskDuration.toIntOrNull() ?: 10
                        selectedTasks.add(ScheduledTask.Preset(task, duration))
                    }
                },
                enabled = selectedTaskType != null && taskDuration.isNotEmpty()
            ) {
                Text("Add")
            }
        }

        // Kiválasztott feladatok listája (Python custom_task_list)
        Text(
            "Selected Sequence:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(selectedTasks) { task ->
                val name = when (task) {
                    is ScheduledTask.Preset -> task.type.displayName
                    is ScheduledTask.Custom -> task.name
                }
                Card(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f))
                        Text("${task.duration}s", color = Color.Gray)
                        IconButton(onClick = { selectedTasks.remove(task) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                }
            }
        }

        // START gomb (Python START QUICK SESSION alapján)
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
        ) {
            Text("START SESSION", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ExecutionScreen(viewModel: SessionViewModel, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(viewModel.currentTaskName, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF64B5F6))
            Text(viewModel.stabilityScore, style = MaterialTheme.typography.bodyLarge, color = Color.LightGray)
        }

        // Óriási időzítő (Python big_timer_text)
        Text(
            text = viewModel.timerText,
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        // Progress Bar (Python progress_value)
        LinearProgressIndicator(
            progress = viewModel.progressValue,
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = Color(0xFF4CAF50),
            trackColor = Color.DarkGray
        )

        // STOP gomb (Python STOP SESSION)
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(0.7f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
        ) {
            Text("STOP SESSION", fontWeight = FontWeight.Bold)
        }
    }
}