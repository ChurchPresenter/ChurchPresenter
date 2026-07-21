package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [LottieFonts] declares the bundled font families by filename. A typo or a font that never made it
 * into `resources/fonts/` doesn't fail the build — it silently falls back to a default typeface,
 * which is exactly the blocky-lower-third bug this object was written to fix. Resolving every
 * declared path against the classpath is what makes that a build failure instead.
 */
class LottieFontsTest {

    @Test
    fun `every declared bundled font actually exists on the classpath`() {
        val missing = LottieFonts.bundledFontResources().filter {
            LottieFonts::class.java.getResourceAsStream(it) == null
        }
        assertTrue(missing.isEmpty(), "declared but not bundled: $missing")
    }

    @Test
    fun `bundled font paths are well-formed and unique`() {
        val resources = LottieFonts.bundledFontResources()
        assertTrue(resources.isNotEmpty())
        assertTrue(resources.all { it.startsWith("/fonts/") }, "paths must be absolute classpath refs")
        assertTrue(resources.all { it.endsWith(".ttf") }, "only TrueType files are registered")
        assertEquals(resources.size, resources.toSet().size, "duplicate entries would register a font twice")
    }

    @Test
    fun `the families LottieGen offers are all bundled`() {
        // These are the family names lower-third lotties reference by name; a family missing here
        // renders with the wrong typeface rather than failing.
        val resources = LottieFonts.bundledFontResources()
        for (stem in listOf(
            "OpenSans", "Poppins", "Raleway", "AnonymousPro", "PatuaOne",
            "Lora", "AbrilFatface", "Cookie", "OleoScript", "Kalam", "FredokaOne",
        )) {
            assertTrue(resources.any { it.contains(stem) }, "no bundled font for family stem $stem")
        }
    }

    @Test
    fun `every font file has a regular cut`() {
        val resources = LottieFonts.bundledFontResources()
        val regulars = resources.filter { it.endsWith("-Regular.ttf") }
        val bolds = resources.filter { it.endsWith("-Bold.ttf") }
        assertEquals(11, regulars.size, "one regular cut per declared family")
        // Bold is optional per family, but a bold must never appear without its regular.
        for (bold in bolds) {
            val regular = bold.removeSuffix("-Bold.ttf") + "-Regular.ttf"
            assertTrue(regular in resources, "$bold has no matching regular cut")
        }
    }
}

/**
 * [HeicDecoder] shells out to `sips` on macOS and falls back to ImageIO elsewhere. Its contract is
 * "returns JPEG bytes, or null" — never an exception, because it runs while loading a user's
 * picture folder where a single bad file must not take the slideshow down.
 */
class HeicDecoderTest {

    @Test
    fun `a missing file yields null instead of throwing`() {
        assertNull(HeicDecoder.toJpegBytes(File("/no/such/path/photo.heic")))
    }

    @Test
    fun `a file that is not an image yields null`() {
        val notAnImage = Files.createTempFile("cp-heic-test", ".heic").toFile()
        try {
            notAnImage.writeText("this is plain text, not a HEIC container")
            assertNull(HeicDecoder.toJpegBytes(notAnImage))
        } finally {
            notAnImage.delete()
        }
    }

    @Test
    fun `an empty file yields null`() {
        val empty = Files.createTempFile("cp-heic-empty", ".heic").toFile()
        try {
            assertNull(HeicDecoder.toJpegBytes(empty))
        } finally {
            empty.delete()
        }
    }

    @Test
    fun `a directory passed instead of a file yields null`() {
        val dir = Files.createTempDirectory("cp-heic-dir").toFile()
        try {
            assertNull(HeicDecoder.toJpegBytes(dir))
        } finally {
            dir.delete()
        }
    }
}

/**
 * [DevFlags.forceDevWindow] gates the extra windowed presenter output. It is a `by lazy` read of an
 * env var and a system property, so only the default branch is observable once initialised — the
 * override paths are documented rather than asserted.
 */
class DevFlagsTest {

    @Test
    fun `the dev window is off unless explicitly forced`() {
        // Neither CHURCHPRESENTER_FORCE_DEV_WINDOW nor -Dchurchpresenter.forceDevWindow is set in
        // the test JVM, so this must be false. If it ever reads true here, the flag is leaking from
        // the environment into builds that should not have it.
        assertFalse(DevFlags.forceDevWindow)
    }
}

/**
 * [AutoStartManager] treats the OS registration as the source of truth. Under Gradle/IDE there is
 * no `jpackage.app-path`, so it must report unsupported and refuse to write anything — these tests
 * confirm the refusal, and deliberately never exercise the registering path, which would write a
 * real LaunchAgent plist / registry value / XDG entry on the developer's machine.
 */
class AutoStartManagerTest {

    @Test
    fun `autostart is unsupported when not running from an installed app`() {
        assertNull(System.getProperty("jpackage.app-path"), "test precondition: running from Gradle")
        assertFalse(AutoStartManager.isSupported)
    }

    @Test
    fun `enabling is refused when unsupported, rather than half-registering`() {
        assertFalse(AutoStartManager.setEnabled(true), "must report failure, not silently do nothing")
        assertFalse(AutoStartManager.setEnabled(false))
    }

    @Test
    fun `querying the current state never throws`() {
        // Reads a registry value on Windows and a file elsewhere; the test home has neither.
        assertFalse(AutoStartManager.isEnabled())
    }
}

/**
 * [ContactReporter]'s network path can't be exercised offline, but its request shape carries an
 * anti-abuse contract worth pinning down.
 */
class ContactReporterTest {

    @Test
    fun `the honeypot field defaults to empty`() {
        // The server rejects a non-empty `company`; a real client must never populate it, so the
        // default has to stay empty even as other fields are added.
        val request = ContactReporter.ContactRequest(type = "bug", name = "Sam", message = "hello")
        assertEquals("", request.company)
        assertEquals("", request.email, "email is optional")
        assertEquals("", request.context)
    }

    @Test
    fun `the rate-limit escalation target is a public https url`() {
        assertTrue(ContactReporter.WEB_CONTACT_URL.startsWith("https://"), "must not fall back to http")
        assertTrue(ContactReporter.WEB_CONTACT_URL.contains("churchpresenter.org"))
    }

    @Test
    fun `outcomes are distinguishable from one another`() {
        // These drive different UI branches (retry vs. open the browser vs. fix your input), so
        // they must not collapse onto one another via data-class equality.
        val outcomes = listOf(
            ContactReporter.Outcome.Success,
            ContactReporter.Outcome.RateLimited,
            ContactReporter.Outcome.NetworkError,
            ContactReporter.Outcome.Failure,
            ContactReporter.Outcome.Invalid(null),
        )
        assertEquals(outcomes.size, outcomes.toSet().size)
        assertTrue(ContactReporter.Outcome.Invalid("bad email") != ContactReporter.Outcome.Invalid(null))
    }
}

/**
 * Fixed constants that other parts of the system are pinned to. The ports in particular must stay
 * distinct: two features binding the same localhost port fail at runtime, on a user's machine, with
 * an error that looks unrelated to either.
 */
class ConstantsTest {

    @Test
    fun `the fixed localhost ports are distinct and in the valid range`() {
        val ports = mapOf(
            "single instance" to Constants.SINGLE_INSTANCE_PORT,
            "planning center oauth" to Constants.PLANNING_CENTER_OAUTH_PORT,
            "companion server" to Constants.SERVER_DEFAULT_PORT,
        )
        for ((name, port) in ports) {
            assertTrue(port in 1024..65535, "$name port $port is outside the usable range")
        }
        assertEquals(ports.size, ports.values.toSet().size, "ports collide: $ports")
    }

    @Test
    fun `the Planning Center oauth port matches its registered redirect uri`() {
        // PCO requires an exact pre-registered redirect URI, so this value cannot drift without
        // also being changed in the developer app's settings.
        assertEquals(47850, Constants.PLANNING_CENTER_OAUTH_PORT)
    }

    @Test
    fun `timer modes are distinct identifiers`() {
        val modes = listOf(
            Constants.TIMER_MODE_DURATION,
            Constants.TIMER_MODE_CLOCK,
            Constants.TIMER_MODE_COUNT_UP,
            Constants.TIMER_MODE_CLOCK_DISPLAY,
        )
        assertEquals(modes.size, modes.toSet().size, "duplicate mode ids would alias two behaviours")
        assertTrue(modes.none { it.isBlank() })
    }

    @Test
    fun `the media upload default is a sane size`() {
        assertTrue(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB > 0)
        assertNotNull(Constants.MEDIA_SEEK_MS)
        assertTrue(Constants.MEDIA_SEEK_MS > 0)
    }
}
