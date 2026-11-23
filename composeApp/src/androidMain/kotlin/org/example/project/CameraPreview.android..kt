package org.example.project.ui

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

@SuppressLint("MissingPermission")
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    isRecording: Boolean,
    outputFileName: String?,
    onVideoSaved: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val videoCapture = remember {
        val qualitySelector = QualitySelector.from(
            Quality.HD,
            FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        VideoCapture.withOutput(recorder)
    }

    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    // Előkészítjük a kamera nézetet
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // 1. Kamera hozzákötése az életciklushoz
    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            try {
                cameraProvider.unbindAll() // Tiszta lap
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    videoCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // 2. Videórögzítés indítása és leállítása az állapot alapján
    LaunchedEffect(isRecording) {
        if (isRecording && outputFileName != null) {
            val videoFile = File(context.filesDir, outputFileName)
            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            activeRecording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    if (recordEvent is VideoRecordEvent.Finalize) {
                        if (!recordEvent.hasError()) {
                            println("VIDEÓ SIKERESEN LEZÁRVA ÉS ELMENTVE: ${videoFile.absolutePath}")
                            onVideoSaved?.invoke(videoFile.absolutePath)
                        } else {
                            println("KAMERA HIBA: A videó rögzítése sikertelen! Kód: ${recordEvent.error}")
                        }
                    }
                }
        } else {
            activeRecording?.stop()
            activeRecording = null
        }
    }

    // 3. Maga a felület
    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )
}