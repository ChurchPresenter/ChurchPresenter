@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEvent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventDialogContent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.app.churchpresenter.dialogs.resolveRemoteEventPresentation
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.server.batchEventSummary
import org.churchpresenter.app.churchpresenter.server.remoteEventLabel
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The remote-permission dialog — what an operator is asked when a phone tries to do something — in
 * both themes.
 *
 * Every `RemoteEventType` gets its own image. They are not cosmetic variants of one another: each
 * carries its own wording, its own icon, and one of two accents (schedule edits read amber, the rest
 * read as the theme's primary), and the whole point of the dialog is that the operator can tell at a
 * glance what is being asked before they approve it.
 *
 * Presentation is resolved through the app's own `resolveRemoteEventPresentation` rather than
 * hand-passed, so an image cannot drift from what the dialog would really show for that type.
 *
 * Boxed at the dialog's real 500x290, or 330 tall when something is queued behind — the two heights
 * the app itself picks between.
 */
class RemoteEventDialogScreenshotTest {

    private fun shoot(
        name: String,
        event: RemoteEvent,
        queueSize: Int = 1,
        isClientKnownAllowed: Boolean = false,
        isClientKnownBlocked: Boolean = false,
        isInstanceLinkFollower: Boolean = false,
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    val presentation = resolveRemoteEventPresentation(
                        event = event,
                        queueSize = queueSize,
                        isClientKnownAllowed = isClientKnownAllowed,
                        isClientKnownBlocked = isClientKnownBlocked,
                    )
                    Box(Modifier.size(500.dp, presentation.dialogHeight)) {
                        RemoteEventDialogContent(
                            event = event,
                            actionLabel = presentation.actionLabel,
                            typeIcon = presentation.typeIcon,
                            typeAccent = presentation.typeAccent,
                            bodyTitle = presentation.bodyTitle,
                            remaining = presentation.remaining,
                            showAllowPermanently = presentation.showAllowPermanently,
                            isClientKnownAllowed = isClientKnownAllowed,
                            isClientKnownBlocked = isClientKnownBlocked,
                            isInstanceLinkFollower = isInstanceLinkFollower,
                            onAllow = {},
                            onAllowForSession = {},
                            onAllowPermanently = {},
                            onBlockForSession = {},
                            onBlockPermanently = {},
                            onDeny = {},
                        )
                    }
                }
            }
            waitForIdle()
            captureTo(file)
        }
    }

    // ── What is being added, one per kind of thing a phone can send ─────────────────────────────
    // The type is the same (ADD_TO_SCHEDULE); what differs is the item, and the operator is deciding
    // on *that*. Titles come from the app's own `remoteEventLabel`, so these read exactly as a real
    // request would rather than as something written out here.

    @Test
    fun `adding a song`() = shoot("add_song", added(SONG))

    @Test
    fun `adding a presentation`() = shoot("add_presentation", added(PRESENTATION))

    @Test
    fun `adding a picture folder`() = shoot("add_pictures", added(PICTURES))

    @Test
    fun `adding a video`() = shoot("add_media", added(MEDIA))

    @Test
    fun `adding a Bible verse`() = shoot("add_bible_verse", added(VERSE))

    /** Several at once — the dialog counts them and names the first three. */
    @Test
    fun `adding a batch`() = shoot("add_batch", batch(SONG, VERSE, PRESENTATION, PICTURES))

    @Test
    fun `removing from the schedule`() = shoot(
        "remove_from_schedule",
        labelled(RemoteEventType.REMOVE_FROM_SCHEDULE, remoteEventLabel(SONG)),
    )

    // ── Taking control of what is on screen ─────────────────────────────────────────────────────

    @Test
    fun `putting a verse on screen`() = shoot("project_bible", projected(VERSE))

    @Test
    fun `putting a song on screen`() = shoot("project_song", projected(SONG))

    /** Driving the media player — play, seek, volume all arrive as this. */
    @Test
    fun `controlling media`() = shoot("project_media", projected(MEDIA))

    @Test
    fun `putting a presentation on screen`() = shoot("project_presentation", projected(PRESENTATION))

    /** A phone asking to drive the slides — the title is filled in by the dialog itself. */
    @Test
    fun `a presentation remote connecting`() =
        shoot("presentation_connect", event(RemoteEventType.PRESENTATION_CONNECT, ""))

    @Test
    fun `a Q&A admin panel connecting`() =
        shoot("qa_admin_connect", event(RemoteEventType.QA_ADMIN_CONNECT, ""))

    @Test
    fun `an instant present`() = shoot(
        "present",
        event(RemoteEventType.PRESENT, "Verse 2", "Amazing Grace"),
    )

    @Test
    fun `an upload`() = shoot("upload", event(RemoteEventType.UPLOAD, "Sermon.pptx", "6 slides"))

    @Test
    fun `clearing the display`() = shoot("clear", event(RemoteEventType.CLEAR, ""))

    // ── The Q&A moderation actions ──────────────────────────────────────────────────────────────

    @Test
    fun `a question added`() = shoot("qa_add", event(RemoteEventType.QA_ADD, QUESTION))

    @Test
    fun `a question edited`() = shoot("qa_edit", event(RemoteEventType.QA_EDIT, QUESTION))

    @Test
    fun `a question deleted`() = shoot("qa_delete", event(RemoteEventType.QA_DELETE, QUESTION))

    @Test
    fun `a question approved`() = shoot("qa_approve", event(RemoteEventType.QA_APPROVE, QUESTION))

    @Test
    fun `a question denied`() = shoot("qa_deny", event(RemoteEventType.QA_DENY, QUESTION))

    @Test
    fun `a question marked done`() = shoot("qa_done", event(RemoteEventType.QA_DONE, QUESTION))

    @Test
    fun `a question put on screen`() = shoot("qa_display", event(RemoteEventType.QA_DISPLAY, QUESTION))

    @Test
    fun `the question display cleared`() =
        shoot("qa_clear_display", event(RemoteEventType.QA_CLEAR_DISPLAY, ""))

    // ── Who is asking, and how many are waiting ─────────────────────────────────────────────────

    @Test
    fun `a device with no saved name`() = shoot(
        "unnamed_client",
        event(RemoteEventType.PROJECT, "Psalms 23:1-3").copy(clientLabel = ""),
    )

    /** Already on the allow list: the badge says so, and Allow Permanently is no longer offered. */
    @Test
    fun `a device already allowed`() = shoot(
        "client_allowed",
        event(RemoteEventType.PROJECT, "Psalms 23:1-3"),
        isClientKnownAllowed = true,
    )

    @Test
    fun `a device already blocked`() = shoot(
        "client_blocked",
        event(RemoteEventType.PROJECT, "Psalms 23:1-3"),
        isClientKnownBlocked = true,
    )

    /** A linked second instance rather than somebody's phone. */
    @Test
    fun `an Instance Link follower`() = shoot(
        "instance_link_follower",
        event(RemoteEventType.PROJECT, "Psalms 23:1-3"),
        isInstanceLinkFollower = true,
    )

    /** More requests queued behind this one: a count badge, a hint, and a taller dialog. */
    @Test
    fun `others waiting behind this one`() = shoot(
        "queued",
        event(RemoteEventType.ADD_TO_SCHEDULE, "Amazing Grace", "Hymnal 42"),
        queueSize = 4,
    )

    @Test
    fun `a long title that has to be cut`() = shoot(
        "long_title",
        event(
            RemoteEventType.ADD_TO_SCHEDULE,
            "Guest Speaker — Dr Margaret Whitfield on the Overseas Missions Partnership",
            "Announcements · added from the foyer tablet",
        ),
    )

    private fun added(item: ScheduleItem) =
        labelled(RemoteEventType.ADD_TO_SCHEDULE, remoteEventLabel(item))

    private fun projected(item: ScheduleItem) =
        labelled(RemoteEventType.PROJECT, remoteEventLabel(item))

    private fun batch(vararg items: ScheduleItem) =
        labelled(RemoteEventType.ADD_TO_SCHEDULE, batchEventSummary(items.toList()))

    /** An event carrying a (title, detail) pair as the app's own labelling produced it. */
    private fun labelled(type: RemoteEventType, label: Pair<String, String>) =
        event(type, label.first, label.second)

    private fun event(type: RemoteEventType, title: String, detail: String = "") = RemoteEvent(
        type = type,
        title = title,
        detail = detail,
        clientId = "a4f1c9e2-7b30",
        clientLabel = "Sound desk iPad",
    )

    private companion object {
        const val SECTION = "remoteEventDialog"

        const val QUESTION = "How do I join a small group?"

        val SONG = ScheduleItem.SongItem(
            id = "s1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal",
        )
        val VERSE = ScheduleItem.BibleVerseItem(
            id = "b1", bookName = "Psalms", chapter = 23, verseNumber = 1,
            verseText = "The LORD is my shepherd; I shall not want.", verseRange = "1-3",
        )
        val PRESENTATION = ScheduleItem.PresentationItem(
            id = "p1", filePath = "/decks/Sermon.pptx", fileName = "Sermon.pptx",
            slideCount = 6, fileType = "pptx",
        )
        val PICTURES = ScheduleItem.PictureItem(
            id = "i1", folderPath = "/photos/Sunday Service", folderName = "Sunday Service",
            imageCount = 12,
        )
        val MEDIA = ScheduleItem.MediaItem(
            id = "m1", mediaUrl = "/media/Welcome Loop.mp4", mediaTitle = "Welcome Loop",
            mediaType = "local",
        )
    }
}
