package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.churchpresenter.sharedui.models.Presenting
import java.io.File

/*
 * The presentation and picture decisions behind the main screen's remote commands and slide pushes.
 */

/**
 * Whether a remote next/previous-slide command should push a slide to the presenter.
 *
 * Both halves matter. Presentation has to be the *live* content already — these commands only step
 * whatever is on screen, so pushing while a song or a verse is live would replace it with a slide
 * nobody asked for. And the index has to be a slide that exists, which it is not at either end of
 * the deck once `nextSlide`/`previousSlide` has clamped, or before a deck is loaded at all.
 */
internal fun shouldPushSlide(presentingMode: Presenting, selectedIndex: Int, slideCount: Int): Boolean =
    presentingMode == Presenting.PRESENTATION && selectedIndex in 0 until slideCount

/**
 * The current slide and the one after it, decoded for the presenter and the stage monitor.
 *
 * Shared by the two paths that push a slide — the remote step commands and remote slide selection —
 * which carried identical copies of this. The second value is the *next* slide, which is null on the
 * last slide of the deck; the stage monitor draws its "next" pane from it, so returning the current
 * slide there would show the operator the wrong thing to prepare for.
 *
 * A file that will not decode throws, as it did inline: unlike the slide grid, which falls back to a
 * blank thumbnail, there is no sensible blank frame to put on the output here.
 */
internal suspend fun decodeSlideBitmaps(
    slideFiles: List<File>,
    index: Int,
    /** The slide shown as "next": the one after [index] unless hidden slides are passed over. */
    nextIndex: Int? = index + 1,
): Pair<ImageBitmap?, ImageBitmap?> {
    val current = slideFiles.getOrNull(index)?.let { f ->
        withContext(Dispatchers.IO) {
            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
        }
    }
    val next = nextIndex?.let { slideFiles.getOrNull(it) }?.let { f ->
        withContext(Dispatchers.IO) {
            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
        }
    }
    return current to next
}

/** Whether a slide index names a slide that exists. */
internal fun isValidSlideIndex(index: Int, slideCount: Int): Boolean = index in 0 until slideCount

/**
 * Whether turning remote control on should republish the deck that is already open.
 *
 * All three have to hold: remote control is on, a presentation is open, and it has slides. A phone
 * connecting to a deck with no slides would be handed an empty list it could not navigate.
 */
internal fun shouldPublishPresentation(
    remoteControlEnabled: Boolean,
    hasSelectedPresentation: Boolean,
    slideCount: Int,
): Boolean = remoteControlEnabled && hasSelectedPresentation && slideCount > 0

/**
 * Whether a slide chosen remotely has to take the output as well as move the selection. Only when
 * something else is live — a presentation already on screen just changes slide.
 */
internal fun shouldTakePresentationLive(presentingMode: Presenting): Boolean =
    presentingMode != Presenting.PRESENTATION

/** The speaker notes for a slide, or none — a deck may carry fewer notes than slides. */
internal fun presenterNotesAt(notes: List<String>, index: Int): String = notes.getOrElse(index) { "" }

/**
 * The index the stage monitor should preload as the *next* picture, or `-1` for none.
 *
 * Fed straight to `getOrNull`, so both "there is no next" cases collapse to null: the last picture
 * in the folder, and a requested [index] that is not in the folder at all. The out-of-range check is
 * what makes the second true — a bare `index + 1` would turn a request for index `-1` into a preload
 * of picture 0, showing the platform a "next" slide that is really the first one.
 */
internal fun nextImageIndex(index: Int, imageCount: Int): Int =
    if (index in 0 until imageCount) index + 1 else -1

/**
 * Whether a remote picture selection names a different folder than the one on screen, in which case
 * that folder has to be loaded before the selection means anything.
 */
internal fun shouldSwitchPictureFolder(requestedFolderId: String, activeFolderId: String?): Boolean =
    requestedFolderId != activeFolderId

/** Whether a file resolved from the server's map is one that can actually be shown. */
internal fun isUsableImageFile(file: File?): Boolean = file != null && file.exists()

/** Whether a scheduled picture item still points at a folder worth loading. */
internal fun isLoadablePictureFolder(folder: File): Boolean = folder.exists() && folder.isDirectory
