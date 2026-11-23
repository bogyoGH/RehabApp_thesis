package org.example.project.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
actual fun VideoPlayer(videoName: String, isPaused: Boolean, modifier: Modifier) {
    val context = LocalContext.current


    val exoPlayer = remember(videoName) {
        ExoPlayer.Builder(context).build().apply {


            val uri = if (videoName.startsWith("http://", ignoreCase = true) || videoName.startsWith("https://", ignoreCase = true)) {
                Uri.parse(videoName) // Internetes videó link
            } else {
                val videoResId = context.resources.getIdentifier(videoName, "raw", context.packageName)
                if (videoResId != 0) {
                    Uri.parse("android.resource://${context.packageName}/$videoResId") // Helyi raw fájl
                } else null
            }

            if (uri != null) {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ALL // Végtelenítés
                prepare()
                playWhenReady = true // Automatikus indulás
            }
        }
    }

    LaunchedEffect(isPaused) {
        if (isPaused) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    // Memóriaszivárgás megelőzése
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // A natív Androidos nézet beágyazása a Compose-ba
    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = false // Lejátszásvezérlők elrejtése
            }
        },
        modifier = modifier
    )
}