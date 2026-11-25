@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.sharedui.screenshot.PinnedFonts
import org.churchpresenter.sharedui.screenshot.bothThemes
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.theme.ThemeMode

private const val SETTLE_MS = 500L

class FrozenInteractionSource(vararg seed: Interaction) : MutableInteractionSource {
    override val interactions = MutableSharedFlow<Interaction>(replay = seed.size.coerceAtLeast(1))

    init {
        seed.forEach { interactions.tryEmit(it) }
    }

    override suspend fun emit(interaction: Interaction) = interactions.emit(interaction)

    override fun tryEmit(interaction: Interaction) = interactions.tryEmit(interaction)
}

@Composable
fun testIcon(): Painter = rememberVectorPainter(
    ImageVector.Builder(
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f,
    ).addPath(
        PathParser().parsePathString(
            "M2.5 3h11a.8.8 0 0 1 .8.8v6.4a.8.8 0 0 1-.8.8h-11a.8.8 0 0 1-.8-.8V3.8a.8.8 0 0 1 .8-.8z" +
                "M5.5 13.5h5M8 11v2.5",
        ).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
    ).build(),
)

@Composable
fun HosannaSurface(dark: Boolean = false, content: @Composable () -> Unit) {
    HosannaTheme(darkTheme = dark) {
        Box(Modifier.background(HosannaTheme.colors.surface).padding(12.dp)) { content() }
    }
}

fun hosannaComponent(section: String, name: String, content: @Composable () -> Unit) =
    bothThemes(section, name, trim = true) { mode, file ->
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                PinnedFonts { HosannaSurface(dark = mode == ThemeMode.DARK, content = content) }
            }
            settle()
            captureTo(file)
        }
    }

fun hosannaPopup(
    section: String,
    name: String,
    keepHover: Boolean = false,
    drive: ComposeUiTest.() -> Unit,
    content: @Composable () -> Unit,
) = bothThemes(section, name, trim = true) { mode, file ->
    runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            PinnedFonts { HosannaSurface(dark = mode == ThemeMode.DARK, content = content) }
        }
        settle()
        drive()
        settle()
        val roots = onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false).size
        captureTo(file, rootIndex = roots - 1, keepHover = keepHover)
    }
}

fun ComposeUiTest.settle() {
    mainClock.advanceTimeBy(SETTLE_MS)
    waitForIdle()
}
