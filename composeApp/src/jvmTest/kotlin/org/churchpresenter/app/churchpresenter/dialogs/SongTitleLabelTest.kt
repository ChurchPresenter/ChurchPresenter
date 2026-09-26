package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Edit Song title cards take their languages' names (#672).
 *
 * A named language reads "<name> Title", the same name its pane tab shows; an unnamed one keeps the
 * label it always had, so nobody who never named a language sees anything change.
 */
@OptIn(ExperimentalTestApi::class)
class SongTitleLabelTest {

    private fun labels(block: @Composable () -> List<String>): List<String> {
        var result = emptyList<String>()
        runComposeUiTest {
            setContent { result = block() }
            waitForIdle()
        }
        return result
    }

    @Test
    fun `the first language's card follows its name, and says Song Title until it has one`() {
        assertEquals(
            listOf("English Title", "Song Title"),
            labels { listOf(primaryTitleLabel("English"), primaryTitleLabel("")) },
        )
    }

    @Test
    fun `the other languages' cards follow their names, and keep their positions until named`() {
        assertEquals(
            listOf("Yoruba Title", "Language 2 Title", "French Title", "Language 3 Title", "Language 4 Title"),
            labels {
                listOf(
                    translationTitleLabel(0, "Yoruba"),
                    translationTitleLabel(0, ""),
                    translationTitleLabel(1, "French"),
                    translationTitleLabel(1, ""),
                    translationTitleLabel(2, ""),
                )
            },
        )
    }
}
