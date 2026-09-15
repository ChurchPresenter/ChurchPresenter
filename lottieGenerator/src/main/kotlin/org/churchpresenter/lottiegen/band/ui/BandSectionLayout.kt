package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BandTextAlign
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.band.ReferencePlacement
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.theme.components.DropdownSelector

private const val PERCENT = 100
private const val MIN_REFERENCE_FRACTION = 0.1f
private const val MAX_REFERENCE_FRACTION = 0.5f

/** The Layout pane: the four pickers, the text area's four margins, the reference height, the outlines. */
@Composable
internal fun LayoutSection(viewModel: BibleLottieGenViewModel) {
    val cfg = viewModel.config
    val kind = cfg.kind.name.lowercase()
    val alignLabel: (BandTextAlign) -> String = {
        if (it == BandTextAlign.FOLLOW_SETTINGS) Strings.bandLabel("align_follow_settings", kind)
        else Strings.bandEnumLabel("align", it.name)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnumDropdown(
                Strings.bandLayout, cfg.layout, SlotLayout.entries, { Strings.bandEnumLabel("layout", it.name) },
                modifier = Modifier.weight(1f),
            ) { v -> viewModel.updateConfig { it.copy(layout = v) } }
            EnumDropdown(
                Strings.bandLabel("reference", kind), cfg.referencePlacement, ReferencePlacement.entries,
                { Strings.bandLabel("reference_${it.name.lowercase()}", kind) },
                modifier = Modifier.weight(1f),
            ) { v -> viewModel.updateConfig { it.copy(referencePlacement = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnumDropdown(
                Strings.bandLabel("text_align", kind), cfg.textAlign, BandTextAlign.entries, alignLabel,
                modifier = Modifier.weight(1f),
            ) { v -> viewModel.updateConfig { it.copy(textAlign = v) } }
            EnumDropdown(
                Strings.bandLabel("reference_align", kind), cfg.referenceAlign, BandTextAlign.entries, alignLabel,
                modifier = Modifier.weight(1f),
            ) { v -> viewModel.updateConfig { it.copy(referenceAlign = v) } }
        }
    }
    Hairline()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(Strings.bandTextArea, Modifier.weight(1f))
        LinkToggle(viewModel.linkTextArea) { viewModel.linkTextArea = it }
    }
    // Room off each edge, or given back when negative, as far as the band's edge: all the way
    // down on both sides is the band's full width. Half the canvas either way keeps a slot.
    val maxSide = cfg.canvasW / 2f
    val maxEdge = cfg.canvasH / 2f
    val set: (Int, (Int) -> Unit) -> Unit = { v, one ->
        if (viewModel.linkTextArea) viewModel.setTextArea(v) else one(v)
    }
    SliderGrid(
        listOf(
            GridSlider(Strings.bandTextAreaLeft, cfg.textAreaLeftPx, maxSide, -maxSide) { v ->
                set(v) { viewModel.updateConfig { it.copy(textAreaLeftPx = v) } }
            },
            GridSlider(Strings.bandTextAreaRight, cfg.textAreaRightPx, maxSide, -maxSide) { v ->
                set(v) { viewModel.updateConfig { it.copy(textAreaRightPx = v) } }
            },
            GridSlider(Strings.bandTextAreaTop, cfg.textAreaTopPx, maxEdge, -maxEdge) { v ->
                set(v) { viewModel.updateConfig { it.copy(textAreaTopPx = v) } }
            },
            GridSlider(Strings.bandTextAreaBottom, cfg.textAreaBottomPx, maxEdge, -maxEdge) { v ->
                set(v) { viewModel.updateConfig { it.copy(textAreaBottomPx = v) } }
            },
        ),
    )
    Hairline()
    ThinSlider(
        label = Strings.bandLabel("reference_height", kind),
        value = cfg.referenceHeightFraction,
        onValueChange = { v -> viewModel.updateConfig { it.copy(referenceHeightFraction = v) } },
        valueRange = MIN_REFERENCE_FRACTION..MAX_REFERENCE_FRACTION,
        format = { "${(it * PERCENT).toInt()}%" },
    )
    BandCheckbox(Strings.bandShowSlotGuides, viewModel.showSlotGuides) { viewModel.showSlotGuides = it }
}

/** The app's dropdown over an enum: keyed by constant name, shown by [labelOf]. */
@Composable
internal fun <T : Enum<T>> EnumDropdown(
    label: String,
    value: T,
    entries: List<T>,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier,
    onPick: (T) -> Unit,
) {
    DropdownSelector(
        label = label,
        value = value.name,
        options = entries.map { it.name to labelOf(it) },
        onValueChange = { name -> entries.firstOrNull { it.name == name }?.let(onPick) },
        modifier = modifier,
    )
}

/** The small pill that makes one margin slider move all four. */
@Composable
private fun LinkToggle(linked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(FIELD_SHAPE)
            .background(if (linked) Tokens.Accent else Tokens.FieldBg)
            .border(1.dp, if (linked) Tokens.Accent else Tokens.FieldBorder, FIELD_SHAPE)
            .clickable { onChange(!linked) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Strings.bandTextAreaLink, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (linked) Tokens.OnAccent else Tokens.LabelText,
        )
    }
}
