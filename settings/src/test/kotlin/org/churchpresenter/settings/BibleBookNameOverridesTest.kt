package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BibleBookNameOverridesTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-book-names-settings").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val psalms = mapOf(19 to "Псалмы")

    private fun stack(overrides: Map<Int, String> = psalms) =
        BibleSettings(storageDirectory = "/bibles").withTranslations(
            listOf(
                BibleTranslationSettings(fileName = "rst.spb", bookNameOverrides = overrides),
                BibleTranslationSettings(fileName = "kjv.spb"),
            ),
        )

    @Test
    fun `each translation answers with its own names`() {
        assertEquals(psalms, stack().bookNameOverridesOf("rst.spb"))
        assertEquals(emptyMap(), stack().bookNameOverridesOf("kjv.spb"))
        assertEquals(emptyMap(), stack().bookNameOverridesOf("niv.spb"))
    }

    @Test
    fun `changing a name changes the key a caller watches`() {
        assertNotEquals(stack().customNameKey(), stack(mapOf(19 to "Псалтирь")).customNameKey())
        assertEquals(stack().customNameKey(), stack().customNameKey())
    }

    @Test
    fun `a save and a load bring the names back`() {
        val manager = SettingsManager()

        manager.saveSettings(AppSettings(bibleSettings = stack()))

        assertEquals(psalms, SettingsManager().loadSettings().bibleSettings.bookNameOverridesOf("rst.spb"))
    }

    @Test
    fun `a profile's copy of a translation cannot carry names of its own`() {
        val styled = Json.encodeToJsonElement(stack(mapOf(19 to "From a profile"))).jsonObject

        val resolved = stack().withSparseBibleOverride(styled)

        assertEquals(psalms, resolved.bookNameOverridesOf("rst.spb"))
    }

    @Test
    fun `a profile's baseline keeps the names`() {
        val baseline = OutputProfile(id = "a", bibleSettings = stack()).defaultBaseline()

        assertEquals(psalms, baseline.bibleSettings.bookNameOverridesOf("rst.spb"))
    }

    @Test
    fun `names are not a style a translation owns`() {
        val plain = BibleTranslationSettings(fileName = "rst.spb")

        assertEquals(emptySet(), styleFieldsChanged(plain, plain.copy(bookNameOverrides = psalms)))
    }

    @Test
    fun `the All layer holds no names and an edit under it leaves them alone`() {
        val seeded = stack().migrateAllLayer()

        assertEquals(emptyMap(), seeded.allTranslationStyle?.bookNameOverrides)
        val edited = seeded.updateAllLayer { it.copy(textFontSize = 12) }
        assertEquals(psalms, edited.bookNameOverridesOf("rst.spb"))
        assertTrue(edited.translationList().all { it.textFontSize == 12 })
    }
}
