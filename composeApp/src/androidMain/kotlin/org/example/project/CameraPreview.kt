package org.example.project.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview

@Composable
actual fun CameraPreview(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                // --- EZ A LEGFONTOSABB SOR ---
                // A COMPATIBLE mód TextureView-t használ, ami engedi a Compose-nak,
                // hogy levágja a széleit (clip), így nem fog kilógni a keretből.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                // Kitölti a 200dp-t, a felesleget pedig levágja
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                } catch (e: Exception) { e.printStackTrace() }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}