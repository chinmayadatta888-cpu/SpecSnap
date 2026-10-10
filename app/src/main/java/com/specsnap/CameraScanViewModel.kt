package com.specsnap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.specsnap.data.repository.GeminiRepository
import com.specsnap.domain.model.ComponentResponse
import com.specsnap.util.BitmapCropUtils
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CameraScanUiState(
    val isAnalyzing: Boolean = false,
    val statusMessage: String? = null,
    val component: ComponentResponse? = null,
)

/** Owns the capture-to-analysis work and exposes only screen state to Compose. */
class CameraScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraScanUiState())
    val uiState: StateFlow<CameraScanUiState> = _uiState.asStateFlow()

    private var geminiRepository: GeminiRepository? = null

    fun analyzeCapturedImage(
        jpegBytes: ByteArray,
        cameraCropRect: Rect,
        rotationDegrees: Int,
        previewBounds: RectF,
        previewSize: Size,
    ) {
        if (_uiState.value.isAnalyzing) return

        _uiState.value = CameraScanUiState(
            isAnalyzing = true,
            statusMessage = "Preparing component image…",
        )
        viewModelScope.launch {
            try {
                val croppedImage = withContext(Dispatchers.IO) {
                    cropCapturedImage(
                        jpegBytes = jpegBytes,
                        cameraCropRect = cameraCropRect,
                        rotationDegrees = rotationDegrees,
                        previewBounds = previewBounds,
                        previewSize = previewSize,
                    )
                }
                _uiState.value = _uiState.value.copy(statusMessage = "Analyzing component…")

                val jsonResponse = (geminiRepository ?: GeminiRepository().also {
                    geminiRepository = it
                }).analyzeComponent(croppedImage)
                val component = Json.decodeFromString<ComponentResponse>(jsonResponse)
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Identified ${component.partNumber} · ${component.category}",
                    component = component,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = if (BuildConfig.GEMINI_API_KEY.isBlank()) {
                        "Add GEMINI_API_KEY to local.properties, then rebuild the app."
                    } else {
                        "Analysis failed. Check the connection and try capturing again."
                    },
                    component = null,
                )
            } finally {
                _uiState.value = _uiState.value.copy(isAnalyzing = false)
            }
        }
    }

    override fun onCleared() {
        geminiRepository?.close()
        geminiRepository = null
        super.onCleared()
    }

    private fun cropCapturedImage(
        jpegBytes: ByteArray,
        cameraCropRect: Rect,
        rotationDegrees: Int,
        previewBounds: RectF,
        previewSize: Size,
    ): ByteArray {
        var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: throw IllegalArgumentException("The captured image could not be decoded.")

        try {
            bitmap = BitmapCropUtils.cropRegion(bitmap, RectF(cameraCropRect)).also { cropped ->
                if (cropped !== bitmap) bitmap.recycle()
            }

            require(rotationDegrees in setOf(0, 90, 180, 270)) {
                "Unsupported camera image rotation: $rotationDegrees degrees."
            }
            if (rotationDegrees != 0) {
                val rotation = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotation, true)
                    .also { rotated -> if (rotated !== bitmap) bitmap.recycle() }
            }

            bitmap = BitmapCropUtils.cropPreviewRegion(
                source = bitmap,
                previewBounds = previewBounds,
                previewSize = previewSize,
            ).also { cropped -> if (cropped !== bitmap) bitmap.recycle() }

            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "The cropped component image could not be encoded."
                }
                output.toByteArray()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private companion object {
        const val JPEG_QUALITY = 95
    }
}
