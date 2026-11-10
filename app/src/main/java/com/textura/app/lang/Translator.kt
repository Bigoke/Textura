package com.textura.app.lang

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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

    /** Splits a line into sentences so each one is translated with its own context; keeps the spacing. */
    private fun sentences(line: String): List<String> =
        line.split(Regex("(?<=[.!?\u2026])\\s+(?=[\\p{Lu}\u201C\"'(\\[])")).filter { it.isNotEmpty() }

    private fun tidy(source: String, out: String): String {
        var t = out.trim()
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .replace(Regex("\\s{2,}"), " ")
        val lead = source.trimStart().firstOrNull()
        if (lead != null && lead.isUpperCase() && t.isNotEmpty() && t[0].isLowerCase()) t = t[0].uppercaseChar() + t.substring(1)
        val end = source.trimEnd().lastOrNull()
        if (end != null && end in ".!?" && t.isNotEmpty() && t.last() !in ".!?\u201D\"") t += end
        return t
    }

    /** Google Cloud Translation (v2) with the person's own API key: far better quality than the on-device model. */
    private suspend fun viaGoogle(lines: List<String>, from: Lang, to: Lang, key: String): List<String> = withContext(Dispatchers.IO) {
        val body = StringBuilder()
        lines.forEach { body.append("q=").append(URLEncoder.encode(it, "UTF-8")).append('&') }
        body.append("source=").append(if (from == Lang.Afrikaans) "af" else "en")
        body.append("&target=").append(if (to == Lang.Afrikaans) "af" else "en")
        body.append("&format=text&key=").append(URLEncoder.encode(key, "UTF-8"))
        val con = URL("https://translation.googleapis.com/language/translate/v2").openConnection() as HttpURLConnection
        try {
            con.requestMethod = "POST"
            con.connectTimeout = 15000
            con.readTimeout = 30000
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            con.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = con.responseCode
            if (code != 200) {
                val err = runCatching { con.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
                throw IllegalStateException(msg)
            }
            val arr = JSONObject(con.inputStream.bufferedReader().readText()).getJSONObject("data").getJSONArray("translations")
            List(arr.length()) { arr.getJSONObject(it).getString("translatedText") }
        } finally {
            con.disconnect()
        }
    }

    suspend fun translate(text: String, from: Lang, to: Lang, apiKey: String = "", onStatus: (String) -> Unit): Outcome {
        if (from == to) return Outcome.Ok(text)
        val lines = text.split("\n")

        if (apiKey.isNotBlank()) {
            onStatus("Translating online…")
            try {
                val idx = lines.indices.filter { lines[it].isNotBlank() }
                val got = viaGoogle(idx.map { lines[it] }, from, to, apiKey.trim())
                val out = lines.toMutableList()
                idx.forEachIndexed { k, i -> out[i] = tidy(lines[i], got[k]) }
                return Outcome.Ok(out.joinToString("\n"))
            } catch (e: java.io.IOException) {
                onStatus("No connection, using the on-device translator…")
            } catch (e: Exception) {
                return Outcome.Failed(e.message ?: "Online translation failed")
            }
        }

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
            for ((i, line) in lines.withIndex()) {
                if (line.isBlank()) {
                    out.append(line)
                } else {
                    val lead = line.takeWhile { it.isWhitespace() }
                    out.append(lead)
                    out.append(sentences(line.trim()).joinToString(" ") { sent -> tidy(sent, client.translate(sent).await()) })
                }
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
