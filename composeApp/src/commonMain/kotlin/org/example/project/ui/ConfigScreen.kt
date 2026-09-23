package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.example.project.data.ScheduledTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    patientId: String,
    onPatientIdChange: (String) -> Unit,

    athleteTasks: List<ScheduledTask>,
    onAthleteTaskDurationChange: (Int, String) -> Unit,
    onStartSession: () -> Unit,

    isBluetoothEnabled: Boolean,
    bluetoothStatus: String,
    onToggleBluetooth: (Boolean) -> Unit,
    onOpenSavedSessions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Munkamenet", style = MaterialTheme.typography.headlineMedium, color = Color.White)
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
            label = { Text("Felhasználónév (Azonosító)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- 2. BLOKK: SPORTOLÓI FELADATSOR ---
        Text("Sportolói Feladatsor", style = MaterialTheme.typography.titleMedium, color = Color(0xFF64B5F6))
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                athleteTasks.forEachIndexed { index, task ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(task.displayName, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = if (task.duration == 0) "" else task.duration.toString(),
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    onAthleteTaskDurationChange(index, newValue)
                                }
                            },
                            label = { Text("Sec") },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Start Gomb
                Button(
                    onClick = onStartSession,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("MÉRÉS INDÍTÁSA", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. BLOKK: ALSÓ NAVIGÁCIÓ ---
        OutlinedButton(
            onClick = onOpenSavedSessions,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("MENTETT MUNKAMENETEK")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}