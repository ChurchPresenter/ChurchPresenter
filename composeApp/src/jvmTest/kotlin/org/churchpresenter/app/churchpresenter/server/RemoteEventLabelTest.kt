package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three pure functions behind the remote-request banner: how an incoming action is classified,
 * how the item it names is described to the operator, and how a projected announcement's own styling
 * is folded into settings.
 *
 * The banner is an approval prompt — the operator reads it and decides whether a phone or a linked
 * instance gets to change what the congregation sees. A label naming the wrong song, or an action
 * classified as the wrong type, means approving something other than what was asked for.
 *
 * These lived in `main.kt` until the extraction that added this file, where nothing could reach them:
 * excluded from the coverage gate as app-entry wiring *and* sitting at 0%.
 */
class RemoteEventLabelTest {

    // ── qaActionType ────────────────────────────────────────────────────────────

    @Test
    fun `every Q&A action maps to its own event type`() {
        assertEquals(RemoteEventType.QA_EDIT, qaActionType("edit"))
        assertEquals(RemoteEventType.QA_DELETE, qaActionType("delete"))
        assertEquals(RemoteEventType.QA_APPROVE, qaActionType("approve"))
        assertEquals(RemoteEventType.QA_DENY, qaActionType("deny"))
        assertEquals(RemoteEventType.QA_DONE, qaActionType("done"))
        assertEquals(RemoteEventType.QA_DISPLAY, qaActionType("display"))
        assertEquals(RemoteEventType.QA_CLEAR_DISPLAY, qaActionType("clear-display"))
    }

    @Test
    fun `an unrecognised action is treated as an addition`() {
        // The fallback decides what an unknown action from a newer client looks like on the banner.
        // "Add" is the safe reading: it is the one action that cannot destroy an existing question.
        assertEquals(RemoteEventType.QA_ADD, qaActionType("add"))
        assertEquals(RemoteEventType.QA_ADD, qaActionType(""))
        assertEquals(RemoteEventType.QA_ADD, qaActionType("something-from-a-future-version"))
    }

    @Test
    fun `the action is matched exactly, not loosely`() {
        // Case and spacing come off the wire, so a near-miss must not be read as a destructive action.
        assertEquals(RemoteEventType.QA_ADD, qaActionType("DELETE"))
        assertEquals(RemoteEventType.QA_ADD, qaActionType(" delete"))
    }

    // ── remoteEventLabel ────────────────────────────────────────────────────────

    @Test
    fun `a song is labelled by number and title, detailed by its songbook`() {
        val (title, detail) = remoteEventLabel(
            ScheduleItem.SongItem(id = "1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")
        )

        assertEquals("42 - Amazing Grace", title)
        assertEquals("Hymnal", detail)
    }

    @Test
    fun `a single verse is labelled by its reference`() {
        val (title, detail) = remoteEventLabel(
            ScheduleItem.BibleVerseItem(
                id = "1", bookName = "John", chapter = 3, verseNumber = 16,
                verseText = "For God so loved the world.",
            )
        )

        assertEquals("John 3:16", title)
        assertTrue(detail.startsWith("For God so loved"), "the detail previews the verse, was '$detail'")
    }

    @Test
    fun `a verse range is labelled by the range rather than the first verse`() {
        val (title, _) = remoteEventLabel(
            ScheduleItem.BibleVerseItem(
                id = "1", bookName = "John", chapter = 3, verseNumber = 16,
                verseText = "…", verseRange = "16-18",
            )
        )

        // Approving "John 3:16" when the request was for three verses would put the wrong thing up.
        assertEquals("John 3:16-18", title)
    }

    @Test
    fun `a long verse preview is truncated`() {
        val (_, detail) = remoteEventLabel(
            ScheduleItem.BibleVerseItem(
                id = "1", bookName = "Psalm", chapter = 119, verseNumber = 1,
                verseText = "x".repeat(500),
            )
        )

        // The banner is one line; an untruncated psalm would push the buttons off it.
        assertEquals(60, detail.length)
    }

    @Test
    fun `every schedule item type produces a label`() {
        val items = listOf(
            ScheduleItem.SongItem(id = "1", songNumber = 1, title = "T", songbook = "B"),
            ScheduleItem.BibleVerseItem(id = "2", bookName = "John", chapter = 3, verseNumber = 16, verseText = "v"),
            ScheduleItem.PictureItem(id = "3", folderPath = "/p", folderName = "Advent", imageCount = 12),
            ScheduleItem.PresentationItem(
                id = "4",
                filePath = "/d.pptx",
                fileName = "d.pptx",
                slideCount = 3,
                fileType = "pptx",
            ),
            ScheduleItem.MediaItem(id = "5", mediaUrl = "u", mediaTitle = "Clip", mediaType = "video"),
            ScheduleItem.LabelItem(id = "6", text = "Welcome", textColor = "#FFF", backgroundColor = "#000"),
            ScheduleItem.AnnouncementItem(id = "7", text = "Notice"),
            ScheduleItem.LowerThirdItem(
                id = "8",
                presetId = "p",
                presetLabel = "Speaker",
                pauseAtFrame = false,
                pauseDurationMs = 0L,
            ),
            ScheduleItem.WebsiteItem(id = "9", url = "https://example.org", title = "Notices"),
            ScheduleItem.SceneItem(id = "10", sceneId = "s", sceneName = "Opening"),
            ScheduleItem.DictionaryItem(
                id = "11",
                number = "G5485",
                word = "χάρις",
                transliteration = "charis",
                definition = "grace",
            ),
        )

        // The `when` is exhaustive over a sealed class, so a new item type is a compile error rather
        // than a blank banner — but a type mapped to an empty title would still read as blank.
        items.forEach { item ->
            val (title, _) = remoteEventLabel(item)
            assertTrue(title.isNotBlank(), "${item::class.simpleName} must be nameable on the banner")
        }
    }

    @Test
    fun `a picture folder is detailed by how many images it holds`() {
        val (title, detail) = remoteEventLabel(
            ScheduleItem.PictureItem(id = "3", folderPath = "/p", folderName = "Advent", imageCount = 12)
        )

        assertEquals("Advent", title)
        assertEquals("12 images", detail)
    }

    @Test
    fun `a website is detailed by its url so the operator can see where it leads`() {
        val (title, detail) = remoteEventLabel(
            ScheduleItem.WebsiteItem(id = "9", url = "https://example.org/notices", title = "Notices")
        )

        // Approving a website request without seeing the URL would be approving a link unseen.
        assertEquals("Notices", title)
        assertEquals("https://example.org/notices", detail)
    }

    // ── withAnnouncement ────────────────────────────────────────────────────────

    @Test
    fun `a projected announcement brings its own styling into settings`() {
        val item = ScheduleItem.AnnouncementItem(
            id = "1",
            text = "Service starts in",
            textColor = "#FFEEAA",
            backgroundColor = "#101020",
            fontSize = 64,
            fontType = "Georgia",
            bold = true,
        )

        val applied = AppSettings().withAnnouncement(item)

        // The point of the copy: the announcement renders as whoever built it intended, rather than
        // picking up whatever this desktop's announcement settings happen to be.
        assertEquals("#FFEEAA", applied.announcementsSettings.textColor)
        assertEquals("#101020", applied.announcementsSettings.backgroundColor)
        assertEquals(64, applied.announcementsSettings.fontSize)
        assertEquals("Georgia", applied.announcementsSettings.fontType)
        assertTrue(applied.announcementsSettings.bold)
    }

    @Test
    fun `applying an announcement's style leaves the rest of settings alone`() {
        val before = AppSettings()
        val after = before.withAnnouncement(ScheduleItem.AnnouncementItem(id = "1", text = "Notice"))

        // A remote announcement must not quietly rewrite the Bible or song configuration on its way
        // to the screen.
        assertEquals(before.bibleSettings, after.bibleSettings)
        assertEquals(before.songSettings, after.songSettings)
        assertEquals(before.projectionSettings, after.projectionSettings)
    }
}
