package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * Records what the schedule was asked to do, as a stand-in for the real actions.
 *
 * `ScheduleActions` is a data class of lambdas with no-op defaults, so the "fake" is a constructor
 * call — no mocking library, and every recorded string is the real argument list the production code
 * passed. Supplying only the lambdas a test cares about leaves the rest as no-ops.
 *
 * Supersedes the private `Recorder` in [ExecuteProjectItemTest], which predates this file and covers
 * fewer actions; that copy can be deleted once something needs to touch that suite anyway.
 */
internal class ScheduleActionsRecorder {

    /** Every add, in the order it was requested. */
    val added = mutableListOf<String>()

    /** Items handed over whole, so a test can assert on identity rather than on a formatted string. */
    val announcements = mutableListOf<ScheduleItem.AnnouncementItem>()

    /** Ids passed to `removeById`. */
    val removed = mutableListOf<String>()

    fun actions() = ScheduleActions(
        removeById = { id -> removed += id },
        addSong = { number, title, songbook, id -> added += "song:$number:$title:$songbook:$id" },
        addBibleVerse = { book, chapter, verse, text, range, bookId ->
            added += "bible:$book:$chapter:$verse:$text:$range:$bookId"
        },
        addPicture = { path, name, count -> added += "picture:$path:$name:$count" },
        addPresentation = { path, name, slides, type -> added += "presentation:$path:$name:$slides:$type" },
        addMedia = { url, title, type -> added += "media:$url:$title:$type" },
        addScene = { id, name -> added += "scene:$id:$name" },
        addDictionary = { number, word, translit, definition ->
            added += "dictionary:$number:$word:$translit:$definition"
        },
        addAnnouncement = { item ->
            added += "announcement:${item.id}"
            announcements += item
        },
        addWebsite = { url, title -> added += "website:$url:$title" },
    )
}
