package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import org.churchpresenter.hosanna.composables.model.MessageIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.hosanna.composables.testing.LightThemeColors

class MessageColorsTest {

    private val c = LightThemeColors

    @Test
    fun `each intent uses its soft container role`() {
        assertEquals(
            MessageColors(c.primaryContainer, c.onPrimaryContainer, c.onPrimaryContainer, Color.Transparent),
            c.messageColors(MessageIntent.Info),
        )
        assertEquals(
            MessageColors(c.successContainer, c.onSuccessContainer, c.onSuccessContainer, Color.Transparent),
            c.messageColors(MessageIntent.Success),
        )
        assertEquals(
            MessageColors(c.tertiaryContainer, c.onTertiaryContainer, c.onTertiaryContainer, Color.Transparent),
            c.messageColors(MessageIntent.Warning),
        )
        assertEquals(
            MessageColors(c.errorContainer, c.onErrorContainer, c.onErrorContainer, Color.Transparent),
            c.messageColors(MessageIntent.Error),
        )
    }

    @Test
    fun `neutral is a quiet surface with an edge`() {
        assertEquals(
            MessageColors(c.surfaceContainerLow, c.onSurface, c.onSurfaceVariant, c.outlineVariant),
            c.messageColors(MessageIntent.Neutral),
        )
    }
}
