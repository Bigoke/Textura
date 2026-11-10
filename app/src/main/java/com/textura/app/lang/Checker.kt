package com.textura.app.lang

import android.content.Context
import com.textura.app.render.Markup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Spelling (bundled dictionaries) and grammar (rules, plus optional online check for English). */
object Checker {
    class Result(val lang: Lang, val issues: List<Issue>, val deepFailed: Boolean = false)

    /** Hides [b], [c=#fff] and similar tags so offsets still match the original text. */
    fun mask(text: String): String = Markup.TAG.replace(text) { " ".repeat(it.value.length) }

    suspend fun detect(context: Context, text: String): Lang = withContext(Dispatchers.Default) {
        val en = Dictionary.get(context, Lang.English)
        val af = Dictionary.get(context, Lang.Afrikaans)
        var scoreEn = 0
        var scoreAf = 0
        var n = 0
        for (m in WORD.findAll(mask(text))) {
            val w = m.value.lowercase()
            if (w.length < 2) continue
            val inEn = w in en
            val inAf = w in af
            if (inEn && !inAf) scoreEn++ else if (inAf && !inEn) scoreAf++
            if (++n >= 500) break
        }
        if (scoreAf > scoreEn) Lang.Afrikaans else Lang.English
    }

    suspend fun check(
        context: Context,
        text: String,
        forced: Lang?,
        user: Set<String>,
        ignored: Set<String>,
        deep: Boolean,
    ): Result {
        val lang = forced ?: detect(context, text)
        val masked = mask(text)
        val base = withContext(Dispatchers.Default) {
            val engine = SpellEngine(Dictionary.get(context, lang), lang, user)
            val spelling = spelling(masked, engine)
            val grammar = GrammarRules.run(masked, lang)
            spelling to grammar
        }
        var grammar = base.second
        var deepFailed = false
        if (deep && lang == Lang.English) {
            val online = withContext(Dispatchers.IO) { LanguageToolClient.check(masked, lang.toolCode ?: "en-ZA") }
            if (online == null) deepFailed = true else grammar = grammar + online
        }
        // a grammar finding replaces a spelling finding in the same place, and overlapping grammar findings collapse
        val sortedGrammar = grammar.sortedBy { it.start }
        val keptGrammar = ArrayList<Issue>()
        for (g in sortedGrammar) if (keptGrammar.none { it.start < g.end && g.start < it.end }) keptGrammar.add(g)
        val keptSpelling = base.first.filter { s -> keptGrammar.none { it.start < s.end && s.start < it.end } }
        val all = (keptSpelling + keptGrammar).filter { it.key !in ignored }.sortedBy { it.start }
        return Result(lang, all, deepFailed)
    }

    private val WORD = Regex("['’]?\\p{L}[\\p{L}'’\\-]*")
    private val URLS = Regex("(https?://|www\\.)\\S+|\\S+@\\S+\\.\\S+")

    private fun isSentenceStart(text: String, index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t' || text[i] == '"' || text[i] == '“' || text[i] == '(' || text[i] == '[' || text[i] == '‘')) i--
        if (i < 0) return true
        return text[i] == '.' || text[i] == '!' || text[i] == '?' || text[i] == '\n' || text[i] == ':'
    }

    private fun spelling(masked: String, engine: SpellEngine): List<Issue> {
        val urlRanges = URLS.findAll(masked).map { it.range }.toList()
        val raw = ArrayList<Triple<Int, Int, String>>()
        for (m in WORD.findAll(masked)) {
            var start = m.range.first
            var end = m.range.last + 1
            var w = m.value
            // trim quotes and hyphens that are punctuation, not part of the word
            while (w.isNotEmpty() && (w.last() == '\'' || w.last() == '’' || w.last() == '-')) { w = w.dropLast(1); end-- }
            if (w.startsWith("'n", ignoreCase = true) && (w.length == 2)) continue
            while (w.isNotEmpty() && (w.first() == '\'' || w.first() == '’')) { w = w.drop(1); start++ }
            if (w.length < 2) continue
            if (urlRanges.any { start >= it.first && start <= it.last }) continue
            val before = if (start > 0) masked[start - 1] else ' '
            val after = if (end < masked.length) masked[end] else ' '
            if (before.isDigit() || after.isDigit() || before == '@' || before == '#' || before == '/' || before == '_') continue
            if (w.length <= 5 && w.all { it.isUpperCase() || it == '-' }) continue // acronyms
            if (engine.isValid(w)) continue
            // a capitalised word in the middle of a sentence is most likely a name
            if (w.first().isUpperCase() && !isSentenceStart(masked, start) && w.drop(1).any { it.isLowerCase() }) continue
            raw.add(Triple(start, end, w))
        }
        val out = ArrayList<Issue>()
        for ((i, t) in raw.withIndex()) {
            val sug = if (i < 60) engine.suggest(t.third) else emptyList()
            out.add(Issue(t.first, t.second, IssueKind.Spelling, "Not found in the dictionary", sug, t.third))
        }
        return out
    }
}

/** Simple, reliable grammar rules that run on the phone. */
object GrammarRules {
    private class Rule(
        val re: Regex,
        val msg: String,
        val group: Int = 0,
        val filter: ((String, MatchResult) -> Boolean)? = null,
        val fix: (MatchResult) -> String?,
    )

    fun run(text: String, lang: Lang): List<Issue> {
        val out = ArrayList<Issue>()
        val rules = common() + if (lang == Lang.English) english() else afrikaans()
        for (r in rules) {
            for (m in r.re.findAll(text)) {
                if (r.filter != null && !r.filter.invoke(text, m)) continue
                val g = m.groups[r.group] ?: continue
                val rep = r.fix(m)
                out.add(
                    Issue(
                        g.range.first, g.range.last + 1, IssueKind.Grammar, r.msg,
                        if (rep != null) listOf(rep) else emptyList(),
                        g.value,
                    ),
                )
            }
        }
        return out
    }

    private fun prevWord(text: String, index: Int): String {
        val from = maxOf(0, index - 40)
        return Regex("(\\p{L}+)[^\\p{L}]*$").find(text.substring(from, index))?.groupValues?.get(1)?.lowercase() ?: ""
    }

    private val ABBREV = setOf("e.g", "i.e", "etc", "vs", "mr", "mrs", "ms", "dr", "prof", "no", "st", "nr", "mnr", "me", "dr", "ca", "bv", "bl")

    private fun sentenceCapital() = Rule(
        Regex("(?:^|[.!?][\"”')]*\\s+)(\\p{Ll}[\\p{L}'’]*)"),
        "Start a sentence with a capital letter", group = 1,
        filter = { text, m ->
            val g = m.groups[1]!!
            val word = g.value
            val idx = g.range.first
            val prefix = text.substring(0, idx).trimEnd()
            val prevTok = Regex("(\\S+)$").find(prefix)?.value?.trim('.', '!', '?', '"', '”', '\'', ')')?.lowercase() ?: ""
            val abbreviation = prefix.endsWith(".") && (prevTok in ABBREV || prevTok.length == 1)
            !abbreviation && !word.startsWith("www") && !word.startsWith("http") && !word.equals("n", true)
        },
        fix = { m -> m.groups[1]!!.value.replaceFirstChar { it.uppercase() } },
    )

    private fun common(): List<Rule> = listOf(
        Rule(
            Regex("\\b(\\p{L}{2,})\\s+\\1\\b", RegexOption.IGNORE_CASE),
            "Repeated word",
            filter = { _, m -> m.groupValues[1].lowercase() !in setOf("that", "had", "bye", "very", "no") },
            fix = { m -> m.groupValues[1] },
        ),
        Rule(
            Regex("(?<=[\\p{L}\\d]) +([,.;!?])(?![.,;!?])"),
            "Remove the space before the punctuation mark",
            fix = { m -> m.groupValues[1] },
        ),
        Rule(
            Regex("(?<=\\p{L})([,;])(?=\\p{L})"),
            "Add a space after the punctuation mark",
            group = 1,
            fix = { m -> m.groupValues[1] + " " },
        ),
        Rule(
            Regex("(?<=\\S) {2,}(?=\\S)"),
            "Extra spaces",
            fix = { " " },
        ),
        sentenceCapital(),
    )

    private fun english(): List<Rule> {
        val ic = RegexOption.IGNORE_CASE
        val auxiliaries = setOf("did", "does", "do", "will", "would", "could", "should", "can", "may", "might", "must", "shall", "to", "let", "make", "made", "if", "wish", "as", "though")
        val consonantSoundVowel = Regex("^(uni(?!n)|use|usu|uti|eu|one|once|ubiq|uran)", ic)
        val vowelSoundConsonant = Regex("^(hour|honest|honou?r|heir)", ic)
        val comparatives = "better|bigger|easier|faster|smaller|larger|older|happier|stronger|harder|higher|longer|cheaper|worse|nicer|taller|simpler|lighter"
        val superlatives = "best|biggest|easiest|fastest|smallest|largest|oldest|happiest|strongest|hardest|highest|longest|cheapest|worst|nicest|tallest"
        return listOf(
            Rule(
                Regex("(?<![\\p{L}\\d.'’)])i(?![\\p{L}\\d.)])(?=\\s|['’]|[,;:!?]|$)"),
                "Write \"I\" with a capital letter",
                fix = { "I" },
            ),
            Rule(
                Regex("\\b(a)\\s+([aeiou]\\p{L}*)", ic),
                "Use \"an\" before a vowel sound",
                group = 1,
                filter = { _, m -> m.groups[1]!!.value == "a" && !consonantSoundVowel.containsMatchIn(m.groupValues[2]) && !m.groupValues[2].all { it.isUpperCase() } },
                fix = { "an" },
            ),
            Rule(
                Regex("\\b(an)\\s+([bcdfgjklmnpqrstvwxyz]\\p{L}*)", ic),
                "Use \"a\" before a consonant sound",
                group = 1,
                filter = { _, m -> m.groups[1]!!.value == "an" && !vowelSoundConsonant.containsMatchIn(m.groupValues[2]) && !m.groupValues[2].all { it.isUpperCase() } },
                fix = { "a" },
            ),
            Rule(
                Regex("\\b(should|could|would|must|might)\\s+(of)\\b", ic),
                "Use \"have\", not \"of\"",
                group = 2,
                fix = { "have" },
            ),
            Rule(Regex("\\byour\\s+(welcome)\\b", ic), "Use \"you're\" (you are)", group = 0, fix = { "you're welcome" }),
            Rule(
                Regex("\\b(your)\\s+(going|not|being|doing)\\b", ic),
                "Use \"you're\" (you are)", group = 1, fix = { "you're" },
            ),
            Rule(
                Regex("\\b(its)\\s+(a|an|the|been|not|going|very)\\b", ic),
                "Use \"it's\" (it is / it has)", group = 1, fix = { "it's" },
            ),
            Rule(
                Regex("\\b(more|less|better|worse|rather|other|bigger|smaller|faster|larger|older|younger)\\s+(then)\\b", ic),
                "Use \"than\" when comparing", group = 2, fix = { "than" },
            ),
            Rule(
                Regex("\\b(he|she|it)\\s+(don't|dont|don’t)\\b", ic),
                "Use \"doesn't\" with he, she and it", group = 2, fix = { "doesn't" },
            ),
            Rule(
                Regex("\\b(I|you|we|they)\\s+(doesn't|doesn’t)\\b", ic),
                "Use \"don't\" with I, you, we and they", group = 2, fix = { "don't" },
            ),
            Rule(
                Regex("\\b(he|she|it)\\s+(have)\\b", ic),
                "Use \"has\" with he, she and it", group = 2,
                filter = { text, m -> prevWord(text, m.range.first) !in auxiliaries },
                fix = { "has" },
            ),
            Rule(
                Regex("\\b(we|they|you)\\s+(has)\\b", ic),
                "Use \"have\" with we, you and they", group = 2, fix = { "have" },
            ),
            Rule(Regex("\\bI\\s+(has)\\b", ic), "Use \"have\" with I", group = 1, fix = { "have" }),
            Rule(Regex("\\bI\\s+(is)\\b", ic), "Use \"am\" with I", group = 1, fix = { "am" }),
            Rule(Regex("\\b(we|they|you)\\s+(is)\\b", ic), "Use \"are\" with we, you and they", group = 2, fix = { "are" }),
            Rule(Regex("\\bI\\s+(are)\\b", ic), "Use \"am\" with I", group = 1, fix = { "am" }),
            Rule(Regex("\\b(he|she|it)\\s+(are)\\b", ic), "Use \"is\" with he, she and it", group = 2, fix = { "is" }),
            Rule(
                Regex("\\b(he|she|it)\\s+(were)\\b", ic),
                "Use \"was\" with he, she and it", group = 2,
                filter = { text, m -> prevWord(text, m.range.first) !in auxiliaries },
                fix = { "was" },
            ),
            Rule(Regex("\\b(more)\\s+($comparatives)\\b", ic), "Don't use \"more\" with this word", fix = { m -> m.groupValues[2] }),
            Rule(Regex("\\b(most)\\s+($superlatives)\\b", ic), "Don't use \"most\" with this word", fix = { m -> m.groupValues[2] }),
            Rule(Regex("\\birregardless\\b", ic), "The word is \"regardless\"", fix = { "regardless" }),
            Rule(Regex("\\bbetween you and (I)\\b", ic), "Use \"me\" after \"between you and\"", group = 1, fix = { "me" }),
            Rule(Regex("\\balot\\b", ic), "Write \"a lot\" as two words", fix = { "a lot" }),
            Rule(
                Regex("\\b(don't|doesn't|didn't|can't|won't|couldn't|wouldn't)\\s+(have|know|want|see|get|need|got)\\s+(no|nothing|nobody)\\b", ic),
                "Double negative: use \"any\" or \"anything\" instead", fix = { null },
            ),
            Rule(
                Regex("\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|january|february|april|june|july|august|september|october|november|december)\\b"),
                "Days and months start with a capital letter in English",
                fix = { m -> m.value.replaceFirstChar { it.uppercase() } },
            ),
        )
    }

    private fun afrikaans(): List<Rule> {
        val words = "beter|groter|kleiner|hoër|laer|mooier|ouer|jonger|sterker|swakker|vinniger|stadiger|ryker|slimmer|dikker|dunner|langer|korter"
        val bests = "beste|grootste|kleinste|hoogste|laagste|mooiste|oudste|jongste|sterkste|vinnigste|rykste|slimste|langste|kortste"
        return listOf(
            Rule(
                Regex("(?<![\\p{L}'’])(n)(?=\\s+\\p{Ll})"),
                "Die lidwoord word 'n geskryf (met 'n apostrof)",
                group = 1,
                filter = { text, m ->
                    val i = m.groups[1]!!.range.first
                    !isStart(text, i) // sentence start is handled below
                },
                fix = { "'n" },
            ),
            Rule(
                Regex("(?<![\\p{L}'’])(n)(?=\\s+\\p{Ll})"),
                "Die lidwoord aan die begin van 'n sin is 'N",
                group = 1,
                filter = { text, m -> isStart(text, m.groups[1]!!.range.first) },
                fix = { "'N" },
            ),
            Rule(
                Regex("(?<![\\p{L}])['’](n)(?=\\s+\\p{Ll})"),
                "Aan die begin van 'n sin skryf mens 'N",
                group = 1,
                filter = { text, m -> isStart(text, m.range.first) },
                fix = { "N" },
            ),
            Rule(
                Regex("\\b(meer)\\s+($words)\\b", RegexOption.IGNORE_CASE),
                "Moenie \"meer\" saam met 'n vergrotende trap gebruik nie", fix = { m -> m.groupValues[2] },
            ),
            Rule(
                Regex("\\b(mees)\\s+($bests)\\b", RegexOption.IGNORE_CASE),
                "Moenie \"mees\" saam met 'n oortreffende trap gebruik nie", fix = { m -> m.groupValues[2] },
            ),
            Rule(
                Regex("\\b(Maandag|Dinsdag|Woensdag|Donderdag|Vrydag|Saterdag|Sondag|Januarie|Februarie|Maart|April|Mei|Junie|Julie|Augustus|September|Oktober|November|Desember)\\b"),
                "In Afrikaans skryf mens dae en maande met 'n kleinletter",
                filter = { text, m -> !isStart(text, m.range.first) },
                fix = { m -> m.value.replaceFirstChar { it.lowercase() } },
            ),
        )
    }

    private fun isStart(text: String, index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t' || text[i] == '"' || text[i] == '“' || text[i] == '(' || text[i] == '[' || text[i] == '‘')) i--
        return i < 0 || text[i] == '.' || text[i] == '!' || text[i] == '?' || text[i] == '\n'
    }
}
