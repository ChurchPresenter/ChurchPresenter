package org.churchpresenter.app.churchpresenter.tabs

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.stt_connect
import churchpresenter.composeapp.generated.resources.stt_disconnect
import churchpresenter.composeapp.generated.resources.stt_go_live
import churchpresenter.composeapp.generated.resources.stt_live_preview
import churchpresenter.composeapp.generated.resources.stt_not_connected
import churchpresenter.composeapp.generated.resources.stt_server_url
import churchpresenter.composeapp.generated.resources.stt_status_connecting
import churchpresenter.composeapp.generated.resources.stt_status_unreachable
import churchpresenter.composeapp.generated.resources.stt_status_reconnecting
import churchpresenter.composeapp.generated.resources.stt_transcription_label
import churchpresenter.composeapp.generated.resources.stt_translation_label
import churchpresenter.composeapp.generated.resources.stt_waiting_for_transcription
import churchpresenter.composeapp.generated.resources.tooltip_stt_settings
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import org.churchpresenter.app.churchpresenter.composables.StyledTextField
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.STTSettingsDialog
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.viewmodel.HighlightedWord
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.compose.resources.stringResource

@Composable
fun STTTab(
    modifier: Modifier = Modifier,
    sttManager: STTManager,
    presenterManager: PresenterManager,
    presenting: (Presenting) -> Unit,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val sttSettings = appSettings.sttSettings
    val connected by sttManager.connected
    val connecting by sttManager.connecting
    val connectError by sttManager.connectError
    val reconnecting by sttManager.reconnecting
    val presentingMode by presenterManager.presentingMode
    val isLive = presentingMode == Presenting.STT
    val segments = sttManager.segments
    val inProgressText by sttManager.inProgressText
    val translationSegments = sttManager.translationSegments
    val inProgressTranslation by sttManager.inProgressTranslation

    var urlInput by remember(sttSettings.serverUrl) { mutableStateOf(sttSettings.serverUrl.ifEmpty { "http://" }) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Connection row
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
                        IconButton(onClick = { urlInput = "" }, colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
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
                        connected -> Color(0xFF43A047)
                        connecting || reconnecting -> Color(0xFFFFA726)
                        else -> Color(0xFFE53935)
                    }
                )
            )

            if (connected) {
                ActionIconButton(
                    onClick = { sttManager.disconnect() },
                    tooltipText = stringResource(Res.string.stt_disconnect),
                    icon = Icons.Default.Stop,
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White
                )
            } else {
                ActionIconButton(
                    onClick = {
                        val url = if (urlInput.isNotBlank() && !urlInput.startsWith("http://") && !urlInput.startsWith("https://")) "http://$urlInput" else urlInput
                        urlInput = url
                        onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(serverUrl = url)) }
                        sttManager.connect(url)
                    },
                    enabled = !connecting && urlInput.isNotBlank(),
                    tooltipText = stringResource(Res.string.stt_connect),
                    icon = Icons.Default.PlayArrow,
                    containerColor = Color(0xFF43A047),
                    contentColor = Color.White
                )
            }

            ActionIconButton(
                onClick = { showSettingsDialog = true },
                tooltipText = stringResource(Res.string.tooltip_stt_settings),
                icon = Icons.Default.Tune,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            // Go Live
            GoLiveButton(
                onClick = {
                    presenting(Presenting.STT)
                },
                enabled = connected && !isLive,
                tooltipText = stringResource(Res.string.stt_go_live)
            )
        }

        // Connection status label — names the transient/problem states the colour dot can't
        // distinguish (connecting / unreachable / reconnecting). The plain idle "not connected"
        // case is already explained in the live-preview area below, so it's left out here.
        val connStatus: String? = when {
            reconnecting -> stringResource(Res.string.stt_status_reconnecting)
            connectError -> stringResource(Res.string.stt_status_unreachable)
            connecting -> stringResource(Res.string.stt_status_connecting)
            else -> null
        }
        if (connStatus != null) {
            Text(
                text = connStatus,
                style = MaterialTheme.typography.labelSmall,
                color = if (connectError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Live preview area
        Text(stringResource(Res.string.stt_live_preview), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        val maxSeg = sttSettings.maxSegments
        val displaySegments = if (maxSeg > 0) segments.takeLast(maxSeg) else segments
        val displayTranslation = if (maxSeg > 0) translationSegments.takeLast(maxSeg) else translationSegments

        val showTranscription = sttSettings.displayMode == "transcribe" || sttSettings.displayMode == "both"
        val showTranslation = sttSettings.displayMode == "translate" || sttSettings.displayMode == "both"

        val hasTranscriptionContent = displaySegments.isNotEmpty() || inProgressText.isNotBlank()
        val hasTranslationContent = displayTranslation.isNotEmpty() || inProgressTranslation.isNotBlank()
        val hasContent = (showTranscription && hasTranscriptionContent) || (showTranslation && hasTranslationContent)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            if (!connected) {
                Text(
                    stringResource(Res.string.stt_not_connected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (!hasContent) {
                Text(
                    stringResource(Res.string.stt_waiting_for_transcription),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                val highlightedWords = sttManager.highlightedWords
                val highlightingEnabled = sttSettings.showWordHighlighting && sttManager.wordHighlightingEnabled.value

                // Transcription column
                if (showTranscription) {
                    val transcriptionScrollState = rememberScrollState()
                    LaunchedEffect(displaySegments.size, inProgressText) {
                        transcriptionScrollState.animateScrollTo(transcriptionScrollState.maxValue)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(transcriptionScrollState).padding(4.dp)
                        ) {
                            Text(stringResource(Res.string.stt_transcription_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            displaySegments.forEach { segment ->
                                Text(
                                    text = applyHighlighting(segment.text, highlightedWords, highlightingEnabled, MaterialTheme.colorScheme.onSurface),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                            if (sttSettings.showInProgress && inProgressText.isNotBlank()) {
                                Text(
                                    text = inProgressText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(transcriptionScrollState)
                        )
                    }
                }

                // Translation column
                if (showTranslation) {
                    val translationScrollState = rememberScrollState()
                    LaunchedEffect(displayTranslation.size, inProgressTranslation) {
                        translationScrollState.animateScrollTo(translationScrollState.maxValue)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(translationScrollState).padding(4.dp)
                        ) {
                            Text(stringResource(Res.string.stt_translation_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            displayTranslation.forEach { segment ->
                                Text(
                                    text = applyHighlighting(segment.text, highlightedWords, highlightingEnabled, MaterialTheme.colorScheme.primary),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                            if (sttSettings.showTranslationInProgress && inProgressTranslation.isNotBlank()) {
                                Text(
                                    text = inProgressTranslation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(translationScrollState)
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        STTSettingsDialog(
            appSettings = appSettings,
            onSettingsChange = onSettingsChange,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

private fun applyHighlighting(
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
    for (hw in highlightedWords) {
        if (hw.word.isBlank()) continue
        try {
            val highlightColor = Utils.parseHexColor(hw.color)
            val wb = "(?<![\\p{L}\\p{N}])"
            val we = "(?![\\p{L}\\p{N}])"
            val rawPattern = if (hw.isRegex) {
                "$wb(?:${hw.word})$we"
            } else {
                val escaped = Regex.escape(hw.word)
                "$wb$escaped$we"
            }
            var flags = java.util.regex.Pattern.UNICODE_CHARACTER_CLASS
            if (!hw.caseSensitive) flags = flags or java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE
            val regex = java.util.regex.Pattern.compile(rawPattern, flags).toRegex()
            regex.findAll(text).forEach { match ->
                for (j in match.range) colors[j] = highlightColor
            }
        } catch (_: Exception) {}
    }
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
