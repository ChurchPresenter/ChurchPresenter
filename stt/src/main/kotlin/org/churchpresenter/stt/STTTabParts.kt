package org.churchpresenter.stt

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.churchpresenter.settings.STTTabView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.clear
import org.churchpresenter.strings.generated.resources.stt_connect
import org.churchpresenter.strings.generated.resources.stt_disconnect
import org.churchpresenter.strings.generated.resources.stt_go_live
import org.churchpresenter.strings.generated.resources.stt_live_preview
import org.churchpresenter.strings.generated.resources.stt_not_connected
import org.churchpresenter.strings.generated.resources.stt_server_url
import org.churchpresenter.strings.generated.resources.stt_status_connecting
import org.churchpresenter.strings.generated.resources.stt_status_unreachable
import org.churchpresenter.strings.generated.resources.stt_status_reconnecting
import org.churchpresenter.strings.generated.resources.obs_mode_stt
import org.churchpresenter.strings.generated.resources.stt_translation_label
import org.churchpresenter.strings.generated.resources.stt_waiting_for_transcription
import org.churchpresenter.strings.generated.resources.tooltip_stt_settings
import org.churchpresenter.sharedui.composables.ActionIconButton
import org.churchpresenter.sharedui.composables.GoLiveButton
import org.churchpresenter.sharedui.composables.StyledTextField
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.core.models.stt.STTSegment
import org.churchpresenter.core.models.stt.HighlightedWord

private const val IN_PROGRESS_ALPHA = 0.6f

/** The server address, the status dot, Connect or Disconnect, the settings and Go Live. */
@Composable
internal fun ConnectionBar(
    sttManager: STTManager,
    savedUrl: String,
    isLive: Boolean,
    onConnect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onGoLive: () -> Unit,
) {
    val connected by sttManager.connected
    val connecting by sttManager.connecting
    val reconnecting by sttManager.reconnecting
    var urlInput by remember(savedUrl) { mutableStateOf(savedUrl.ifEmpty { "http://" }) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StyledTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = stringResource(Res.string.stt_server_url),
            singleLine = true,
            modifier = Modifier.weight(1f),
            enabled = !connected && !connecting,
            trailingIcon = {
                if (!connected && !connecting && urlInput.isNotEmpty()) {
                    KeyIconButton(
                        onClick = { urlInput = "" },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = stringResource(Res.string.clear),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        // Status indicator
        Box(
            modifier = Modifier.size(12.dp).clip(CircleShape).background(
                when {
                    connected -> MaterialTheme.semantic.success
                    connecting || reconnecting -> MaterialTheme.semantic.warning
                    else -> MaterialTheme.colorScheme.error
                }
            )
        )

        if (connected) {
            ActionIconButton(
                onClick = { sttManager.disconnect() },
                tooltipText = stringResource(Res.string.stt_disconnect),
                icon = Icons.Default.Stop,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        } else {
            ActionIconButton(
                onClick = {
                    urlInput = connectUrl(urlInput)
                    onConnect(urlInput)
                },
                enabled = !connecting && urlInput.isNotBlank(),
                tooltipText = stringResource(Res.string.stt_connect),
                icon = Icons.Default.PlayArrow,
                containerColor = MaterialTheme.semantic.success,
                contentColor = MaterialTheme.semantic.onSuccess
            )
        }

        ActionIconButton(
            onClick = onOpenSettings,
            tooltipText = stringResource(Res.string.tooltip_stt_settings),
            icon = Icons.Default.Tune,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )

        GoLiveButton(
            onClick = onGoLive,
            enabled = connected && !isLive,
            tooltipText = stringResource(Res.string.stt_go_live),
            showsShortcut = true,
        )
    }
}

/**
 * Names the transient/problem states the colour dot can't distinguish (connecting / unreachable /
 * reconnecting). The plain idle "not connected" case is already explained in the live-preview area
 * below, so it's left out here.
 */
@Composable
internal fun ConnectionStatus(sttManager: STTManager) {
    val connectError by sttManager.connectError
    val connStatus: String = when {
        sttManager.reconnecting.value -> stringResource(Res.string.stt_status_reconnecting)
        connectError -> stringResource(Res.string.stt_status_unreachable)
        sttManager.connecting.value -> stringResource(Res.string.stt_status_connecting)
        else -> return
    }
    Text(
        text = connStatus,
        style = MaterialTheme.typography.labelSmall,
        color = if (connectError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The transcript and its translation as they arrive, read the tab's own way ([STTTabView]): which of
 * them, how many lines, the text still being spoken, highlighting, size, and side by side or one
 * under the other. Each output picks its own captions in its profile, so the tab follows none of them.
 */
@Composable
internal fun LivePreview(sttManager: STTManager, view: STTTabView) {
    val connected by sttManager.connected
    val inProgressText by sttManager.inProgressText
    val inProgressTranslation by sttManager.inProgressTranslation

    Text(
        stringResource(Res.string.stt_live_preview),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    val maxSeg = view.maxSegments
    val displaySegments = sttManager.segments.let { if (maxSeg > 0) it.takeLast(maxSeg) else it }
    val displayTranslation = sttManager.translationSegments.let { if (maxSeg > 0) it.takeLast(maxSeg) else it }

    val showTranscription = view.shows != STTTabView.SHOWS_TRANSLATION
    val showTranslation = view.shows != STTTabView.SHOWS_TRANSCRIPTION
    val hasContent = (showTranscription && (displaySegments.isNotEmpty() || inProgressText.isNotBlank())) ||
        (showTranslation && (displayTranslation.isNotEmpty() || inProgressTranslation.isNotBlank()))

    when {
        !connected -> PreviewNotice(stringResource(Res.string.stt_not_connected))
        !hasContent -> PreviewNotice(stringResource(Res.string.stt_waiting_for_transcription))
        else -> {
            val highlighting = Highlighting(
                words = sttManager.highlightedWords,
                enabled = view.wordHighlighting && sttManager.wordHighlightingEnabled.value,
            )
            val style = MaterialTheme.typography.bodyMedium.copy(fontSize = view.fontSize.sp)
            val transcription: @Composable (Modifier) -> Unit = { modifier ->
                TranscriptColumn(
                    title = stringResource(Res.string.obs_mode_stt),
                    color = MaterialTheme.colorScheme.onSurface,
                    segments = displaySegments,
                    inProgress = inProgressText.takeIf { view.showInProgress }.orEmpty(),
                    highlighting = highlighting,
                    style = style,
                    modifier = modifier,
                )
            }
            val translation: @Composable (Modifier) -> Unit = { modifier ->
                TranscriptColumn(
                    title = stringResource(Res.string.stt_translation_label),
                    color = MaterialTheme.colorScheme.primary,
                    segments = displayTranslation,
                    inProgress = inProgressTranslation.takeIf { view.showTranslationInProgress }.orEmpty(),
                    highlighting = highlighting,
                    style = style,
                    modifier = modifier,
                )
            }
            if (view.layout == STTTabView.LAYOUT_STACKED) {
                Column(Modifier.fillMaxSize().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showTranscription) transcription(Modifier.weight(1f).fillMaxWidth())
                    if (showTranslation) translation(Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxSize().padding(top = 4.dp)) {
                    if (showTranscription) transcription(Modifier.weight(1f).fillMaxHeight())
                    if (showTranslation) translation(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun PreviewNotice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // The 4 dp the columns also sit below the title, then the notice's own margin.
        modifier = Modifier.padding(top = 4.dp).padding(16.dp)
    )
}

/** The words to highlight, and whether highlighting is on at all. */
internal class Highlighting(val words: List<HighlightedWord>, val enabled: Boolean)

/** One language's column: its title, the finished segments, then what is still being said. */
@Composable
private fun TranscriptColumn(
    title: String,
    color: Color,
    segments: List<STTSegment>,
    inProgress: String,
    highlighting: Highlighting,
    style: TextStyle,
    modifier: Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(segments.size, inProgress) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Spacer(Modifier.height(4.dp))
            segments.forEach { segment ->
                Text(
                    text = applyHighlighting(segment.text, highlighting.words, highlighting.enabled, color),
                    style = style,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
            if (inProgress.isNotBlank()) {
                Text(
                    text = inProgress,
                    style = style,
                    color = color.copy(alpha = IN_PROGRESS_ALPHA),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}
