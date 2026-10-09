package org.churchpresenter.helper.pack

import kotlinx.serialization.json.Json
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.semantic.CatalogTarget
import org.churchpresenter.helper.intent.semantic.WickCatalog
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.ProfileFocus
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WickPackTest {

    private val vector = WickCatalog.encodeVector(FloatArray(VECTOR) { if (it == 0) 1f else 0f })

    private fun phrase(target: String, text: String = "some words", vector: String = this.vector) =
        "$target\t$text\t$vector"

    private fun step(target: String, before: String? = null, hint: String = "Look here.") =
        PackStepFile(target, before, hint)

    private fun tour(id: String, vararg steps: PackStepFile, title: String = "A tour") =
        PackTourFile(id, title, triggers = listOf("x"), steps = steps.toList())

    private fun pack(
        phrases: List<String> = emptyList(),
        tours: List<PackTourFile> = emptyList(),
        tips: List<PackTipFile> = emptyList(),
        minApp: String = "26.1.0",
    ) = Json.encodeToString(PackFile.serializer(), PackFile(7, minApp, phrases, tours, tips))

    private fun load(text: String) = assertNotNull(parseWickPack(text, APP))

    @Test
    fun `a pack's tours, phrases and tips are read`() {
        val loaded = load(
            pack(
                phrases = listOf(phrase("request:add a song"), phrase("tour:ccli")),
                tours = listOf(
                    tour(
                        "ccli",
                        step("songs.edit", "tab=SONGS"),
                        step("songEditor.ccli"),
                        step("settings.SYSTEM", "settings=SYSTEM"),
                        step("settingsRow.$ROW", "profile=SONGS:$ROW"),
                        step("tab.BIBLE", "profile=BIBLE"),
                        title = "Enter a CCLI number",
                    ),
                ),
                tips = listOf(PackTipFile("Try it.", tour = "ccli"), PackTipFile("No tour here.")),
            ),
        )
        assertEquals(7, loaded.version)
        assertEquals(
            listOf(CatalogTarget.Request("add a song"), CatalogTarget.PackTour("ccli")),
            loaded.phrases.map { it.target },
        )
        val action = assertIs<HelperAction.Highlight>(loaded.tours.getValue("ccli").action)
        assertEquals(HelperText.Plain("Enter a CCLI number"), action.label)
        val steps = action.tour.steps
        assertEquals(GuideTarget("songs.edit"), steps[0].target)
        assertEquals(HelperText.Plain("Look here."), steps[0].hint)
        assertEquals(HelperAction.SelectTab(Tabs.SONGS), steps[0].before)
        assertNull(steps[1].before)
        assertEquals(HelperAction.OpenSettings(SettingsPage.SYSTEM), steps[2].before)
        assertEquals(
            HelperAction.OpenSettings(
                SettingsPage.PROFILES,
                ProfileFocus(page = "SONGS", rowKey = ROW),
            ),
            steps[3].before,
        )
        assertEquals(HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(page = "BIBLE")), steps[4].before)
        assertEquals(
            listOf(HelperText.Plain("Try it."), HelperText.Plain("No tour here.")),
            loaded.tips.map { it.text },
        )
        assertEquals(action, loaded.tips[0].action)
        assertNull(loaded.tips[1].action)
    }

    @Test
    fun `a tour with any step this build cannot show is dropped whole, with what points at it`() {
        val loaded = load(
            pack(
                phrases = listOf(phrase("tour:gone-target"), phrase("tour:gone-before"), phrase("tour:missing")),
                tours = listOf(
                    tour("gone-target", step("songs.edit"), step("songs.notAThing")),
                    tour("gone-before", step("songs.edit", "tab=GONE")),
                    tour("bad-settings", step("songs.edit", "settings=GONE")),
                    tour("bad-profile-page", step("songs.edit", "profile=GONE")),
                    tour("bad-profile-row", step("songs.edit", "profile=SONGS:not_a_string_key")),
                    tour("bad-kind", step("songs.edit", "window=SONGS")),
                    tour("no-steps"),
                    tour("blank-hint", step("songs.edit", hint = "")),
                    tour("Bad Id", step("songs.edit")),
                    tour("long-title", step("songs.edit"), title = "x".repeat(400)),
                    tour("ok", step("preview.clear")),
                ),
                tips = listOf(
                    PackTipFile("Gone.", tour = "gone-target"),
                    PackTipFile(""),
                    PackTipFile("x".repeat(400)),
                ),
            ),
        )
        assertEquals(setOf("ok"), loaded.tours.keys)
        assertTrue(loaded.phrases.isEmpty())
        assertNull(loaded.tips.single().action)
    }

    @Test
    fun `phrases naming what this build does not have, or badly formed, are dropped`() {
        val nan = WickCatalog.encodeVector(FloatArray(VECTOR) { Float.NaN })
        val loaded = load(
            pack(
                phrases = listOf(
                    phrase("request:qwertyuiop asdf"),
                    phrase("request:add a song", vector = WickCatalog.encodeVector(FloatArray(3))),
                    phrase("request:add a song", vector = nan),
                    phrase("request:add a song", text = "x".repeat(400)),
                    phrase("request:add a song", text = ""),
                    "request:add a song\tonly two",
                    phrase("tab:GONE"),
                    phrase("control:songs.notAThing:edit_song:-"),
                    phrase("control:songs.edit:not_a_string_key:-"),
                    phrase("control:songs.edit:edit_song:tab=GONE"),
                    phrase("profileRow:not_a_string_key:SONGS"),
                    phrase("profileRow:background_above_band_caption:GONE"),
                    phrase("pageRow:not_a_string_key:SYSTEM"),
                    phrase("control:preview.clear:tooltip_clear_display:-"),
                    phrase("control:announcements.goLive:tooltip_go_live:tab=ANNOUNCEMENTS"),
                    phrase("profileRow:background_above_band_caption:SONGS"),
                    phrase("pageRow:preview_mode:SYSTEM"),
                    phrase("tab:SONGS"),
                    phrase("settings:SYSTEM"),
                    phrase("shortcut:TAKE"),
                    phrase("suggested:CLEAR"),
                ),
            ),
        )
        assertEquals(
            listOf(
                "control:preview.clear:tooltip_clear_display:-",
                "control:announcements.goLive:tooltip_go_live:tab=ANNOUNCEMENTS",
                "profileRow:background_above_band_caption:SONGS",
                "pageRow:preview_mode:SYSTEM",
                "tab:SONGS",
                "settings:SYSTEM",
                "shortcut:TAKE",
                "suggested:CLEAR",
            ),
            loaded.phrases.map { it.target.format() },
        )
    }

    @Test
    fun `a step can ring any control the app tags, and nothing else`() {
        val known = listOf(
            "tab.SONGS", "settings.PROJECTION", "settingsRow.preview_mode", "shortcuts.TAKE",
            "songEditor.ccli", "announcements.goLive",
        )
        val unknown = listOf(
            "tab.GONE", "settings.GONE", "settingsRow.not_a_string_key", "shortcuts.GONE", "option.a.b",
        )
        val loaded = load(pack(tours = (known + unknown).mapIndexed { i, id -> tour("t$i", step(id)) }))
        assertEquals(known.indices.map { "t$it" }.toSet(), loaded.tours.keys)
    }

    @Test
    fun `a pack for a newer app, too big, malformed or with unknown fields is not read`() {
        assertNull(parseWickPack(pack(minApp = "26.16.0"), APP))
        assertNull(parseWickPack(pack(minApp = "27"), APP))
        assertNotNull(parseWickPack(pack(minApp = APP), APP))
        assertNull(parseWickPack("{", APP))
        assertNull(parseWickPack("""{"version":1,"minApp":"1","extra":true}""", APP))
        assertNull(parseWickPack(" ".repeat(MAX_PACK_BYTES + 1), APP))
    }

    @Test
    fun `versions compare as dotted numbers`() {
        assertTrue(isNewerVersion("26.15.10", "26.15.9"))
        assertTrue(isNewerVersion("26.16", "26.15.99"))
        assertFalse(isNewerVersion("26.15", "26.15.0"))
        assertFalse(isNewerVersion("26.15.0", "26.15"))
        assertFalse(isNewerVersion("25.99.99", "26.0.0"))
        assertFalse(isNewerVersion("x", "26.0.0"))
    }

    private companion object {
        const val APP = "26.15.96"
        const val VECTOR = 384
        const val ROW = "background_above_band_caption"
    }
}
