package org.example.project.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberVideoPickerLauncher(
    onVideoPicked: (ByteArray?) -> Unit
): VideoPickerLauncher {
    // Egy üres, helyettesítő implementáció iOS-re
    return remember {
        object : VideoPickerLauncher {
            override fun launch() {
                println("iOS fájlválasztó egyelőre nincs implementálva (Android fókuszú alkalmazás).")
                // Biztonság kedvéért null-t adunk vissza, mintha a felhasználó megszakította volna a választást
                onVideoPicked(null)
            }
        }
    }
}