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
import org.example.project.dto.SessionExport
import org.example.project.network.SessionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSessionsScreen(
    sessions: List<SessionExport>,
    deletedSessions: List<SessionExport>, // ÚJ: Törölt mérések listája
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenSession: (SessionExport) -> Unit,
    onRestore: (String) -> Unit // ÚJ: Visszaállítás callback
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Aktív mérések", "Törölt mérések")

    // ÚJ: Törlés megerősítő ablak állapota a mérésekhez
    var sessionToDelete by remember { mutableStateOf<SessionExport?>(null) }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    // Törlés tájékoztató ablak
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Mérés törlése") },
            text = { Text("Tájékoztatás: A mérés eltűnik az aktív nézetből, de a háttérben az adatok megmaradnak, így a 'Törölt mérések' fülön bármikor visszaállíthatod őket.") },
            confirmButton = {
                Button(
                    onClick = {
                        val sid = sessionToDelete!!.sessionInfo.sessionId
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

            // FÜLEK (Aktív / Törölt)
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
                                containerColor = if (selectedTabIndex == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.sessionInfo.timeStamp.replace("T", " ").substringBefore("."),
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTabIndex == 1) Color.Gray else Color.White
                                    )
                                    Text(
                                        text = "Mód: ${session.sessionInfo.mode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selectedTabIndex == 1) Color.Gray else Color.LightGray
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // AKTÍV FÜL GOMBJAI
                                    if (selectedTabIndex == 0) {
                                        IconButton(onClick = { onOpenSession(session) }) {
                                            Icon(Icons.Default.Assessment, contentDescription = "Megnyitás", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { sessionToDelete = session }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Törlés", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    // TÖRÖLT FÜL GOMBJAI
                                    else {
                                        IconButton(onClick = { onRestore(session.sessionInfo.sessionId) }) {
                                            Icon(Icons.Default.Restore, contentDescription = "Visszaállítás", tint = Color(0xFF4CAF50))
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