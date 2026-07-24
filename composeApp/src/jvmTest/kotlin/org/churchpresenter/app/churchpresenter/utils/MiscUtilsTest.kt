package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.alexzhirkevich.compottie.assets.LottieFontSpec
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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

    @Test
    fun `a bundled family loads real font bytes`() {
        val bytes = assertNotNull(LottieFonts.bundledFontBytes("Open Sans", wantBold = false))
        assertTrue(bytes.size > 1000, "a TTF should be more than a few bytes")
    }

    @Test
    fun `a bold request loads a different file than the regular one`() {
        val regular = assertNotNull(LottieFonts.bundledFontBytes("Open Sans", wantBold = false))
        val bold = assertNotNull(LottieFonts.bundledFontBytes("Open Sans", wantBold = true))
        assertFalse(regular.contentEquals(bold), "the bold cut must be a distinct file from the regular one")
    }

    @Test
    fun `a boldless family serves its regular bytes even when bold is asked for`() {
        val regular = assertNotNull(LottieFonts.bundledFontBytes("Patua One", wantBold = false))
        val boldRequest = assertNotNull(LottieFonts.bundledFontBytes("Patua One", wantBold = true))
        assertTrue(regular.contentEquals(boldRequest), "no bold file means the regular cut is served")
    }

    @Test
    fun `an unbundled family yields no bytes`() {
        assertNull(LottieFonts.bundledFontBytes("No Such Family", wantBold = false))
    }

    @Test
    fun `bold is wanted for heavy weights or an explicit -Bold name`() {
        assertTrue(LottieFonts.wantsBold(FontWeight.SemiBold, "Poppins"))
        assertTrue(LottieFonts.wantsBold(FontWeight.Bold, "Poppins"))
        assertTrue(LottieFonts.wantsBold(FontWeight.Normal, "Poppins-Bold"), "the -Bold suffix forces bold at any weight")
    }

    @Test
    fun `bold is not wanted for light weights without a -Bold name`() {
        assertFalse(LottieFonts.wantsBold(FontWeight.Normal, "Poppins"))
        assertFalse(LottieFonts.wantsBold(FontWeight.Light, "Lora"))
        assertFalse(LottieFonts.wantsBold(FontWeight.Medium, "Raleway"), "Medium is below SemiBold")
    }

    @Test
    fun `systemFontBytes yields null for a family no platform installs`() {
        assertNull(LottieFonts.systemFontBytes("No Such Family ZZZ", wantBold = false))
    }

    @Test
    fun `loadFont returns null when the family is neither bundled nor installed`() {
        assertNull(LottieFonts.loadFont("No Such Family ZZZ", wantBold = false, style = FontStyle.Normal))
    }

    @Test
    fun `loadFont builds a typeface for a bundled family`() {
        assertNotNull(LottieFonts.loadFont("Open Sans", wantBold = false, style = FontStyle.Normal))
        assertNotNull(LottieFonts.loadFont("Open Sans", wantBold = true, style = FontStyle.Italic))
    }

    private fun fontSpec(
        family: String,
        name: String = family,
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal,
    ): LottieFontSpec {
        val spec = mockk<LottieFontSpec>()
        every { spec.family } returns family
        every { spec.name } returns name
        every { spec.weight } returns weight
        every { spec.style } returns style
        return spec
    }

    @Test
    fun `font resolves a bundled family and caches the result`() = runBlocking {
        val first = assertNotNull(LottieFonts.font(fontSpec("Open Sans")))
        val second = LottieFonts.font(fontSpec("Open Sans"))
        assertSame(first, second, "a repeat lookup must return the cached typeface, not rebuild it")
    }

    @Test
    fun `font returns null for a family that is nowhere`() = runBlocking {
        assertNull(LottieFonts.font(fontSpec("No Such Family ZZZ")))
    }

    @Test
    fun `font takes the bold cut when the name ends in -Bold`() = runBlocking {
        assertNotNull(LottieFonts.font(fontSpec(family = "Open Sans", name = "Open Sans-Bold")))
        Unit
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

    // The ImageIO fallback is the non-macOS decode path, so `sips`-based toJpegBytes never reaches
    // it on the dev/CI mac; call it directly to exercise the real decode on any platform.

    @Test
    fun `the ImageIO fallback turns a readable image into valid JPEG bytes`() {
        val png = Files.createTempFile("cp-heic-io", ".png").toFile()
        try {
            ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", png)
            val jpeg = HeicDecoder.convertWithImageIO(png)
            assertNotNull(jpeg, "a readable image should convert")
            assertTrue(jpeg.isNotEmpty(), "converted JPEG must not be empty")
            assertNotNull(ImageIO.read(ByteArrayInputStream(jpeg)), "output must itself be a decodable image")
        } finally {
            png.delete()
        }
    }

    @Test
    fun `the ImageIO fallback yields null for a file it cannot decode`() {
        val notAnImage = Files.createTempFile("cp-heic-io-bad", ".png").toFile()
        try {
            notAnImage.writeText("not image bytes")
            assertNull(HeicDecoder.convertWithImageIO(notAnImage))
        } finally {
            notAnImage.delete()
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
 * [AutoStartManager] treats the OS registration as the source of truth. The platform decision, the
 * payload contents and the escaping are pure and tested directly; the file-based registration
 * (mac/linux) is exercised for real against the redirected test-home; the Windows registry path is
 * driven through an in-memory [AutoStartManager.WindowsRunKey] so its control flow is reachable off
 * Windows. Only the real registry binding itself stays uncovered — it can only run on Windows.
 */
class AutoStartManagerTest {

    private val macPlist = File(System.getProperty("user.home"), "Library/LaunchAgents/org.churchpresenter.app.plist")
    private val linuxDesktop = File(System.getProperty("user.home"), ".config/autostart/churchpresenter.desktop")

    private fun clean() {
        for (f in listOf(macPlist, linuxDesktop)) {
            if (f.isDirectory) f.deleteRecursively() else f.delete()
        }
    }

    @BeforeTest fun before() = clean()
    @AfterTest fun after() = clean()

    /** In-memory stand-in for the Windows `Run` key, so its branches run on any host. */
    private class FakeRunKey(var value: String? = null) : AutoStartManager.WindowsRunKey {
        override fun exists(): Boolean = value != null
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
        override fun delete() { value = null }
    }

    // ── unsupported (dev run) ────────────────────────────────────────────────────

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
        assertFalse(AutoStartManager.isEnabled())
    }

    @Test
    fun `syncRegistration is a no-op when unsupported`() {
        AutoStartManager.syncRegistration() // exePath is null under Gradle: returns without touching the OS
    }

    // ── platform decision & payloads (pure) ──────────────────────────────────────

    @Test
    fun `os name maps to the platform whose registration mechanism applies`() {
        assertEquals(AutoStartManager.Platform.WINDOWS, AutoStartManager.platformFor("Windows 11"))
        assertEquals(AutoStartManager.Platform.MAC, AutoStartManager.platformFor("Mac OS X"))
        assertEquals(AutoStartManager.Platform.LINUX, AutoStartManager.platformFor("Linux"))
        assertEquals(AutoStartManager.Platform.LINUX, AutoStartManager.platformFor("SunOS"), "unknown falls to XDG")
    }

    @Test
    fun `only windows lacks a backing file`() {
        assertNull(AutoStartManager.autostartFile(AutoStartManager.Platform.WINDOWS))
        assertTrue(AutoStartManager.autostartFile(AutoStartManager.Platform.MAC)!!.name.endsWith(".plist"))
        assertTrue(AutoStartManager.autostartFile(AutoStartManager.Platform.LINUX)!!.name.endsWith(".desktop"))
    }

    @Test
    fun `registration content is the payload for the platform`() {
        val exe = "/opt/CP/cp"
        assertEquals(AutoStartManager.windowsRunValue(exe), AutoStartManager.registrationContent(exe, AutoStartManager.Platform.WINDOWS))
        assertEquals(AutoStartManager.macPlistContent(exe), AutoStartManager.registrationContent(exe, AutoStartManager.Platform.MAC))
        assertEquals(AutoStartManager.linuxDesktopContent(exe), AutoStartManager.registrationContent(exe, AutoStartManager.Platform.LINUX))
    }

    @Test
    fun `the windows run value quotes the launcher path`() {
        assertEquals("\"C:\\Program Files\\CP\\CP.exe\"", AutoStartManager.windowsRunValue("C:\\Program Files\\CP\\CP.exe"))
    }

    @Test
    fun `xml escaping neutralises every markup character`() {
        assertEquals("&amp;", AutoStartManager.escapeXml("&"))
        assertEquals("&lt;", AutoStartManager.escapeXml("<"))
        assertEquals("&gt;", AutoStartManager.escapeXml(">"))
        assertEquals("&quot;", AutoStartManager.escapeXml("\""))
        assertEquals("&apos;", AutoStartManager.escapeXml("'"))
        // Ampersand must be escaped first, or the '&' introduced for '<' would itself be re-escaped.
        assertEquals("&amp;lt;", AutoStartManager.escapeXml("&lt;"))
    }

    @Test
    fun `exec escaping backslash-escapes each freedesktop reserved character`() {
        assertEquals("\\\\", AutoStartManager.escapeExec("\\"))
        assertEquals("\\\"", AutoStartManager.escapeExec("\""))
        assertEquals("\\\$", AutoStartManager.escapeExec("\$"))
        assertEquals("\\`", AutoStartManager.escapeExec("`"))
        // Backslash first, or the backslashes added for the others would themselves be doubled.
        assertEquals("\\\\\\\"", AutoStartManager.escapeExec("\\\""))
    }

    @Test
    fun `the mac plist embeds the launcher path xml-escaped and runs at load`() {
        val plist = AutoStartManager.macPlistContent("/Apps/Church & Co/CP")
        assertTrue(plist.startsWith("<?xml"))
        assertTrue("<key>RunAtLoad</key>" in plist)
        assertTrue("<true/>" in plist)
        assertTrue("Church &amp; Co" in plist, "an & in the path must be escaped")
        assertFalse("Church & Co" in plist, "no raw ampersand may reach the plist")
    }

    @Test
    fun `the linux desktop entry escapes the exec path and enables autostart`() {
        val entry = AutoStartManager.linuxDesktopContent("/opt/\$weird/cp")
        assertTrue("[Desktop Entry]" in entry)
        assertTrue("Type=Application" in entry)
        assertTrue("X-GNOME-Autostart-enabled=true" in entry)
        assertTrue("Exec=\"/opt/\\\$weird/cp\"" in entry, "a \$ in the path must be escaped inside Exec")
    }

    // ── file-based registration (mac/linux), against the redirected test-home ─────

    private fun fileFlowIsSourceOfTruth(platform: AutoStartManager.Platform, file: File) {
        val exe = "/opt/ChurchPresenter/bin/ChurchPresenter"
        assertFalse(AutoStartManager.isEnabledFor(platform), "starts unregistered")

        assertTrue(AutoStartManager.setEnabledFor(exe, platform, enabled = true), "register reports success")
        assertTrue(file.exists(), "registration writes the backing file")
        assertEquals(AutoStartManager.registrationContent(exe, platform), file.readText(), "writes the exact payload")
        assertTrue(AutoStartManager.isEnabledFor(platform), "now reported enabled")

        assertTrue(AutoStartManager.setEnabledFor(exe, platform, enabled = false), "unregister reports success")
        assertFalse(file.exists(), "unregister removes the file")
        assertFalse(AutoStartManager.isEnabledFor(platform))
    }

    @Test
    fun `the mac plist is the source of truth across register and unregister`() {
        fileFlowIsSourceOfTruth(AutoStartManager.Platform.MAC, macPlist)
    }

    @Test
    fun `the linux desktop entry is the source of truth across register and unregister`() {
        fileFlowIsSourceOfTruth(AutoStartManager.Platform.LINUX, linuxDesktop)
    }

    @Test
    fun `sync does nothing when autostart is not enabled`() {
        AutoStartManager.syncRegistrationFor("/opt/CP/cp", AutoStartManager.Platform.LINUX)
        assertFalse(linuxDesktop.exists(), "an unregistered app must not be registered by a sync")
    }

    @Test
    fun `sync leaves a matching registration untouched`() {
        val exe = "/opt/CP/cp"
        AutoStartManager.setEnabledFor(exe, AutoStartManager.Platform.MAC, enabled = true)
        val before = macPlist.readText()
        AutoStartManager.syncRegistrationFor(exe, AutoStartManager.Platform.MAC)
        assertEquals(before, macPlist.readText(), "matching content must not be rewritten")
    }

    @Test
    fun `sync rewrites a stale registration to the new launcher path`() {
        val mac = AutoStartManager.Platform.MAC
        AutoStartManager.setEnabledFor("/old/location/cp", mac, enabled = true)
        AutoStartManager.syncRegistrationFor("/new/location/cp", mac)
        assertEquals(AutoStartManager.registrationContent("/new/location/cp", mac), macPlist.readText())
    }

    @Test
    fun `a failed registration is swallowed into a false result, never thrown`() {
        // A directory sitting at the plist's path makes writeText fail: register throws, the failure
        // is reported and turns into false (the toggle stays off) instead of propagating to the UI.
        macPlist.parentFile?.mkdirs()
        macPlist.mkdir()
        assertFalse(AutoStartManager.setEnabledFor("/opt/CP/cp", AutoStartManager.Platform.MAC, enabled = true))
    }

    @Test
    fun `sync survives an unreadable registration without throwing`() {
        // A directory at the plist path "exists" (so sync proceeds) but can neither be read nor
        // rewritten; both failures must be swallowed rather than propagated to startup.
        macPlist.parentFile?.mkdirs()
        macPlist.mkdir()
        AutoStartManager.syncRegistrationFor("/opt/CP/cp", AutoStartManager.Platform.MAC)
    }

    // ── windows registration, driven through the in-memory run key ────────────────

    @Test
    fun `on windows the registry value is the source of truth across register and unregister`() {
        val key = FakeRunKey()
        val exe = "C:\\Program Files\\CP\\CP.exe"
        val win = AutoStartManager.Platform.WINDOWS

        assertFalse(AutoStartManager.isEnabledFor(win, key))
        assertTrue(AutoStartManager.setEnabledFor(exe, win, enabled = true, runKey = key))
        assertEquals(AutoStartManager.windowsRunValue(exe), key.value, "writes the quoted launcher path")
        assertTrue(AutoStartManager.isEnabledFor(win, key))

        assertTrue(AutoStartManager.setEnabledFor(exe, win, enabled = false, runKey = key))
        assertNull(key.value, "unregister clears the value")
        assertFalse(AutoStartManager.isEnabledFor(win, key))
    }

    @Test
    fun `on windows unregister is a no-op when nothing is registered`() {
        val key = FakeRunKey()
        assertTrue(AutoStartManager.setEnabledFor("C:\\CP.exe", AutoStartManager.Platform.WINDOWS, enabled = false, runKey = key))
        assertNull(key.value)
    }

    @Test
    fun `on windows sync rewrites a stale registry value but leaves a matching one`() {
        val key = FakeRunKey()
        val win = AutoStartManager.Platform.WINDOWS
        AutoStartManager.setEnabledFor("C:\\old\\CP.exe", win, enabled = true, runKey = key)

        val matching = key.value
        AutoStartManager.syncRegistrationFor("C:\\old\\CP.exe", win, key)
        assertEquals(matching, key.value, "matching value must not be rewritten")

        AutoStartManager.syncRegistrationFor("C:\\new\\CP.exe", win, key)
        assertEquals(AutoStartManager.windowsRunValue("C:\\new\\CP.exe"), key.value, "stale value re-registered")
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

    // ── The status mapping in submit ────────────────────────────────────────────
    // This is the part of submit that decides the user's next step; the socket around it can't be
    // exercised offline, but the decision itself is pure once the status and body are in hand.

    @Test
    fun `a 200 succeeds and a 429 is a rate limit`() {
        assertEquals(ContactReporter.Outcome.Success, ContactReporter.classifyStatus(200))
        // 429 falls inside the 4xx range but must be caught first — it escalates to the web form,
        // it is not a validation error to be explained from the body.
        assertEquals(ContactReporter.Outcome.RateLimited, ContactReporter.classifyStatus(429))
    }

    @Test
    fun `any other 4xx defers to the body for its reason`() {
        // null is the signal to submit that the outcome isn't terminal yet — read the body to learn
        // why the request was rejected. Boundaries of the range plus one inside.
        assertNull(ContactReporter.classifyStatus(400))
        assertNull(ContactReporter.classifyStatus(422))
        assertNull(ContactReporter.classifyStatus(499))
    }

    @Test
    fun `a 5xx or unexpected status is a retryable failure`() {
        assertEquals(ContactReporter.Outcome.Failure, ContactReporter.classifyStatus(500))
        assertEquals(ContactReporter.Outcome.Failure, ContactReporter.classifyStatus(503))
        // Anything outside the mapped cases is treated as a transient failure, not a success.
        assertEquals(ContactReporter.Outcome.Failure, ContactReporter.classifyStatus(302))
    }

    @Test
    fun `the validation reason is lifted out of the server's error body`() {
        assertEquals(
            "email is invalid",
            ContactReporter.parseErrorMessage("""{"error":"email is invalid"}"""),
        )
    }

    @Test
    fun `a body with no usable reason yields null rather than throwing`() {
        // Each of these reaches Outcome.Invalid(null) in submit — a rejection the UI states plainly
        // without a server message. None may surface as a thrown exception.
        assertNull(ContactReporter.parseErrorMessage("{}"), "field absent")
        assertNull(ContactReporter.parseErrorMessage("not json at all"), "unparseable body")
        assertNull(ContactReporter.parseErrorMessage(""), "empty body")
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
