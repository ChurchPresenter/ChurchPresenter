package org.churchpresenter.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals

class SecretsTest {

    private val mask = Secrets.MASK

    @Test
    fun `every secret query parameter is masked and the rest of the url kept`() {
        listOf("apiKey", "password", "token", "access_token", "refresh_token", "client_secret").forEach { name ->
            assertEquals(
                "http://host:8765/api/x?$name=$mask&page=2",
                Secrets.redact("http://host:8765/api/x?$name=hunter2&page=2"),
                name,
            )
        }
    }

    @Test
    fun `a secret that is not the first parameter is masked too`() {
        assertEquals("/media?file=a.mp4&apiKey=$mask", Secrets.redact("/media?file=a.mp4&apiKey=abc123"))
    }

    @Test
    fun `parameter names match whatever their case`() {
        assertEquals("?APIKEY=$mask", Secrets.redact("?APIKEY=abc"))
    }

    @Test
    fun `a value stops at a fragment, a quote or whitespace`() {
        assertEquals("?token=$mask#top", Secrets.redact("?token=abc#top"))
        assertEquals("url \"?token=$mask\" failed", Secrets.redact("url \"?token=abc\" failed"))
        assertEquals("?token=$mask then", Secrets.redact("?token=abc then"))
    }

    @Test
    fun `a parameter that only ends in a secret name is left alone`() {
        assertEquals("?mytoken=abc&tokens=2", Secrets.redact("?mytoken=abc&tokens=2"))
    }

    @Test
    fun `the api key header is masked`() {
        assertEquals("X-Api-Key: $mask", Secrets.redact("X-Api-Key: abc123"))
        assertEquals("x-api-key:$mask, next", Secrets.redact("x-api-key:abc123, next"))
    }

    @Test
    fun `bearer and basic authorization values are masked`() {
        assertEquals("Authorization: Bearer $mask", Secrets.redact("Authorization: Bearer eyJhbGci.x.y"))
        assertEquals("authorization: basic $mask", Secrets.redact("authorization: basic dXNlcjpwYXNz"))
    }

    @Test
    fun `text with no credential is returned unchanged`() {
        val line = "[Camera] Stream interrupted at http://host/stream?quality=high"
        assertEquals(line, Secrets.redact(line))
        assertEquals("", Secrets.redact(""))
    }
}
