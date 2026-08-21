package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.ServerSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The URLs an operator copies out of the Lower third triggers card and pastes into a Stream Deck.
 *
 * These were local functions inside the composable, reachable only by clicking a Copy button — which
 * writes to the system clipboard and throws `HeadlessException` under this suite's JVM, so none of
 * this could be tested through the UI. They are now `internal` top-level functions that the tab
 * calls, and this class exercises them directly.
 *
 * That matters more than the coverage: a wrong URL here is silent. The button copies, the operator
 * pastes into Companion, and the failure only shows up mid-service when the button does nothing or
 * keys the wrong layer. Every combination of API key, key type and keying is pinned below.
 */
class ServerSettingsTabUrlBuilderTest {

    private val host = "http://192.168.1.50:8765"

    // ── The API key tail ────────────────────────────────────────────────────────────────────────

    @Test
    fun `no protection means no key on the URL`() {
        assertEquals("", effectiveApiKey(ServerSettings(apiKeyEnabled = false, apiKey = "secret")))
    }

    @Test
    fun `protection with no key set still means no key on the URL`() {
        assertEquals("", effectiveApiKey(ServerSettings(apiKeyEnabled = true, apiKey = "")))
    }

    @Test
    fun `protection with a key set puts that key on the URL`() {
        assertEquals("secret", effectiveApiKey(ServerSettings(apiKeyEnabled = true, apiKey = "secret")))
    }

    @Test
    fun `an empty query builds no tail at all`() {
        assertEquals("", apiQueryString(), "nothing to add means nothing appended")
    }

    @Test
    fun `a query with only an extra parameter needs no ampersand`() {
        assertEquals("?keytype=dsk", apiQueryString(extra = "keytype=dsk"))
    }

    @Test
    fun `a query with only an API key needs no ampersand either`() {
        assertEquals("?apiKey=secret", apiQueryString(apiKey = "secret"))
    }

    @Test
    fun `a query with both joins them with an ampersand`() {
        assertEquals("?keytype=dsk&apiKey=secret", apiQueryString(extra = "keytype=dsk", apiKey = "secret"))
    }

    /** The key is URL-encoded, so a key with a space or an ampersand cannot break the query. */
    @Test
    fun `an awkward API key is encoded rather than pasted in raw`() {
        assertEquals("?apiKey=a+b%26c", apiQueryString(apiKey = "a b&c"))
    }

    // ── Path encoding ───────────────────────────────────────────────────────────────────────────

    /**
     * Spaces become `%20` rather than `+`. In a *path* segment `+` is a literal plus, so the default
     * form encoder's output would name a different file.
     */
    @Test
    fun `a name with spaces encodes them as percent-twenty`() {
        assertEquals("Welcome%20Home", encodeUrlPathSegment("Welcome Home"))
    }

    @Test
    fun `a name with a slash or ampersand is encoded`() {
        assertEquals("a%2Fb%26c", encodeUrlPathSegment("a/b&c"))
    }

    @Test
    fun `a plain name is left alone`() {
        assertEquals("welcome", encodeUrlPathSegment("welcome"))
    }

    // ── Lower-third triggers ────────────────────────────────────────────────────────────────────

    /** Running defaults to keying, so the keyed URL is the one with no `key` parameter. */
    @Test
    fun `a keyed trigger carries no key parameter`() {
        assertEquals(
            "$host/api/lowerthirds/welcome/run",
            lowerThirdTriggerUrl(host, "welcome", withKey = true),
        )
    }

    @Test
    fun `an unkeyed trigger switches keying off with key equals zero`() {
        assertEquals(
            "$host/api/lowerthirds/welcome/run?key=0",
            lowerThirdTriggerUrl(host, "welcome", withKey = false),
        )
    }

    @Test
    fun `a keyed trigger with protection carries only the API key`() {
        assertEquals(
            "$host/api/lowerthirds/welcome/run?apiKey=secret",
            lowerThirdTriggerUrl(host, "welcome", withKey = true, apiKey = "secret"),
        )
    }

    @Test
    fun `an unkeyed trigger with protection carries both parameters in order`() {
        assertEquals(
            "$host/api/lowerthirds/welcome/run?key=0&apiKey=secret",
            lowerThirdTriggerUrl(host, "welcome", withKey = false, apiKey = "secret"),
        )
    }

    @Test
    fun `a trigger for a name with spaces encodes the path`() {
        assertEquals(
            "$host/api/lowerthirds/Welcome%20Home/run",
            lowerThirdTriggerUrl(host, "Welcome Home", withKey = true),
        )
    }

    // ── ATEM key targets ────────────────────────────────────────────────────────────────────────

    @Test
    fun `an upstream key names its type`() {
        assertEquals("keytype=usk", atemKeyTypeParam(AtemSettings(useDownstreamKey = false)))
    }

    @Test
    fun `a downstream key names its type`() {
        assertEquals("keytype=dsk", atemKeyTypeParam(AtemSettings(useDownstreamKey = true)))
    }

    /** Indices are stored zero-based and published one-based, to match the switcher's own numbering. */
    @Test
    fun `a downstream key target names only the key, one-based`() {
        assertEquals(
            "keytype=dsk&key=2",
            atemKeyTarget(AtemSettings(useDownstreamKey = true, dskIndex = 1)),
        )
    }

    @Test
    fun `an upstream key target names the mix effect as well, both one-based`() {
        assertEquals(
            "keytype=usk&me=2&key=3",
            atemKeyTarget(AtemSettings(useDownstreamKey = false, keyMixEffect = 1, keyIndex = 2)),
        )
    }

    @Test
    fun `the default upstream key target is the first key of the first mix effect`() {
        assertEquals("keytype=usk&me=1&key=1", atemKeyTarget(AtemSettings(useDownstreamKey = false)))
    }

    // ── ATEM media uploads ──────────────────────────────────────────────────────────────────────

    @Test
    fun `an unkeyed still upload carries nothing but its path`() {
        assertEquals(
            "$host/api/atem/still/welcome",
            atemMediaUrl(host, "still", "welcome", keyTarget = ""),
        )
    }

    @Test
    fun `a keyed still upload carries the key target`() {
        assertEquals(
            "$host/api/atem/still/welcome?keytype=dsk&key=1",
            atemMediaUrl(host, "still", "welcome", keyTarget = "keytype=dsk&key=1"),
        )
    }

    @Test
    fun `a keyed clip upload with protection carries the target and the key`() {
        assertEquals(
            "$host/api/atem/clip/welcome?keytype=usk&me=1&key=1&apiKey=secret",
            atemMediaUrl(host, "clip", "welcome", keyTarget = "keytype=usk&me=1&key=1", apiKey = "secret"),
        )
    }

    @Test
    fun `an unkeyed clip upload with protection carries only the key`() {
        assertEquals(
            "$host/api/atem/clip/welcome?apiKey=secret",
            atemMediaUrl(host, "clip", "welcome", keyTarget = "", apiKey = "secret"),
        )
    }

    // ── The global action URLs ──────────────────────────────────────────────────────────────────

    @Test
    fun `the key-on URL names the key type`() {
        assertEquals("$host/api/atem/key/on?keytype=dsk", atemKeyUrl(host, on = true, keyTypeParam = "keytype=dsk"))
    }

    @Test
    fun `the key-off URL differs only in its verb`() {
        assertEquals("$host/api/atem/key/off?keytype=usk", atemKeyUrl(host, on = false, keyTypeParam = "keytype=usk"))
    }

    @Test
    fun `a key URL with protection carries the type and the key`() {
        assertEquals(
            "$host/api/atem/key/on?keytype=usk&apiKey=secret",
            atemKeyUrl(host, on = true, keyTypeParam = "keytype=usk", apiKey = "secret"),
        )
    }

    /** Hide takes down only the lower third; Clear takes down every output. Different endpoints. */
    @Test
    fun `hide and clear are different endpoints`() {
        assertEquals("$host/api/lowerthirds/hide", lowerThirdHideUrl(host))
        assertEquals("$host/api/clear", clearDisplayUrl(host))
    }

    @Test
    fun `hide and clear carry the API key when there is one`() {
        assertEquals("$host/api/lowerthirds/hide?apiKey=secret", lowerThirdHideUrl(host, "secret"))
        assertEquals("$host/api/clear?apiKey=secret", clearDisplayUrl(host, "secret"))
    }

    // ── Splitting the server URL for the QR ─────────────────────────────────────────────────────

    @Test
    fun `a normal server URL splits into host and port`() {
        assertEquals("192.168.1.50" to "8765", parseServerUrlHostPort("http://192.168.1.50:8765"))
    }

    @Test
    fun `a URL with no port yields a blank port`() {
        assertEquals("church.local" to "", parseServerUrlHostPort("http://church.local"))
    }

    /** A hand-typed host override is not a URL; it must still produce something scannable. */
    @Test
    fun `an unparseable URL falls back to using the whole string as the host`() {
        assertEquals("not a url" to "", parseServerUrlHostPort("not a url"))
    }

    @Test
    fun `a blank URL falls back to blank`() {
        assertEquals("" to "", parseServerUrlHostPort(""))
    }

    @Test
    fun `a hostname URL keeps its name rather than resolving it`() {
        assertEquals("church-mac.local" to "9000", parseServerUrlHostPort("http://church-mac.local:9000"))
    }

    // ── The connection QR ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a QR deep link names the host`() {
        assertEquals(
            "churchpresenter://connect?host=192.168.1.50",
            connectionQrContent("192.168.1.50", port = "", apiKey = null),
        )
    }

    @Test
    fun `a QR deep link adds the port when there is one`() {
        assertEquals(
            "churchpresenter://connect?host=192.168.1.50&port=8765",
            connectionQrContent("192.168.1.50", port = "8765", apiKey = null),
        )
    }

    @Test
    fun `a QR deep link adds the API key when there is one`() {
        assertEquals(
            "churchpresenter://connect?host=192.168.1.50&port=8765&apikey=secret",
            connectionQrContent("192.168.1.50", port = "8765", apiKey = "secret"),
        )
    }

    @Test
    fun `a blank API key is left off the QR deep link`() {
        assertEquals(
            "churchpresenter://connect?host=192.168.1.50&port=8765",
            connectionQrContent("192.168.1.50", port = "8765", apiKey = ""),
        )
    }

    @Test
    fun `a QR deep link with a key but no port skips only the port`() {
        assertEquals(
            "churchpresenter://connect?host=church.local&apikey=secret",
            connectionQrContent("church.local", port = "", apiKey = "secret"),
        )
    }
}
