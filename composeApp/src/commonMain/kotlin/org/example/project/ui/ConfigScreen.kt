package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.RehabTaskType
import org.example.project.ScheduledTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    patientId: String,
    onPatientIdChange: (String) -> Unit,
    selectedTasks: MutableList<ScheduledTask>, // Custom session listája

    // Standard Rehab Session paraméterei
    standardTasks: List<ScheduledTask>,
    onStandardTaskDurationChange: (Int, String) -> Unit,
    onStartRehab: () -> Unit,

    isBluetoothEnabled: Boolean,
    bluetoothStatus: String,
    onToggleBluetooth: (Boolean) -> Unit,
    onStartCustom: () -> Unit, // Átnevezve az egyértelműség kedvéért
    onClearSession: () -> Unit,
    onOpenSavedSessions: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedTaskType by remember { mutableStateOf<RehabTaskType?>(null) }
    var taskDuration by remember { mutableStateOf("15") }
    var showClearSessionDialog by remember { mutableStateOf(false) }

    // A teljes képernyő görgethető
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Munkamenet létrehozása", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        // --- 1. BLOKK: BLUETOOTH WIDGET ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isBluetoothEnabled,
                        onCheckedChange = { onToggleBluetooth(it) },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50), uncheckedColor = Color.LightGray)
                    )
                    Text("Bluetooth kapcsolat", fontWeight = FontWeight.Bold)

                    if (bluetoothStatus.contains("Keresés") || bluetoothStatus.contains("Csatlakozás")) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                Text(
                    text = "Státusz: $bluetoothStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        bluetoothStatus.contains("Hiba") || bluetoothStatus.contains("Megszakadt") -> Color(0xFFEF5350)
                        bluetoothStatus.contains("Adat fogadva") -> Color(0xFF4CAF50)
                        bluetoothStatus.contains("Csatlakoztatva") -> Color(0xFF64B5F6)
                        else -> Color.LightGray
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = patientId,
            onValueChange = onPatientIdChange,
            label = { Text("Felhasználónév") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))


        // --- 2. BLOKK: STANDARD REHAB SESSION ---
        Text("Standard Feladatsor", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4CAF50))
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Feladatok felsorolása módosítható idővel
                standardTasks.forEachIndexed { index, task ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(task.displayName, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = if (task.duration == 0) "" else task.duration.toString(),
                            onValueChange = { newValue ->
                                // Csak számokat engedünk be, de az üres stringet is továbbadjuk a ViewModelnek
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    onStandardTaskDurationChange(index, newValue)
                                }
                            },
                            label = { Text("Sec") },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number) // Számbillentyűzet felugrása
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start Rehab Session Gomb
                Button(
                    onClick = onStartRehab,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("INDÍTÁS", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(24.dp))

        // --- 3. BLOKK: CUSTOM SESSION BUILDER ---
        Text("Egyéni Feladatsor", style = MaterialTheme.typography.titleMedium, color = Color(0xFF9C27B0))

        Spacer(modifier = Modifier.height(8.dp))


        // Legördülő menüs hozzáadás
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
                    value = selectedTaskType?.displayName ?: "Válassz feladatot...",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    label = { Text("Feladat típus") }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    RehabTaskType.entries.forEach { taskType ->
                        DropdownMenuItem(
                            text = { Text(taskType.displayName) },
                            onClick = { selectedTaskType = taskType; expanded = false }
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
                Text("Hozzáadás")
            }
        }

        // Custom feladatok listája
        // LazyColumn helyett sima Column-t használunk, mert a szülő már görgethető
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            selectedTasks.forEach { task ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(task.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("${task.duration}s", color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { selectedTasks.remove(task) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Törlés", tint = Color(0xFFEF5350))
                        }
                    }
                }
            }
        }

        // --- 4. BLOKK: ALSÓ AKCIÓGOMBOK ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showClearSessionDialog = true },
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = selectedTasks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) {
                Text("MUNKAMENET TÖRLÉSE", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onStartCustom,
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = selectedTasks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
            ) {
                Text("INDÍTÁS", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOpenSavedSessions,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("MENTETT MUNKAMENET")
        }

        Spacer(modifier = Modifier.height(32.dp)) // Extra hely a görgetés végén
    }

    // Megerősítő dialógus
    if (showClearSessionDialog) {
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            title = { Text("Törlés megerősítése") },
            text = { Text("Biztosan szeretné törölni az egyedileg összeállított feladatokat?") },
            confirmButton = {
                TextButton(onClick = { onClearSession(); showClearSessionDialog = false }) {
                    Text("Igen", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionDialog = false }) {
                    Text("Nem", color = Color.White)
                }
            }
        )
    }
}