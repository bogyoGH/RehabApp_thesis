package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.data.FileStorage
import org.example.project.data.SessionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSessionsScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit // ÚJ: Ezt hívjuk meg, ha ráklikkelnek a megnyitásra
) {
    var files by remember { mutableStateOf(emptyList<String>()) }

    val coroutineScope = rememberCoroutineScope()


    fun reloadFiles() {
        files = FileStorage.listFiles()
            .filter { it.endsWith(".json", ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        reloadFiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mentett Munkamenetek") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { reloadFiles() }) {
                        Text("Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Nincs mentett session fájl.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { fileName ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Fájlnév bal oldalon
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileName,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Gombok jobb oldalon (Megnyitás és Törlés)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Megtekintés / Megnyitás gomb
                                IconButton(onClick = { onOpenSession(fileName) }) {
                                    Icon(
                                        imageVector = Icons.Default.Assessment,
                                        contentDescription = "Eredmények megtekintése",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Törlés gomb
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        // A teljes Hard Delete folyamat meghívása (Felhő + Helyi)
                                        val result = SessionRepository.deleteSession(fileName)

                                        // Ha a törlés sikeres volt (vagy offline módban helyileg törlődött), frissítjük a listát
                                        if (result.isSuccess) {
                                            reloadFiles()
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Törlés",
                                        tint = MaterialTheme.colorScheme.error
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