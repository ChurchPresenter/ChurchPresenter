package org.churchpresenter.app.churchpresenter.dialogs

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The number a step's instruction carries in its own text.
 *
 * The instruction strings were written for a plain numbered list and start "1. ", "2. ". The step
 * now draws that number in its own gutter, so the prefix is stripped at render time rather than by
 * re-cutting thirty-odd translations. That makes this a translation-facing transform: it runs over
 * every locale's text, and getting it wrong shows as "(1) 1. Open Settings" in some languages and a
 * swallowed first word in others.
 */
class SetupWizardInstructionTest {

    @Test
    fun `a leading number and period is dropped`() {
        assertEquals("Open Settings", withoutLeadingNumber("1. Open Settings"))
    }

    @Test
    fun `a leading number and bracket is dropped`() {
        assertEquals("Open Settings", withoutLeadingNumber("2) Open Settings"))
    }

    @Test
    fun `double figures are dropped whole`() {
        assertEquals("Open Settings", withoutLeadingNumber("10. Open Settings"))
    }

    @Test
    fun `leading space before the number is tolerated`() {
        assertEquals("Open Settings", withoutLeadingNumber("  3. Open Settings"))
    }

    @Test
    fun `text with no number is returned untouched`() {
        // Most locales keep the "1. " prefix, but a translation that drops it must not lose a word.
        assertEquals("Open Settings", withoutLeadingNumber("Open Settings"))
    }

    @Test
    fun `a number inside the sentence is left alone`() {
        assertEquals(
            "Connect display 2 to this computer",
            withoutLeadingNumber("Connect display 2 to this computer"),
        )
    }

    @Test
    fun `a sentence opening with a year is not mistaken for a list number`() {
        // The regex needs the separator, so a bare leading number stays.
        assertEquals("1920 by 1080 is the reference size", withoutLeadingNumber("1920 by 1080 is the reference size"))
    }

    @Test
    fun `only the first number is dropped`() {
        assertEquals("2. is still part of the sentence", withoutLeadingNumber("1. 2. is still part of the sentence"))
    }

    @Test
    fun `an empty string survives`() {
        assertEquals("", withoutLeadingNumber(""))
    }
}
