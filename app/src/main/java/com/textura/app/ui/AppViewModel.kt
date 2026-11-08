package com.textura.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.textura.app.data.Exporter
import com.textura.app.data.HistoryItem
import com.textura.app.data.ImageFormat
import com.textura.app.data.Settings
import com.textura.app.data.Store
import com.textura.app.model.CanvasShape
import com.textura.app.model.Catalogue
import com.textura.app.model.Look
import com.textura.app.render.Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

object Samples {
    val all = listOf(
        "Write it down. Make it beautiful.\nShare it with someone who needs to read it today.",
        "Some days you carry the whole sky.\nOther days the sky carries you.\nEither way, keep walking.",
        "Dear future me,\nI hope you finally learned to rest without guilt, to say no without explaining, and to keep the small joys close.\nYours, today.",
        "Good design is invisible until it is missing. Make the words fill the page, let the shape decide the rhythm, and choose the one look that feels like you.",
    )
}

enum class ExportResult { Saved, Shared, Failed }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val WORKING_ID = "working"
        private const val HISTORY_MAX = 40
    }

    private val store = Store(app)

    var settings by mutableStateOf(store.loadSettings()); private set
    var text by mutableStateOf(store.loadText() ?: Samples.all.first()); private set
    var shape by mutableStateOf(settings.defaultShape); private set
    var favorites by mutableStateOf(store.loadFavorites()); private set
    var custom by mutableStateOf(store.loadCustom()); private set
    var history by mutableStateOf(store.loadHistory()); private set

    /** The look the user is editing / has edited; shown as the first page of the browser. */
    var working by mutableStateOf<Look?>(null); private set
    var browserIndex by mutableIntStateOf(0)

    val looks: List<Look> get() = listOfNotNull(working) + Catalogue.all + custom

    // ---------------------------------------------------------------- text & shape
    fun updateText(value: String) {
        text = value
        store.saveText(value)
    }

    fun randomSample() {
        val others = Samples.all.filter { it != text }
        updateText(others.random())
    }

    fun updateShape(value: CanvasShape) {
        shape = value
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        settings = transform(settings)
        store.saveSettings(settings)
    }

    // ---------------------------------------------------------------- editing with undo / redo
    var editing by mutableStateOf<Look?>(null); private set
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    private var editBase: Look? = null
    private val undoStack = ArrayList<Look>()
    private val redoStack = ArrayList<Look>()
    private var lastPush = 0L

    fun startEditing(look: Look) {
        editBase = if (look.id == WORKING_ID) (editBase ?: look) else look
        editing = look.copy(id = WORKING_ID)
        undoStack.clear()
        redoStack.clear()
        lastPush = 0L
        refreshFlags()
    }

    fun edit(transform: (Look) -> Look) {
        val current = editing ?: return
        val next = transform(current).copy(id = WORKING_ID)
        if (next == current) return
        val now = SystemClock.elapsedRealtime()
        // changes arriving in a quick burst (a slider drag) collapse into one undo step
        if (undoStack.isEmpty() || now - lastPush > 700L) {
            undoStack.add(current)
            if (undoStack.size > 60) undoStack.removeAt(0)
        }
        lastPush = now
        redoStack.clear()
        editing = next
        refreshFlags()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        editing?.let { redoStack.add(it) }
        editing = undoStack.removeAt(undoStack.size - 1)
        lastPush = 0L
        refreshFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        editing?.let { undoStack.add(it) }
        editing = redoStack.removeAt(redoStack.size - 1)
        lastPush = 0L
        refreshFlags()
    }

    fun resetToTemplate() {
        val base = editBase ?: return
        edit { base }
    }

    /** Commits the edit so it appears as the first page of the browser. */
    fun finishEditing() {
        editing?.let {
            working = it
            browserIndex = 0
        }
    }

    private fun refreshFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun importPhoto(uri: Uri): String? = store.importPhoto(uri)

    // ---------------------------------------------------------------- favourites & templates
    fun isFavorite(look: Look) = look.id in favorites

    fun toggleFavorite(look: Look) {
        if (look.id == WORKING_ID) {
            saveAsTemplate(look, "My look ${custom.size + 1}")
            return
        }
        favorites = if (look.id in favorites) favorites - look.id else favorites + look.id
        store.saveFavorites(favorites)
    }

    fun favoriteLooks(): List<Look> = (Catalogue.all + custom).filter { it.id in favorites }

    fun saveAsTemplate(look: Look, name: String) {
        val t = look.copy(id = "custom_" + UUID.randomUUID(), name = name.trim().ifEmpty { "My look" })
        custom = custom + t
        store.saveCustom(custom)
    }

    fun deleteTemplate(id: String) {
        custom = custom.filter { it.id != id }
        favorites = favorites - id
        store.saveCustom(custom)
        store.saveFavorites(favorites)
    }

    /** Jump the browser to a given look. */
    fun openLook(look: Look) {
        val idx = looks.indexOfFirst { it.id == look.id }
        browserIndex = if (idx >= 0) idx else 0
    }

    // ---------------------------------------------------------------- history
    fun thumb(id: String): Bitmap? = store.loadThumb(id)

    fun restore(item: HistoryItem) {
        updateText(item.text)
        shape = item.shape
        working = item.look.copy(id = WORKING_ID)
        browserIndex = 0
    }

    fun deleteHistory(item: HistoryItem) {
        store.deleteThumb(item.id)
        history = history.filter { it.id != item.id }
        store.saveHistory(history)
    }

    private fun record(look: Look, bmp: Bitmap) {
        val id = UUID.randomUUID().toString()
        val f = 480f / max(bmp.width, bmp.height)
        val thumb = Bitmap.createScaledBitmap(bmp, max(2, (bmp.width * f).roundToInt()), max(2, (bmp.height * f).roundToInt()), true)
        store.saveThumb(id, thumb)
        val item = HistoryItem(id, System.currentTimeMillis(), text, shape, look.copy(id = look.id))
        val next = listOf(item) + history
        next.drop(HISTORY_MAX).forEach { store.deleteThumb(it.id) }
        history = next.take(HISTORY_MAX)
        store.saveHistory(history)
    }

    // ---------------------------------------------------------------- export
    suspend fun export(
        look: Look,
        share: Boolean,
        longEdge: Int,
        format: ImageFormat,
        transparent: Boolean,
    ): ExportResult {
        val ctx = getApplication<Application>()
        val s = settings
        val currentText = text
        val currentShape = shape
        return withContext(Dispatchers.Default) {
            val bmp = try {
                Renderer.render(ctx, look, currentText, currentShape, longEdge, transparent && format == ImageFormat.PNG)
            } catch (e: OutOfMemoryError) {
                return@withContext ExportResult.Failed
            }
            val ok = if (share) {
                Exporter.share(ctx, bmp, format, s.jpgQuality)
            } else {
                Exporter.saveToGallery(ctx, bmp, format, s.jpgQuality) != null
            }
            if (ok) withContext(Dispatchers.Main) { record(look, bmp) }
            bmp.recycle()
            if (!ok) ExportResult.Failed else if (share) ExportResult.Shared else ExportResult.Saved
        }
    }
}
