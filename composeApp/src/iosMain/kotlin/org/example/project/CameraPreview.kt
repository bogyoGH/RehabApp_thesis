package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.UIKit.UIView
import platform.CoreGraphics.CGRectMake

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(modifier: Modifier) {
    var isCameraAuthorized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        when (status) {
            AVAuthorizationStatusAuthorized -> isCameraAuthorized = true
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    isCameraAuthorized = granted
                }
            }
            else -> isCameraAuthorized = false
        }
    }

    if (isCameraAuthorized) {
        UIKitView(
            modifier = modifier.fillMaxSize(),
            factory = {
                // Kamera session inicializálása
                val session = AVCaptureSession()
                session.sessionPreset = AVCaptureSessionPresetMedium

                // Előlapi kamera megkeresése
                val device = AVCaptureDevice.defaultDeviceWithDeviceType(
                    deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
                    mediaType = AVMediaTypeVideo,
                    position = AVCaptureDevicePositionFront
                )

                if (device != null) {
                    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) as? AVCaptureDeviceInput
                    if (input != null && session.canAddInput(input)) {
                        session.addInput(input)
                    }
                }

                val previewLayer = AVCaptureVideoPreviewLayer(session = session)
                previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill

                val view = UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
                view.layer.addSublayer(previewLayer)

                // Indítás egy háttérszálon, hogy ne blokkolja a UI-t
                platform.Foundation.NSOperationQueue.mainQueue.addOperationWithBlock {
                    session.startRunning()
                }

                // Amikor a view mérete változik, frissítjük a layer méretét
                view.layer.setNeedsLayout()
                view.layer.layoutIfNeeded()

                view
            },
            update = { view ->
                // Frissítjük a preview layer keretét a szülő nézet méretére
                val layer = view.layer.sublayers?.firstOrNull() as? AVCaptureVideoPreviewLayer
                layer?.frame = view.bounds
            }
        )
    } else {
        Box(modifier = modifier.background(Color.DarkGray), contentAlignment = Alignment.Center) {
            Text("Kamera hozzáférés megtagadva", color = Color.White)
        }
    }
}