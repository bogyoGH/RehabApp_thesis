package org.example.project.ui

import androidx.compose.runtime.Composable

// Egy közös interfész a fájlválasztó elindításához
interface VideoPickerLauncher {
    fun launch()
}

// Az expect függvény, ami platformtól függetlenül kér egy bájttömböt
@Composable
expect fun rememberVideoPickerLauncher(
    onVideoPicked: (ByteArray?) -> Unit
): VideoPickerLauncher