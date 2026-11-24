package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.camera.core.ImageProxy
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.example.model.AppLanguage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class ScanStorageStats(
    val fileCount: Int = 0,
    val totalBytes: Long = 0L
) {
    fun formattedSize(language: AppLanguage = AppLanguage.ENGLISH): String {
        val isMn = language == AppLanguage.MONGOLIAN
        val mbUnit = if (isMn) "МБ" else "MB"
        val kbUnit = if (isMn) "КБ" else "KB"
        val bUnit = if (isMn) "Б" else "B"
        val kb = totalBytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f $mbUnit", mb)
            kb >= 1.0 -> "${kb.roundToInt()} $kbUnit"
            else -> "$totalBytes $bUnit"
        }
    }
}

data class VisualTelemetry(
    val width: Int,
    val height: Int,
    val brightnessPercent: Int,
    val warmCastScore: Int,
    val saturationPercent: Int,
    val sharpnessPercent: Int
) {
    val promptSummary: String
        get() {
            val lightingHint = when {
                brightnessPercent < 32 -> "dim/underexposed frame"
                brightnessPercent > 82 -> "bright/high-key frame"
                else -> "well-exposed frame"
            }
            val castHint = when {
                warmCastScore > 12 -> "warm yellow/tungsten cast (+$warmCastScore)"
                warmCastScore < -10 -> "cool blue/fluorescent cast ($warmCastScore)"
                else -> "neutral white balance"
            }
            val detailHint = if (sharpnessPercent < 25) "soft focus" else "crisp surface texture"
            val ratio = if (height > 0) String.format(Locale.US, "%.2f:1", width.toDouble() / height.toDouble()) else "1:1"
            return "${width}x${height} ($ratio), $lightingHint ($brightnessPercent% luminance), $castHint, $detailHint, chroma ${saturationPercent}%"
        }
}

object ImageUtils {

    fun loadScaledBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 640): Bitmap {
        val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                val width = info.size.width
                val height = info.size.height
                val largest = max(width, height)
                if (largest > maxDim) {
                    val scale = maxDim.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val exifOrientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                maxDim
            )
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream, null, decodeOptions)
                    ?: throw IllegalArgumentException("Could not decode selected image.")
                scaleDown(bmp, maxDim)
            } ?: throw IllegalArgumentException("Unable to open selected image.")

            applyExifOrientation(decoded, exifOrientation)
        }
        return if (rawBitmap.config == Bitmap.Config.ARGB_8888) {
            rawBitmap
        } else {
            rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    fun loadScaledBitmapFromDrawable(
        context: Context,
        @DrawableRes resId: Int,
        maxDim: Int = 640
    ): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeResource(context.resources, resId, options)
            ?: throw IllegalStateException("Could not load produce specimen image.")
        return scaleDown(decoded, maxDim)
    }

    fun loadBitmapFromFilePath(path: String?, maxDim: Int = 640): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@runCatching null
            scaleDown(decoded, maxDim)
        }.getOrNull()
    }

    fun imageProxyToBitmap(imageProxy: ImageProxy, maxDim: Int = 640): Bitmap {
        val rawBitmap = imageProxy.toBitmap()
        val scaled = scaleDown(rawBitmap, maxDim)
        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
        } else {
            scaled
        }
    }

    fun bitmapToJpegBytes(bitmap: Bitmap, maxDim: Int = 512, quality: Int = 72): ByteArray {
        val scaled = scaleDown(bitmap, maxDim)
        return ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
    }

    fun extractVisualTelemetry(bitmap: Bitmap): VisualTelemetry {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) {
            return VisualTelemetry(0, 0, 50, 0, 40, 40)
        }
        val sampleCols = 16
        val sampleRows = 16
        val stepX = max(1, w / sampleCols)
        val stepY = max(1, h / sampleRows)

        var count = 0
        var lumaSum = 0.0
        var warmDiffSum = 0.0
        var satSum = 0.0
        var edgeSum = 0.0
        var prevLuma = -1.0

        var y = stepY / 2
        while (y < h) {
            var x = stepX / 2
            while (x < w) {
                val pixel = bitmap[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val sat = if (maxC > 0) (maxC - minC).toDouble() / maxC.toDouble() else 0.0

                lumaSum += luma / 255.0
                warmDiffSum += (r - b) / 255.0
                satSum += sat
                if (prevLuma >= 0.0) {
                    edgeSum += abs(luma - prevLuma) / 255.0
                }
                prevLuma = luma
                count++
                x += stepX
            }
            prevLuma = -1.0
            y += stepY
        }

        if (count == 0) return VisualTelemetry(w, h, 50, 0, 40, 40)
        val brightness = ((lumaSum / count) * 100.0).roundToInt().coerceIn(0, 100)
        val warmCast = ((warmDiffSum / count) * 100.0).roundToInt().coerceIn(-100, 100)
        val saturation = ((satSum / count) * 100.0).roundToInt().coerceIn(0, 100)
        val sharpness = ((edgeSum / count) * 350.0).roundToInt().coerceIn(0, 100)
        return VisualTelemetry(
            width = w,
            height = h,
            brightnessPercent = brightness,
            warmCastScore = warmCast,
            saturationPercent = saturation,
            sharpnessPercent = sharpness
        )
    }

    fun saveScanBitmap(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "scans").apply { mkdirs() }
        val file = File(dir, "scan_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
        return file.absolutePath
    }

    fun deleteScanFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun getScanStorageStats(context: Context): ScanStorageStats {
        val dir = File(context.filesDir, "scans")
        if (!dir.exists() || !dir.isDirectory) return ScanStorageStats()
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        val bytes = files.sumOf { it.length() }
        return ScanStorageStats(fileCount = files.size, totalBytes = bytes)
    }

    fun clearAllScanFiles(context: Context): Int {
        val dir = File(context.filesDir, "scans")
        if (!dir.exists() || !dir.isDirectory) return 0
        val files = dir.listFiles() ?: return 0
        var deleted = 0
        for (file in files) {
            if (file.isFile && runCatching { file.delete() }.getOrDefault(false)) {
                deleted++
            }
        }
        return deleted
    }

    fun cleanupOrphanScanFiles(
        context: Context,
        activePaths: Set<String>,
        minAgeMillis: Long = 120_000L,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val dir = File(context.filesDir, "scans")
        if (!dir.exists() || !dir.isDirectory) return 0
        val files = dir.listFiles() ?: return 0
        var deletedCount = 0
        for (file in files) {
            if (!file.isFile) continue
            val isTracked = file.absolutePath in activePaths
            val isOldEnough = (nowMillis - file.lastModified()) >= minAgeMillis
            if (!isTracked && isOldEnough && runCatching { file.delete() }.getOrDefault(false)) {
                deletedCount++
            }
        }
        return deletedCount
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        if (largest > maxDim) {
            while ((largest / inSampleSize) > maxDim * 2) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / largest.toFloat()
        val targetW = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(targetW, targetH)
    }
}
