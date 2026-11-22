@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.shortcuts.KeyChord
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

class QuickBackgroundTrayShortcutsTest {

    private fun entry(id: String, color: String) = QuickBackground(
        id = id,
        background = SongBackground(type = SongBackgroundType.COLOR, color = color),
        lowerThirdBackground = SongBackground(type = SongBackgroundType.COLOR, color = color),
    )

    private val trayActions = ShortcutAction.entries.filter { it.name.startsWith("QUICK_BACKGROUND_") }

    private fun bindingOnly(bound: Set<ShortcutAction>) = ShortcutMap.from(
        KeyboardShortcutSettings(
            overrides = trayActions.associate { action ->
                action.name to (if (action in bound) action.defaults else emptyList<KeyChord>())
            },
        ),
    )

    private fun tray(
        backgrounds: List<QuickBackground>,
        expanded: Boolean,
        shortcuts: ShortcutMap = ShortcutMap.DEFAULT,
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
        waitForIdle()
        block()
    }

    private fun ComposeUiTest.swatches() = onAllNodes(hasClickAction() and !hasTestTag(QUICK_BACKGROUND_HEADER_TAG))

    private fun ComposeUiTest.hint() = onAllNodes(hasText("manage in Settings", substring = true))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)

    @Test
    fun `a shut tray still picks from its swatches`() {
        var picked: QuickBackground? = null
        val second = entry("b", "#445566")
        tray(listOf(entry("a", "#112233"), second), expanded = false, onPick = { picked = it }) {
            assertEquals(2, swatches().fetchSemanticsNodes().size)
            swatches()[1].performClick()
            waitForIdle()
        }
        assertSame(second, picked)
    }

    @Test
    fun `hovering a swatch names its background and its key`() =
        tray(listOf(entry("a", "#112233")), expanded = false) {
            onAllNodesWithText("Custom color", substring = true).assertCountEquals(0)

            swatches()[0].performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(600)
            waitForIdle()

            onAllNodes(hasText("Custom color · ", substring = true)).assertCountEquals(1)
        }

    @Test
    fun `with every slot unbound the hint is gone and a tooltip is the name alone`() =
        tray(listOf(entry("a", "#112233")), expanded = true, shortcuts = bindingOnly(emptySet())) {
            assertEquals(0, hint().size)

            onNode(hasTextExactly("Custom color")).performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(600)
            waitForIdle()

            onAllNodes(hasTextExactly("Custom color")).assertCountEquals(2)
        }

    @Test
    fun `a single bound slot is no range, so no hint`() = tray(
        listOf(entry("a", "#112233")),
        expanded = true,
        shortcuts = bindingOnly(setOf(ShortcutAction.QUICK_BACKGROUND_1)),
    ) {
        assertEquals(0, hint().size)
    }

    @Test
    fun `the shipped bindings give a hint with a range`() = tray(listOf(entry("a", "#112233")), expanded = true) {
        assertEquals(1, hint().size)
    }
}
