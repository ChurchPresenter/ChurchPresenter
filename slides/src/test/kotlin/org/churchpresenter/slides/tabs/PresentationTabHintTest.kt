@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.testing.showsContainingText
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationTabHintTest {

    private fun unbound(vararg actions: ShortcutAction) = ShortcutMap.from(
        KeyboardShortcutSettings(overrides = actions.associate { it.name to emptyList<KeyChord>() })
    )

    private fun withSlides(shortcuts: ShortcutMap, width: Dp, block: ComposeUiTest.() -> Unit) {
        val settings = AppSettings()
        val vm = PresentationViewModel(settings)
        val (dir, files) = fakeSlideFiles(2)
        try {
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        CompositionLocalProvider(LocalShortcuts provides shortcuts) {
                            Box(Modifier.width(width)) {
                                PresentationTab(appSettings = settings, viewModel = vm)
                            }
                        }
                    }
                }
                vm.slideFiles.addAll(files)
                waitUntil("the thumbnails drawn", 5_000) {
                    onAllNodesWithContentDescription("Slide 1").fetchSemanticsNodes().isNotEmpty()
                }
                block()
            }
        } finally {
            runCatching { vm.dispose() }
            dir.deleteRecursively()
        }
    }

    @Test
    fun `with every key unbound no hint is drawn, even on a narrow tab`() = withSlides(
        unbound(
            ShortcutAction.PRESENTATION_PREVIOUS,
            ShortcutAction.PRESENTATION_NEXT,
            ShortcutAction.PRESENTATION_PLAY_PAUSE,
            ShortcutAction.PRESENTATION_BLANK,
        ),
        width = 320.dp,
    ) {
        assertFalse(showsContainingText("blank screen"))
    }

    @Test
    fun `with only the blank key bound the hint is still drawn`() = withSlides(
        unbound(
            ShortcutAction.PRESENTATION_PREVIOUS,
            ShortcutAction.PRESENTATION_NEXT,
            ShortcutAction.PRESENTATION_PLAY_PAUSE,
        ),
        width = 1600.dp,
    ) {
        assertTrue(showsContainingText("blank screen"))
    }

    @Test
    fun `with only play-pause bound the hint is still drawn`() = withSlides(
        unbound(
            ShortcutAction.PRESENTATION_PREVIOUS,
            ShortcutAction.PRESENTATION_NEXT,
            ShortcutAction.PRESENTATION_BLANK,
        ),
        width = 1600.dp,
    ) {
        assertTrue(showsContainingText("play/pause"))
    }
}
