package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.dto.MeasurementDocument
import org.example.project.network.SessionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSessionsScreen(
    patientId: String,
    sessions: List<MeasurementDocument>,
    deletedSessions: List<MeasurementDocument>,
    evaluatedSessionIds: List<String>,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenSession: (MeasurementDocument) -> Unit,
    onRestore: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Aktív mérések", "Törölt mérések")

    var sessionToDelete by remember { mutableStateOf<MeasurementDocument?>(null) }

    // ÚJ: Unit helyett a patientId-t figyeljük. Ha új felhasználó lép be (új ID), automatikusan frissít.
    LaunchedEffect(patientId) {
        onRefresh()
    }

    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Mérés törlése") },
            text = { Text("Tájékoztatás: A mérés eltűnik az aktív nézetből, de a háttérben az adatok megmaradnak, így a 'Törölt mérések' fülön bármikor visszaállíthatod őket.") },
            confirmButton = {
                Button(
                    onClick = {
                        val sid = sessionToDelete!!.measurement_id
                        sessionToDelete = null
                        coroutineScope.launch {
                            val result = SessionRepository.deleteSession(sid)
                            if (result.isSuccess) {
                                onRefresh()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Törlés") }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("Mégsem") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mentett munkamenetek") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh) { Text("Frissítés") }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            val currentList = if (selectedTabIndex == 0) sessions else deletedSessions

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (selectedTabIndex == 0) "Nincsenek mentett mérések a felhőben." else "A törölt mérések listája üres.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentList) { session ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedTabIndex == 1) MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.5f
                                ) else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.start_time.replace("T", " ").substringBefore("."),
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTabIndex == 1) Color.Gray else Color.White
                                    )
                                    Text(
                                        text = "Feladat: ${session.exercise_name ?: "Ismeretlen"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selectedTabIndex == 1) Color.Gray else Color.LightGray
                                    )
                                }

                                // VISSZARAKVA: Ez a Row és az if feltétel hiányzott!
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selectedTabIndex == 0) {
                                        val isReady = evaluatedSessionIds.contains(session.measurement_id)

                                        IconButton(
                                            onClick = {
                                                println("ZOKNI_DEBUG: Kattintás a megnyitás gombon. SessionID: ${session.measurement_id}, isReady: $isReady")

                                                if (isReady) {
                                                    println("ZOKNI_DEBUG: A mérés feldolgozva, navigálás a Results képernyőre...")
                                                    onOpenSession(session)
                                                } else {
                                                    println("ZOKNI_DEBUG: A mérés még NINCS feldolgozva (vagy a kliens nem látja). Evaluated IDs: $evaluatedSessionIds")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Assessment,
                                                contentDescription = "Megnyitás",
                                                tint = if (isReady) MaterialTheme.colorScheme.primary else Color.Gray.copy(
                                                    alpha = 0.3f
                                                )
                                            )
                                        }
                                        IconButton(onClick = { sessionToDelete = session }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Törlés",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = { onRestore(session.measurement_id) }) {
                                            Icon(
                                                Icons.Default.Restore,
                                                contentDescription = "Visszaállítás",
                                                tint = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


