package com.specsnap

import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.TimeUnit

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            contentDescription = "Live camera preview"
            isClickable = true
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    view.performClick()
                    camera?.let { activeCamera ->
                        val meteringPoint = meteringPointFactory.createPoint(event.x, event.y)
                        val focusAndExposure = FocusMeteringAction.Builder(meteringPoint)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        activeCamera.cameraControl.startFocusAndMetering(focusAndExposure)
                    }
                }
                true
            }
        }
    }
    var cameraUnavailable by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context.applicationContext)
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        cameraProviderFuture.addListener(
            {
                if (!disposed) {
                    try {
                        cameraProvider = cameraProviderFuture.get()
                        val previewUseCase = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageCaptureUseCase = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        camera = cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            previewUseCase,
                            imageCaptureUseCase,
                        )
                        cameraUnavailable = false
                    } catch (_: Exception) {
                        camera = null
                        cameraUnavailable = true
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            camera = null
            cameraProvider?.unbindAll()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        if (cameraUnavailable) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                Text(
                    text = "Camera preview is unavailable. Check that a camera is available and try again.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
