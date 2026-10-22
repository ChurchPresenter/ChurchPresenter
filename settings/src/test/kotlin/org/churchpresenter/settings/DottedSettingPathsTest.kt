package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Paths to a value whose key holds a dot or a bracket: a Bible box is kept under the translation's
 * file name -- `REFERENCE#kjv.spb` -- and a translation's file name may hold a bracket. A linked
 * profile's own value there must stay its own, and version 24 renames the paths older builds wrote.
 */
class DottedSettingPathsTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-dotted-paths-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val box = TextBox(enabled = true, xPercent = 10f, yPercent = 2f, widthPercent = 50f, heightPercent = 10f)

    private fun bible(y: Float = 2f, fileName: String = KJV) = BibleSettings(
        translations = listOf(BibleTranslationSettings(fileName = fileName)),
        textBoxes = mapOf(bibleBoxKey(fileName) to box.copy(yPercent = y)),
    )

    private fun linked(fileName: String = KJV) = ProjectionSettings(
        outputProfiles = listOf(
            OutputProfile(id = "main", bibleSettings = bible(fileName = fileName)),
            OutputProfile(id = "social", parentId = "main", bibleSettings = bible(fileName = fileName)),
        ),
    ).withLinksResolved()

    private fun ProjectionSettings.p(id: String) = outputProfiles.first { it.id == id }

    private fun ProjectionSettings.moveBox(id: String, y: Float, fileName: String = KJV) = editProfile(id) { p ->
        val key = bibleBoxKey(fileName)
        p.copy(bibleSettings = p.bibleSettings.copy(textBoxes = mapOf(key to box.copy(yPercent = y))))
    }

    private fun OutputProfile.boxY(fileName: String = KJV) =
        bibleSettings.textBoxes.getValue(bibleBoxKey(fileName)).yPercent

    @Test
    fun `a key with a dot in it is named in brackets`() {
        val paths = OutputProfile(bibleSettings = bible()).settingPaths()
        assertTrue("bibleSettings.textBoxes[REFERENCE#kjv.spb].yPercent" in paths)
        assertFalse(paths.keys.any { it.startsWith("bibleSettings.textBoxes.REFERENCE#kjv") })
    }

    @Test
    fun `a linked profile keeps its own box through its master's edits`() {
        val moved = linked().moveBox("social", y = 40f)
        assertEquals(setOf("bibleSettings.textBoxes[REFERENCE#kjv.spb].yPercent"), moved.p("social").overrides)
        assertEquals(40f, moved.p("social").boxY())

        val masterMoved = moved.moveBox("main", y = 5f).withLinksResolved()
        assertEquals(40f, masterMoved.p("social").boxY(), "its own")
        assertEquals(5f, masterMoved.p("main").boxY())
        val boxes = masterMoved.p("social").bibleSettings.textBoxes.keys
        assertEquals(setOf(bibleBoxKey(KJV)), boxes, "no box made under a cut-short key")
    }

    @Test
    fun `a box the follower never moved follows its master`() {
        val masterMoved = linked().moveBox("main", y = 5f)
        assertEquals(5f, masterMoved.p("social").boxY())
        assertTrue(masterMoved.p("social").overrides.isEmpty())
    }

    @Test
    fun `a file name with brackets in it is escaped and read back`() {
        val name = "Bible [KJV].spb"
        val moved = linked(name).moveBox("social", y = 40f, fileName = name)
        assertEquals(
            setOf("bibleSettings.textBoxes[REFERENCE#Bible [KJV\\].spb].yPercent"),
            moved.p("social").overrides,
        )
        val masterMoved = moved.moveBox("main", y = 5f, fileName = name)
        assertEquals(40f, masterMoved.p("social").boxY(name))

        val translationPath = "bibleSettings.translations[Bible [KJV\\].spb].textFontSize"
        val sized = masterMoved.p("social").withValueAt(translationPath, JsonPrimitive(99))
        assertEquals(99, sized.bibleSettings.translations.single().textFontSize)
    }

    @Test
    fun `a box takes its default back by being taken out`() {
        val profile = OutputProfile(bibleSettings = bible())
        val path = "bibleSettings.textBoxes[REFERENCE#kjv.spb].yPercent"
        assertTrue(path in defaultChanges(profile))
        assertTrue(profile.withDefaultAt(path).bibleSettings.textBoxes.isEmpty())
    }

    @Test
    fun `version 24 renames the old paths and takes out the boxes they made`() {
        val oldBox = """{"enabled":true,"xPercent":10.0,"yPercent":%s,"widthPercent":50.0,"heightPercent":10.0}"""
        val emptyBox = """{"enabled":false,"xPercent":10.0,"yPercent":10.0,"widthPercent":80.0,"heightPercent":80.0}"""
        val translations = """"translations":[{"fileName":"kjv.spb"}]"""
        val main = """
            {"id":"main","bibleSettings":{$translations,"textBoxes":{"REFERENCE#kjv.spb":${oldBox.format("2.0")}}}}
        """.trimIndent()
        val social = """
            {"id":"social","parentId":"main",
             "overrides":["bibleSettings.textBoxes.REFERENCE#kjv.spb.yPercent","bibleSettings.textBoxes.REFERENCE#kjv.xPercent",
                          "bibleSettings.marginTop"],
             "bibleSettings":{$translations,"marginTop":7,
               "textBoxes":{"REFERENCE#kjv.spb":${oldBox.format("40.0")},"REFERENCE#kjv":$emptyBox}}}
        """.trimIndent()
        val loaded = SettingsManager().migrateAndDecode(
            """{"settingsVersion":23,"projectionSettings":{"outputProfiles":[$main,$social]}}""",
        ).projectionSettings

        val follower = loaded.p("social")
        assertEquals(
            setOf("bibleSettings.textBoxes[REFERENCE#kjv.spb].yPercent", "bibleSettings.marginTop"),
            follower.overrides,
        )
        assertEquals(setOf(bibleBoxKey(KJV)), follower.bibleSettings.textBoxes.keys)
        assertEquals(40f, follower.boxY(), "its own box survives the load")
        assertEquals(2f, loaded.p("main").boxY())
    }

    @Test
    fun `old paths are renamed only where they differ`() {
        val profile = OutputProfile(
            bibleSettings = bible(fileName = "a]b.spb"),
        ).let { linkJson.encodeToJsonElement(OutputProfile.serializer(), it) as JsonObject }
        val renames = legacyPathRenames(profile)
        assertEquals(
            "bibleSettings.translations[a\\]b.spb].textFontSize",
            renames["bibleSettings.translations[a]b.spb].textFontSize"],
        )
        assertEquals(
            "bibleSettings.textBoxes[REFERENCE#a\\]b.spb].yPercent",
            renames["bibleSettings.textBoxes.REFERENCE#a]b.spb.yPercent"],
        )
        assertFalse(renames.keys.any { it == "bibleSettings.marginTop" })
    }

    private fun bibleBoxKey(fileName: String) = textBoxKey("REFERENCE", lowerThird = false, language = fileName)

    private companion object {
        const val KJV = "kjv.spb"
    }
}
