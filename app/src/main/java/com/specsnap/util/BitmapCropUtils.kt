package com.specsnap.util

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Size
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Cropping and coordinate mapping helpers for the FILL_CENTER camera preview. */
object BitmapCropUtils {

    /**
     * Crops a bitmap-space rectangle without resizing or distorting the selected region.
     * Bounds use pixel coordinates with a right/bottom-exclusive edge.
     */
    fun cropRegion(source: Bitmap, targetBounds: RectF): Bitmap {
        require(!source.isRecycled) { "Cannot crop a recycled bitmap." }
        requireValidRect(targetBounds)

        val clippedBounds = clipToBounds(
            targetBounds = targetBounds,
            width = source.width.toFloat(),
            height = source.height.toFloat(),
            description = "Bitmap",
        )
        val left = floor(clippedBounds.left).toInt().coerceIn(0, source.width)
        val top = floor(clippedBounds.top).toInt().coerceIn(0, source.height)
        val right = ceil(clippedBounds.right).toInt().coerceIn(0, source.width)
        val bottom = ceil(clippedBounds.bottom).toInt().coerceIn(0, source.height)
        require(right > left && bottom > top) { "The crop rectangle contains no bitmap pixels." }

        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    /**
     * Maps a rectangle in PreviewView-local pixel coordinates to an upright source bitmap.
     * The preview is assumed to use PreviewView.ScaleType.FILL_CENTER, as CameraPreview does.
     * The bitmap must have the same upright orientation as the displayed preview.
     */
    fun mapPreviewBoundsToBitmap(
        previewBounds: RectF,
        previewSize: Size,
        bitmapSize: Size,
    ): RectF {
        requireValidSize(previewSize, "Preview")
        requireValidSize(bitmapSize, "Bitmap")
        requireValidRect(previewBounds)

        val clippedPreviewBounds = clipToBounds(
            targetBounds = previewBounds,
            width = previewSize.width.toFloat(),
            height = previewSize.height.toFloat(),
            description = "Preview",
        )

        // FILL_CENTER scales uniformly until the preview is filled, then center-crops overflow.
        val scale = max(
            previewSize.width.toFloat() / bitmapSize.width,
            previewSize.height.toFloat() / bitmapSize.height,
        )
        val scaledWidth = bitmapSize.width * scale
        val scaledHeight = bitmapSize.height * scale
        val offsetX = (previewSize.width - scaledWidth) / 2f
        val offsetY = (previewSize.height - scaledHeight) / 2f

        val mappedBounds = RectF(
            (clippedPreviewBounds.left - offsetX) / scale,
            (clippedPreviewBounds.top - offsetY) / scale,
            (clippedPreviewBounds.right - offsetX) / scale,
            (clippedPreviewBounds.bottom - offsetY) / scale,
        )
        return clipToBounds(
            targetBounds = mappedBounds,
            width = bitmapSize.width.toFloat(),
            height = bitmapSize.height.toFloat(),
            description = "Mapped bitmap",
        )
    }

    /** Maps a preview-space rectangle and crops it directly from the supplied bitmap. */
    fun cropPreviewRegion(
        source: Bitmap,
        previewBounds: RectF,
        previewSize: Size,
    ): Bitmap {
        require(!source.isRecycled) { "Cannot crop a recycled bitmap." }
        val bitmapBounds = mapPreviewBoundsToBitmap(
            previewBounds = previewBounds,
            previewSize = previewSize,
            bitmapSize = Size(source.width, source.height),
        )
        return cropRegion(source, bitmapBounds)
    }

    private fun requireValidSize(size: Size, name: String) {
        require(size.width > 0 && size.height > 0) { "$name dimensions must be positive." }
    }

    private fun requireValidRect(rect: RectF) {
        require(
            rect.left.isFinite() && rect.top.isFinite() &&
                rect.right.isFinite() && rect.bottom.isFinite() &&
                rect.right > rect.left && rect.bottom > rect.top
        ) { "The crop rectangle must have finite, positive dimensions." }
    }

    private fun clipToBounds(
        targetBounds: RectF,
        width: Float,
        height: Float,
        description: String,
    ): RectF {
        val clipped = RectF(
            targetBounds.left.coerceIn(0f, width),
            targetBounds.top.coerceIn(0f, height),
            targetBounds.right.coerceIn(0f, width),
            targetBounds.bottom.coerceIn(0f, height),
        )
        require(clipped.right > clipped.left && clipped.bottom > clipped.top) {
            "The crop rectangle does not intersect the $description bounds."
        }
        return clipped
    }
}
