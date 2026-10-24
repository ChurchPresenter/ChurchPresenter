@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import javax.imageio.ImageIO
import kotlin.test.Test

/** The Web tab tour: a church web page found, bookmarked, put live, and scheduled. */
class WebDemoTour {

    @Test
    fun web() = recordTour("web", tab = "Web", liveLabel = "Show a web page", steps = 9) { webTour() }

    private fun DemoTour.webTour() {
        say("intro")
        intro("Show any web page on the big screen.")
        say("open-tab")
        openTab("Web", "Any web page, live on the projector")
        say("url")
        type("Type an address", "Any website, then Enter", urlField(), URL, enter = true)
        say("bookmark")
        step("Bookmark it", "Saved pages are one click away", find(hasContentDescription("Add Bookmark")))
        say("bookmarks")
        note("Your bookmarks", "Giving, notices, prayer — whatever you use", text("Giving"))
        say("navigate")
        note("Back, forward and refresh", "Just like any browser", inTab(hasContentDescription("Refresh")))
        say("zoom")
        note("Zoom", "Make the page bigger or smaller on screen", inTab(hasContentDescription("Zoom In")))
        say("mobile")
        note("Mobile view", "Show the page as a phone would", inTab(hasText("Desktop") or hasText("Mobile")))
        say("go-live")
        // Without a browser engine no page loads, so the button stays disabled; the click is shown and
        // the page put live the way the Web tab's own screenshot tests do it.
        val live = findAll(hasContentDescription("Go Live")) { it.left > dp(380f) && it.top < dp(140f) }
            .maxBy { it.bounds.left }
        step("Click Go Live", "Sends the page to the projector", live, live = true) {
            showWebPage("Hope City Church — Weekly Notices", page())
        }
        say("live")
        showLive(sub = "The church notices page, live")
        say("mirror")
        note("Mirror or Interactive", "Watch the live page, or click and scroll it", text("Mirror"))
        say("type-to-page")
        note(
            "Type into the page",
            "Fill in a search box or a form on the live page",
            text("Click an input on the live page first"),
        )
        say("schedule")
        step(
            "Add it to the schedule",
            "Bring the page back during the service",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("schedule-row")
        val row = reveal { findAll(hasText("Hope City", substring = true)) { it.right < dp(380f) }.last() }
        note("Added to your schedule", "Ready whenever you need it", row)
        say("outro")
        outro()
    }
}

private fun DemoTour.urlField() = find(hasText("https://")) { it.left > dp(380f) }

/** The control matching [m] in the Web tab itself — the schedule panel has a zoom of its own. */
private fun DemoTour.inTab(m: SemanticsMatcher) = find(m) { it.left > dp(380f) }

private const val URL = "https://hopecity.church/notices"

/** The stand-in page a browser would have drawn. */
private fun page() = WebDemoTour::class.java.getResourceAsStream("/demo-web/page.png")!!.use { ImageIO.read(it) }
