package com.textura.app.lang

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Optional deeper English grammar check using the public LanguageTool service. This sends the text over the
 * internet, so it only runs when the user turns it on.
 */
object LanguageToolClient {
    fun check(text: String, languageCode: String): List<Issue>? = runCatching {
        if (text.isBlank() || text.length > 18_000) return emptyList()
        val body = "text=" + URLEncoder.encode(text, "UTF-8") + "&language=" + languageCode + "&level=default"
        val conn = (URL("https://api.languagetool.org/v2/check").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        if (conn.responseCode != 200) return null
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val matches = json.getJSONArray("matches")
        val out = ArrayList<Issue>()
        for (i in 0 until matches.length()) {
            val m = matches.getJSONObject(i)
            val rule = m.optJSONObject("rule")
            val type = rule?.optString("issueType") ?: ""
            if (type == "misspelling") continue // spelling is handled by the on-device dictionary
            val off = m.getInt("offset")
            val len = m.getInt("length")
            val reps = m.optJSONArray("replacements")
            val list = ArrayList<String>()
            if (reps != null) for (k in 0 until minOf(5, reps.length())) list.add(reps.getJSONObject(k).getString("value"))
            out.add(Issue(off, off + len, IssueKind.Grammar, m.optString("message"), list, text.substring(off, minOf(text.length, off + len))))
        }
        out
    }.getOrNull()
}
