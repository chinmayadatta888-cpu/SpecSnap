package com.specsnap

import android.view.MotionEvent
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.view.Surface
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
fun CameraPreview(
    viewModel: CameraScanViewModel,
    modifier: Modifier = Modifier,
) {
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
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureStatus by remember { mutableStateOf<String?>(null) }
    val scanState by viewModel.uiState.collectAsState()
    var showComponentDetails by remember { mutableStateOf(false) }

    LaunchedEffect(scanState.component) {
        if (scanState.component != null) showComponentDetails = true
    }

    fun captureFrame(reticleBounds: RectF, previewSize: Size) {
        val captureUseCase = imageCapture ?: return
        if (isCapturing) return
        if (previewView.width <= 0 || previewView.height <= 0) {
            captureStatus = "Camera preview is not ready. Try again."
            return
        }

        isCapturing = true
        captureStatus = null
        try {
            captureUseCase.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            captureUseCase.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            require(image.format == ImageFormat.JPEG) {
                                "Camera capture returned an unsupported image format."
                            }
                            val jpegBuffer = image.planes.firstOrNull()?.buffer?.duplicate()
                                ?: throw IllegalArgumentException("Camera capture returned no image data.")
                            val jpegBytes = ByteArray(jpegBuffer.remaining())
                            jpegBuffer.get(jpegBytes)
                            viewModel.analyzeCapturedImage(
                                jpegBytes = jpegBytes,
                                cameraCropRect = Rect(image.cropRect),
                                rotationDegrees = image.imageInfo.rotationDegrees,
                                previewBounds = RectF(reticleBounds),
                                previewSize = previewSize,
                            )
                        } catch (_: Exception) {
                            captureStatus = "Could not prepare the captured image. Try again."
                        } finally {
                            image.close()
                            isCapturing = false
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        isCapturing = false
                        captureStatus = "Could not capture an image. Try again."
                    }
                },
            )
        } catch (_: IllegalStateException) {
            isCapturing = false
            captureStatus = "Camera is not ready. Try again."
        }
    }

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
                            .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
                            .build()
                        imageCapture = imageCaptureUseCase
                        camera = cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            previewUseCase,
                            imageCaptureUseCase,
                        )
                        cameraUnavailable = false
                    } catch (_: Exception) {
                        camera = null
                        imageCapture = null
                        cameraUnavailable = true
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            camera = null
            imageCapture = null
            cameraProvider?.unbindAll()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        ViewfinderOverlay(
            onManualTrigger = ::captureFrame,
            captureEnabled = imageCapture != null && !cameraUnavailable && !scanState.isAnalyzing,
            isCapturing = isCapturing,
            statusMessage = captureStatus ?: scanState.statusMessage,
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
        if (showComponentDetails) {
            scanState.component?.let { component ->
                ComponentDetailSheet(
                    component = component,
                    onDismiss = { showComponentDetails = false },
                )
            }
        }
    }
}
