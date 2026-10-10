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
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    isRecording: Boolean,
    outputFileName: String?,
    onVideoSaved: ((String) -> Unit)?
) {
    var isCameraAuthorized by remember { mutableStateOf(false) }

    // AVCapture objektumok megjegyzése a recomposition túléléséhez
    val captureSession = remember { AVCaptureSession() }
    val movieFileOutput = remember { AVCaptureMovieFileOutput() }

    // Delegate a videórögzítés befejezésének lekezeléséhez (Kotlin/Native interop)
    val recordingDelegate = remember {
        object : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {
            override fun captureOutput(
                output: AVCaptureFileOutput,
                didFinishRecordingToOutputFileAtURL: NSURL,
                fromConnections: List<*>,
                error: platform.Foundation.NSError?
            ) {
                if (error == null) {
                    onVideoSaved?.invoke(didFinishRecordingToOutputFileAtURL.path ?: "")
                } else {
                    println("Hiba a videó mentésekor: ${error.localizedDescription}")
                }
            }
        }
    }

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

    // A rögzítés elindítása és leállítása a paraméter változásakor
    LaunchedEffect(isRecording) {
        if (isRecording && outputFileName != null) {
            // Ideiglenes könyvtár használata iOS-en a rögzítéshez
            val filePath = NSTemporaryDirectory() + outputFileName
            val fileUrl = NSURL.fileURLWithPath(filePath)

            movieFileOutput.startRecordingToOutputFileURL(fileUrl, recordingDelegate)
        } else {
            if (movieFileOutput.isRecording()) {
                movieFileOutput.stopRecording()
            }
        }
    }

    if (isCameraAuthorized) {
        UIKitView(
            modifier = modifier.fillMaxSize(),
            factory = {
                captureSession.sessionPreset = AVCaptureSessionPresetMedium

                val device = AVCaptureDevice.defaultDeviceWithDeviceType(
                    deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
                    mediaType = AVMediaTypeVideo,
                    position = AVCaptureDevicePositionFront
                )

                if (device != null) {
                    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) as? AVCaptureDeviceInput
                    if (input != null && captureSession.canAddInput(input)) {
                        captureSession.addInput(input)
                    }
                }

                // Hozzáadjuk a videórögzítő kimenetet a sessionhöz
                if (captureSession.canAddOutput(movieFileOutput)) {
                    captureSession.addOutput(movieFileOutput)
                }

                val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
                previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill

                val view = UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
                view.layer.addSublayer(previewLayer)

                platform.Foundation.NSOperationQueue.mainQueue.addOperationWithBlock {
                    captureSession.startRunning()
                }

                view
            },
            update = { view ->
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