package org.example.project.viewmodel

data class SessionUiState(
    val currentTaskName: String = "Ready to start",
    val timerText: String = "00",
    val progressValue: Float = 0f,
    val isRunning: Boolean = false,
    val sessionCompleted: Boolean = false,
    val stabilityScore: String = "Stability: --",
    val patientId: String = "",
    val virtualPatientId: String = "",
    val saveStatus: String = "",
    val currentVideoName: String? = "",
    val currentInstruction: String = "",
    val currentFallback: String = "",
    val isBluetoothEnabled: Boolean = false,
    val bluetoothStatus: String = "Nincs csatlakoztatva",
    val dataPacketCount: Int = 0,
    val isPreviewPhase: Boolean = false,
    val viewPreference: ViewPreference = ViewPreference.BOTH,
    val showViewModeDialog: Boolean = false,
    val isPaused: Boolean = false,
    val showSaveDialog: Boolean = false
)