package org.churchpresenter.server

import org.churchpresenter.calendar.sync.Envelope
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarJoinTest {

    private val now = Instant.parse("2026-10-01T09:00:00Z")
    private val join = CalendarJoin(
        relay = "https://relay.example",
        instance = "inst-1",
        desktop = "comp-1",
        token = "Abc_def-".repeat(4),
        key = Envelope.encodeKey(Envelope.newKey()),
        role = "follower",
        mainName = "Office",
        expires = "2026-10-02T09:00:00Z",
    )

    private fun parse(candidate: CalendarJoin) = CalendarJoin.parse(candidate.toJson(), now)

    @Test
    fun `a code is one pasteable word that reads back as the join`() {
        val code = join.toCode()

        assertTrue(code.startsWith(CalendarJoin.CODE_PREFIX))
        assertTrue(code.none { it.isWhitespace() || it == '=' || it == '+' || it == '/' })
        assertEquals(JoinParse.Ok(join), CalendarJoin.parse(code, now))
    }

    @Test
    fun `a code broken across lines by a mail program still reads`() {
        val code = join.toCode()
        val wrapped = "  " + code.chunked(20).joinToString("\n") + "\n"

        assertEquals(JoinParse.Ok(join), CalendarJoin.parse(wrapped, now))
    }

    @Test
    fun `a join file's JSON reads, and fields it does not know are ignored`() {
        assertEquals(JoinParse.Ok(join), CalendarJoin.parse(join.toJson(), now))
        val withExtra = join.toJson().replaceFirst("{", """{"later":"field",""")
        assertEquals(JoinParse.Ok(join), CalendarJoin.parse(withExtra, now))
        assertEquals(JoinParse.Ok(join.copy(mainName = "")), parse(join.copy(mainName = "")))
    }

    @Test
    fun `text that is neither a file nor a code is invalid`() {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        listOf(
            "",
            "hello",
            "CPJ-",
            "CPJ-***not base64***",
            "CPJ-" + encoder.encodeToString("not json".toByteArray()),
            "CPJ-" + encoder.encodeToString("""{"relay":"https://relay.example"}""".toByteArray()),
            """{"relay":"https://relay.example"}""",
            "[1, 2, 3]",
        ).forEach { assertEquals(JoinParse.Invalid, CalendarJoin.parse(it, now), it) }
    }

    @Test
    fun `every field out of shape makes it invalid`() {
        listOf(
            join.copy(v = 2),
            join.copy(relay = "http://relay.example"),
            join.copy(instance = "has spaces"),
            join.copy(instance = ""),
            join.copy(desktop = "../etc"),
            join.copy(token = "short"),
            join.copy(token = "a".repeat(129)),
            join.copy(token = "has spaces and is long enough"),
            join.copy(key = "not-a-key"),
            join.copy(key = Envelope.encodeKey(ByteArray(16))),
            join.copy(role = "main"),
            join.copy(role = ""),
            join.copy(expires = "tomorrow"),
        ).forEach { assertEquals(JoinParse.Invalid, parse(it), it.toString()) }
    }

    @Test
    fun `the token may be as short as sixteen and as long as a hundred and twenty-eight`() {
        assertEquals(JoinParse.Ok(join.copy(token = "a".repeat(16))), parse(join.copy(token = "a".repeat(16))))
        assertEquals(JoinParse.Ok(join.copy(token = "a".repeat(128))), parse(join.copy(token = "a".repeat(128))))
        assertEquals(JoinParse.Ok(join.copy(role = "viewer")), parse(join.copy(role = "viewer")))
    }

    @Test
    fun `a join past its day is expired, and one at the very moment is not`() {
        assertEquals(JoinParse.Expired, parse(join.copy(expires = "2026-10-01T08:59:59Z")))
        assertEquals(JoinParse.Ok(join.copy(expires = now.toString())), parse(join.copy(expires = now.toString())))
    }
}
