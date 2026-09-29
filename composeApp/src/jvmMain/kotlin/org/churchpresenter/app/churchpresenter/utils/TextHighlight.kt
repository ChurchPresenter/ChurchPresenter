package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * The `[start, end)` spans of [text] that match [query] case-insensitively, left to right and
 * non-overlapping — the segments a search result highlights. Extracted from BibleTab's
 * `buildAnnotatedString` loop so the match-finding can be tested apart from the Compose styling.
 *
 * - A blank query yields no spans; an empty query would otherwise make `indexOf` loop forever.
 * - Indices are clamped to `text.length`, because `lowercase()` can change string length in some
 *   locales and the spans are used to slice the original (non-lowercased) [text].
 */
internal fun highlightRanges(text: String, query: String): List<Pair<Int, Int>> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val lowerText = text.lowercase()
    val lowerQuery = trimmed.lowercase()
    val ranges = mutableListOf<Pair<Int, Int>>()
    var start = lowerText.indexOf(lowerQuery)
    while (start != -1) {
        val safeStart = start.coerceAtMost(text.length)
        val safeEnd = (start + lowerQuery.length).coerceAtMost(text.length)
        ranges.add(safeStart to safeEnd)
        start = lowerText.indexOf(lowerQuery, start + lowerQuery.length)
    }
    return ranges
}

/**
 * [text] with every match of [query] marked the way search results mark them: bold, on the primary
 * container colour. Shared by the Bible and Song search results so a match looks the same in both.
 */
@Composable
internal fun highlightedText(text: String, query: String): AnnotatedString {
    val style = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.Bold,
    )
    return buildAnnotatedString {
        var lastIndex = 0
        for ((start, end) in highlightRanges(text, query)) {
            append(text.substring(lastIndex.coerceAtMost(start), start))
            withStyle(style) { append(text.substring(start, end)) }
            lastIndex = end
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
}
