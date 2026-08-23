package org.churchpresenter.companionserver

import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompanionServerHelpersTest {

    @Test
    fun `jsonEscape passes ordinary text through untouched`() {
        assertEquals("Amazing Grace", jsonEscape("Amazing Grace"))
    }

    @Test
    fun `jsonEscape escapes the characters that would break a JSON string`() {
        assertEquals("a\\\\b", jsonEscape("a\\b"))
        assertEquals("say \\\"hi\\\"", jsonEscape("say \"hi\""))
        assertEquals("line1\\nline2", jsonEscape("line1\nline2"))
        assertEquals("a\\rb", jsonEscape("a\rb"))
        assertEquals("a\\tb", jsonEscape("a\tb"))
    }

    @Test
    fun `jsonEscape renders other control characters as unicode escapes`() {
        assertEquals("a\\u0007b", jsonEscape("a\u0007b"))
        assertEquals("\\u0001", jsonEscape("\u0001"))
    }

    @Test
    fun `jsonEscape output survives a round trip through a JSON parser`() {
        val raw = "quote\" backslash\\ newline\n tab\t bell\u0007"
        val decoded = Json.parseToJsonElement("\"${jsonEscape(raw)}\"").jsonPrimitive.content
        assertEquals(raw, decoded)
    }

    @Test
    fun `contentTypeForExtension maps every image extension it claims to support`() {
        assertEquals(ContentType.Image.JPEG, contentTypeForExtension("jpg"))
        assertEquals(ContentType.Image.JPEG, contentTypeForExtension("jpeg"))
        assertEquals(ContentType.Image.PNG, contentTypeForExtension("png"))
        assertEquals(ContentType.Image.GIF, contentTypeForExtension("gif"))
        assertEquals(ContentType.parse("image/webp"), contentTypeForExtension("webp"))
        assertEquals(ContentType.parse("image/bmp"), contentTypeForExtension("bmp"))
        assertEquals(ContentType.parse("image/heic"), contentTypeForExtension("heic"))
        assertEquals(ContentType.parse("image/heic"), contentTypeForExtension("heif"))
    }

    @Test
    fun `contentTypeForExtension is case insensitive`() {
        assertEquals(ContentType.Image.PNG, contentTypeForExtension("PNG"))
        assertEquals(ContentType.Image.JPEG, contentTypeForExtension("JPeG"))
    }

    @Test
    fun `contentTypeForExtension falls back to JPEG for anything unrecognised`() {
        assertEquals(ContentType.Image.JPEG, contentTypeForExtension("tiff"))
        assertEquals(ContentType.Image.JPEG, contentTypeForExtension(""))
    }

    @Test
    fun `localIpAddress returns a dotted address or the localhost fallback`() {
        val address = localIpAddress()
        assertTrue(
            address == "localhost" || Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(address),
            "expected an IPv4 address or \"localhost\", got \"$address\""
        )
    }
}
