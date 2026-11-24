@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.churchpresenter.hosanna.HosannaDarkColors
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaLightColors
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.settle
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StandardButtonTest {

    private val c = HosannaLightColors
    private val role = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `disabled outranks loading, loading outranks every interaction`() {
        assertEquals(ButtonState.Disabled, buttonState(false, true, true, true, true))
        assertEquals(ButtonState.Loading, buttonState(true, true, true, true, true))
        assertEquals(ButtonState.Pressed, buttonState(true, false, true, true, true))
        assertEquals(ButtonState.Focused, buttonState(true, false, true, false, true))
        assertEquals(ButtonState.Hovered, buttonState(true, false, true, false, false))
        assertEquals(ButtonState.Rest, buttonState(true, false, false, false, false))
    }

    @Test
    fun `each hierarchy and intent rests on its theme roles`() {
        val expected = mapOf(
            (ButtonHierarchy.Primary to ButtonIntent.Default) to (c.primary to c.onPrimary),
            (ButtonHierarchy.Primary to ButtonIntent.Critical) to (c.error to c.onError),
            (ButtonHierarchy.Primary to ButtonIntent.Neutral) to (c.secondary to c.onSecondary),
            (ButtonHierarchy.Secondary to ButtonIntent.Default) to (c.primaryContainer to c.onPrimaryContainer),
            (ButtonHierarchy.Secondary to ButtonIntent.Critical) to (c.errorContainer to c.onErrorContainer),
            (ButtonHierarchy.Secondary to ButtonIntent.Neutral) to
                (c.secondaryContainer to c.onSecondaryContainer),
            (ButtonHierarchy.Tertiary to ButtonIntent.Default) to (Color.Transparent to c.primary),
            (ButtonHierarchy.Tertiary to ButtonIntent.Critical) to (Color.Transparent to c.error),
            (ButtonHierarchy.Tertiary to ButtonIntent.Neutral) to (Color.Transparent to c.secondary),
        )
        expected.forEach { (key, colors) ->
            assertEquals(colors, c.restColors(key.first, key.second), "$key")
            assertEquals(
                ButtonColors(colors.first, colors.second, null),
                c.buttonColors(key.first, key.second, ButtonState.Rest),
            )
        }
    }

    @Test
    fun `hover pressed and focus lay the content color over the container`() {
        listOf(c, HosannaDarkColors).forEach { colors ->
            ButtonHierarchy.entries.forEach { h ->
                ButtonIntent.entries.forEach { i ->
                    val (container, content) = colors.restColors(h, i)
                    fun layer(alpha: Float) = content.copy(alpha = alpha).compositeOver(container)
                    val hovered = colors.buttonColors(h, i, ButtonState.Hovered)
                    val pressed = colors.buttonColors(h, i, ButtonState.Pressed)
                    val focused = colors.buttonColors(h, i, ButtonState.Focused)
                    assertEquals(ButtonColors(layer(HosannaStateAlpha.HOVER), content, null), hovered)
                    assertEquals(ButtonColors(layer(HosannaStateAlpha.PRESSED), content, null), pressed)
                    assertEquals(
                        ButtonColors(
                            layer(HosannaStateAlpha.FOCUS),
                            content,
                            colors.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
                        ),
                        focused,
                    )
                }
            }
        }
    }

    @Test
    fun `loading keeps the rest colors`() {
        ButtonHierarchy.entries.forEach { h ->
            ButtonIntent.entries.forEach { i ->
                assertEquals(c.buttonColors(h, i, ButtonState.Rest), c.buttonColors(h, i, ButtonState.Loading))
            }
        }
    }

    @Test
    fun `disabled looks the same for every intent`() {
        val content = c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        val filled = c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER)
        ButtonIntent.entries.forEach { i ->
            assertEquals(
                ButtonColors(filled, content, null),
                c.buttonColors(ButtonHierarchy.Primary, i, ButtonState.Disabled),
            )
            assertEquals(
                ButtonColors(filled, content, null),
                c.buttonColors(ButtonHierarchy.Secondary, i, ButtonState.Disabled),
            )
            assertEquals(
                ButtonColors(Color.Transparent, content, null),
                c.buttonColors(ButtonHierarchy.Tertiary, i, ButtonState.Disabled),
            )
        }
    }

    @Test
    fun `only a primary button at rest, hovered or loading is raised`() {
        ButtonState.entries.forEach { state ->
            val raised = state in setOf(ButtonState.Rest, ButtonState.Hovered, ButtonState.Loading)
            assertEquals(
                if (raised) HosannaLevel.Level1 else HosannaLevel.Level0,
                buttonLevel(ButtonHierarchy.Primary, state),
                "$state",
            )
            assertEquals(HosannaLevel.Level0, buttonLevel(ButtonHierarchy.Secondary, state))
            assertEquals(HosannaLevel.Level0, buttonLevel(ButtonHierarchy.Tertiary, state))
        }
    }

    @Test
    fun `a click reaches onClick and the interaction source`() = runComposeUiTest {
        var clicks = 0
        val source = MutableInteractionSource()
        val seen = mutableListOf<Interaction>()
        CoroutineScope(Dispatchers.Unconfined).launch { source.interactions.collect { seen += it } }
        setContent { HosannaSurface { StandardButton("Save", { clicks++ }, source) } }
        onNodeWithText("Save").assert(role).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(1, clicks)
        assertTrue(seen.any { it is PressInteraction.Press })
        assertTrue(seen.any { it is PressInteraction.Release })
    }

    @Test
    fun `a disabled button cannot be clicked`() = runComposeUiTest {
        var clicks = 0
        setContent {
            HosannaSurface {
                StandardButton("Delete", { clicks++ }, MutableInteractionSource(), enabled = false)
            }
        }
        onNodeWithText("Delete").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `a loading button cannot be clicked and says it is busy`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var clicks = 0
        setContent {
            HosannaSurface {
                StandardButton("Upload", { clicks++ }, MutableInteractionSource(), icon = testIcon(), loading = true)
            }
        }
        settle()
        onNodeWithText("Upload").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
        onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `a loading button without an icon keeps its label for screen readers`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { HosannaSurface { StandardButton("Save", {}, MutableInteractionSource(), loading = true) } }
        settle()
        onNodeWithText("Save").assertExists().assertIsNotEnabled()
    }

    @Test
    fun `a text button is 34dp tall and at least 64dp wide`() = runComposeUiTest {
        setContent { HosannaSurface { StandardButton("OK", {}, MutableInteractionSource()) } }
        onNodeWithText("OK")
            .assertHeightIsEqualTo(StandardButtonDefaults.Height)
            .assertWidthIsAtLeast(StandardButtonDefaults.MinWidth)
    }

    @Test
    fun `an icon-only button is named and square`() = runComposeUiTest {
        var clicks = 0
        setContent {
            HosannaSurface {
                StandardButton(testIcon(), "Add to schedule", { clicks++ }, MutableInteractionSource())
            }
        }
        onNodeWithContentDescription("Add to schedule")
            .assert(role)
            .assertWidthIsEqualTo(StandardButtonDefaults.Height)
            .assertHeightIsEqualTo(StandardButtonDefaults.Height)
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `hovering reaches the interaction source`() = runComposeUiTest {
        val source = MutableInteractionSource()
        val seen = mutableListOf<Interaction>()
        CoroutineScope(Dispatchers.Unconfined).launch { source.interactions.collect { seen += it } }
        setContent { HosannaSurface { StandardButton("Save", {}, source, hierarchy = ButtonHierarchy.Tertiary) } }
        onNodeWithText("Save").performMouseInput { enter(center) }
        waitForIdle()
        assertTrue(seen.any { it is HoverInteraction.Enter })
        assertNull(seen.firstOrNull { it is FocusInteraction.Focus })
    }
}
