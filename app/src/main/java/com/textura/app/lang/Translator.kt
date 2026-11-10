package com.textura.app.lang

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Translation between Afrikaans and English, done on the phone with Google ML Kit. */
object Translator {
    sealed class Outcome {
        class Ok(val text: String) : Outcome()
        /** The language data could not be downloaded (needs an internet connection the first time). */
        object NeedsInternet : Outcome()
        class Failed(val reason: String) : Outcome()
    }

    private fun code(l: Lang) = if (l == Lang.Afrikaans) TranslateLanguage.AFRIKAANS else TranslateLanguage.ENGLISH

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun translate(text: String, from: Lang, to: Lang, onStatus: (String) -> Unit): Outcome {
        if (from == to) return Outcome.Ok(text)
        val options = TranslatorOptions.Builder().setSourceLanguage(code(from)).setTargetLanguage(code(to)).build()
        val client = Translation.getClient(options)
        try {
            onStatus("Preparing ${from.label} to ${to.label} (downloaded once, then works offline)…")
            try {
                client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            } catch (e: Exception) {
                return Outcome.NeedsInternet
            }
            onStatus("Translating…")
            val out = StringBuilder()
            val lines = text.split("\n")
            for ((i, line) in lines.withIndex()) {
                if (line.isBlank()) out.append(line) else out.append(client.translate(line).await())
                if (i < lines.size - 1) out.append('\n')
            }
            return Outcome.Ok(out.toString())
        } catch (e: Exception) {
            return Outcome.Failed(e.message ?: "Translation failed")
        } finally {
            client.close()
        }
    }
}
