package com.example.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Utilitários para captura, correção automática de orientação EXIF e preservação
 * máxima da qualidade original das fotos de produtos e evidências.
 */
object ImageCompressionUtils {

    private const val MAX_FIRESTORE_BINARY_BYTES = 735_000 // ~980 KB em Base64 (abaixo do limite de 1 MB do Firestore)
    private const val MAX_STORAGE_BINARY_BYTES = 8_500_000 // Limite seguro abaixo dos 10 MB do Storage

    /**
     * Cria uma URI temporária compatível com FileProvider para salvar fotos da câmera
     * em resolução nativa total (sem miniaturas).
     */
    fun createCameraPhotoUri(context: Context): Uri {
        val storageDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val photoFile = File(storageDir, "photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    /**
     * Lê a foto da câmera ou galeria preservando a qualidade original e corrigindo
     * a orientação EXIF (evita fotos deitadas/rotacionadas).
     * Se a imagem já estiver na orientação correta e couber no limite do Firestore sem recompressão,
     * preserva 100% dos bytes originais sem perda alguma.
     */
    fun readImageToHighQualityBase64(context: Context, uri: Uri): String? {
        return try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            if (rawBytes.isEmpty()) return null

            val rotationDegrees = readExifRotationDegrees(rawBytes)

            // Se não precisar rotacionar e o arquivo original já couber no documento Firestore,
            // preserva 100% os bytes originais sem recomprimir.
            if (rotationDegrees == 0 && rawBytes.size <= MAX_FIRESTORE_BINARY_BYTES) {
                return Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            }

            val decoded = decodeBytesKeepingResolution(rawBytes, maxDimension = 3072) ?: return null
            val uprightBitmap = rotateBitmapIfNeeded(decoded, rotationDegrees)

            resizeAndCompressBitmap(
                bitmap = uprightBitmap,
                maxDimension = 2560,
                initialQuality = 96,
                maxBytes = MAX_FIRESTORE_BINARY_BYTES
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lê os bytes da foto em qualidade original/máxima (até 8.5 MB) para envio ao Firebase Storage.
     */
    fun readOriginalBytesWithOrientation(context: Context, uri: Uri): ByteArray? {
        return try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            if (rawBytes.isEmpty()) return null

            val rotationDegrees = readExifRotationDegrees(rawBytes)
            if (rotationDegrees == 0 && rawBytes.size <= MAX_STORAGE_BINARY_BYTES) {
                return rawBytes
            }

            val decoded = decodeBytesKeepingResolution(rawBytes, maxDimension = 4096) ?: return rawBytes
            val uprightBitmap = rotateBitmapIfNeeded(decoded, rotationDegrees)

            var quality = 96
            var outBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                uprightBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                outBytes = out.toByteArray()
                quality -= 4
            } while (outBytes.size > MAX_STORAGE_BINARY_BYTES && quality >= 82)
            outBytes
        } catch (_: Exception) {
            null
        }
    }

    private fun readExifRotationDegrees(bytes: ByteArray): Int {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun decodeBytesKeepingResolution(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        var inSampleSize = 1
        if (rawWidth > maxDimension || rawHeight > maxDimension) {
            val largest = maxOf(rawWidth, rawHeight)
            while ((largest / inSampleSize) > maxDimension * 1.4) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    /**
     * Decodifica a foto mantendo a resolução original cristalina com orientação EXIF corrigida.
     */
    fun decodeScaledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 3072): Bitmap? {
        return try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val decoded = decodeBytesKeepingResolution(rawBytes, maxDimension) ?: return null
            val rotationDegrees = readExifRotationDegrees(rawBytes)
            rotateBitmapIfNeeded(decoded, rotationDegrees)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Preserva alta fidelidade e resolução da foto original respeitando o limite seguro do Firestore.
     */
    fun resizeAndCompressBitmap(
        bitmap: Bitmap,
        maxDimension: Int = 2560,
        initialQuality: Int = 95,
        maxBytes: Int = MAX_FIRESTORE_BINARY_BYTES
    ): String {
        val width = bitmap.width
        val height = bitmap.height
        val scale = if (width > maxDimension || height > maxDimension) {
            val maxSide = maxOf(width, height)
            maxDimension.toFloat() / maxSide
        } else 1.0f

        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else bitmap

        var workingBitmap = scaledBitmap
        var currentQuality = initialQuality
        var bytes: ByteArray
        do {
            val outputStream = ByteArrayOutputStream()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
            bytes = outputStream.toByteArray()
            currentQuality -= 3
        } while (bytes.size > maxBytes && currentQuality >= 72)

        // Fallback seguro caso a foto 12MP+ seja muito densa: reduz levemente a dimensão para garantir caber no Firestore
        var safetyScale = 0.85f
        while (bytes.size > maxBytes && workingBitmap.width > 640 && workingBitmap.height > 640 && safetyScale >= 0.4f) {
            val nextW = (workingBitmap.width * 0.85f).toInt().coerceAtLeast(1)
            val nextH = (workingBitmap.height * 0.85f).toInt().coerceAtLeast(1)
            workingBitmap = Bitmap.createScaledBitmap(workingBitmap, nextW, nextH, true)
            val outputStream = ByteArrayOutputStream()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, 78, outputStream)
            bytes = outputStream.toByteArray()
            safetyScale -= 0.15f
        }

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return try {
            val cleaned = if (base64.contains(",")) {
                base64.substringAfter(",").trim()
            } else {
                base64.trim()
            }
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
