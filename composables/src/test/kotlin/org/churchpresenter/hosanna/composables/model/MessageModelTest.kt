package org.churchpresenter.hosanna.composables.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageModelTest {

    @Test
    fun `only info, success and neutral can be dismissed`() {
        assertTrue(MessageIntent.Info.dismissible())
        assertTrue(MessageIntent.Success.dismissible())
        assertTrue(MessageIntent.Neutral.dismissible())
        assertFalse(MessageIntent.Warning.dismissible())
        assertFalse(MessageIntent.Error.dismissible())
    }

    @Test
    fun `each intent's actions use its strong color`() {
        assertEquals(ButtonIntent.Default, MessageIntent.Info.buttonIntent())
        assertEquals(ButtonIntent.Success, MessageIntent.Success.buttonIntent())
        assertEquals(ButtonIntent.Warning, MessageIntent.Warning.buttonIntent())
        assertEquals(ButtonIntent.Critical, MessageIntent.Error.buttonIntent())
        assertEquals(ButtonIntent.Default, MessageIntent.Neutral.buttonIntent())
    }
}
