package org.churchpresenter.stt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.Utils
import androidx.compose.ui.text.AnnotatedString
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.sharedui.composables.searchBarCard

@Composable
fun STTTab(
    modifier: Modifier = Modifier,
    sttManager: STTManager,
    /** What the output is presenting; the tab shows itself live when it is captions. */
    presentingMode: State<Presenting>,
    presenting: (Presenting) -> Unit,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /** The caption settings dialog, drawn by the app, which owns it; [onDismiss] closes it. */
    settingsDialog: @Composable (onDismiss: () -> Unit) -> Unit = {},
) {
    // The server is the install's; how the transcript below reads -- how many segments, which
    // languages, in-progress text, highlighting -- follows the captions an output actually shows,
    // since those are set per profile now and the document's own copy is no longer edited.
    val sttSettings = remember(appSettings) { appSettings.captionSettingsOnScreen() }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Connection controls on the top card.
        Column(
            modifier = Modifier.fillMaxWidth().searchBarCard(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConnectionBar(
                sttManager = sttManager,
                savedUrl = sttSettings.serverUrl,
                isLive = presentingMode.value == Presenting.STT,
                onConnect = { url ->
                    onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(serverUrl = url)) }
                    sttManager.connect(url)
                },
                onOpenSettings = { showSettingsDialog = true },
                onGoLive = { presenting(Presenting.STT) },
            )
            ConnectionStatus(sttManager)
        }
        // The live transcript on its own card.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                .bibleListCard()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LivePreview(sttManager, sttSettings)
        }
    }

    if (showSettingsDialog) {
        settingsDialog { showSettingsDialog = false }
    }
}

/** What Connect connects to: what was typed, with `http://` put in front when no scheme was given. */
internal fun connectUrl(typed: String): String =
    if (typed.isNotBlank() && !typed.startsWith("http://") && !typed.startsWith("https://")) "http://$typed"
    else typed

internal fun applyHighlighting(
    text: String,
    highlightedWords: List<HighlightedWord>,
    enabled: Boolean,
    baseColor: Color
): AnnotatedString {
    if (!enabled || highlightedWords.isEmpty()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor)) { append(text) }
        }
    }
    // Build per-character color array then construct contiguous runs (no overlapping spans)
    val colors = Array(text.length) { baseColor }
    highlightedWords.forEach { paintWord(it, text, colors) }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val color = colors[i]
            val start = i
            while (i < text.length && colors[i] == color) i++
            withStyle(SpanStyle(color = color)) { append(text.substring(start, i)) }
        }
    }
}

/** Paints every whole-word match of [hw] into [colors]; a pattern that won't compile is skipped. */
private fun paintWord(hw: HighlightedWord, text: String, colors: Array<Color>) {
    if (hw.word.isBlank()) return
    try {
        val highlightColor = Utils.parseHexColor(hw.color)
        val wb = "(?<![\\p{L}\\p{N}])"
        val we = "(?![\\p{L}\\p{N}])"
        val rawPattern = if (hw.isRegex) "$wb(?:${hw.word})$we" else "$wb${Regex.escape(hw.word)}$we"
        var flags = java.util.regex.Pattern.UNICODE_CHARACTER_CLASS
        if (!hw.caseSensitive) {
            flags = flags or java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE
        }
        java.util.regex.Pattern.compile(rawPattern, flags).toRegex().findAll(text).forEach { match ->
            for (j in match.range) colors[j] = highlightColor
        }
    } catch (_: Exception) {}
}
