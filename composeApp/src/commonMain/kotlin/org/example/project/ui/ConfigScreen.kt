package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.example.project.data.Exercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    patientId: String,
    onStartSingleTask: (Exercise, Int) -> Unit, // Közvetlen indítás Exercise + Idővel
    isBluetoothEnabled: Boolean,
    bluetoothStatus: String,
    onToggleBluetooth: (Boolean) -> Unit,
    onLogout: () -> Unit,
    availableExercises: List<Exercise>,
    onCreateNewExercise: () -> Unit,
    onEditExercise: (Exercise) -> Unit
) {
    // Állapotok a felugró indító panelhez
    var exerciseToStart by remember { mutableStateOf<Exercise?>(null) }
    var durationInput by remember { mutableStateOf("10") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
    ) {
        // CÍMSOR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Elérhető gyakorlatok", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            IconButton(onClick = onLogout) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Kijelentkezés", tint = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = patientId,
            onValueChange = { },
            label = { Text("Felhasználó (automatikus)") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        // GYAKORLATOK LISTÁJA
        val groupedExercises = availableExercises.groupBy { it.category ?: "Egyéb" }

        LazyColumn(modifier = Modifier.weight(1f)) {
            groupedExercises.forEach { (category, exercises) ->
                item {
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }

                items(exercises) { exercise ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = exercise.name, style = MaterialTheme.typography.titleMedium)
                            }

                            // Szerkesztés (Ha saját feladat)
                            if (!exercise.is_global) {
                                IconButton(onClick = { onEditExercise(exercise) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // INDÍTÁS GOMB
                            IconButton(onClick = { exerciseToStart = exercise }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Mérés előkészítése", tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCreateNewExercise,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text("+ Új saját gyakorlat létrehozása")
                }
            }
        }
    }

    // --- FELUGRÓ PANEL INDÍTÁS ELŐTT (Instrukció, Idő, Bluetooth) ---
    if (exerciseToStart != null) {
        ModalBottomSheet(
            onDismissRequest = { exerciseToStart = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp).imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = exerciseToStart!!.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Instrukció megjelenítése
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Instrukció:", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = exerciseToStart!!.instructions ?: "Nincs részletes instrukció megadva.",
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Bluetooth csatlakozás a panelen belül
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isBluetoothEnabled,
                                onCheckedChange = { onToggleBluetooth(it) },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                            )
                            Text("Bluetooth csatlakoztatása", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Státusz: $bluetoothStatus",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Időtartam megadása
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) durationInput = it },
                    label = { Text("Feladat időtartama (másodperc)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(24.dp))

                // START GOMB
                // Biztosítjuk, hogy csak offline módban, vagy sikeresen csatlakoztatott (és adatot küldő) zoknival indulhasson a mérés
                val isBluetoothReady = !isBluetoothEnabled || bluetoothStatus.startsWith("📡")

                Button(
                    onClick = {
                        val time = durationInput.toIntOrNull() ?: 10
                        onStartSingleTask(exerciseToStart!!, time)
                        exerciseToStart = null
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBluetoothReady) Color(0xFF4CAF50) else Color.Gray
                    ),
                    enabled = durationInput.isNotBlank() && isBluetoothReady
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isBluetoothEnabled && !isBluetoothReady) "VÁRAKOZÁS A ZOKNIRA..." else "MÉRÉS INDÍTÁSA",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(32.dp)) // Hely az alsó navigációs sáv miatt
            }
        }
    }
}