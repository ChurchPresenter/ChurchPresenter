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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.band.ui.BandControlPanel
import org.churchpresenter.lottiegen.ui.DarkPalette
import org.churchpresenter.lottiegen.ui.LightPalette
import org.churchpresenter.lottiegen.ui.LottieGenTheme
import org.churchpresenter.lottiegen.ui.PreviewPanel
import org.churchpresenter.lottiegen.ui.ProvideLottieGenPalette
import org.churchpresenter.lottiegen.ui.Tokens
import java.awt.Cursor
import java.io.File

private const val MIN_PANEL_WIDTH = 350f
private const val MAX_PANEL_WIDTH = 800f
private const val DEFAULT_PANEL_WIDTH = 436f

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
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(scope) { BibleLottieGenViewModel(scope, outputDir, onFileSaved, seed) }

    val content: @Composable () -> Unit = {
        Surface(modifier = Modifier.fillMaxSize(), color = Tokens.AppBg) {
            var controlPanelWidth by remember { mutableStateOf(DEFAULT_PANEL_WIDTH) }
            val density = LocalDensity.current
            Row(modifier = Modifier.fillMaxSize()) {
                BandControlPanel(viewModel, controlPanelWidth.dp, pickImage)
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
                PreviewPanel(
                    jsonString = viewModel.generatedJson,
                    aspectRatio = viewModel.config.canvasW.toFloat() / viewModel.config.canvasH.toFloat(),
                    statusText = viewModel.statusText,
                    canvasW = viewModel.config.canvasW,
                    canvasH = viewModel.config.canvasH,
                    durationSeconds = viewModel.timeline.totalSeconds,
                    canvasCornerRadius = 0.dp,
                )
            }
        }
    }

    if (embedded) {
        val isLight = MaterialTheme.colorScheme.surface.luminance() > LIGHT_SURFACE_LUMINANCE
        ProvideLottieGenPalette(if (isLight) LightPalette else DarkPalette) { content() }
    } else {
        LottieGenTheme { content() }
    }
}

private const val LIGHT_SURFACE_LUMINANCE = 0.5f
