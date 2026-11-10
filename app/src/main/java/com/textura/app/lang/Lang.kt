package com.textura.app.lang

enum class Lang(val code: String, val label: String, val toolCode: String?) {
    English("en", "English", "en-ZA"),
    Afrikaans("af", "Afrikaans", null),
}

enum class IssueKind { Spelling, Grammar }

/** A problem found in the text. [start] and [end] index into the original text. */
data class Issue(
    val start: Int,
    val end: Int,
    val kind: IssueKind,
    val message: String,
    val suggestions: List<String>,
    val word: String,
) {
    val key: String get() = kind.name + ":" + word.lowercase() + ":" + message
}
