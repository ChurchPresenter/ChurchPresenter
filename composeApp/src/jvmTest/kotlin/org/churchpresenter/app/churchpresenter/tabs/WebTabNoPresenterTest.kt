@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.app.churchpresenter.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Web tab with no [org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager] behind it.
 *
 * `WebTab` declares `presenterManager: PresenterManager? = null` and reaches it through roughly forty
 * `presenterManager?.` calls — restoring the saved URL and title, publishing what goes live, driving
 * the live browser, clearing the snapshot on the way out. Every other test in this suite hands it a
 * real manager, so none of those null sides was ever taken, and a null-hostile change to any one of
 * them would go unnoticed until someone composed the tab without one.
 *
 * That is a real configuration rather than a contrivance — the parameter defaults to null so the tab
 * can be composed standalone. What has to hold is that the operator still gets a working toolbar: the
 * URL bar takes input and normalises it, bookmarks and Add to Schedule work, the navigation buttons
 * fall through to the preview's own controller, and the one action that genuinely needs an output is
 * the only one disabled.
 */
class WebTabNoPresenterTest {

    @Test
    fun `the toolbar is present and usable with no presenter attached`() = webTabWithoutPresenter { _ ->
        // Restoring the saved URL and title is the first thing the tab does. With no manager both
        // fall back to blank rather than throwing on the way in, so the bar shows its placeholder.
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).assertExists()
        webButton(WebLabel.BOOKMARK_ADD).assertIsNotEnabled()
    }

    @Test
    fun `the URL bar still normalises what is typed with no presenter`() = webTabWithoutPresenter { reports ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.org")

        webButton(WebLabel.ADD_TO_SCHEDULE).performClick()

        assertEquals(
            listOf("https://example.org" to "https://example.org"), reports.scheduled,
            "the scheme is still prepended, and the URL still stands in for the title the " +
                "missing presenter would have supplied",
        )
    }

    @Test
    fun `bookmarking works with no presenter to publish through`() = webTabWithoutPresenter { reports ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.org")
        webButton(WebLabel.BOOKMARK_ADD).assertIsEnabled()

        webButton(WebLabel.BOOKMARK_ADD).performClick()

        assertEquals(1, reports.settingsChanges)
        assertTrue(
            reports.settingsAfterChange?.webBookmarks?.any { it.url == "https://example.org" } == true,
            "the bookmark is stored against the normalised URL",
        )
    }

    @Test
    fun `Go Live is disabled with nothing to go live on`() = webTabWithoutPresenter { _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.org")

        // A test window has no secondary display, so the button must report itself unavailable
        // rather than dispatch a presenting mode into a null manager.
        webButton(WebLabel.GO_LIVE).assertIsNotEnabled()
    }

    @Test
    fun `a selected website item still loads into the bar with no presenter to notify`() {
        val item = ScheduleItem.WebsiteItem(id = "w1", url = "https://example.org/one", title = "One")

        webTabWithoutPresenter(selectedWebsiteItem = item) { _ ->
            // Selecting an item pushes the URL to the manager *and* into the bar. With no manager
            // the bar still has to receive it, or picking a website from the schedule does nothing.
            onNodeWithText("https://example.org/one").assertExists()
        }
    }

    @Test
    fun `the navigation buttons fall through to the preview controller instead of failing`() =
        webTabWithoutPresenter { _ ->
            // Back, Forward and Refresh each choose between the live browser and the preview's own
            // controller. With no manager the live side is null, so every one of them takes the
            // fallback — and none may throw on the way.
            listOf(WebLabel.BACK, WebLabel.FORWARD, WebLabel.REFRESH).forEach {
                webButton(it).performClick()
            }
        }

    @Test
    fun `zooming with no presenter drives the preview rather than the live browser`() =
        webTabWithoutPresenter { _ ->
            webButton(WebLabel.ZOOM_IN).performClick()
            onNodeWithText(zoomPercentText(0.5)).assertExists()

            webButton(WebLabel.ZOOM_OUT).performClick()
            onNodeWithText(zoomPercentText(0.0)).assertExists()
        }

    @Test
    fun `the desktop-mobile toggle works with no live browser to reload`() = webTabWithoutPresenter { _ ->
        // Toggling reloads the live browser when there is one; with none it must still flip label.
        onNodeWithText(WebLabel.DESKTOP).performClick()

        onNodeWithText(WebLabel.MOBILE).assertExists()
    }
}
