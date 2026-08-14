@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.tabs.PictureLabel
import org.churchpresenter.app.churchpresenter.tabs.RecentPictureFolders
import org.churchpresenter.app.churchpresenter.tabs.openAnimationDropdown
import org.churchpresenter.app.churchpresenter.tabs.openIntervalEditor
import org.churchpresenter.app.churchpresenter.tabs.openTransitionEditor
import org.churchpresenter.app.churchpresenter.tabs.pictureButton
import org.churchpresenter.app.churchpresenter.tabs.picturesTab
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PicturesTabScreenshotTest {

    private fun writePhoto(dir: File, name: String, index: Int) {
        val image = BufferedImage(480, 300, BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        val hue = (index * 0.14f) % 1f
        canvas.paint = GradientPaint(
            0f, 0f, Color.getHSBColor(hue, 0.5f, 0.9f),
            480f, 300f, Color.getHSBColor((hue + 0.09f) % 1f, 0.85f, 0.4f),
        )
        canvas.fillRect(0, 0, 480, 300)
        canvas.dispose()
        ImageIO.write(image, name.substringAfterLast('.'), File(dir, name))
    }

    // Fixed path, not a temp dir: the tab prints the folder's absolute path in every shot, so a
    // random name would make each recording a diff.
    private fun folderOf(folderName: String, vararg names: String): File {
        val dir = File(FIXTURES, folderName).absoluteFile
        dir.deleteRecursively()
        dir.mkdirs()
        names.forEachIndexed { index, name -> writePhoto(dir, name, index) }
        return dir
    }

    private fun gallery() = folderOf("Sunday Service", *GALLERY)

    private fun shoot(
        name: String,
        folder: () -> File? = { gallery() },
        settings: (AppSettings) -> AppSettings = { it },
        presenter: Boolean = true,
        width: Dp? = null,
        rootIndex: Int = 0,
        drive: ComposeUiTest.(PicturesViewModel) -> Unit = { awaitAll(it) },
    ) = stackedThemes(SECTION, name) { mode, file ->
        picturesTab(
            folder = folder(),
            settings = settings,
            presenterManager = if (presenter) PresenterManager() else null,
            width = width,
            themeMode = mode,
        ) { vm, _ ->
            drive(vm)
            captureTo(file, rootIndex)
        }
    }

    /**
     * Waits for every thumbnail to have resolved, one way or the other.
     *
     * The condition is deliberately **not** "the Loading… label is gone". That is the absence of a
     * label, which a decode that failed satisfies never rather than late: the view model used to
     * swallow the exception, so the tile kept saying "Loading…" for the rest of the run and this
     * wait could only end by spending its whole budget. That is what took `main` red on CI run
     * 31232339922 — 30s exhausted on five 480x300 gradients, which was never a decode speed problem.
     *
     * Now each file lands in either `thumbnails` or `thumbnailFailures`, so this ends on a positive
     * signal, and a failure is reported immediately, naming the file and the reason, instead of
     * thirty seconds later naming nothing.
     */
    private fun ComposeUiTest.awaitAll(vm: PicturesViewModel) {
        if (vm.images.isEmpty()) return
        waitUntil("every thumbnail to finish decoding", RENDER_TIMEOUT_MS) {
            // Thumbnails are decoded on Dispatchers.IO and written into a SnapshotStateMap from
            // there. Without this the write can sit in the global snapshot unapplied while this
            // loop spins, and the placeholder never goes away.
            Snapshot.sendApplyNotifications()
            vm.images.all { it in vm.thumbnails || it in vm.thumbnailFailures }
        }
        assertTrue(
            vm.thumbnailFailures.isEmpty(),
            "every fixture is a gradient this test just wrote, so none of them should fail to " +
                "decode: ${vm.thumbnailFailures}"
        )
        waitForIdle()
    }

    private lateinit var savedFolders: List<String>
    private lateinit var savedPinned: List<String>

    @BeforeTest
    fun snapshotRecents() {
        savedFolders = RecentPictureFolders.folders.toList()
        savedPinned = RecentPictureFolders.pinned.toList()
        RecentPictureFolders.folders.clear()
        RecentPictureFolders.pinned.clear()
    }

    @AfterTest
    fun restoreRecents() {
        RecentPictureFolders.folders.clear()
        RecentPictureFolders.folders.addAll(savedFolders)
        RecentPictureFolders.pinned.clear()
        RecentPictureFolders.pinned.addAll(savedPinned)
    }

    @Test
    fun `no folder chosen yet`() = shoot("no_folder", folder = { null })

    @Test
    fun browsing() = shoot("browsing")

    @Test
    fun `a single image folder`() =
        shoot("single_image", folder = { folderOf("Notices", "Announcement.png") })

    @Test
    fun `a folder deep enough to fill the grid`() =
        shoot("many_images", folder = { folderOf("Slide Wall", *BIG_GALLERY) })

    @Test
    fun `a later image selected`() = shoot("image_selected") { vm ->
        awaitAll(vm)
        vm.selectImage(3)
        waitForIdle()
    }

    @Test
    fun `the slideshow running`() = shoot("playing") { vm ->
        awaitAll(vm)
        pictureButton(PictureLabel.PLAY).performClick()
        waitForIdle()
    }

    @Test
    fun `looping turned off`() = shoot(
        "loop_off",
        settings = { it.copy(pictureSettings = it.pictureSettings.copy(isLooping = false)) },
    )

    @Test
    fun `with no output to go live on the button is gone`() = shoot("no_presenter", presenter = false)

    @Test
    fun `an empty folder disables every action`() = shoot("actions_disabled", folder = { folderOf("Empty Folder") })

    @Test
    fun `settings tiles carrying non-default values`() = shoot(
        "settings_customised",
        settings = {
            it.copy(
                pictureSettings = it.pictureSettings.copy(
                    autoScrollInterval = 12f,
                    transitionDuration = 1200f,
                    animationType = Constants.ANIMATION_SLIDE_LEFT,
                )
            )
        },
    )

    @Test
    fun `the animation picker`() = shoot("animation_picker", rootIndex = 1) { vm ->
        awaitAll(vm)
        openAnimationDropdown()
    }

    /**
     * Both editors focus a text field, and a focused field draws a *blinking* caret — so whether
     * the caret is up or down when the frame is taken depends on how the run happened to be timed.
     * `interval_editor` had already started failing that way: it passes on its own and differs
     * inside a full-suite run, where warm-up shifts the capture into the other half of the blink.
     *
     * Freezing the clock pins the phase, so every capture sees the same frame.
     */
    @Test
    fun `the auto-scroll interval editor`() = shoot("interval_editor", rootIndex = 1) { vm ->
        awaitAll(vm)
        openIntervalEditor()
        freezeCaret()
    }

    @Test
    fun `the transition duration editor`() = shoot("transition_editor", rootIndex = 1) { vm ->
        awaitAll(vm)
        openTransitionEditor()
        freezeCaret()
    }

    private fun ComposeUiTest.freezeCaret() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the recent folders bar, with the open folder marked`() = shoot(
        "recent_folders",
        folder = {
            gallery().also { open ->
                RecentPictureFolders.folders.clear()
                RecentPictureFolders.pinned.clear()
                RecentPictureFolders.folders.addAll(
                    listOf(
                        open.absolutePath,
                        "/Volumes/Services/photos/Baptism",
                        "/Volumes/Services/photos/Youth Camp"
                    ),
                )
                RecentPictureFolders.pinned.add("/Volumes/Services/photos/Every Week")
            }
        },
    )

    @Test
    fun `a narrow panel wraps the controls and the grid`() = shoot("narrow_panel", width = 420.dp)

    @Test
    fun `a half-width panel`() = shoot("medium_panel", width = 760.dp)

    private companion object {
        const val SECTION = "picturesTab"

        /**
         * A neutral root, not a repo-relative `build/` one.
         *
         * The path is printed into the image, and a repo-relative fixture resolves through the
         * developer's home directory — which on most machines is their name, committed into the PNG
         * for ever. `/tmp` where there is one, the JVM's temp directory otherwise (Windows).
         */
        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/pictures") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/pictures")

        val GALLERY = arrayOf(
            "01 Welcome.png",
            "02 Sunrise.jpg",
            "03 Baptism.png",
            "04 Youth Camp.jpg",
            "05 Choir.png",
            "06 Missions.jpg",
        )

        val BIG_GALLERY = Array(14) { "%02d Slide %d.png".format(it + 1, it + 1) }
    }
}
