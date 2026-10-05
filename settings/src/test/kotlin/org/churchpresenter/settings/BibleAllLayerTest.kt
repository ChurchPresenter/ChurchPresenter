package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Bible stack's All layer: one look every translation follows except where it has its own.
 *
 * `KJV 50 with All at 70` is the case the Profiles tab exists for -- KJV keeps its 50 through any
 * later change to All, and handing its size back puts All's value on it.
 */
class BibleAllLayerTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-all-layer-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val stack = BibleSettings(
        translations = listOf(
            BibleTranslationSettings(fileName = "kjv.spb", textFontSize = 70),
            BibleTranslationSettings(fileName = "rst.spb", textFontSize = 70),
        ),
    )

    private fun BibleSettings.sizes() = translationList().map { it.textFontSize }

    @Test
    fun `All reaches every translation, except a field one has set for itself`() {
        val kjvOwn = stack.updateOwnStyle(0) { it.copy(textFontSize = 50) }
        assertEquals(setOf("textFontSize"), kjvOwn.translationList()[0].ownStyleKeys)
        val allAt80 = kjvOwn.updateAllLayer { it.copy(textFontSize = 80, textColor = "#FF0000") }
        assertEquals(listOf(50, 80), allAt80.sizes())
        assertEquals(listOf("#FF0000", "#FF0000"), allAt80.translationList().map { it.textColor })
        assertEquals(80, allAt80.allStyle().textFontSize)
        assertEquals("", allAt80.allTranslationStyle?.fileName, "the layer belongs to no translation")
    }

    @Test
    fun `clearing a translation's own value puts All's back on it`() {
        val kjvOwn = stack.updateOwnStyle(0) { it.copy(textFontSize = 50) }
            .updateAllLayer { it.copy(textFontSize = 80) }
        val cleared = kjvOwn.clearOwnStyle(0, listOf("textFontSize"))
        assertEquals(listOf(80, 80), cleared.sizes())
        assertTrue(cleared.translationList()[0].ownStyleKeys.isEmpty())
    }

    @Test
    fun `without a layer yet, All is the first translation`() {
        assertNull(stack.allTranslationStyle)
        assertEquals(70, stack.allStyle().textFontSize)
        assertEquals(BibleTranslationSettings(), BibleSettings().allStyle())
    }

    @Test
    fun `an old stack is seeded so nothing drawn changes`() {
        val old = stack.copy(
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb", textFontSize = 70),
                BibleTranslationSettings(fileName = "rst.spb", textFontSize = 40, textColor = "#00FF00"),
            ),
        )
        val seeded = old.migrateAllLayer()
        assertEquals(70, seeded.allStyle().textFontSize)
        assertEquals(setOf("textFontSize", "textColor"), seeded.translationList()[1].ownStyleKeys)
        assertTrue(seeded.translationList()[0].ownStyleKeys.isEmpty())
        assertEquals(listOf(70, 40), seeded.sizes())
        assertSame(seeded, seeded.migrateAllLayer(), "a seeded stack is left alone")
        val empty = BibleSettings()
        assertSame(empty, empty.migrateAllLayer(), "an empty stack has nothing to seed")
    }

    @Test
    fun `copying named fields leaves the rest, and changed fields leave out identity`() {
        val from = BibleTranslationSettings(fileName = "x", textFontSize = 12, textColor = "#111111")
        val to = BibleTranslationSettings(fileName = "y")
        val copied = to.withFieldsFrom(from, listOf("textFontSize"))
        assertEquals(12, copied.textFontSize)
        assertEquals("y", copied.fileName)
        assertEquals(to.textColor, copied.textColor)
        assertSame(to, to.withFieldsFrom(from, emptyList()))
        assertEquals(setOf("textFontSize", "textColor"), styleFieldsChanged(to, from))
    }

    @Test
    fun `loading a document seeds the layer and resolves the links`() {
        val raw = """
            {"settingsVersion":${AppSettings.CURRENT_SETTINGS_VERSION},
             "bibleSettings":{"translations":[{"fileName":"kjv.spb","textFontSize":70},{"fileName":"rst.spb","textFontSize":40}]},
             "projectionSettings":{"outputProfiles":[
                {"id":"youth","parentId":"main","overrides":["look.slide.qa"],"look":{"slide":{"qa":false}}},
                {"id":"main","look":{"media":{"video":false}}}
             ]}}
        """.trimIndent()
        val loaded = SettingsManager().migrateAndDecode(raw)
        assertEquals(setOf("textFontSize"), loaded.bibleSettings.translationList()[1].ownStyleKeys)
        val profiles = loaded.projectionSettings.outputProfiles
        assertEquals(listOf("main", "youth"), profiles.map { it.id })
        assertEquals(false, profiles[1].look.media.video, "the follower takes its master's value")
        assertEquals(false, profiles[1].look.slide.qa)
    }
}
