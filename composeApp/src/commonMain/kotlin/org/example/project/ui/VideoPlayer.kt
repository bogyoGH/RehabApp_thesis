package org.example.project.ui


import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayer(videoName: String, modifier: Modifier = Modifier)