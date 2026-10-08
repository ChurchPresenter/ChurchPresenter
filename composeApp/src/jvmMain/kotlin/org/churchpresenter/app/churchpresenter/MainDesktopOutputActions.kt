package org.churchpresenter.app.churchpresenter

import org.churchpresenter.liveoutput.clearFromOperator
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.cueOrSetAnnouncementText
import org.churchpresenter.liveoutput.showLowerThird
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.profiles.stageMonitorScreenIndices
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import java.io.File

/*
 * What the main window's output actions do, over only the collaborators each one uses -- so they can
 * be driven without building the whole main window. The `MainDesktopScope` members and extensions
 * of the same purpose delegate here.
 */

/**
 * Pushes the presentation's current slide to the presenter — shared by the clicker keys and the
 * next/previous slide Instance Link commands. Only pushes when Presentation is actually the live
 * content, same gate PresentationTab's own slide-push effect uses.
 */
internal suspend fun pushPresentationSlideIfLive(
    presentationViewModel: PresentationViewModel,
    presenterManager: PresenterManager,
) {
    val index = presentationViewModel.selectedSlideIndex
    val slideCount = presentationViewModel.slideFiles.size
    if (!shouldPushSlide(presenterManager.slideContent.value, index, slideCount)) return
    val (bitmap, nextBitmap) = decodeSlideBitmaps(
        presentationViewModel.slideFiles,
        index,
        presentationViewModel.nextShownSlideIndex(index),
    )
    presenterManager.setSelectedSlide(bitmap)
    presenterManager.setLiveSlide(presentationViewModel.selectedPresentation?.name, index)
    presenterManager.setNextSlide(nextBitmap)
    presenterManager.setPresenterNotes(presenterNotesAt(presentationViewModel.slideNotes, index))
    // Keep animated playback in sync (or cleared) so a stale animated frame from a
    // previous slide can never override the freshly pushed static slide.
    presentationViewModel.deck?.let { presenterManager.presentationShowSlide(it, index) }
        ?: presenterManager.clearPresentationPlayback()
}

/**
 * One clicker press: the deck's next or previous animation step, else the next or previous slide.
 *
 * [deck] is the selected presentation's parsed deck, read as the press arrives; a parameter only so a
 * test can hand in an animated one, which no file a test can cheaply load would parse into.
 */
internal suspend fun clickPresentationSlide(
    forward: Boolean,
    presentationViewModel: PresentationViewModel,
    presenterManager: PresenterManager,
    link: InstanceLinkBridge,
    deck: Deck? = presentationViewModel.deck,
) {
    val index = presentationViewModel.selectedSlideIndex
    val stepped = deck != null && if (forward) {
        presenterManager.advancePresentationStep(deck, index)
    } else {
        presenterManager.rewindPresentationStep(deck, index)
    }
    if (!stepped) {
        if (forward) {
            presentationViewModel.nextSlide(link.sendNextSlide)
        } else {
            presentationViewModel.previousSlide(link.sendPreviousSlide)
        }
        pushPresentationSlideIfLive(presentationViewModel, presenterManager)
    }
}

/**
 * Clears every output, including a "Send to Stage Monitor" lock, from the Clear shortcut.
 * [pauseMedia] is the Media tab's pause, null when there is no media player.
 */
internal fun clearAllOutputs(
    presenterManager: PresenterManager,
    projectionSettings: ProjectionSettings,
    link: InstanceLinkBridge,
    pauseMedia: (() -> Unit)?,
) {
    pauseMedia?.invoke()
    presenterManager.clearFromOperator()
    link.sendClear?.invoke()
    // Also release any "Send to Stage Monitor" lock (e.g. from Announcements)
    // so the stage monitor goes back to following the main presenting mode.
    stageMonitorScreenIndices(projectionSettings)
        .forEach { presenterManager.setScreenLock(it, null) }
}

/**
 * Puts a Schedule announcement row on screen. [timerExpiredDefaultLabel] is what a timer row with no
 * expiry text of its own shows when it runs out; [presenting] takes announcements live.
 */
internal fun presentAnnouncementItem(
    item: ScheduleItem.AnnouncementItem,
    timerExpiredDefaultLabel: String,
    presenterManager: PresenterManager,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    presenting: (Presenting) -> Unit,
) {
    onSettingsChange { settings ->
        withAnnouncementFrom(settings, item)
    }
    if (item.isTimer) {
        presenterManager.goLiveAnnouncementTimer(
            item,
            timerExpiredText = item.timerExpiredText.ifBlank { timerExpiredDefaultLabel },
        )
    } else {
        cueOrSetAnnouncementText(presenterManager, item.text)
    }
    presenting(Presenting.ANNOUNCEMENTS)
}

/** Puts a Schedule lower-third row up from the preset of that name in [lowerThirdFolder], if there is one. */
internal fun presentLowerThirdItem(
    item: ScheduleItem.LowerThirdItem,
    lowerThirdFolder: String,
    presenterManager: PresenterManager,
) {
    val lottieFolder = File(lowerThirdFolder)
    val lottieFile = findLottiePresetFile(lottieFolder.listFiles()?.toList(), item.presetLabel, item.presetId)
    if (lottieFile != null && lottieFile.exists()) {
        val json = lottieFile.readText()
        presenterManager.previewBus.showLowerThird(
            json, item.pauseAtFrame, -1f, item.pauseDurationMs, lottieFile.nameWithoutExtension,
        )
    }
}
