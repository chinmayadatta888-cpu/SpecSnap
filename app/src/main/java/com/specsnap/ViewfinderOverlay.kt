package com.specsnap

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.RectF
import android.util.Size

private const val RETICLE_WIDTH_FRACTION = 0.76f
private const val RETICLE_HEIGHT_FRACTION = 0.34f

@Composable
fun ViewfinderOverlay(
    onManualTrigger: (reticleBounds: RectF, previewSize: Size) -> Unit,
    modifier: Modifier = Modifier,
    captureEnabled: Boolean = true,
    isCapturing: Boolean = false,
    statusMessage: String? = null,
) {
    val scanTransition = rememberInfiniteTransition(label = "scan-line")
    val scanProgress by scanTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan-progress",
    )
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { overlaySize = it }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidth = size.width * RETICLE_WIDTH_FRACTION
            val frameHeight = size.height * RETICLE_HEIGHT_FRACTION
            val left = (size.width - frameWidth) / 2f
            val top = (size.height - frameHeight) / 2f
            val right = left + frameWidth
            val bottom = top + frameHeight
            val cornerLength = minOf(frameWidth, frameHeight) * 0.16f
            val strokeWidth = 3.dp.toPx()

            // Draw the four corner brackets of the component reticle.
            drawLine(
                Color.White,
                Offset(left, top + cornerLength),
                Offset(left, top),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(left, top),
                Offset(left + cornerLength, top),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(right - cornerLength, top),
                Offset(right, top),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(right, top),
                Offset(right, top + cornerLength),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(left, bottom - cornerLength),
                Offset(left, bottom),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(left, bottom),
                Offset(left + cornerLength, bottom),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(right - cornerLength, bottom),
                Offset(right, bottom),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(right, bottom - cornerLength),
                Offset(right, bottom),
                strokeWidth,
                StrokeCap.Round,
            )

            val scanY = top + frameHeight * scanProgress
            drawLine(
                color = Color(0xFF67E8F9),
                start = Offset(left + cornerLength * 0.4f, scanY),
                end = Offset(right - cornerLength * 0.4f, scanY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = {
                val width = overlaySize.width.toFloat()
                val height = overlaySize.height.toFloat()
                val frameWidth = width * RETICLE_WIDTH_FRACTION
                val frameHeight = height * RETICLE_HEIGHT_FRACTION
                val left = (width - frameWidth) / 2f
                val top = (height - frameHeight) / 2f
                onManualTrigger(
                    RectF(left, top, left + frameWidth, top + frameHeight),
                    Size(overlaySize.width, overlaySize.height),
                )
            },
            enabled = captureEnabled && !isCapturing && overlaySize.width > 0 && overlaySize.height > 0,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Text(if (isCapturing) "Capturing…" else "Capture component")
        }
    }
}
