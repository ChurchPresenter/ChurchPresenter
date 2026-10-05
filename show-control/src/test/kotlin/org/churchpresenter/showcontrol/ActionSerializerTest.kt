package org.churchpresenter.showcontrol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Actions as a file keeps them: by their stored names, and surviving a newer build's. */
class ActionSerializerTest {

    private val json = Json
    private val list = ListSerializer(Action.serializer())

    private val every: List<Action> = listOf(
        Action.GoLive(rowId = "row1", plays = 0),
        Action.GoLive(item = ScheduleItem.LabelItem("l", "Welcome", "#FFFFFF", "#000000")),
        Action.ToPreview(rowId = "row2"),
        Action.Take(),
        Action.Take("SLIDE"),
        Action.Clear("MESSAGES"),
        Action.ClearAll,
        Action.ClearGroup("Clear text"),
        Action.Message(template = "Nursery", tokens = mapOf("number" to "12"), durationSeconds = 30),
        Action.Prop("Logo", on = false),
        Action.LowerThird("Pastor"),
        Action.Timer(TimerModes.CLOCK, until = "10:30"),
        Action.Media(MediaCommand.PAUSE),
        Action.ObsScene("Wide"),
        Action.AtemKey(downstream = true, keyer = 1, on = false),
        Action.AtemMacro(3),
        Action.CompanionPress("SIDEBAR", 4),
        Action.NextItem,
        Action.PreviousItem,
        Action.Wait(1.5),
        Action.RunMacro("Walk in"),
    )

    @Test
    fun `every action survives the file`() {
        val written = json.encodeToString(list, every)
        assertEquals(every, json.decodeFromString(list, written))
    }

    @Test
    fun `each is stored by its own name, which never changes`() {
        val types = json.parseToJsonElement(json.encodeToString(list, every)).jsonArray
            .map { it.jsonObject.getValue("type").jsonPrimitive.content }.toSet()
        val pinned = setOf(
            "set", "preview", "take", "clear", "clearAll", "clearGroup", "message", "prop", "lowerThird",
            "timer", "media", "obsScene", "atemKey", "atemMacro", "companion", "next", "previous", "wait", "macro",
        )
        assertEquals(pinned, types)
        assertEquals(pinned, ActionSerializer.typeNames)
        assertEquals(
            """{"type":"media","command":"pause"}""",
            json.encodeToString(Action.serializer(), Action.Media(MediaCommand.PAUSE)),
        )
    }

    @Test
    fun `an action from a newer build is kept as written`() {
        val written = """[{"type":"hologram","colour":"blue"},{"type":"clearAll"}]"""
        val read = json.decodeFromString(list, written)
        assertEquals(
            Action.Unknown(JsonObject(mapOf("type" to JsonPrimitive("hologram"), "colour" to JsonPrimitive("blue")))),
            read.first(),
        )
        assertEquals(written, json.encodeToString(list, read))
    }

    @Test
    fun `a field it does not have is ignored, and one it cannot read keeps it unknown`() {
        val extra = """{"type":"obsScene","scene":"Wide","fade":3}"""
        assertEquals(Action.ObsScene("Wide"), json.decodeFromString(Action.serializer(), extra))
        assertIs<Action.Unknown>(json.decodeFromString(Action.serializer(), """{"type":"obsScene"}"""))
        assertIs<Action.Unknown>(json.decodeFromString(Action.serializer(), """{"type":"media","command":"rewind"}"""))
        assertIs<Action.Unknown>(json.decodeFromString(Action.serializer(), """{"scene":"Wide"}"""))
    }

    @Test
    fun `an action is an object`() {
        assertFailsWith<SerializationException> { json.decodeFromString(Action.serializer(), "\"clearAll\"") }
    }
}
