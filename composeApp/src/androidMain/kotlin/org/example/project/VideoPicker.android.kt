package org.example.project.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberVideoPickerLauncher(
    onVideoPicked: (ByteArray?) -> Unit
): VideoPickerLauncher {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                // A kiválasztott videó beolvasása bájttömbként
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                onVideoPicked(bytes)
            } catch (e: Exception) {
                e.printStackTrace()
                onVideoPicked(null)
            }
        } else {
            onVideoPicked(null)
        }
    }

    return remember {
        object : VideoPickerLauncher {
            override fun launch() {
                launcher.launch("video/*")
            }
        }
    }
}