package com.fileflip.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

enum class ImageFormat(val label: String, val ext: String, val mime: String, val compress: Bitmap.CompressFormat) {
    PNG("PNG", "png", "image/png", Bitmap.CompressFormat.PNG),
    JPG("JPG", "jpg", "image/jpeg", Bitmap.CompressFormat.JPEG)
}

/** Renders every page of a PDF to an image using Android's built-in PdfRenderer (fully offline). */
object PdfConverter {

    private const val MAX_SIDE_PX = 3000   // caps memory use for very large pages
    private const val TARGET_SCALE = 2f    // ~144 dpi

    suspend fun pageCount(context: Context, source: Uri): Int = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(source, "r") ?: throw IOException("Cannot open file")
        pfd.use { PdfRenderer(it).use { r -> r.pageCount } }
    }

    /** Returns the saved image URIs (in Pictures/FileFlip/<name>_<timestamp>/). */
    suspend fun convert(
        context: Context,
        source: Uri,
        baseName: String,
        format: ImageFormat,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<Uri> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val folder = "${sanitize(baseName)}_$stamp"
        val saved = mutableListOf<Uri>()

        val pfd = resolver.openFileDescriptor(source, "r") ?: throw IOException("Cannot open file")
        try {
            pfd.use {
                PdfRenderer(it).use { renderer ->
                    val total = renderer.pageCount
                    if (total == 0) throw IOException("PDF has no pages")
                    for (i in 0 until total) {
                        coroutineContext.ensureActive()
                        renderer.openPage(i).use { page ->
                            val scale = minOf(TARGET_SCALE, MAX_SIDE_PX.toFloat() / maxOf(page.width, page.height))
                            val w = (page.width * scale).toInt().coerceAtLeast(1)
                            val h = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE) // PDFs are transparent by default
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val name = "${sanitize(baseName)}_page_%03d.%s".format(i + 1, format.ext)
                                saved += save(context, bitmap, name, folder, format)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        onProgress(i + 1, total)
                    }
                }
            }
        } catch (e: Throwable) {
            // Remove partial output if conversion fails or is cancelled.
            saved.forEach { runCatching { resolver.delete(it, null, null) } }
            throw e
        }
        saved
    }

    private fun save(context: Context, bitmap: Bitmap, fileName: String, folder: String, format: ImageFormat): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FileFlip/$folder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Cannot create output file")
        try {
            val ok = resolver.openOutputStream(uri)?.use { bitmap.compress(format.compress, 92, it) }
                ?: throw IOException("Cannot write output file")
            if (!ok) throw IOException("Image encoding failed")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun sanitize(name: String) = name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(60).ifBlank { "document" }
}
