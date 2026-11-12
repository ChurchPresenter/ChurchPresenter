package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which website layout a profile's screens show, and how a profile carries the choice. */
class WebPageViewTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `following the Web tab takes its button, and the two forced layouts ignore it`() {
        assertTrue(WebPageView.FOLLOW_WEB_TAB.isMobile(webTabMobile = true))
        assertFalse(WebPageView.FOLLOW_WEB_TAB.isMobile(webTabMobile = false))
        listOf(true, false).forEach { button ->
            assertTrue(WebPageView.MOBILE.isMobile(button))
            assertFalse(WebPageView.DESKTOP.isMobile(button))
        }
    }

    @Test
    fun `a profile saved before the setting existed follows the Web tab`() {
        val profile = json.decodeFromString(OutputProfile.serializer(), """{"id":"p1"}""")
        assertEquals(WebPageView.FOLLOW_WEB_TAB, profile.webPageView)
    }

    @Test
    fun `each choice survives a round-trip under its own name`() {
        WebPageView.entries.forEach { view ->
            val saved = json.encodeToString(OutputProfile.serializer(), OutputProfile(id = "p1", webPageView = view))
            assertEquals(view, json.decodeFromString(OutputProfile.serializer(), saved).webPageView)
            if (view != WebPageView.FOLLOW_WEB_TAB) {
                assertTrue(saved.contains("\"webPageView\":\"${view.name}\""), saved)
            }
        }
    }

    @Test
    fun `the choice belongs to the content section`() {
        assertEquals(ProfileSection.of("pictureScaleMode"), ProfileSection.of("webPageView"))
    }
}
