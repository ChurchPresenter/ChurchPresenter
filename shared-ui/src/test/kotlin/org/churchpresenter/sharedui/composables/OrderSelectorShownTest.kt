package org.churchpresenter.sharedui.composables

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An [OrderSelector] given an [OrderShown]: an eye on each row that shows or hides the entry, which
 * cannot hide the last one shown. Without one the rows carry no eye.
 */
@OptIn(ExperimentalTestApi::class)
class OrderSelectorShownTest {

    private fun ComposeUiTest.selector(
        rows: List<OrderEntry>,
        toggles: MutableList<Pair<String, Boolean>>? = mutableListOf(),
    ) {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                OrderSelector(
                    label = "Languages",
                    entries = rows,
                    subtitle = "Which language leads",
                    moveUpLabel = "Move up",
                    moveDownLabel = "Move down",
                    onMove = { _, _ -> },
                    shown = toggles?.let { calls ->
                        OrderShown(
                            label = "Show in the list",
                            hint = "Hides a language here only",
                            onToggle = { key, shown -> calls += key to shown },
                        )
                    },
                )
            }
        }
        onAllNodes(hasText(rows.first().shownName))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.eye(name: String) = onNode(hasContentDescription("Show in the list: $name"))

    private fun ComposeUiTest.eyes() =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).fetchSemanticsNodes()

    private val english = OrderEntry("0", "English")
    private val yoruba = OrderEntry("1", "Yoruba")
    private val french = OrderEntry("2", "French")

    @Test
    fun `each row's eye says whether it is shown`() = runComposeUiTest {
        selector(listOf(english, yoruba.copy(shown = false), french))

        assertEquals(3, eyes().size)
        eye("English").assertIsOn()
        eye("Yoruba").assertIsOff()
        val hint = onAllNodes(hasText("Hides a language here only"), useUnmergedTree = true)
        assertTrue(hint.fetchSemanticsNodes().isNotEmpty(), "the hint says what the eye changes")
    }

    @Test
    fun `clicking an eye hides a shown entry and shows a hidden one`() = runComposeUiTest {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        selector(listOf(english, yoruba, french.copy(shown = false)), toggles)

        eye("Yoruba").performClick()
        eye("French").performClick()
        waitForIdle()

        assertEquals(listOf("1" to false, "2" to true), toggles)
    }

    @Test
    fun `the last entry shown cannot be hidden`() = runComposeUiTest {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        selector(listOf(english.copy(shown = false), yoruba, french.copy(shown = false)), toggles)

        eye("Yoruba").assertIsNotEnabled()
        eye("English").assertIsEnabled()
        eye("Yoruba").performClick()
        waitForIdle()

        assertEquals(emptyList(), toggles)
    }

    @Test
    fun `without an OrderShown the rows have no eye`() = runComposeUiTest {
        selector(listOf(english, yoruba), toggles = null)

        assertEquals(0, eyes().size)
    }
}
