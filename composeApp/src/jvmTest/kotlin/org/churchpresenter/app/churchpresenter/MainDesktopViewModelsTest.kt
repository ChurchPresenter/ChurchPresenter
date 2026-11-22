package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.bibletab.ContinuationSpeed
import org.churchpresenter.bibletab.TextMatchLevel
import org.churchpresenter.bibletab.bibleFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.SongSettings
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainDesktopViewModelsTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        dir = Files.createTempDirectory("cp-main-desktop-vms").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `a loaded Bible reaches the publishers and the link, through their latest values`() {
        SpbFixture.spbFile(dir, "primary.spb", content = bibleFixture)
        SpbFixture.spbFile(dir, "secondary.spb", content = bibleFixture)
        val settings = AppSettings(
            songSettings = SongSettings(storageDirectory = dir.absolutePath),
            bibleSettings = BibleSettings(
                storageDirectory = dir.absolutePath,
                primaryBible = "primary.spb",
                secondaryBible = "secondary.spb",
                translations = listOf(
                    BibleTranslationSettings(fileName = "primary.spb"),
                    BibleTranslationSettings(fileName = "secondary.spb"),
                ),
            ),
        )
        val loaded = CopyOnWriteArrayList<String>()
        val paths = CopyOnWriteArrayList<List<String>>()
        val secondary = CopyOnWriteArrayList<String>()
        val vms = MainDesktopViewModels(
            settings,
            publish = mutableStateOf(
                MainDesktopPublishers(onBibleLoaded = { _, translation -> loaded += translation }),
            ),
            link = mutableStateOf(
                InstanceLinkBridge(
                    onSecondaryBibleFilePathChanged = { secondary += it },
                    onBibleFilePathsChanged = { paths += it },
                ),
            ),
            settings = mutableStateOf(settings),
        )
        try {
            waitFor("the Bible to load") { loaded.isNotEmpty() && paths.isNotEmpty() && secondary.isNotEmpty() }
            assertEquals("primary.spb", loaded.first())
            assertTrue(paths.first().any { it.endsWith("primary.spb") })
            assertTrue(secondary.first().endsWith("secondary.spb"))

            vms.bibleViewModel.setTextMatchLevel(TextMatchLevel.AGGRESSIVE)
            vms.bibleViewModel.setContinuationSpeed(ContinuationSpeed.FAST)
            assertEquals(TextMatchLevel.AGGRESSIVE, vms.bibleViewModel.textMatchLevel.value)
        } finally {
            vms.dispose()
        }
    }
}
