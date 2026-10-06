package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.data.Exercise
import org.example.project.network.ExerciseRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseFormScreen(
    initialExercise: Exercise?,
    isSaving: Boolean,
    onSave: (Exercise) -> Unit,
    onDelete: (Exercise) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(initialExercise?.name ?: "") }
    var category by remember { mutableStateOf(initialExercise?.category ?: "") }
    var description by remember { mutableStateOf(initialExercise?.description ?: "") }
    var instructions by remember { mutableStateOf(initialExercise?.instructions ?: "") }
    var videoURL by remember { mutableStateOf(initialExercise?.video_url ?: "") }

    // ÚJ: Uri helyett közvetlenül a ByteArray-t (fájladatokat) tároljuk
    var selectedVideoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isUploadingVideo by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // ÚJ: A platformfüggetlen fájlválasztónk meghívása
    val videoPicker = rememberVideoPickerLauncher { bytes ->
        selectedVideoBytes = bytes
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Biztosan törlöd?") },
            text = { Text("A gyakorlat és a hozzá tartozó videó véglegesen törlődik a rendszerből. Ez a művelet nem vonható vissza!") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        initialExercise?.let { onDelete(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Végleges Törlés") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Mégsem") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialExercise == null) "Új gyakorlat" else "Gyakorlat szerkesztése") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Gyakorlat neve *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Kategória (opcionális)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Rövid leírás (opcionális)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Instrukciók (opcionális)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Oktatóvideó (opcionális)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // ÚJ: Gomb, ami meghívja a platformfüggetlen Launchert
                    Button(onClick = { videoPicker.launch() }) {
                        Text(if (selectedVideoBytes == null && videoURL.isBlank()) "Videó kiválasztása galériából" else "Másik videó választása")
                    }

                    if (selectedVideoBytes != null) {
                        Text(
                            "Új videó kiválasztva. Mentéskor feltöltésre kerül.",
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (videoURL.isNotBlank()) {
                        Text(
                            "A gyakorlathoz már tartozik videó.",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isSaving || isUploadingVideo) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    if (isUploadingVideo) {
                        Text("Videó feltöltése folyamatban...", modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                // MENTÉS GOMB
                Button(
                    onClick = {
                        scope.launch {
                            focusManager.clearFocus()
                            var finalUrl = videoURL

                            if (selectedVideoBytes != null) {
                                isUploadingVideo = true
                                val uploadResult = ExerciseRepository.uploadVideo(selectedVideoBytes!!)
                                if (uploadResult.isSuccess) {
                                    finalUrl = uploadResult.getOrNull() ?: ""
                                }
                                isUploadingVideo = false
                            }

                            val savedExercise = Exercise(
                                id = initialExercise?.id ?: "",
                                name = name.trim(),
                                category = category.ifBlank { null },
                                description = description.ifBlank { null },
                                instructions = instructions.ifBlank { null },
                                video_url = finalUrl.ifBlank { null },
                                is_global = initialExercise?.is_global ?: false,
                                created_by = initialExercise?.created_by,
                                created_at = initialExercise?.created_at
                            )
                            onSave(savedExercise)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = name.isNotBlank() && !isSaving && !isUploadingVideo
                ) {
                    Text("Gyakorlat Mentése")
                }

                // ÚJ: TÖRLÉS GOMB (Csak meglévő feladat szerkesztésekor jelenik meg)
                if (initialExercise != null) {
                    OutlinedButton(
                        onClick = { onDelete(initialExercise) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Gyakorlat törlése")
                    }
                }
            }
        }
    }
}