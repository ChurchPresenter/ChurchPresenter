@file:OptIn(ExperimentalLayoutApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.recent
import org.churchpresenter.resources.generated.resources.theme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.colorSchemeFor
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** A schedule label's two colours: the band it is drawn on, and the text on that band. */
@Serializable
internal data class LabelColors(val background: String, val text: String)

/** Case-insensitive equality: the pickers hand back upper-case hex, saved labels may not be. */
internal fun LabelColors.matches(other: LabelColors): Boolean =
    background.equals(other.background, ignoreCase = true) && text.equals(other.text, ignoreCase = true)

/**
 * The colour pairs a new label can be started from: one per theme the app ships, plus the current
 * theme's own card tone at the head of the list.
 *
 * Every theme, not five roles of the active one -- an operator running Dark still wants an
 * Ocean-blue or Forest-green section band, and the nine palettes are the set of colours this app
 * already stands behind. Each theme contributes its `primaryContainer`/`onPrimaryContainer`, the
 * accent pair that gives a palette its character; [ThemeMode.SYSTEM] contributes nothing because it
 * is not a palette, it resolves to Light or Dark.
 *
 * The first entry is the default and is deliberately not an accent: it is the tone an ordinary
 * schedule card is drawn in, because a label is a heading *in* the list rather than a slab across
 * it. Its bold, letter-spaced text and the accent bar beside it are what mark it as a heading.
 */
@Composable
internal fun themeLabelPresets(): List<LabelColors> {
    val current = MaterialTheme.colorScheme
    val palettes = ThemeMode.entries
        .filter { it != ThemeMode.SYSTEM }
        .map { colorSchemeFor(it) }
        .map { LabelColors(cpColorToHex(it.primaryContainer), cpColorToHex(it.onPrimaryContainer)) }
    return buildList {
        add(LabelColors(cpColorToHex(current.surfaceContainer), cpColorToHex(current.onSurface)))
        // Distinct pairs only: Light and Dark are what SYSTEM resolves to, and two themes sharing
        // an accent would show as two swatches doing the same thing.
        palettes.forEach { pair -> if (none { it.matches(pair) }) add(pair) }
    }
}

/**
 * The label colour pairs this user has actually chosen, newest first, across runs.
 *
 * Pairs, not single colours: a band and its text are picked together and only make sense together,
 * so the existing per-colour [RecentColors] list cannot serve this. A theme preset is never
 * recorded here — those already have their own column, and repeating them would push out the custom
 * combinations this list exists to keep.
 *
 * The file is resolved per call rather than latched at class-init, so a test can point `user.home`
 * at a temp directory and have this follow it.
 */
internal object RecentLabelColors {
    private const val MAX = 8
    private fun file() = File(System.getProperty("user.home"), ".churchpresenter/recent_label_colors.json")

    val combos = mutableStateListOf<LabelColors>()

    init { load() }

    fun add(colors: LabelColors) {
        val normalised = LabelColors(colors.background.uppercase(), colors.text.uppercase())
        combos.remove(normalised)
        combos.add(0, normalised)
        while (combos.size > MAX) combos.removeLast()
        save()
    }

    internal fun load() {
        combos.clear()
        try {
            val f = file()
            if (!f.exists()) return
            val json = Json { ignoreUnknownKeys = true }
            combos.addAll(json.decodeFromString<List<LabelColors>>(f.readText()).take(MAX))
        } catch (_: Exception) {
            // A corrupt or half-written file is not worth failing the dialog over: an empty
            // history is the same as a first run.
        }
    }

    private fun save() {
        try {
            val f = file()
            f.parentFile?.mkdirs()
            f.writeText(Json.encodeToString(combos.toList()))
        } catch (_: Exception) {}
    }
}

/**
 * The two swatch columns above the pickers: what the theme offers, and what this user has picked
 * before.
 *
 * Each swatch is the pair itself -- the band's colour with its own text drawn on it -- because a
 * band and a text colour only mean anything together, and a row of plain colour chips would say
 * nothing about whether the two read against each other. The recent column is left out entirely
 * when there is no history rather than shown empty.
 */
@Composable
internal fun LabelColorColumns(
    presets: List<LabelColors>,
    recents: List<LabelColors>,
    onPick: (LabelColors) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LabelColorColumn(
            title = stringResource(Res.string.theme),
            colors = presets,
            tagPrefix = LABEL_PRESET_TAG,
            onPick = onPick,
            modifier = Modifier.weight(1f),
        )
        if (recents.isNotEmpty()) {
            LabelColorColumn(
                title = stringResource(Res.string.recent),
                colors = recents,
                tagPrefix = LABEL_RECENT_TAG,
                onPick = onPick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Test tags for the swatches; the colours themselves are theme-derived, so nothing else names them. */
internal const val LABEL_PRESET_TAG = "labelPreset"
internal const val LABEL_RECENT_TAG = "labelRecent"

@Composable
private fun LabelColorColumn(
    title: String,
    colors: List<LabelColors>,
    tagPrefix: String,
    onPick: (LabelColors) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            colors.forEachIndexed { index, pair ->
                val background = parseHexColor(pair.background)
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(background)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
                        .testTag("${tagPrefix}_$index")
                        .clickable { onPick(pair) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Aa",
                        style = MaterialTheme.typography.labelSmall,
                        // What the label will actually look like, contrast fix included: the row
                        // itself renders through ensureContrast, so a swatch showing the raw pair
                        // would promise something the schedule does not draw.
                        color = Utils.ensureContrast(parseHexColor(pair.text), background, minRatio = 7.0),
                    )
                }
            }
        }
    }
}
