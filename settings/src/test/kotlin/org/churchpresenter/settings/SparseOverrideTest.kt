package org.churchpresenter.settings

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The tree arithmetic under every override in the document: what one settings object says that
 * another does not, and what it looks like written back over it.
 *
 * Worth pinning key by key rather than only through a settings round trip. Both functions are
 * recursive and both have a branch that only shows up on a shape a whole settings object rarely
 * has -- a key on one side and not the other, an object where the other side holds a scalar -- and
 * getting either wrong does not fail loudly. It silently drops a setting, or pins one that should
 * have been following the document, on one output and not the rest.
 */
class SparseOverrideTest {

    // ── diffObjects: what changed ───────────────────────────────────────────────────────────────

    @Test
    fun `a key with the same value on both sides is not stored`() {
        val diff = diffObjects(
            buildJsonObject { put("marginTop", 54); put("fontSize", 40) },
            buildJsonObject { put("marginTop", 54); put("fontSize", 72) },
        )

        assertEquals(setOf("fontSize"), diff.keys, "an untouched setting follows the document")
        assertEquals("72", diff.getValue("fontSize").toString())
    }

    @Test
    fun `nothing changed is an empty tree`() {
        val same = buildJsonObject { put("marginTop", 54) }

        assertTrue(diffObjects(same, same).isEmpty())
    }

    @Test
    fun `a nested object is descended into, so one corner is stored and not the record`() {
        val diff = diffObjects(
            buildJsonObject {
                put("backdrop", buildJsonObject { put("color", "#000000"); put("dim", 0) })
            },
            buildJsonObject {
                put("backdrop", buildJsonObject { put("color", "#000000"); put("dim", 40) })
            },
        )

        val backdrop = diff.getValue("backdrop") as JsonObject
        assertEquals(setOf("dim"), backdrop.keys, "the colour it did not touch is not pinned")
    }

    @Test
    fun `a nested object identical on both sides leaves no empty husk behind`() {
        val backdrop = buildJsonObject { put("backdrop", buildJsonObject { put("dim", 0) }) }

        // An empty `{}` stored here would merge as nothing but still read as "this output is
        // customized", which is the state the sparse form exists to avoid.
        assertTrue(diffObjects(backdrop, backdrop).isEmpty())
    }

    @Test
    fun `an atomic key is compared whole rather than descended into`() {
        val global = buildJsonObject { put("zones", buildJsonObject { put("a", 1); put("b", 2) }) }
        val mine = buildJsonObject { put("zones", buildJsonObject { put("a", 1); put("b", 9) }) }

        val descended = diffObjects(global, mine).getValue("zones") as JsonObject
        val whole = diffObjects(global, mine, atomicKeys = setOf("zones")).getValue("zones") as JsonObject

        assertEquals(setOf("b"), descended.keys)
        assertEquals(setOf("a", "b"), whole.keys, "named atomic, it is stored as it stands")
    }

    @Test
    fun `an object facing a scalar is stored whole`() {
        val diff = diffObjects(
            buildJsonObject { put("backdrop", "none") },
            buildJsonObject { put("backdrop", buildJsonObject { put("dim", 40) }) },
        )

        assertTrue(diff.getValue("backdrop") is JsonObject, "there is nothing to descend into")
    }

    // ── mergeObjects: written back over ─────────────────────────────────────────────────────────

    @Test
    fun `a key the override is silent about keeps the document's value`() {
        val merged = mergeObjects(
            buildJsonObject { put("marginTop", 54); put("fontSize", 40) },
            buildJsonObject { put("fontSize", 72) },
        )

        assertEquals("54", merged.getValue("marginTop").toString())
        assertEquals("72", merged.getValue("fontSize").toString())
    }

    @Test
    fun `an object on both sides is merged rather than replaced`() {
        val merged = mergeObjects(
            buildJsonObject {
                put("backdrop", buildJsonObject { put("color", "#000000"); put("dim", 0) })
            },
            buildJsonObject { put("backdrop", buildJsonObject { put("dim", 40) }) },
        )

        val backdrop = merged.getValue("backdrop") as JsonObject
        assertEquals("\"#000000\"", backdrop.getValue("color").toString(), "the colour still follows")
        assertEquals("40", backdrop.getValue("dim").toString())
    }

    @Test
    fun `an object facing a scalar replaces it outright`() {
        val merged = mergeObjects(
            buildJsonObject { put("backdrop", "none") },
            buildJsonObject { put("backdrop", buildJsonObject { put("dim", 40) }) },
        )

        assertTrue(merged.getValue("backdrop") is JsonObject)
    }

    @Test
    fun `a key the override carries and the document does not is kept`() {
        // A setting removed from the global object since this override was stored. Keeping it makes
        // the decoder's `ignoreUnknownKeys` the one place it is dropped, rather than losing it here
        // where a rename would look exactly the same as a removal.
        val merged = mergeObjects(
            buildJsonObject { put("marginTop", 54) },
            buildJsonObject { put("retiredSetting", true) },
        )

        assertEquals(setOf("marginTop", "retiredSetting"), merged.keys)
    }

    // ── withSparseOverride: the two together, on a real settings object ─────────────────────────

    @Test
    fun `no override at all is the document itself`() {
        val global = BibleSettings(marginTop = 54)

        assertSame(global, withSparseOverride(global, null, BibleSettings.serializer()))
        assertSame(global, withSparseOverride(global, JsonObject(emptyMap()), BibleSettings.serializer()))
    }

    @Test
    fun `an override names one field and the rest of the document comes through`() {
        val global = BibleSettings(marginTop = 54, multiTranslationSpacing = 40)
        val override = buildJsonObject { put("multiTranslationSpacing", 72) }

        val merged = withSparseOverride(global, override, BibleSettings.serializer())

        assertEquals(72, merged.multiTranslationSpacing)
        assertEquals(54, merged.marginTop, "a field the override is silent about follows the document")
    }

    // ── styleTreeOf: a keep-list, not a diff ────────────────────────────────────────────────────

    @Test
    fun `a style tree keeps a field sitting at its class default`() {
        // The distinction from a diff: `marginTop` here equals `BibleSettings()`'s own default, and
        // a diff against the document would drop it. A style tree keeps it, which is what lets a
        // profile hold a complete look rather than a delta against whatever the document says today.
        val tree = styleTreeOf(BibleSettings(), BibleSettings.serializer(), ignoredKeys = setOf("translations"))

        assertTrue("marginTop" in tree.keys)
        assertTrue("translations" !in tree.keys, "the stack is one per install, never a profile's")
    }

    @Test
    fun `an ignored key is dropped even when the object sets it`() {
        val tree = styleTreeOf(
            BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
            BibleSettings.serializer(),
            ignoredKeys = setOf("translations"),
        )

        assertNull(tree["translations"])
    }
}
