package com.textura.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.textura.app.model.CanvasShape
import com.textura.app.model.Look
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class ThemeMode { System, Dark, Light }
enum class ImageFormat(val label: String, val ext: String, val mime: String) {
    PNG("PNG", "png", "image/png"),
    JPG("JPG", "jpg", "image/jpeg"),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.Dark,
    val defaultShape: CanvasShape = CanvasShape.Portrait,
    val defaultLongEdge: Int = 2048,
    val defaultFormat: ImageFormat = ImageFormat.PNG,
    val jpgQuality: Int = 92,
    val haptics: Boolean = true,
)

data class HistoryItem(
    val id: String = "",
    val time: Long = 0L,
    val text: String = "",
    val shape: CanvasShape = CanvasShape.Portrait,
    val look: Look = Look(),
)

/** Small JSON-in-SharedPreferences store. Everything stays on the device. */
class Store(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("textura", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun <T> read(key: String, type: TypeToken<T>, fallback: T): T =
        runCatching { gson.fromJson<T>(prefs.getString(key, null), type.type) }.getOrNull() ?: fallback

    private fun write(key: String, value: Any) {
        prefs.edit().putString(key, gson.toJson(value)).apply()
    }

    fun loadText(): String? = prefs.getString("text", null)
    fun saveText(text: String) = prefs.edit().putString("text", text).apply()

    fun loadSettings(): Settings = read("settings", object : TypeToken<Settings>() {}, Settings())
    fun saveSettings(s: Settings) = write("settings", s)

    fun loadFavorites(): Set<String> = read("favorites", object : TypeToken<List<String>>() {}, emptyList<String>()).toSet()
    fun saveFavorites(f: Set<String>) = write("favorites", f.toList())

    fun loadCustom(): List<Look> = read("custom", object : TypeToken<List<Look>>() {}, emptyList<Look>())
    fun saveCustom(l: List<Look>) = write("custom", l)

    fun loadHistory(): List<HistoryItem> = read("history", object : TypeToken<List<HistoryItem>>() {}, emptyList<HistoryItem>())
    fun saveHistory(h: List<HistoryItem>) = write("history", h)

    // ---- thumbnails for history -------------------------------------------------------------
    private fun thumbDir(): File = File(app.filesDir, "thumbs").apply { mkdirs() }
    fun thumbFile(id: String): File = File(thumbDir(), "$id.jpg")

    fun saveThumb(id: String, bmp: Bitmap) {
        runCatching { FileOutputStream(thumbFile(id)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) } }
    }

    fun deleteThumb(id: String) {
        thumbFile(id).delete()
    }

    fun loadThumb(id: String): Bitmap? = runCatching { BitmapFactory.decodeFile(thumbFile(id).absolutePath) }.getOrNull()

    // ---- imported background photos ---------------------------------------------------------
    /** Copies a picked photo into app storage (max 2400 px) so it keeps working after the picker closes. */
    fun importPhoto(uri: Uri): String? = runCatching {
        val source = ImageDecoder.createSource(app.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > 2400) {
                val f = 2400f / longest
                decoder.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
            }
        }
        val dir = File(app.filesDir, "bg").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }.getOrNull()
}
