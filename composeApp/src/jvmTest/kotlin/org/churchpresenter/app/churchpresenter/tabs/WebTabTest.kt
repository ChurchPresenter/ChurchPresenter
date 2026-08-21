@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.app.churchpresenter.data.settings.WebBookmark
import org.churchpresenter.app.churchpresenter.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebTabTest {

    @Test
    fun `the URL bar starts on the https placeholder with the clear button hidden`() = webTab { _, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).assertExists()
        webButton(WebLabel.BOOKMARK_ADD).assertIsNotEnabled()
    }

    @Test
    fun `typing a URL updates the bar and enables the bookmark star`() = webTab { _, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")

        onNodeWithText("example.com").assertExists()
        webButton(WebLabel.BOOKMARK_ADD).assertIsEnabled()
    }

    @Test
    fun `clicking the bookmark star records the normalised URL as a new bookmark`() = webTab { _, reports ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        webButton(WebLabel.BOOKMARK_ADD).performClick()

        assertEquals(1, reports.settingsChanges)
        assertEquals(
            listOf(WebBookmark(url = "https://example.com", title = "https://example.com")),
            reports.settingsAfterChange?.webBookmarks,
        )
    }

    @Test
    fun `the star shows Remove Bookmark once the current URL is already bookmarked`() = webTab(
        settings = { it.copy(webBookmarks = listOf(WebBookmark(url = "https://example.com", title = "Example"))) },
    ) { _, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")

        webButton(WebLabel.BOOKMARK_REMOVE).assertExists()
    }

    @Test
    fun `clicking the star again removes an already-bookmarked URL`() = webTab(
        settings = { it.copy(webBookmarks = listOf(WebBookmark(url = "https://example.com", title = "Example"))) },
    ) { _, reports ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        webButton(WebLabel.BOOKMARK_REMOVE).performClick()

        assertEquals(1, reports.settingsChanges)
        assertEquals(emptyList(), reports.settingsAfterChange?.webBookmarks)
    }

    @Test
    fun `clicking Add to Schedule reports the normalised URL and a title falling back to the URL`() =
        webTab { _, reports ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        webButton(WebLabel.ADD_TO_SCHEDULE).performClick()

        assertEquals(listOf("https://example.com" to "https://example.com"), reports.scheduled)
    }

    @Test
    fun `Add to Schedule is absent when the tab is given no callback for it`() =
        webTab(includeAddToSchedule = false) { _, _ ->
        assertTrue(!hasWebButton(WebLabel.ADD_TO_SCHEDULE))
    }

    @Test
    fun `Go Live is disabled with no secondary display attached`() = webTab { _, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        webButton(WebLabel.GO_LIVE).assertIsNotEnabled()
    }

    @Test
    fun `the preview pane shows the go-live hint until a page has ever loaded`() = webTab { _, _ ->
        onNodeWithText(WebLabel.PREVIEW_HINT).assertExists()
    }

    @Test
    fun `Back Forward Refresh and Clear Cache are all present and clickable with no browser attached`() =
        webTab { _, _ ->
        webButton(WebLabel.BACK).apply { assertIsEnabled(); performClick() }
        webButton(WebLabel.FORWARD).apply { assertIsEnabled(); performClick() }
        webButton(WebLabel.REFRESH).apply { assertIsEnabled(); performClick() }
        webButton(WebLabel.CLEAR_CACHE).apply { assertIsEnabled(); performClick() }
    }

    @Test
    fun `zoom in and out update the displayed percentage using the same formula`() = webTab { _, _ ->
        onNodeWithText(zoomPercentText(0.0)).assertExists()

        webButton(WebLabel.ZOOM_IN).performClick()
        onNodeWithText(zoomPercentText(0.5)).assertExists()

        webButton(WebLabel.ZOOM_OUT).performClick()
        webButton(WebLabel.ZOOM_OUT).performClick()
        onNodeWithText(zoomPercentText(-0.5)).assertExists()
    }

    @Test
    fun `the mobile-desktop toggle flips its own label`() = webTab { _, _ ->
        onNodeWithText(WebLabel.DESKTOP).assertExists()

        onNodeWithText(WebLabel.DESKTOP).performClick()
        onNodeWithText(WebLabel.MOBILE).assertExists()

        onNodeWithText(WebLabel.MOBILE).performClick()
        onNodeWithText(WebLabel.DESKTOP).assertExists()
    }

    @Test
    fun `bookmark chips are hidden when there are no bookmarks`() = webTab { _, _ ->
        assertTrue(!showsExactly("A"))
    }

    @Test
    fun `clicking a bookmark chip loads its URL and title into the bar`() = webTab(
        settings = { it.copy(webBookmarks = listOf(WebBookmark(url = "https://a.example", title = "A Site"))) },
    ) { _, _ ->
        onNodeWithText("A Site").performClick()

        onNodeWithText("https://a.example").assertExists()
    }

    @Test
    fun `removing a bookmark chip reports the remaining list`() = webTab(
        settings = { it.copy(webBookmarks = listOf(WebBookmark(url = "https://a.example", title = "A Site"))) },
    ) { _, reports ->
        onNodeWithText("✕").performClick()

        assertEquals(1, reports.settingsChanges)
        assertEquals(emptyList(), reports.settingsAfterChange?.webBookmarks)
    }

    @Test
    fun `selecting a website schedule item loads it and goes live`() {
        val item = ScheduleItem.WebsiteItem(id = "w1", url = "https://scheduled.example", title = "Scheduled Page")
        webTab(selectedWebsiteItem = item) { presenter, _ ->
            waitForIdle()

            onNodeWithText("https://scheduled.example").assertExists()
            assertEquals(Presenting.WEBSITE, presenter.presentingMode.value)
            assertEquals("https://scheduled.example", presenter.websiteUrl.value)
        }
    }
}
