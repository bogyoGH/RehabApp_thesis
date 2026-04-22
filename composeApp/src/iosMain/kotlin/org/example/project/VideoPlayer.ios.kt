package org.example.project.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(videoName: String, modifier: Modifier) {
    val player = remember(videoName) {
        // Megkeressük a videót az app bundle-ben
        val path = NSBundle.mainBundle.pathForResource(videoName, ofType = "mp4")

        if (path != null) {
            val url = NSURL.fileURLWithPath(path)
            AVPlayer(uRL = url)
        } else {
            null
        }
    }

    if (player != null) {
        // iOS natív UIView beágyazása a Compose-ba
        UIKitView(
            factory = {
                val view = UIView()
                val playerLayer = AVPlayerLayer()
                playerLayer.player = player
                playerLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
                view.layer.addSublayer(playerLayer)

                // Végtelenítés iOS-en (opcionális, de hasznos)
                player.actionAtItemEnd = AVPlayerActionAtItemEndNone

                view
            },
            modifier = modifier,
            update = { view ->
                // Méretezés frissítése és lejátszás
                val playerLayer = view.layer.sublayers?.firstOrNull() as? AVPlayerLayer
                playerLayer?.frame = view.bounds
                player.play()
            }
        )
    }
}