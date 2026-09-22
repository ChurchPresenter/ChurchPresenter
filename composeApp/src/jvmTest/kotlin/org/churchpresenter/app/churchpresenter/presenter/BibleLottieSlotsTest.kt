package org.churchpresenter.app.churchpresenter.presenter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where each Bible band slot's text box is read from: the generator's declared boxes first, then
 * each text document's own wrap box, with a slot's track matte overriding its document box.
 *
 * The band files come from the generator, from older versions of it, and from people's own tools,
 * so every step of the read has to survive a file shaped a little differently. Each malformed case
 * below is one of those shapes, and each asserts what the slot comes out as rather than only that
 * nothing threw.
 */
class BibleLottieSlotsTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun layers(vararg json: String): List<JsonObject> = json.map(::obj)

    /** A text layer whose document says its box is at [ps] and sized [sz]. */
    private fun textLayer(name: String = "Text1", ps: String = "[10, 20]", sz: String = "[300, 40]") =
        """{"ty": 5, "nm": "$name", "t": {"d": {"k": [{"s": {"ps": $ps, "sz": $sz}}]}}}"""

    /** A track matte for [slot]: a rectangle of size [s] centred on [p]. */
    private fun matteLayer(slot: String = "Text1", s: String = "[104, 54]", p: String = "[200, 100]", td: Int = 1) =
        """{"nm": "${slot}Matte", "td": $td,
            "shapes": [{"it": [{"ty": "rc", "s": {"a": 0, "k": $s}, "p": {"a": 0, "k": $p}}]}]}"""

    private val documentBox = LottieSlotBox(10f, 20f, 300f, 40f)

    // ── Declared boxes ──────────────────────────────────────────────────────────

    @Test
    fun `declared boxes win over anything the layers say`() {
        val meta = obj("""{"slots": {"Text1": [1, 2, 3, 4], "Reference1": [5, 6, 7, 8]}}""")

        assertEquals(
            mapOf("Text1" to LottieSlotBox(1f, 2f, 3f, 4f), "Reference1" to LottieSlotBox(5f, 6f, 7f, 8f)),
            readSlots(meta, layers(textLayer())),
        )
    }

    @Test
    fun `a declared box that is not four numbers is left out`() {
        val meta = obj(
            """{"slots": {"Text1": [1, 2, 3, 4], "Short": [1, 2], "Word": "wide", "Nulls": [null, 1, 2, 3]}}""",
        )

        assertEquals(mapOf("Text1" to LottieSlotBox(1f, 2f, 3f, 4f)), readSlots(meta, emptyList()))
    }

    @Test
    fun `metadata that declares nothing usable falls through to the documents`() {
        listOf(
            null,
            obj("""{}"""),
            obj("""{"slots": "none"}"""),
            obj("""{"slots": {}}"""),
            obj("""{"slots": {"Text1": [1, 2]}}"""),
        ).forEach { meta ->
            assertEquals(mapOf("Text1" to documentBox), readSlots(meta, layers(textLayer())), "$meta")
        }
    }

    // ── Document boxes ──────────────────────────────────────────────────────────

    @Test
    fun `a text document's position and size are its box`() {
        assertEquals(
            mapOf("Text1" to documentBox, "Reference1" to LottieSlotBox(0f, 0f, 50f, 10f)),
            readSlots(null, layers(textLayer(), textLayer("Reference1", ps = "[0, 0]", sz = "[50, 10]"))),
        )
    }

    @Test
    fun `layers that are not a readable text document give no box`() {
        val unreadable = layers(
            """{"ty": 4, "nm": "Shape"}""",
            """{"nm": "NoType"}""",
            """{"ty": "five", "nm": "WordType"}""",
            """{"ty": 5}""",
            """{"ty": 5, "nm": null}""",
            """{"ty": 5, "nm": "NoT"}""",
            """{"ty": 5, "nm": "TNotObject", "t": 1}""",
            """{"ty": 5, "nm": "NoD", "t": {}}""",
            """{"ty": 5, "nm": "DNotObject", "t": {"d": 1}}""",
            """{"ty": 5, "nm": "NoK", "t": {"d": {}}}""",
            """{"ty": 5, "nm": "EmptyK", "t": {"d": {"k": []}}}""",
            """{"ty": 5, "nm": "NoS", "t": {"d": {"k": [{}]}}}""",
            """{"ty": 5, "nm": "SNotObject", "t": {"d": {"k": [{"s": 1}]}}}""",
            """{"ty": 5, "nm": "NoSz", "t": {"d": {"k": [{"s": {"ps": [1, 2]}}]}}}""",
            """{"ty": 5, "nm": "NoPs", "t": {"d": {"k": [{"s": {"sz": [1, 2]}}]}}}""",
            textLayer("ShortSz", sz = "[1]"),
            textLayer("ShortPs", ps = "[1]"),
        )

        assertEquals(emptyMap(), readSlots(null, unreadable))
    }

    // ── Mattes ──────────────────────────────────────────────────────────────────

    @Test
    fun `a slot's matte replaces its document box, less a hair of padding`() {
        // 104 x 54 less 4 either way is 100 x 50, centred on (200, 100).
        assertEquals(
            mapOf("Text1" to LottieSlotBox(150f, 75f, 100f, 50f)),
            readSlots(null, layers(textLayer(), matteLayer())),
        )
    }

    @Test
    fun `a matte only moves a slot that has a document of its own`() {
        assertEquals(emptyMap(), readSlots(null, layers(matteLayer())))
    }

    @Test
    fun `mattes that cannot be read leave the document box alone`() {
        val broken = listOf(
            """{"td": 1}""",
            """{"nm": "Text1", "td": 1}""",
            matteLayer(td = 0),
            """{"nm": "Text1Matte"}""",
            matteLayer(slot = "Logo"),
            """{"nm": "Text1Matte", "td": 1}""",
            """{"nm": "Text1Matte", "td": 1, "shapes": []}""",
            """{"nm": "Text1Matte", "td": 1, "shapes": [{}]}""",
            """{"nm": "Text1Matte", "td": 1, "shapes": [{"it": [1, {"ty": "el"}]}]}""",
            """{"nm": "Text1Matte", "td": 1, "shapes": [{"it": [{"ty": "rc", "p": {"k": [1, 2]}}]}]}""",
            """{"nm": "Text1Matte", "td": 1, "shapes": [{"it": [{"ty": "rc", "s": {"k": [1, 2]}}]}]}""",
            """{"nm": "Text1Matte", "td": 1, "shapes": [{"it": [{"ty": "rc", "s": 1, "p": {"k": [1, 2]}}]}]}""",
            """{"nm": "Text1Matte", "td": 1, "shapes": [{"it": [{"ty": "rc", "s": {"a": 0}, "p": {"k": [1, 2]}}]}]}""",
            matteLayer(s = "[1]"),
            matteLayer(p = "[1]"),
        )

        broken.forEach { matte ->
            assertEquals(mapOf("Text1" to documentBox), readSlots(null, layers(textLayer(), matte)), matte)
        }
    }
}
