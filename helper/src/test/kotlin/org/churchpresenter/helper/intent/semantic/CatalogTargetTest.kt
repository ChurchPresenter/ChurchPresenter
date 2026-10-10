package org.churchpresenter.helper.intent.semantic

import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTargetTest {

    private val every = listOf(
        CatalogTarget.Suggested(SuggestedRequest.entries.first()),
        CatalogTarget.Request("make the song background blue"),
        CatalogTarget.Control("songs.search", "search_songs", null),
        CatalogTarget.Control("songs.search", "search_songs", CatalogTarget.Before.OnTab(Tabs.entries.first())),
        CatalogTarget.Control("x.y", "label", CatalogTarget.Before.OnSettings(SettingsPage.entries.first())),
        CatalogTarget.Settings(SettingsPage.entries.last()),
        CatalogTarget.Tab(Tabs.entries.last()),
        CatalogTarget.Shortcut(ShortcutAction.entries.first()),
        CatalogTarget.PackTour("song-ccli-number"),
    )

    @Test
    fun `every target reads back as written`() {
        every.forEach { assertEquals(it, CatalogTarget.parse(it.format())) }
    }

    @Test
    fun `a name this build no longer has is null`() {
        listOf(
            "suggested:GONE", "settings:GONE", "tab:GONE", "shortcut:GONE", "nothing:at all",
            "control:only:two", "control:a:b:c:d", "tour:", "tour: ",
        ).forEach { assertNull(CatalogTarget.parse(it), it) }
        assertEquals(CatalogTarget.Control("a", "b", null), CatalogTarget.parse("control:a:b:tab=GONE"))
        assertEquals(CatalogTarget.Control("a", "b", null), CatalogTarget.parse("control:a:b:settings=GONE"))
    }

    @Test
    fun `the catalog skips blank, broken and unknown lines`() {
        val vector = floatArrayOf(0.5f, -1f, 0.25f)
        val good = WickCatalog.line(every[1], "the text", vector)
        val file = listOf(good, "", "only\ttwo", "tab:GONE\ttext\t${WickCatalog.encodeVector(vector)}")
        val entries = WickCatalog.read(file.joinToString("\n").byteInputStream())
        assertEquals(1, entries.size)
        assertEquals(every[1], entries.single().target)
        assertEquals("the text", entries.single().text)
        assertTrue(vector.contentEquals(entries.single().vector))
    }
}
