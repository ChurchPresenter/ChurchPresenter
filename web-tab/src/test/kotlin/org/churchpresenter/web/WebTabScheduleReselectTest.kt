@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Clicking the same schedule item twice.
 *
 * `selectedWebsiteItemVersion` exists for one reason, written on the parameter itself: keyed on the
 * item alone, clicking an already-selected item is an unchanged key, so the effect does not re-run
 * and nothing happens. An operator who has navigated away from a scheduled page and clicks it again
 * to get back would find the click did nothing.
 *
 * That is the whole contract, and nothing tested it — the harness passes the version once and never
 * moves it, so this composes the tab directly against state it can change.
 */
class WebTabScheduleReselectTest {

    private val notices = ScheduleItem.WebsiteItem(
        id = "notices",
        url = "https://notices.example",
        title = "Notices",
    )

    private fun scheduled(block: androidx.compose.ui.test.ComposeUiTest.(FakeWebOutput, () -> Unit) -> Unit) =
        runComposeUiTest {
            val output = FakeWebOutput()
            var version by mutableIntStateOf(0)
            var item by mutableStateOf<ScheduleItem.WebsiteItem?>(null)
            setContent {
                MaterialTheme {
                    WebTab(
                        output = output,
                        selectedWebsiteItem = item,
                        selectedWebsiteItemVersion = version,
                        appSettings = AppSettings(),
                        cefInitialized = true,
                    )
                }
            }
            waitForIdle()
            block(output) { item = notices; version++ }
        }

    @Test
    fun `selecting a scheduled website adopts its address and goes live`() = scheduled { output, click ->
        assertEquals("", output.url, "nothing selected yet")

        click()
        waitForIdle()

        assertEquals("https://notices.example", output.url)
        assertEquals("Notices", output.title)
        assertEquals(1, output.goLiveCalls)
    }

    @Test
    fun `clicking the same item again puts it live a second time`() = scheduled { output, click ->
        click()
        waitForIdle()
        assertEquals(1, output.goLiveCalls)

        // The operator has browsed away and wants the scheduled page back. Same item, so the only
        // thing that changed is the version — and that has to be enough.
        click()
        waitForIdle()

        assertEquals(2, output.goLiveCalls, "the second click must not be swallowed")
        assertEquals("https://notices.example", output.url)
    }

    @Test
    fun `a third click works too`() = scheduled { output, click ->
        repeat(3) { click(); waitForIdle() }

        assertEquals(3, output.goLiveCalls)
    }

    @Test
    fun `composing with nothing selected leaves the outputs alone`() = scheduled { output, _ ->
        // The null arm of the effect: the tab is open but the operator has clicked nothing, which is
        // how it composes every time they switch to it.
        assertEquals(0, output.goLiveCalls)
        assertEquals("", output.url)
    }
}
