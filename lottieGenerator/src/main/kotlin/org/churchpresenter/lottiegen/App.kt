package org.churchpresenter.lottiegen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.luminance
import org.churchpresenter.lottiegen.ui.DarkPalette
import org.churchpresenter.lottiegen.ui.LightPalette
import org.churchpresenter.lottiegen.ui.LottieGenTheme
import org.churchpresenter.lottiegen.ui.ProvideLottieGenPalette
import org.churchpresenter.lottiegen.ui.Tokens
import androidx.compose.runtime.Composable
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
import org.churchpresenter.lottiegen.ui.ControlPanel
import org.churchpresenter.lottiegen.ui.PreviewPanel
import org.churchpresenter.lottiegen.viewmodel.LottieGenViewModel
import java.awt.Cursor
import java.io.File

/**
 * The generator.
 *
 * [embedded] says a host app has already applied its own MaterialTheme around this — ChurchPresenter
 * does, through `AppThemeWrapper` — so the generator keeps that theme and only chooses which palette
 * its hand-drawn chrome should draw in, from the host's own surface colour. Standalone it owns the
 * whole theme and stays dark.
 *
 * It defaults to whether an output folder was configured because that is what the embedded caller
 * passes, but the two are not the same thing: opened from the Help menu with no folder set, the
 * generator is still embedded. That is why this is a parameter rather than a test on [outputDir] —
 * as the latter, the Help-menu window forced its own dark theme over a light app.
 */
@Composable
fun App(
    outputDir: File? = null,
    onFileSaved: (() -> Unit)? = null,
    canvasWidth: Int? = null,
    canvasHeight: Int? = null,
    embedded: Boolean = outputDir != null
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(scope) {
        LottieGenViewModel(scope, outputDir, onFileSaved, canvasWidth, canvasHeight)
    }

    // When embedded in ChurchPresenter, the parent already provides a MaterialTheme
    // via AppThemeWrapper. When standalone, we need our own.
    val content: @Composable () -> Unit = {
        Surface(modifier = Modifier.fillMaxSize(), color = Tokens.AppBg) {
            var controlPanelWidth by remember { mutableStateOf(436f) }
            val density = LocalDensity.current

            Row(modifier = Modifier.fillMaxSize()) {
                ControlPanel(viewModel, controlPanelWidth.dp)

                // Draggable divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(Tokens.CardBorder)
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                val deltaDp = with(density) { dragAmount.x.toDp().value }
                                controlPanelWidth = (controlPanelWidth + deltaDp).coerceIn(350f, 800f)
                            }
                        }
                )

                PreviewPanel(
                    jsonString = viewModel.generatedJson,
                    aspectRatio = viewModel.config.canvasW.toFloat() / viewModel.config.canvasH.toFloat(),
                    statusText = viewModel.statusText,
                    canvasW = viewModel.config.canvasW,
                    canvasH = viewModel.config.canvasH,
                    durationSeconds = viewModel.config.animDuration + viewModel.config.holdDuration
                )
            }
        }
    }

    if (embedded) {
        // Inherit the host's MaterialTheme, and follow it: a light host gets the light palette.
        // `isLight` is read off the host's own surface rather than a flag it would have to pass,
        // so any theme it applies — including the six accent themes — lands on the right side.
        val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        ProvideLottieGenPalette(if (isLight) LightPalette else DarkPalette) {
            content()
        }
    } else {
        // Standalone — the tool owns the whole theme, and stays dark.
        LottieGenTheme {
            content()
        }
    }
}
