@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.FALLBACK_STAGE_ASPECT
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class QuickBackgroundTrayKeysTest {

    private val quickActions = ShortcutAction.entries.filter { it.name.startsWith("QUICK_BACKGROUND_") }

    private fun entry(id: String) = QuickBackground(
        id = id,
        background = SongBackground(type = SongBackgroundType.COLOR, color = "#112233"),
        lowerThirdBackground = SongBackground(type = SongBackgroundType.COLOR, color = "#112233"),
    )

    private fun boundOnly(vararg kept: ShortcutAction) = ShortcutMap.from(
        KeyboardShortcutSettings(overrides = (quickActions - kept.toSet()).associate { it.name to emptyList() }),
    )

    private fun tray(
        shortcuts: ShortcutMap,
        backgrounds: List<QuickBackground> = listOf(entry("a"), entry("b")),
        expanded: Boolean = true,
        onPick: (QuickBackground?) -> Unit = {},
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalShortcuts provides shortcuts) {
                MaterialTheme {
                    QuickBackgroundTray(
                        backgrounds = backgrounds,
                        tileAspect = FALLBACK_STAGE_ASPECT,
                        activeId = null,
                        expanded = expanded,
                        onExpandedChange = {},
                        onPick = onPick,
                    )
                }
            }
        }
        block()
    }

    private fun ComposeUiTest.hintCount() =
        onAllNodesWithText("manage in Settings", substring = true).fetchSemanticsNodes().size

    @Test
    fun `the default keys put their range in the hint`() = tray(ShortcutMap.DEFAULT) {
        assertEquals(1, hintCount())
    }

    @Test
    fun `with every slot unbound there is no range to show`() = tray(boundOnly()) {
        assertEquals(0, hintCount())
    }

    @Test
    fun `with a single slot bound there is no range either`() =
        tray(boundOnly(ShortcutAction.QUICK_BACKGROUND_1)) {
            assertEquals(0, hintCount())
        }

    @Test
    fun `an unbound slot's tooltip is only the background's name`() =
        tray(boundOnly(), backgrounds = listOf(entry("a"))) {
            onNodeWithText("Custom color").performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(600)
            waitForIdle()
            assertEquals(2, onAllNodes(hasText("Custom color")).fetchSemanticsNodes().size)
            assertEquals(2, onAllNodesWithText("Custom color", substring = true).fetchSemanticsNodes().size)
        }

    @Test
    fun `a swatch in the shut tray picks its background`() {
        var picked: QuickBackground? = null
        val second = entry("b")
        val backgrounds = listOf(entry("a"), second)
        tray(ShortcutMap.DEFAULT, backgrounds = backgrounds, expanded = false, onPick = { picked = it }) {
            val swatches = onAllNodes(hasClickAction() and !hasTestTag(QUICK_BACKGROUND_HEADER_TAG))
            assertEquals(2, swatches.fetchSemanticsNodes().size)
            swatches[1].performClick()
            waitForIdle()
        }
        assertSame(second, picked)
    }
}
