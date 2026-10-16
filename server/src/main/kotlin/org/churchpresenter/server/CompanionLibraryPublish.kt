package org.churchpresenter.server

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.churchpresenter.bible.Bible
import org.churchpresenter.core.models.songs.SongItem

/*
 * The app's own song and Bible libraries, handed to the server from the window that loaded them.
 * Building a catalog and its JSON takes long enough to stall that window, so it happens on the
 * server's publishing thread instead: one publish at a time, in the order they were asked for, so
 * the library loaded last is the one served.
 */

/** [updateSongs] on [CompanionServer.publishing], returning at once. */
fun CompanionServer.publishSongs(songs: List<SongItem>): Job = scope.launch(publishing) { updateSongs(songs) }

/** [updateBible] on [CompanionServer.publishing], returning at once: its catalog counts every chapter's verses. */
fun CompanionServer.publishBible(bible: Bible, translation: String, filePath: String = ""): Job =
    scope.launch(publishing) { updateBible(bible, translation, filePath) }
