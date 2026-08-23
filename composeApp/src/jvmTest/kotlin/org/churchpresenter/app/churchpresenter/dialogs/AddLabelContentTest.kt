@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.churchpresenter.ui.cpColorToHex
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.test.onNodeWithTag
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.composables.LABEL_PRESET_TAG
import org.churchpresenter.app.churchpresenter.composables.LabelColors
import org.churchpresenter.app.churchpresenter.composables.RecentLabelColors
import java.nio.file.Files
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Adding and editing a schedule label: the text field, the two colour pickers, and what Cancel/OK
 * each do with them.
 *
 * `AddLabelDialog` opens a `DialogWindow`, which cannot be composed headless, so the window's body
 * was lifted into `AddLabelDialogContent` — an extraction, no logic moved or changed — and that is
 * what these drive. The `DialogWindow` call and its sizing are what remain uncovered.
 *
 * The colour field opens the same `ColorPickerDialog` driven elsewhere by the settings tabs, but its
 * own helpers are not reused here: `openColorField` scrolls to the node first, which fails with no
 * scrollable parent to scroll, and `confirmColorDialogWith`'s `onNodeWithText("OK")` is ambiguous
 * here specifically because this dialog's own confirm button is *also* labelled "OK" — a collision
 * the tabs it was written for don't have, since their own confirm button says "Save".
 */
class AddLabelContentTest {

    private class Result {
        var confirmed: Triple<String, String, String>? = null
        var dismissed = 0
    }

    /** Clicks the colour field currently showing [showingHex], without the shared helper's scroll. */
    private fun ComposeUiTest.openColorField(showingHex: String) {
        onAllNodes(hasClickAction() and hasText(showingHex)).onFirst().performClick()
        waitForIdle()
    }

    /**
     * Types [hex] into the open colour picker's hex box and confirms it.
     *
     * The picker's own OK sits later in the tree than this dialog's OK, since it is the most
     * recently opened popup, so [onLast] is what disambiguates the two "OK" buttons on screen.
     */
    private fun ComposeUiTest.confirmColorPickerWith(hex: String) {
        onAllNodes(hasSetTextAction() and hasText("#", substring = true)).onLast().performTextReplacement(hex)
        waitForIdle()
        onAllNodes(hasText("OK") and hasClickAction()).onLast().performClick()
        waitForIdle()
    }

    /** What the theme in force offers a new label, captured as the content resolves it. */
    private class Defaults {
        var text: String = ""
        var background: String = ""
    }

    private fun dialog(
        existingText: String = "",
        existingTextColor: String = "",
        existingBackgroundColor: String = "",
        isEdit: Boolean = false,
        themeMode: ThemeMode? = null,
        defaults: Defaults = Defaults(),
        block: ComposeUiTest.(Result) -> Unit,
    ) {
        val result = Result()
        runComposeUiTest {
            setContent {
                val body = @Composable {
                    defaults.text = cpColorToHex(MaterialTheme.colorScheme.onSurface)
                    defaults.background = cpColorToHex(MaterialTheme.colorScheme.surfaceContainer)
                    AddLabelDialogContent(
                        onDismiss = { result.dismissed++ },
                        onConfirm = { text, textColor, backgroundColor ->
                            result.confirmed = Triple(text, textColor, backgroundColor)
                        },
                        existingText = existingText,
                        existingTextColor = existingTextColor,
                        existingBackgroundColor = existingBackgroundColor,
                        isEdit = isEdit,
                    )
                }
                if (themeMode == null) MaterialTheme { body() }
                else ChurchPresenterTheme(themeMode = themeMode) { body() }
            }
            block(result)
        }
    }

    // ── Title ────────────────────────────────────────────────────────────────────

    @Test
    fun `adding a new label titles itself Add Label`() = dialog(isEdit = false) {
        onNodeWithText("Add Label").assertExists()
    }

    @Test
    fun `editing an existing label titles itself Edit Label`() = dialog(isEdit = true) {
        onNodeWithText("Edit Label").assertExists()
    }

    // ── Field defaults ──────────────────────────────────────────────────────────

    @Test
    fun `a brand new label starts with the theme's own colours`() {
        val defaults = Defaults()
        dialog(defaults = defaults) {
            // Not a fixed white-on-blue: that pair belonged to no palette here and sat oddly in the
            // eight themes that are not the light one.
            onNodeWithText(defaults.text).assertExists()
            onNodeWithText(defaults.background).assertExists()
        }
    }

    @Test
    fun `the default colours change with the theme`() {
        // The point of the change: nine themes, and a new label should look like it belongs to
        // whichever one is on rather than to the light one.
        val light = Defaults()
        val forest = Defaults()
        dialog(themeMode = ThemeMode.LIGHT, defaults = light) {
            onNodeWithText(light.background).assertExists()
        }
        dialog(themeMode = ThemeMode.FOREST, defaults = forest) {
            onNodeWithText(forest.background).assertExists()
        }

        assertNotEquals(
            light.background, forest.background,
            "a themed default that is the same in every theme is not themed",
        )
    }

    @Test
    fun `editing keeps the saved colours, theme or no theme`() = dialog(
        existingTextColor = "#123456",
        existingBackgroundColor = "#ABCDEF",
        isEdit = true,
    ) {
        // A label saved months ago must come back as it was, not be recoloured by the theme in
        // force when it is next opened.
        onNodeWithText("#123456").assertExists()
        onNodeWithText("#ABCDEF").assertExists()
    }

    @Test
    fun `editing a label starts with its own text and colours, not the defaults`() = dialog(
        existingText = "Welcome",
        existingTextColor = "#000000",
        existingBackgroundColor = "#FF0000",
        isEdit = true,
    ) {
        onNodeWithText("Welcome").assertExists()
        onNodeWithText("#000000").assertExists()
        onNodeWithText("#FF0000").assertExists()
    }

    // ── The OK button's blank-text guard ───────────────────────────────────────

    @Test
    fun `OK is disabled while the label has no text`() = dialog {
        onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun `OK is disabled for text that is only whitespace`() = dialog {
        onNodeWithText("Enter label text...").performTextInput("   ")
        onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun `OK becomes enabled once real text is typed`() = dialog {
        onNodeWithText("Enter label text...").performTextInput("Welcome")
        onNodeWithText("OK").assertIsEnabled()
    }

    @Test
    fun `clicking OK while it is disabled confirms nothing`() = dialog { result ->
        onNodeWithText("OK").performClick()
        assertNull(result.confirmed)
        assertEquals(0, result.dismissed)
    }

    // ── Confirming ──────────────────────────────────────────────────────────────

    @Test
    fun `OK hands back the typed text and both colours`() {
        val defaults = Defaults()
        dialog(defaults = defaults) { result ->
            onNodeWithText("Enter label text...").performTextInput("Welcome")
            onNodeWithText("OK").performClick()

            assertEquals(Triple("Welcome", defaults.text, defaults.background), result.confirmed)
        }
    }

    @Test
    fun `OK trims surrounding whitespace from the text`() = dialog { result ->
        onNodeWithText("Enter label text...").performTextInput("  Welcome  ")
        onNodeWithText("OK").performClick()

        assertEquals("Welcome", result.confirmed?.first)
    }

    @Test
    fun `OK closes the dialog after confirming`() = dialog { result ->
        onNodeWithText("Enter label text...").performTextInput("Welcome")
        onNodeWithText("OK").performClick()

        assertEquals(1, result.dismissed)
    }

    @Test
    fun `editing a label's text before confirming sends the edited text`() = dialog(existingText = "Old") { result ->
        onNodeWithText("Old").performTextReplacement("New")
        onNodeWithText("OK").performClick()

        assertEquals("New", result.confirmed?.first)
    }

    // ── Cancel ──────────────────────────────────────────────────────────────────

    @Test
    fun `Cancel dismisses without confirming`() = dialog { result ->
        onNodeWithText("Enter label text...").performTextInput("Welcome")
        onNodeWithText("Cancel").performClick()

        assertNull(result.confirmed, "Cancel must not save whatever was typed")
        assertEquals(1, result.dismissed)
    }

    // ── Colour pickers ──────────────────────────────────────────────────────────

    @Test
    fun `changing the text colour is reflected in what OK confirms`() {
        val defaults = Defaults()
        dialog(defaults = defaults) { result ->
            onNodeWithText("Enter label text...").performTextInput("Welcome")
            openColorField(defaults.text)
            confirmColorPickerWith("#123456")

            onNodeWithText("OK").performClick()

            assertEquals("#123456", result.confirmed?.second)
            assertEquals(
                defaults.background, result.confirmed?.third,
                "the background colour must be untouched by editing the text colour",
            )
        }
    }

    @Test
    fun `changing the background colour is reflected in what OK confirms`() {
        val defaults = Defaults()
        dialog(defaults = defaults) { result ->
            onNodeWithText("Enter label text...").performTextInput("Welcome")
            openColorField(defaults.background)
            confirmColorPickerWith("#654321")

            onNodeWithText("OK").performClick()

            assertEquals(
                defaults.text, result.confirmed?.second,
                "the text colour must be untouched by editing the background colour",
            )
            assertEquals("#654321", result.confirmed?.third)
        }
    }

    @Test
    fun `both colours can be changed independently before confirming`() {
        val defaults = Defaults()
        dialog(defaults = defaults) { result ->
            onNodeWithText("Enter label text...").performTextInput("Welcome")
            openColorField(defaults.text)
            confirmColorPickerWith("#111111")
            openColorField(defaults.background)
            confirmColorPickerWith("#222222")

            onNodeWithText("OK").performClick()

            assertEquals(Triple("Welcome", "#111111", "#222222"), result.confirmed)
        }
    }

    @Test
    fun `every optional parameter can be left to its own default`() = runComposeUiTest {
        var themeBackground = ""
        setContent {
            MaterialTheme {
                themeBackground = cpColorToHex(MaterialTheme.colorScheme.surfaceContainer)
                AddLabelDialogContent(onDismiss = {}, onConfirm = { _, _, _ -> })
            }
        }
        onNodeWithText("Add Label").assertExists()
        onNodeWithText(themeBackground).assertExists()
    }

    // ── AddLabelDialog itself, via its isVisible guard ─────────────────────────

    @Test
    fun `AddLabelDialog renders nothing when not visible, using only its required parameters`() = runComposeUiTest {
        setContent {
            AddLabelDialog(
                isVisible = false,
                onDismiss = {},
                onConfirm = { _, _, _ -> },
            )
        }
        onNodeWithText("Add Label").assertDoesNotExist()
    }

    @Test
    fun `AddLabelDialog renders nothing when not visible, with every optional parameter supplied`() = runComposeUiTest {
        setContent {
            AddLabelDialog(
                isVisible = false,
                onDismiss = {},
                onConfirm = { _, _, _ -> },
                existingText = "Welcome",
                existingTextColor = "#000000",
                existingBackgroundColor = "#FF0000",
                isEdit = true,
            )
        }
        onNodeWithText("Edit Label").assertDoesNotExist()
    }

    // ── History ─────────────────────────────────────────────────────────────────

    @Test
    fun `a custom pair is remembered, a theme preset is not`() {
        TestSingletons.latchToTestHome()
        val realHome = System.getProperty("user.home")
        val tempHome = Files.createTempDirectory("cp-label-dialog").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        RecentLabelColors.load()
        val defaults = Defaults()
        try {
            // Confirming on the untouched default -- which is a theme preset -- must leave the
            // history alone: presets have their own column, and recording them would push out the
            // custom pairs the history exists to keep.
            dialog(defaults = defaults) { _ ->
                onNodeWithText("Enter label text...").performTextInput("Welcome")
                onNodeWithText("OK").performClick()
            }
            assertEquals(emptyList(), RecentLabelColors.combos.toList(), "a preset is not history")

            // A colour of the user's own is the case the history exists for.
            dialog(defaults = defaults) { _ ->
                onNodeWithText("Enter label text...").performTextInput("Welcome")
                openColorField(defaults.background)
                confirmColorPickerWith("#654321")
                onNodeWithText("OK").performClick()
            }

            assertEquals(
                listOf(LabelColors("#654321", defaults.text)),
                RecentLabelColors.combos.toList(),
                "the pair actually used is what gets kept",
            )
        } finally {
            System.setProperty("user.home", realHome)
            tempHome.deleteRecursively()
            RecentLabelColors.load()
        }
    }

    @Test
    fun `picking a swatch sets both colours at once`() {
        val defaults = Defaults()
        dialog(defaults = defaults) { result ->
            onNodeWithText("Enter label text...").performTextInput("Welcome")
            // The second preset, whatever the theme makes it -- the swatch is the pair, so one
            // click has to move the band and its text together.
            onNodeWithTag("${LABEL_PRESET_TAG}_1").performClick()
            onNodeWithText("OK").performClick()

            val confirmed = result.confirmed
            assertNotEquals(defaults.background, confirmed?.third, "the band must have changed")
            assertNotEquals(defaults.text, confirmed?.second, "and its text with it")
        }
    }
}
