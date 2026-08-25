package org.churchpresenter.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The two helpers behind typing into a live page.
 *
 * Everything the operator types in the type-to-page field is spliced into a JavaScript snippet one
 * character at a time and executed in the presenter's browser, and the browser method that executes
 * it is found by walking the class hierarchy. Neither needs Chromium to be exercised, and both fail
 * in ways that would be very hard to diagnose live: a mis-escaped quote produces a syntax error that
 * silently does nothing, and a missed method means keystrokes vanish.
 */
class WebScriptingHelpersTest {

    // ── jsEncode ────────────────────────────────────────────────────────────────

    @Test
    fun `an ordinary character is quoted as-is`() {
        assertEquals("\"a\"", jsEncode('a'))
        assertEquals("\" \"", jsEncode(' '))
    }

    @Test
    fun `a backslash is doubled so the snippet stays valid`() {
        assertEquals("\"\\\\\"", jsEncode('\\'))
    }

    @Test
    fun `a double quote is escaped rather than ending the literal early`() {
        // Unescaped, this would close the string and turn the rest of the snippet into syntax.
        assertEquals("\"\\\"\"", jsEncode('"'))
    }

    @Test
    fun `the whitespace characters use their short escapes`() {
        assertEquals("\"\\n\"", jsEncode('\n'))
        assertEquals("\"\\r\"", jsEncode('\r'))
        assertEquals("\"\\t\"", jsEncode('\t'))
    }

    @Test
    fun `a control character becomes a unicode escape`() {
        assertEquals("\"\\u0000\"", jsEncode('\u0000'))
        assertEquals("\"\\u0007\"", jsEncode('\u0007'))
        assertEquals("\"\\u001f\"", jsEncode('\u001F'))
    }

    @Test
    fun `a non-ascii character is passed through, not escaped`() {
        // The snippet is executed as UTF-8, so accented and non-Latin text needs no encoding —
        // and escaping it would be wrong for the languages this app is translated into.
        assertEquals("\"é\"", jsEncode('é'))
        assertEquals("\"д\"", jsEncode('д'))
    }

    // ── findMethod ──────────────────────────────────────────────────────────────

    private open class Base {
        @Suppress("UnusedPrivateMember")
        private fun onlyOnBase(value: String): String = value
    }

    private class Derived : Base() {
        @Suppress("UnusedPrivateMember")
        private fun onlyOnDerived(value: Int): Int = value
    }

    @Test
    fun `a method declared on the object's own class is found`() {
        assertNotNull(findMethod(Derived(), "onlyOnDerived", Int::class.javaPrimitiveType!!))
    }

    @Test
    fun `a method declared on a superclass is found by walking up`() {
        // The whole reason this helper exists: the browser methods live on a JCEF subclass that is
        // not the object's own class, and `getDeclaredMethod` does not look upwards.
        assertNotNull(findMethod(Derived(), "onlyOnBase", String::class.java))
    }

    @Test
    fun `a method that does not exist anywhere returns null rather than throwing`() {
        assertNull(findMethod(Derived(), "noSuchMethod", String::class.java))
    }

    @Test
    fun `a method whose parameter types do not match is not found`() {
        assertNull(findMethod(Derived(), "onlyOnBase", Int::class.javaPrimitiveType!!))
    }

    @Test
    fun `a found private method is made accessible`() {
        val m = assertNotNull(findMethod(Derived(), "onlyOnBase", String::class.java))
        assertEquals("hello", m.invoke(Derived(), "hello"))
    }
}
