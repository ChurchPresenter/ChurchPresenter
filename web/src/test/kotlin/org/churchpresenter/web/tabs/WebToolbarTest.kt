@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.WebBookmark
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The toolbar's rules beyond what `WebTabTest` drives: where a website may go live, Go Live itself
 * once it may, the page's own title on what the operator saves, and keys the two text fields leave
 * alone.
 */
class WebToolbarTest {

    private fun projection(vararg assignments: ScreenAssignment, showWebsite: Boolean = true) = ProjectionSettings(
        screenAssignments = assignments.toList(),
        outputProfiles = listOf(OutputProfile(id = PROFILE, name = "Main", showWebsite = showWebsite)),
    )

    private fun onDisplay(display: Int, type: String = "screen", profile: String? = PROFILE) =
        ScreenAssignment(targetDisplay = display, targetType = type, activeProfileId = profile)

    // ── Where a website may go live ─────────────────────────────────────────────────────────────

    @Test
    fun `a regular output on a display whose profile shows websites can take one`() {
        assertTrue(hasWebCapableOutput(projection(onDisplay(1))))
    }

    @Test
    fun `a DeckLink output, an output with no display, or a profile hiding websites cannot`() {
        assertFalse(hasWebCapableOutput(projection(onDisplay(1, type = Constants.TARGET_TYPE_DECKLINK))))
        assertFalse(hasWebCapableOutput(projection(onDisplay(-1))))
        assertFalse(hasWebCapableOutput(projection(onDisplay(1), showWebsite = false)))
        assertFalse(hasWebCapableOutput(projection()))
    }

    @Test
    fun `one capable output among others is enough`() {
        assertTrue(hasWebCapableOutput(projection(onDisplay(-2), onDisplay(2))))
    }

    // ── Go Live ─────────────────────────────────────────────────────────────────────────────────

    private val liveCapable: (AppSettings) -> AppSettings =
        { it.copy(projectionSettings = projection(onDisplay(1))) }

    @Test
    fun `with a second screen and a capable output, Go Live puts the typed address on screen`() = webTab(
        settings = liveCapable,
        hasSecondaryDisplay = true,
    ) { presenter, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        webButton(WebLabel.GO_LIVE).assertIsEnabled().performClick()
        waitForIdle()

        assertEquals(Presenting.WEBSITE, presenter.presentingMode.value)
        assertEquals("https://example.com", presenter.websiteUrl.value)
    }

    @Test
    fun `a second screen alone is not enough with no output that shows websites`() = webTab(
        hasSecondaryDisplay = true,
    ) { _, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        webButton(WebLabel.GO_LIVE).assertIsNotEnabled()
    }

    // ── The page's own title ────────────────────────────────────────────────────────────────────

    private val scheduled = ScheduleItem.WebsiteItem(id = "w", url = "https://s.example", title = "Sermon Notes")

    @Test
    fun `a bookmark is saved under the page's title once it has one`() = webTab(
        selectedWebsiteItem = scheduled,
    ) { _, reports ->
        waitForIdle()
        webButton(WebLabel.BOOKMARK_ADD).performClick()

        assertEquals(
            listOf(WebBookmark(url = "https://s.example", title = "Sermon Notes")),
            reports.settingsAfterChange?.webBookmarks,
        )
    }

    @Test
    fun `Add to Schedule carries the page's title once it has one`() = webTab(
        selectedWebsiteItem = scheduled,
    ) { _, reports ->
        waitForIdle()
        webButton(WebLabel.ADD_TO_SCHEDULE).performClick()

        assertEquals(listOf("https://s.example" to "Sermon Notes"), reports.scheduled)
    }

    @Test
    fun `removing one bookmark chip keeps the others`() = webTab(
        settings = {
            it.copy(
                webBookmarks = listOf(
                    WebBookmark(url = "https://a.example", title = "A Site"),
                    WebBookmark(url = "https://b.example", title = "B Site"),
                ),
            )
        },
    ) { _, reports ->
        onAllNodes(hasText("✕"))[0].performClick()

        assertEquals(listOf("https://b.example"), reports.settingsAfterChange?.webBookmarks?.map { it.url })
    }

    @Test
    fun `a bookmark chip clicked while live with no live browser yet still moves the bar`() = webTab(
        settings = { it.copy(webBookmarks = listOf(WebBookmark(url = "https://a.example", title = "A Site"))) },
    ) { presenter, _ ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()

        onNodeWithText("A Site").performClick()
        waitForIdle()

        assertEquals("https://a.example", presenter.websiteUrl.value)
    }

    // ── Keys the fields leave alone ─────────────────────────────────────────────────────────────

    @Test
    fun `only Enter in the address bar navigates`() = webTab { presenter, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        onNodeWithText("example.com").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()

        assertEquals("", presenter.websiteUrl.value, "nothing was sent to the output")
    }

    @Test
    fun `Enter in type-to-page with no live browser just clears the field`() = webTab { presenter, _ ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()

        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextInput("x")
        onNodeWithText("x").performKeyInput { pressKey(Key.Tab) }
        onNodeWithText("x").assertExists()

        onNodeWithText("x").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).assertExists()
    }

    // ── Defaults ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tab works on its own defaults once the engine is up`() = runComposeUiTest {
        setContent { MaterialTheme { WebTab(cefInitialized = true) } }

        onNodeWithText(WebLabel.PREVIEW_HINT).assertExists()
        webButton(WebLabel.GO_LIVE).assertIsNotEnabled()
    }

    @Test
    fun `the unavailable panel works on its own defaults`() = runComposeUiTest {
        setContent { MaterialTheme { WebEngineUnavailable() } }

        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_TITLE).assertExists()
    }

    private companion object {
        const val PROFILE = "main"
    }
}
