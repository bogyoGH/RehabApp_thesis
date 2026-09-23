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

    // Név alapján megkeressük a fájl erőforrás ID-ját a res/raw mappában
    val videoResId = remember(videoName) {
        context.resources.getIdentifier(videoName, "raw", context.packageName)
    }

    // ExoPlayer inicializálása
    val exoPlayer = remember(videoResId) {
        ExoPlayer.Builder(context).build().apply {
            if (videoResId != 0) {
                val uri = Uri.parse("android.resource://${context.packageName}/$videoResId")
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
    if (videoResId != 0) {
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
}