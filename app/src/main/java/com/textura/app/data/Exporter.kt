package com.textura.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    private fun fileName(format: ImageFormat): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "Textura_$stamp.${format.ext}"
    }

    private fun compress(bmp: Bitmap, format: ImageFormat, quality: Int, out: java.io.OutputStream): Boolean =
        when (format) {
            ImageFormat.PNG -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            ImageFormat.JPG -> bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

    /** Saves to Pictures/Textura in the gallery. Returns the content Uri or null on failure. */
    fun saveToGallery(context: Context, bmp: Bitmap, format: ImageFormat, quality: Int): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName(format))
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Textura")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val ok = resolver.openOutputStream(uri)?.use { compress(bmp, format, quality, it) } ?: false
            if (!ok) throw IllegalStateException("compress failed")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /** Writes the image to the cache and opens the Android share sheet. */
    fun share(context: Context, bmp: Bitmap, format: ImageFormat, quality: Int): Boolean {
        return try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, fileName(format))
            FileOutputStream(file).use { compress(bmp, format, quality, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = format.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Share design").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }
}
