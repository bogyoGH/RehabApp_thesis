package org.example.project.ui

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
// Importáljuk a modelljeinket a fő csomagból
import org.example.project.RehabTaskType
import org.example.project.ScheduledTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    patientId: String,
    onPatientIdChange: (String) -> Unit,
    selectedTasks: MutableList<ScheduledTask>,
    onStart: () -> Unit,
    onClearSession: () -> Unit,
    onOpenSavedSessions: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedTaskType by remember { mutableStateOf<RehabTaskType?>(null) }
    var taskDuration by remember { mutableStateOf("15") }
    var showClearSessionDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Session Configuration", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = patientId,
            onValueChange = onPatientIdChange,
            label = { Text("Patient ID") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    RehabTaskType.entries.forEach { taskType ->
                        DropdownMenuItem(
                            text = { Text(taskType.displayName) },
                            onClick = {
                                selectedTaskType = taskType
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = taskDuration,
                onValueChange = { if (it.all { char -> char.isDigit() }) taskDuration = it },
                label = { Text("Sec") },
                modifier = Modifier.width(70.dp)
            )

            Button(
                onClick = {
                    selectedTaskType?.let {
                        selectedTasks.add(ScheduledTask.Preset(it, taskDuration.toIntOrNull() ?: 10))
                    }
                },
                enabled = selectedTaskType != null
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(selectedTasks) { task ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(task.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("${task.duration}s", color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { selectedTasks.remove(task) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350))
                        }
                    }
                }
            }
        }

        // A gombok sorát külön vettem a dialógustól
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showClearSessionDialog = true },
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = selectedTasks.isNotEmpty(), // Csak akkor aktív, ha van mit törölni
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) {
                Text("CLEAR SESSION", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = selectedTasks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
            ) {
                Text("START SESSION", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenSavedSessions,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("SAVED SESSIONS")
        }
    }

    // A dialógust kivettem a Row-ból, és a funkció végére raktam (ahogy a Compose-ban szokás)
    if (showClearSessionDialog) {
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            title = { Text("Törlés megerősítése") },
            text = { Text("Biztosan szeretné törölni a feladatokat?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearSession() // A fentről kapott lambdát hívjuk meg
                        showClearSessionDialog = false
                    }
                ) {
                    Text("Igen", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearSessionDialog = false }
                ) {
                    Text("Nem", color = Color.White)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}