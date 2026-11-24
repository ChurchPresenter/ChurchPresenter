package org.churchpresenter.hosanna.composables.button

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.churchpresenter.hosanna.HosannaDarkColors
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaLightColors
import org.churchpresenter.hosanna.HosannaStateAlpha
import kotlin.test.Test
import kotlin.test.assertEquals

class ButtonFoundationTest {

    private val c = HosannaLightColors

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
}
