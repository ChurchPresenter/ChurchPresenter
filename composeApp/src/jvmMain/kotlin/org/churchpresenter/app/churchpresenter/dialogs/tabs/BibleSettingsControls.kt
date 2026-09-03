package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_split_long_verses
import churchpresenter.composeapp.generated.resources.bible_split_long_verses_off
import churchpresenter.composeapp.generated.resources.words_suffix
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_MAX
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_OFF
import org.churchpresenter.app.churchpresenter.viewmodel.longVerseSliderPosition
import org.churchpresenter.app.churchpresenter.viewmodel.longVerseSliderStop
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource

/** Dim enough to read as "not typed yet", solid enough to read at all. */
private const val PLACEHOLDER_ALPHA = 0.6f

/**
 * A control under its own small caption, which is how every cell of the typography grid is built.
 *
 * The caption drops a trailing colon, because the shared string resources are written for the
 * `Label: [control]` rows used elsewhere and this grid sets them above the control instead.
 */
@Composable
internal fun ControlColumn(
    label: String,
    modifier: Modifier = Modifier,
    /**
     * The control draws [label] inside itself, as a dropdown does, so the caption line above it is
     * left blank rather than dropped: the cells of a row line up on that line, and a cell without
     * one would sit a caption's height above its neighbours.
     */
    labelInsideControl: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (labelInsideControl) "" else label.removeSuffix(":"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

/**
 * The module's own value, shown in an empty name field as the thing that is still in force.
 *
 * A blank name field means "keep using what the module calls itself", so the placeholder is the
 * live value rather than a hint about one.
 */
@Composable
internal fun PanelPlaceholder(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * How long a verse must be before it is broken across two slides -- and, at its first stop, whether
 * it is broken at all.
 *
 * One control rather than a checkbox beside a number, because the two were always the same decision.
 * The checkbox meant "split at 45 words", and 45 only ever fitted English: Esther 8:9 is 90 words in
 * the KJV and 36 in Tamil, so the fixed threshold broke the English text of a verse and left the
 * Tamil text of the very same verse whole. `VerseSplit.kt` carries the measured ranges behind the
 * ends of the track.
 */
@Composable
internal fun LongVerseSplitSlider(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val bible = settings.bibleSettings
    val position = longVerseSliderPosition(bible.splitLongVerses, bible.longVerseWordCount)
    val wordsSuffix = stringResource(Res.string.words_suffix)
    val offLabel = stringResource(Res.string.bible_split_long_verses_off)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // The value sits on the caption line rather than at the end of the track, so the track runs
        // the full width of the pane: at the rail's narrowest a trailing label takes enough of the
        // row that the Off stop and the 25-word stop land within a few pixels of each other.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(Res.string.bible_split_long_verses),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (position == LONG_VERSE_WORDS_OFF) offLabel else "$position $wordsSuffix",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        SlimSlider(
            value = position.toFloat(),
            onValueChange = { raw ->
                onSettingsChange { s ->
                    val (splitting, words) = longVerseSliderStop(raw, s.bibleSettings.longVerseWordCount)
                    s.copy(
                        bibleSettings = s.bibleSettings.copy(
                            splitLongVerses = splitting,
                            longVerseWordCount = words,
                        )
                    )
                }
            },
            valueRange = LONG_VERSE_WORDS_OFF.toFloat()..LONG_VERSE_WORDS_MAX.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
