package org.churchpresenter.stt

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading optional JSON strings without inheriting org.json's `null` handling.
 *
 * `optString` returns the four-character string `"null"` for a JSON null rather than the default it
 * is handed, which silently defeats both `takeIf { it.isNotEmpty() }` and `ifBlank { … }`. Every
 * assertion here is really the same one: a JSON null must be indistinguishable from an absent key.
 */
class JsonExtTest {

    // ── The behaviour being corrected ────────────────────────────────────────────

    @Test
    fun `org_json really does coerce null to the string null`() {
        // Pins the platform behaviour these helpers exist to hide. If a future org.json version
        // fixes this, this test fails and the helpers can be reconsidered.
        assertEquals("null", JSONObject("""{"k":null}""").optString("k", "fallback"))
    }

    // ── JSONObject ───────────────────────────────────────────────────────────────

    @Test
    fun `a json null yields the default, not the word null`() {
        assertEquals("", JSONObject("""{"k":null}""").stringOr("k"))
        assertEquals("dflt", JSONObject("""{"k":null}""").stringOr("k", "dflt"))
    }

    @Test
    fun `an absent key yields the default`() {
        assertEquals("dflt", JSONObject("{}").stringOr("k", "dflt"))
    }

    @Test
    fun `a real value is returned untouched`() {
        assertEquals("hello", JSONObject("""{"k":"hello"}""").stringOr("k", "dflt"))
    }

    @Test
    fun `an empty string is a real value and is not replaced by the default`() {
        // Distinct from absent: the server said "", and callers use ifBlank to decide what that means.
        assertEquals("", JSONObject("""{"k":""}""").stringOr("k", "dflt"))
    }

    @Test
    fun `whitespace and surrounding spaces are preserved`() {
        assertEquals("  a b  ", JSONObject("""{"k":"  a b  "}""").stringOr("k"))
    }

    @Test
    fun `non-string scalars still coerce as org_json does`() {
        // Deliberately unchanged: only the null case is corrected.
        assertEquals("42", JSONObject("""{"k":42}""").stringOr("k"))
        assertEquals("true", JSONObject("""{"k":true}""").stringOr("k"))
    }

    // ── JSONArray ────────────────────────────────────────────────────────────────

    @Test
    fun `a null array entry yields the default`() {
        val a = JSONArray("""["red",null,"blue"]""")
        assertEquals("red", a.stringOr(0))
        assertEquals("", a.stringOr(1))
        assertEquals("blue", a.stringOr(2))
    }

    @Test
    fun `an out-of-range index yields the default`() {
        assertEquals("dflt", JSONArray("[]").stringOr(3, "dflt"))
    }

    // ── stringOrNull ─────────────────────────────────────────────────────────────

    @Test
    fun `stringOrNull treats null, absent, empty and blank alike`() {
        assertNull(JSONObject("""{"k":null}""").stringOrNull("k"))
        assertNull(JSONObject("{}").stringOrNull("k"))
        assertNull(JSONObject("""{"k":""}""").stringOrNull("k"))
        assertNull(JSONObject("""{"k":"   "}""").stringOrNull("k"))
    }

    @Test
    fun `stringOrNull trims a real value`() {
        assertEquals("2026-07-27", JSONObject("""{"k":"  2026-07-27  "}""").stringOrNull("k"))
    }
}
