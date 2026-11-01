@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.screenshot

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.SectionOrderTokens
import kotlin.test.Test

/** A song's order as the song list's Order column draws it: section chips in their legend inks. */
class SectionOrderTokensScreenshotTest {

    @Test
    fun `a short order`() = captureComponent(SECTION, "short") {
        SectionOrderTokens(
            listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}#2"),
            "As written",
            Modifier.width(180.dp),
        )
    }

    @Test
    fun `a long order stops at five and counts the rest`() = captureComponent(SECTION, "long") {
        SectionOrderTokens(
            listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}#2", "[Bridge]", "{Chorus}#3", "[Coda]"),
            "As written",
            Modifier.width(180.dp),
        )
    }

    @Test
    fun `a song sung as written`() = captureComponent(SECTION, "as_written") {
        SectionOrderTokens(emptyList(), "As written", Modifier.width(180.dp))
    }

    private companion object {
        const val SECTION = "sectionOrderTokens"
    }
}
