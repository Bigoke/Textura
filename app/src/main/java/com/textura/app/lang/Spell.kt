package com.textura.app.lang

import android.content.Context

/** The bundled word lists (expanded Hunspell dictionaries), loaded once and kept in memory. */
object Dictionary {
    private val sets = HashMap<Lang, Set<String>>()

    @Synchronized
    fun get(context: Context, lang: Lang): Set<String> = sets.getOrPut(lang) {
        val out = HashSet<String>(200_000)
        // plain text: the build tools unpack .gz assets and drop the extension, so a gzip file cannot be relied on
        context.applicationContext.assets.open("dict/${lang.code}.words").bufferedReader(Charsets.UTF_8).use { r ->
            r.forEachLine { if (it.isNotEmpty()) out.add(it) }
        }
        out
    }
}

class SpellEngine(private val words: Set<String>, private val lang: Lang, private val user: Set<String>) {
    private val linkers = listOf("s", "e", "-")

    private fun known(w: String): Boolean {
        val lower = w.lowercase()
        return w in words || lower in words || lower.replaceFirstChar { it.uppercase() } in words || lower in user
    }

    fun isValid(raw: String): Boolean {
        val w = raw.replace('’', '\'')
        if (w.length <= 1) return true
        if (w.equals("'n", ignoreCase = true)) return true
        if (known(w)) return true
        if (lang == Lang.English && (w.endsWith("'s") || w.endsWith("s'")) && known(w.dropLast(if (w.endsWith("'s")) 2 else 1))) return true
        if (w.contains('-')) {
            val parts = w.split('-').filter { it.isNotEmpty() }
            if (parts.isNotEmpty() && parts.all { isValid(it) }) return true
        }
        if (lang == Lang.Afrikaans && compound(w.lowercase(), 0)) return true
        return false
    }

    /** Afrikaans joins words freely, so a word is fine when it splits into known parts. */
    private fun compound(w: String, depth: Int): Boolean {
        if (w in words || w in user) return true
        if (depth >= 3 || w.length < 6) return false
        for (i in 3..w.length - 3) {
            val a = w.substring(0, i)
            val b = w.substring(i)
            if (a in words && compound(b, depth + 1)) return true
            for (link in linkers) {
                if (a.length > 3 && a.endsWith(link) && a.dropLast(link.length) in words && compound(b, depth + 1)) return true
            }
        }
        return false
    }

    private fun alphabet(): String =
        if (lang == Lang.Afrikaans) "abcdefghijklmnopqrstuvwxyzëêéèôöûüâäïî'-" else "abcdefghijklmnopqrstuvwxyz'-"

    private fun edits1(w: String): Set<String> {
        val out = HashSet<String>()
        val abc = alphabet()
        for (i in 0..w.length) {
            val l = w.substring(0, i)
            val r = w.substring(i)
            if (r.isNotEmpty()) out.add(l + r.substring(1))
            if (r.length > 1) out.add(l + r[1] + r[0] + r.substring(2))
            for (c in abc) {
                if (r.isNotEmpty()) out.add(l + c + r.substring(1))
                out.add(l + c + r)
            }
        }
        out.remove(w)
        return out
    }

    private fun inList(w: String) = w in words

    fun suggest(raw: String, max: Int = 5): List<String> {
        val w = raw.replace('’', '\'')
        val lower = w.lowercase()
        val capital = w.firstOrNull()?.isUpperCase() == true
        val found = LinkedHashMap<String, Int>()
        val e1 = edits1(lower)
        for (c in e1) if (inList(c)) found[c] = 1
        if (found.size < 3 && lower.length in 4..12) {
            var budget = 120_000
            loop@ for (a in e1) {
                for (c in edits1(a)) {
                    if (--budget < 0) break@loop
                    if (c != lower && inList(c) && c !in found) found[c] = 2
                }
            }
        }
        // Afrikaans: a split into two known words ("huisdier" typed as "huis dier" is a different case, so only offer real words)
        val ranked = found.entries.sortedWith(
            compareBy<Map.Entry<String, Int>>({ it.value }, { if (it.key.firstOrNull() == lower.firstOrNull()) 0 else 1 }, { kotlin.math.abs(it.key.length - lower.length) }, { it.key }),
        ).map { it.key }.take(max)
        return if (capital) ranked.map { s -> s.replaceFirstChar { it.uppercase() } } else ranked
    }
}
