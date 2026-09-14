package org.churchpresenter.lottiegen.band

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.band.ui.BandControlPanel
import org.churchpresenter.lottiegen.band.ui.BandPreviewPanel
import org.churchpresenter.lottiegen.ui.LottieGenTheme
import org.churchpresenter.lottiegen.ui.ProvideLottieGenPalette
import org.churchpresenter.lottiegen.ui.paletteFrom
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.lottiegen.band.ui.OwnColorField
import java.awt.Cursor
import java.io.File

/** A font picker a host lends the generator: the current family, where a pick goes, and the field's modifier. */
typealias BandFontPicker = @Composable (value: String, onValueChange: (String) -> Unit, modifier: Modifier) -> Unit

/**
 * A colour field a host lends the generator: the caption, the colour, where a pick goes and the
 * field's modifier — the whole control, so a click anywhere on it opens the host's own dialog.
 */
typealias BandColorField =
    @Composable (label: String, color: String, onColorChange: (String) -> Unit, modifier: Modifier) -> Unit

/** The colour field the band generator draws; the host's, once it has lent one. */
val LocalBandColorField = staticCompositionLocalOf<BandColorField> { ownColorField }

private val ownColorField: BandColorField = { label, color, onColorChange, modifier ->
    OwnColorField(label, color, onColorChange, modifier)
}

private const val MIN_PANEL_WIDTH = 350f
private const val MAX_PANEL_WIDTH = 800f
private const val DEFAULT_PANEL_WIDTH = 376f

/**
 * The Bible band generator: the same two-pane shape as the main generator, over a config of its
 * own. [seed] carries what the host already knows — the band's size and the Bible typography —
 * so the preview starts out looking like the output will.
 */
@Composable
fun BibleLottieGenApp(
    outputDir: File?,
    onFileSaved: ((File) -> Unit)?,
    seed: BibleLottieGenConfig = BibleLottieGenConfig(),
    embedded: Boolean = true,
    /**
     * How a picture is chosen. A host passes its own native chooser — the one that shows the
     * pictures rather than their names; null falls back to a plain Swing dialog.
     */
    pickImage: (suspend () -> File?)? = null,
    /**
     * How the sample's font is chosen. A host passes its own picker — the one that lists the
     * machine's fonts each in its own face; null falls back to a plain list of the bundled ones.
     */
    fontPicker: BandFontPicker? = null,
    /** How a colour is shown and chosen: the host's own field, or the generator's when none is lent. */
    colorField: BandColorField? = null,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(scope) { BibleLottieGenViewModel(scope, outputDir, onFileSaved, seed) }

    val content: @Composable () -> Unit = {
        Surface(modifier = Modifier.fillMaxSize(), color = Tokens.AppBg) {
            var controlPanelWidth by remember { mutableStateOf(DEFAULT_PANEL_WIDTH) }
            val density = LocalDensity.current
            Row(modifier = Modifier.fillMaxSize()) {
                BandControlPanel(viewModel, controlPanelWidth.dp, pickImage, fontPicker)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(Tokens.CardBorder)
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                val deltaDp = with(density) { dragAmount.x.toDp().value }
                                controlPanelWidth = (controlPanelWidth + deltaDp)
                                    .coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH)
                            }
                        },
                )
                BandPreviewPanel(viewModel)
            }
        }
    }

    val themed: @Composable () -> Unit = {
        CompositionLocalProvider(LocalBandColorField provides (colorField ?: ownColorField)) { content() }
    }
    if (embedded) {
        // The host's own scheme, so the window is in whichever of the app's themes is on.
        ProvideLottieGenPalette(paletteFrom(MaterialTheme.colorScheme)) { themed() }
    } else {
        LottieGenTheme { themed() }
    }
}
