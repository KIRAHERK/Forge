package com.forge.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.math.max

/** Progress photos live in app-private storage. Never in the gallery, never uploaded. */
object Photos {
    data class Photo(val file: File, val date: LocalDate)

    private const val MAX_PX = 1600

    fun dir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun list(ctx: Context): List<Photo> =
        dir(ctx).listFiles { f -> f.extension == "jpg" }
            ?.mapNotNull { f -> f.nameWithoutExtension.substringBefore('_').toLongOrNull()?.let { Photo(f, LocalDate.ofEpochDay(it)) } }
            ?.sortedBy { it.file.name }
            ?: emptyList()

    /** Temporary file the camera app writes into. */
    fun cameraUri(ctx: Context): Uri {
        val f = File(File(ctx.cacheDir, "cam").apply { mkdirs() }, "capture.jpg")
        if (f.exists()) f.delete()
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    /** Decodes (applying EXIF rotation where the platform supports it), downsizes and stores as JPEG. */
    suspend fun import(ctx: Context, uri: Uri, date: LocalDate): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = MAX_PX.toFloat() / max(w, h)
                    if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
                ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("decode failed")
            }
            val out = File(dir(ctx), "${date.toEpochDay()}_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            true
        }.getOrDefault(false)
    }

    suspend fun load(file: File, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }

    fun delete(p: Photo) = p.file.delete()
}
