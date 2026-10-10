package org.example.project.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    outputFileName: String? = null,
    onVideoSaved: ((String) -> Unit)? = null
)