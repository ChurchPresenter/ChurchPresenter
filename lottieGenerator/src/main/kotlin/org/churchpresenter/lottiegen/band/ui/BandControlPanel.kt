package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BandFontPicker
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens
import java.io.File
import androidx.compose.material3.LocalContentColor
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette

/** The five panes the band generator's controls are split across. */
private enum class BandPane {
    BAND, LAYOUT, MOTION, TEXT, SAVE;

    val label: String
        get() = when (this) {
            BAND -> Strings.bandSectionBand
            LAYOUT -> Strings.bandSectionLayout
            MOTION -> Strings.bandTabMotion
            TEXT -> Strings.bandTabText
            SAVE -> Strings.bandSectionSave
        }
}

private val SCROLLBAR_GUTTER = 10.dp

/** The band generator's left pane: a strip of tabs, and under it the controls of the chosen one. */
@Composable
internal fun BandControlPanel(
    viewModel: BibleLottieGenViewModel,
    panelWidth: Dp,
    pickImage: (suspend () -> File?)? = null,
    fontPicker: BandFontPicker? = null,
) {
    var pane by remember { mutableStateOf(BandPane.BAND) }
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxHeight().width(panelWidth).background(Tokens.PanelBg)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)) {
            PaneTabs(pane) { pane = it }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(start = 12.dp, end = 12.dp + SCROLLBAR_GUTTER, top = 11.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                when (pane) {
                    BandPane.BAND -> BandSection(viewModel, pickImage)
                    BandPane.LAYOUT -> LayoutSection(viewModel)
                    BandPane.MOTION -> MotionSection(viewModel)
                    BandPane.TEXT -> TextSection(viewModel, fontPicker)
                    BandPane.SAVE -> SaveSection(viewModel)
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp),
            )
        }
    }
}

/** The segmented strip of pane names: the app's sunken track, the chosen pane raised. */
@Composable
private fun PaneTabs(current: BandPane, onPick: (BandPane) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sunken(CARD_SHAPE, elevationPalette())
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BandPane.entries.forEach { p ->
            val on = p == current
            SegmentTrackItem(
                selected = on,
                onClick = { onPick(p) },
                modifier = Modifier.weight(1f).height(29.dp),
            ) {
                Text(
                    p.label, fontSize = 11.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = LocalContentColor.current,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
