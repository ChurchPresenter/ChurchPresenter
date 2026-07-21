package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The per-platform choosers, minus the dialogs themselves.
 *
 * `SwingFileChooser` and `FileKitFileChooser` are mostly calls into a toolkit that needs a display,
 * but each carries a piece of ordinary logic that decides what the dialog is given and what comes
 * back out of it. Those pieces are private, so they are reached by reflection — the same approach
 * [org.churchpresenter.app.churchpresenter.utils.CrashReporterTest] uses for `scrubPii`. Touching
 * these objects does not build any window: the Swing owner frame is created lazily, on first use of
 * a real dialog.
 *
 * [FileChooserTest] covers the shared base class, which is where most of the behaviour lives.
 */
class PlatformFileChooserTest {

    private lateinit var dir: File
    private var realResourcesDir: String? = null
    private var realUserDir: String? = null

    @BeforeTest
    fun isolateLookupPaths() {
        dir = Files.createTempDirectory("cp-platform-chooser-test").toFile()
        realResourcesDir = System.getProperty("compose.application.resources.dir")
        realUserDir = System.getProperty("user.dir")
        System.clearProperty("compose.application.resources.dir")
        // The source-tree fallback walks up from user.dir; pointed at a temp folder it finds
        // nothing, so tests decide for themselves what is discoverable.
        System.setProperty("user.dir", dir.absolutePath)
    }

    @AfterTest
    fun restoreLookupPaths() {
        realResourcesDir?.let { System.setProperty("compose.application.resources.dir", it) }
            ?: System.clearProperty("compose.application.resources.dir")
        realUserDir?.let { System.setProperty("user.dir", it) }
        dir.deleteRecursively()
    }

    /** Writes a real square PNG whose width identifies which file was loaded. */
    private fun icon(name: String, size: Int, into: File = dir) {
        into.mkdirs()
        ImageIO.write(BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB), "png", File(into, name))
    }

    // ── SwingFileChooser: finding the app icon ──────────────────────────────────

    private fun loadAppIcon(): Image? =
        SwingFileChooser::class.java
            .getDeclaredMethod("loadAppIcon")
            .apply { isAccessible = true }
            .invoke(SwingFileChooser) as Image?

    @Test
    fun `a packaged app takes its icon from the resources directory`() {
        val resources = File(dir, "app-resources")
        icon("icon-32.png", 32, into = resources)
        System.setProperty("compose.application.resources.dir", resources.absolutePath)

        assertEquals(32, loadAppIcon()?.getWidth(null))
    }

    @Test
    fun `the smallest icon is preferred when several are packaged`() {
        val resources = File(dir, "app-resources")
        icon("icon-32.png", 32, into = resources)
        icon("icon-48.png", 48, into = resources)
        icon("icon.png", 64, into = resources)
        System.setProperty("compose.application.resources.dir", resources.absolutePath)

        assertEquals(
            32,
            loadAppIcon()?.getWidth(null),
            "the dialog's title bar wants the small one; the others are fallbacks",
        )
    }

    @Test
    fun `the next size is used when the smallest is missing`() {
        val resources = File(dir, "app-resources")
        icon("icon-48.png", 48, into = resources)
        icon("icon.png", 64, into = resources)
        System.setProperty("compose.application.resources.dir", resources.absolutePath)

        assertEquals(48, loadAppIcon()?.getWidth(null))
    }

    @Test
    fun `a development run finds the icon in the source tree`() {
        // The walk climbs up to six levels from the working directory looking for the repo layout.
        val workingDir = File(dir, "some/nested/module").also { it.mkdirs() }
        icon("icon-32.png", 32, into = File(dir, "composeApp/src/jvmMain/appResources/common"))
        System.setProperty("user.dir", workingDir.absolutePath)

        assertEquals(32, loadAppIcon()?.getWidth(null), "running from the IDE must still get an icon")
    }

    @Test
    fun `no icon anywhere is not an error`() {
        assertNull(loadAppIcon(), "a missing icon means a default frame icon, not a broken dialog")
    }

    @Test
    fun `a resources directory that does not exist falls through to the source tree`() {
        System.setProperty("compose.application.resources.dir", File(dir, "not-created").absolutePath)
        icon("icon-32.png", 32, into = File(dir, "composeApp/src/jvmMain/appResources/common"))

        assertEquals(32, loadAppIcon()?.getWidth(null))
    }

    // ── FileKitFileChooser: what the native dialog is told ──────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun allExtensions(filters: List<FileNameExtensionFilter>): List<String> =
        FileKitFileChooser::class.java
            .getDeclaredMethod("allExtensions", List::class.java)
            .apply { isAccessible = true }
            .invoke(FileKitFileChooser, filters) as List<String>

    @Test
    fun `filters are flattened into one extension list`() {
        // The native dialogs take a single combined filter rather than a list of them.
        val extensions = allExtensions(
            listOf(
                FileNameExtensionFilter("PowerPoint", "pptx", "ppt"),
                FileNameExtensionFilter("PDF", "pdf"),
            )
        )

        assertEquals(listOf("pptx", "ppt", "pdf"), extensions)
    }

    @Test
    fun `an extension offered by two filters is listed once`() {
        val extensions = allExtensions(
            listOf(
                FileNameExtensionFilter("Presentations", "pptx", "pdf"),
                FileNameExtensionFilter("Documents", "pdf", "docx"),
            )
        )

        assertEquals(listOf("pptx", "pdf", "docx"), extensions, "a repeated extension would be offered twice")
    }

    @Test
    fun `no filters means no extensions`() {
        assertEquals(emptyList<String>(), allExtensions(emptyList()))
    }

    // ── FileKitFileChooser: what comes back out of it ───────────────────────────

    private fun toPathOrNull(file: File): Any? =
        FileKitFileChooser::class.java
            .getDeclaredMethod("toPathOrNull", File::class.java)
            .apply { isAccessible = true }
            .invoke(FileKitFileChooser, file)

    @Test
    fun `a real selection converts to a path`() {
        val picked = File(dir, "schedule.cps")

        assertNotNull(toPathOrNull(picked))
    }

    @Test
    fun `a selection with no filesystem path is treated as no selection`() {
        // Windows hands back virtual shell items (the "This PC" node and friends) that have no real
        // path, and converting them throws. The guard must turn that into "nothing was picked"
        // rather than a crash that also latches the native dialogs off for the rest of the session.
        // A NUL byte is the portable way to make the same conversion fail.
        val notARealPath = File("bad\u0000name")

        assertNull(toPathOrNull(notARealPath))
    }
}
