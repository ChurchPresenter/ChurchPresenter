package org.churchpresenter.bibleeditor

enum class DiffKind { SAME, DELETED, INSERTED }

data class DiffSpan(val text: String, val kind: DiffKind)

private val WHITESPACE_SPLIT = Regex("(?<=\\s)(?=\\S)|(?<=\\S)(?=\\s)")
private const val LONG_RUN_WORDS = 14
private const val EDGE_WORDS = 8
private const val MIDDLE_WORDS = 5
private const val ELLIPSIS = "…"

private fun tokens(s: String): List<String> = if (s.isEmpty()) emptyList() else s.split(WHITESPACE_SPLIT)

/** A word-level diff of [before] to [after], with long unchanged runs shortened so the change shows. */
fun wordDiff(before: String, after: String): List<DiffSpan> {
    val a = tokens(before)
    val b = tokens(after)
    val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
        for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
    val out = mutableListOf<DiffSpan>()
    fun push(t: String, k: DiffKind) {
        val last = out.lastOrNull()
        if (last != null && last.kind == k) {
            out[out.lastIndex] = last.copy(text = last.text + t)
        } else {
            out += DiffSpan(t, k)
        }
    }
    var i = 0
    var j = 0
    while (i < a.size && j < b.size) {
        when {
            a[i] == b[j] -> { push(a[i], DiffKind.SAME); i++; j++ }
            lcs[i + 1][j] >= lcs[i][j + 1] -> { push(a[i], DiffKind.DELETED); i++ }
            else -> { push(b[j], DiffKind.INSERTED); j++ }
        }
    }
    while (i < a.size) push(a[i++], DiffKind.DELETED)
    while (j < b.size) push(b[j++], DiffKind.INSERTED)
    return shortenRuns(out)
}

private fun shortenRuns(spans: List<DiffSpan>): List<DiffSpan> {
    if (spans.size == 1) return spans
    return spans.mapIndexed { index, span ->
        val words = tokens(span.text)
        if (span.kind != DiffKind.SAME || words.size <= LONG_RUN_WORDS) return@mapIndexed span
        val text = when (index) {
            0 -> "$ELLIPSIS " + words.takeLast(EDGE_WORDS).joinToString("")
            spans.lastIndex -> words.take(EDGE_WORDS).joinToString("") + " $ELLIPSIS"
            else -> words.take(MIDDLE_WORDS).joinToString("") + " $ELLIPSIS " +
                words.takeLast(MIDDLE_WORDS).joinToString("")
        }
        span.copy(text = text)
    }
}
