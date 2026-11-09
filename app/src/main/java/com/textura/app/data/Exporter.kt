package com.textura.app.data

import android.content.ActivityNotFoundException
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
    const val WHATSAPP = "com.whatsapp"
    const val INSTAGRAM = "com.instagram.android"
    const val FACEBOOK = "com.facebook.katana"

    private fun fileName(format: ImageFormat, suffix: String = ""): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "Textura_$stamp$suffix.${format.ext}"
    }

    private fun compress(bmp: Bitmap, format: ImageFormat, quality: Int, out: java.io.OutputStream): Boolean =
        when (format) {
            ImageFormat.PNG -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            ImageFormat.JPG -> bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

    /** Saves to Pictures/Textura (or a sub-folder of it) in the gallery. Returns the content Uri or null on failure. */
    fun saveToGallery(
        context: Context,
        bmp: Bitmap,
        format: ImageFormat,
        quality: Int,
        subfolder: String? = null,
        suffix: String = "",
    ): Uri? {
        val resolver = context.contentResolver
        val folder = Environment.DIRECTORY_PICTURES + "/Textura" + if (subfolder != null) "/$subfolder" else ""
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName(format, suffix))
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, folder)
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

    fun clearShareCache(context: Context) {
        File(context.cacheDir, "shared").apply { mkdirs() }.listFiles()?.forEach { if (it.name != "scan.jpg") it.delete() }
    }

    /** Writes the image into the share cache and returns a shareable content Uri. */
    fun writeToShareCache(context: Context, bmp: Bitmap, format: ImageFormat, quality: Int, index: Int): Uri? {
        return try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, fileName(format, "_" + (index + 1)))
            FileOutputStream(file).use { compress(bmp, format, quality, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Opens the share sheet for one or more images. With [targetPackage] the image goes straight to that app
     * (WhatsApp, Instagram, Facebook); returns false when the app is missing.
     */
    fun shareUris(context: Context, uris: List<Uri>, format: ImageFormat, targetPackage: String?): Boolean {
        if (uris.isEmpty()) return false
        return try {
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
            }
            intent.type = format.mime
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val launch = if (targetPackage != null) {
                intent.setPackage(targetPackage)
                intent
            } else {
                Intent.createChooser(intent, "Share design")
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
