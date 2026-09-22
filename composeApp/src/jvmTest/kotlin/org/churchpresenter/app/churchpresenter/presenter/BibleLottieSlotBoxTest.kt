package org.churchpresenter.app.churchpresenter.presenter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where each `TextN`/`ReferenceN` box is: the generator's own metadata when a file carries it, and
 * otherwise what can be read out of the file itself — a matte where there is one, the text
 * document's wrap box where there is not.
 */
class BibleLottieSlotBoxTest {

    private companion object {
        const val TEXT_1 = BibleLottieTemplate.LAYER_TEXT_1
        const val REFERENCE_1 = BibleLottieTemplate.LAYER_REFERENCE_1
        const val TEXT_LAYER = 5
    }

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObjectOrThrow()

    private fun JsonObject.slots(vararg layers: JsonObject) = readSlots(this, layers.toList())

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrThrow(): JsonObject =
        this as? JsonObject ?: error("not an object")

    /** A text layer with a wrap box: `sz` is its size, `ps` where the box starts. */
    private fun textLayer(name: String, ps: String = "[10,20]", sz: String = "[300,80]") = obj(
        """{"ty":$TEXT_LAYER,"nm":"$name","t":{"d":{"k":[{"s":{"ps":$ps,"sz":$sz}}]}}}""",
    )

    /** The matte a generated file draws behind a slot: a rectangle of size `s` centered on `p`. */
    private fun matteLayer(name: String, size: String = "[204,84]", pos: String = "[160,60]", td: Int = 1) = obj(
        """{"nm":"$name","td":$td,"shapes":[{"it":[{"ty":"rc","s":{"a":0,"k":$size},""" +
            """"p":{"a":0,"k":$pos}}]}]}""",
    )

    @Test
    fun `declared boxes win outright`() {
        val meta = obj("""{"slots":{"$TEXT_1":[1,2,3,4],"$REFERENCE_1":[5,6,7,8]}}""")

        val slots = meta.slots(textLayer(TEXT_1))

        assertEquals(LottieSlotBox(1f, 2f, 3f, 4f), slots[TEXT_1])
        assertEquals(LottieSlotBox(5f, 6f, 7f, 8f), slots[REFERENCE_1])
    }

    @Test
    fun `a declared box that is not four numbers is skipped, and the rest still stand`() {
        val meta = obj("""{"slots":{"$TEXT_1":[1,2,3],"$REFERENCE_1":[5,6,7,8]}}""")

        val slots = meta.slots(textLayer(TEXT_1))

        assertNull(slots[TEXT_1])
        assertEquals(LottieSlotBox(5f, 6f, 7f, 8f), slots[REFERENCE_1])
    }

    @Test
    fun `a slots entry that is not an array at all falls back to the layers`() {
        val meta = obj("""{"slots":{"$TEXT_1":"nope"}}""")

        val slots = meta.slots(textLayer(TEXT_1))

        assertEquals(LottieSlotBox(10f, 20f, 300f, 80f), slots[TEXT_1])
    }

    @Test
    fun `no metadata, and metadata with no slots, both read the file itself`() {
        val fromNull = readSlots(null, listOf(textLayer(TEXT_1)))
        val fromEmpty = obj("""{"slots":{}}""").slots(textLayer(TEXT_1))
        val fromWrongShape = obj("""{"slots":7}""").slots(textLayer(TEXT_1))

        val box = LottieSlotBox(10f, 20f, 300f, 80f)
        assertEquals(box, fromNull[TEXT_1])
        assertEquals(box, fromEmpty[TEXT_1])
        assertEquals(box, fromWrongShape[TEXT_1])
    }

    @Test
    fun `a matte is the box, less its padding, where one is drawn`() {
        val slots = readSlots(null, listOf(textLayer(TEXT_1), matteLayer("${TEXT_1}Matte")))

        // 204x84 padded by 4 is 200x80, centered on (160,60) — so it starts at (60,20).
        assertEquals(LottieSlotBox(60f, 20f, 200f, 80f), slots[TEXT_1])
    }

    @Test
    fun `a matte for a layer with no text document is ignored`() {
        val slots = readSlots(null, listOf(matteLayer("${TEXT_1}Matte")))

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `only a real matte counts`() {
        val notAMatte = matteLayer("${TEXT_1}Matte", td = 0)
        val notASlot = matteLayer("SomethingElseMatte")
        val notNamedMatte = matteLayer(TEXT_1)

        val slots = readSlots(null, listOf(textLayer(TEXT_1), notAMatte, notASlot, notNamedMatte))

        assertEquals(LottieSlotBox(10f, 20f, 300f, 80f), slots[TEXT_1], "the document's own box stands")
    }

    @Test
    fun `a matte missing its shape, its rectangle or its numbers leaves the document's box alone`() {
        val document = LottieSlotBox(10f, 20f, 300f, 80f)
        val noName = obj("""{"td":1,"shapes":[]}""")
        val noShapes = obj("""{"nm":"${TEXT_1}Matte","td":1}""")
        val noRect = obj("""{"nm":"${TEXT_1}Matte","td":1,"shapes":[{"it":[{"ty":"el"}]}]}""")
        val noSize = obj(
            """{"nm":"${TEXT_1}Matte","td":1,"shapes":[{"it":[{"ty":"rc","p":{"a":0,"k":[1,2]}}]}]}""",
        )
        val noPos = obj(
            """{"nm":"${TEXT_1}Matte","td":1,"shapes":[{"it":[{"ty":"rc","s":{"a":0,"k":[1,2]}}]}]}""",
        )
        val shortNumbers = matteLayer("${TEXT_1}Matte", size = "[204]", pos = "[160]")

        listOf(noName, noShapes, noRect, noSize, noPos, shortNumbers).forEach { broken ->
            val slots = readSlots(null, listOf(textLayer(TEXT_1), broken))
            assertEquals(document, slots[TEXT_1], "a matte that cannot be read is not a box")
        }
    }

    @Test
    fun `a layer that is not text, is unnamed, or has no readable box contributes nothing`() {
        val notText = obj("""{"ty":4,"nm":"$TEXT_1","t":{"d":{"k":[{"s":{"ps":[1,2],"sz":[3,4]}}]}}}""")
        val unnamed = obj("""{"ty":$TEXT_LAYER,"t":{"d":{"k":[{"s":{"ps":[1,2],"sz":[3,4]}}]}}}""")
        val noDocument = obj("""{"ty":$TEXT_LAYER,"nm":"$TEXT_1"}""")
        val noSize = obj("""{"ty":$TEXT_LAYER,"nm":"$TEXT_1","t":{"d":{"k":[{"s":{"ps":[1,2]}}]}}}""")
        val noOrigin = obj("""{"ty":$TEXT_LAYER,"nm":"$TEXT_1","t":{"d":{"k":[{"s":{"sz":[3,4]}}]}}}""")
        val shortNumbers = textLayer(TEXT_1, ps = "[1]", sz = "[3]")

        listOf(notText, unnamed, noDocument, noSize, noOrigin, shortNumbers).forEach { layer ->
            assertTrue(readSlots(null, listOf(layer)).isEmpty(), "nothing readable is no slot")
        }
    }

    @Test
    fun `every slot of a two-slot file is read`() {
        val slots = readSlots(
            null,
            listOf(
                textLayer(TEXT_1, ps = "[10,20]", sz = "[300,80]"),
                textLayer(REFERENCE_1, ps = "[10,110]", sz = "[300,40]"),
            ),
        )

        assertEquals(LottieSlotBox(10f, 20f, 300f, 80f), slots[TEXT_1])
        assertEquals(LottieSlotBox(10f, 110f, 300f, 40f), slots[REFERENCE_1])
    }
}
