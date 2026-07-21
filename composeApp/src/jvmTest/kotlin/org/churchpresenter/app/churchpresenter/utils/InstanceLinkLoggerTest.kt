package org.churchpresenter.app.churchpresenter.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [InstanceLinkLogger] is the only window into Instance Link sync bugs — the feature's REST fetches
 * and state application are otherwise silent on failure. Its JSONL is hand-built, so an unescaped
 * quote in a field value produces a line that can't be parsed back, defeating the whole point.
 *
 * The log file is `by lazy` per JVM under `user.home`, which the test task points at
 * `build/test-home`. All tests share the one run-stamped file and read the lines they appended.
 */
class InstanceLinkLoggerTest {

    private val logDir = File(System.getProperty("user.home"), ".churchpresenter/instance-link/logs")

    /** The single run-stamped log file this JVM writes to. */
    private fun logFile(): File = assertNotNull(
        logDir.listFiles()?.filter { it.name.startsWith("instance-link_") && it.name.endsWith(".jsonl") }
            ?.maxByOrNull { it.lastModified() },
        "expected an instance-link log file in ${logDir.absolutePath}",
    )

    /** Parses every line, then returns the last one carrying [event]. */
    private fun lastRowFor(event: String): JsonObject {
        val rows = logFile().readLines().filter { it.isNotBlank() }.map { line ->
            runCatching { Json.parseToJsonElement(line) as JsonObject }
                .getOrElse { e -> throw AssertionError("emitted invalid JSON: $line", e) }
        }
        return assertNotNull(
            rows.lastOrNull { it["event"]?.jsonPrimitive?.content == event },
            "no row found for event \"$event\"",
        )
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.takeIf { it != JsonNull }?.jsonPrimitive?.content

    @Test
    fun `a log line records the side, the event and the timestamp`() {
        InstanceLinkLogger.log(InstanceLinkLogSide.PRIMARY, "unit_basic")
        val row = lastRowFor("unit_basic")
        assertEquals("primary", row.str("side"), "side is lower-cased so both logs diff cleanly")
        assertEquals("unit_basic", row.str("event"))
        assertTrue(row.str("ts_ms")!!.toLong() > 0)
    }

    @Test
    fun `both sides are distinguishable`() {
        InstanceLinkLogger.log(InstanceLinkLogSide.PRIMARY, "unit_side_p")
        InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "unit_side_f")
        assertEquals("primary", lastRowFor("unit_side_p").str("side"))
        assertEquals("follower", lastRowFor("unit_side_f").str("side"))
    }

    @Test
    fun `field types are emitted as their JSON equivalents, not all as strings`() {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "unit_types",
            mapOf(
                "aString" to "text",
                "anInt" to 42,
                "aLong" to 9_000_000_000L,
                "aDouble" to 1.5,
                "aBoolTrue" to true,
                "aBoolFalse" to false,
                "aNull" to null,
            ),
        )
        val row = lastRowFor("unit_types")
        assertEquals("text", row.str("aString"))
        assertEquals("42", row.str("anInt"))
        assertEquals("9000000000", row.str("aLong"))
        assertEquals("1.5", row.str("aDouble"))
        assertEquals("true", row.str("aBoolTrue"))
        assertEquals("false", row.str("aBoolFalse"))
        assertEquals(JsonNull, row["aNull"], "an absent value should be JSON null, not the string \"null\"")

        // Numbers and booleans must be bare, or downstream tooling has to re-parse them.
        val raw = logFile().readLines().last { "unit_types" in it }
        assertTrue("\"anInt\":42" in raw, "int should be unquoted: $raw")
        assertTrue("\"aBoolTrue\":true" in raw, "boolean should be unquoted: $raw")
    }

    @Test
    fun `an unsupported value type is stringified rather than dropped`() {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.PRIMARY, "unit_other_type",
            mapOf("enumValue" to InstanceLinkLogSide.FOLLOWER, "listValue" to listOf(1, 2)),
        )
        val row = lastRowFor("unit_other_type")
        assertEquals("FOLLOWER", row.str("enumValue"))
        assertEquals("[1, 2]", row.str("listValue"))
    }

    @Test
    fun `quotes backslashes and newlines in values do not corrupt the line`() {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "unit_escaping",
            mapOf("payload" to """a "quoted" value with \backslash and
a newline"""),
        )
        // Parsing at all is the assertion; lastRowFor throws on invalid JSON.
        val payload = assertNotNull(lastRowFor("unit_escaping").str("payload"))
        assertTrue("\"quoted\"" in payload)
        assertTrue("\\backslash" in payload)
        assertTrue('\n' !in payload && '\r' !in payload, "newlines should be flattened")
    }

    @Test
    fun `an event name containing a quote is escaped too`() {
        InstanceLinkLogger.log(InstanceLinkLogSide.PRIMARY, """unit_"quoted"_event""")
        val row = lastRowFor("""unit_"quoted"_event""")
        assertEquals("""unit_"quoted"_event""", row.str("event"))
    }

    @Test
    fun `each call appends exactly one line`() {
        val before = logFile().readLines().size
        repeat(3) { InstanceLinkLogger.log(InstanceLinkLogSide.PRIMARY, "unit_append_$it") }
        assertEquals(before + 3, logFile().readLines().size)
    }

    @Test
    fun `an empty field map still produces a well-formed row`() {
        InstanceLinkLogger.log(InstanceLinkLogSide.PRIMARY, "unit_no_fields")
        val row = lastRowFor("unit_no_fields")
        assertEquals(setOf("ts_ms", "side", "event"), row.keys)
    }
}
