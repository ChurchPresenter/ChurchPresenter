package org.churchpresenter.app.churchpresenter

import kotlin.math.roundToInt
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.app.churchpresenter.tabs.ScheduleToolbarButton
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.settings.utils.Constants
import java.io.File

private const val MILLIS_PER_SECOND = 1000
private const val HEX_RADIX = 16

/**
 * The decisions the root composable makes, held apart from the composable that makes them.
 *
 * Split out of `MainDesktop.kt` for a reason worth stating: that file's `MainDesktop` function
 * compiles to roughly 44KB of bytecode in a 739KB class, close enough to the JVM's 64KB per-method
 * ceiling that JaCoCo cannot fit its probes in. An uninstrumentable class reports **zero** coverage
 * for everything in it — so while these lived beside the composable, their tests ran and passed but
 * counted for nothing. Here they are an ordinary class and measure normally.
 *
 * Everything below is pure: no Compose, no view models, no I/O. Anything that needs those stays in
 * the composable and is reached through one of these.
 */

internal fun computeVisibleTabs(
    hiddenTabs: Set<String>,
    showCrosswordTab: Boolean,
    hasCompanionTabConnections: Boolean,
): List<Tabs> =
    (Tabs.entries.filter { tab ->
        tab != Tabs.CROSSWORD && tab.name !in hiddenTabs &&
            (tab != Tabs.COMPANION_SURFACE || hasCompanionTabConnections)
    } + if (showCrosswordTab) listOf(Tabs.CROSSWORD) else emptyList())
        .ifEmpty { listOf(Tabs.BIBLE) }

internal fun clampedTabIndex(selectedTabIndex: Int, visibleTabs: List<Tabs>): Int =
    selectedTabIndex.coerceIn(visibleTabs.indices)

internal fun resolveTabSelection(tab: Tabs, visibleTabs: List<Tabs>, currentIndex: Int): Int {
    val idx = visibleTabs.indexOf(tab)
    return if (idx >= 0) idx else currentIndex
}

internal data class SequenceStep(val progress: Int, val completed: Boolean)

internal fun advanceKeySequence(pressedKey: Key, sequence: List<Key>, currentProgress: Int): SequenceStep {
    val expected = sequence.getOrNull(currentProgress)
    if (pressedKey == expected) {
        val next = currentProgress + 1
        return if (next == sequence.size) SequenceStep(progress = 0, completed = true)
        else SequenceStep(progress = next, completed = false)
    }
    return SequenceStep(progress = if (pressedKey == sequence[0]) 1 else 0, completed = false)
}

internal fun computePanelCapPx(availablePx: Float, otherPanelPx: Float, reservePx: Float, absMaxPx: Float): Float =
    if (availablePx <= 0f) Float.MAX_VALUE
    else (availablePx - otherPanelPx - reservePx).coerceIn(0f, absMaxPx)

internal fun withScheduleWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(schedulePanelWidthDp = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(schedulePanelWidthDp = widthDp))

internal fun withPreviewWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(previewPanelWidthDp = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(previewPanelWidthDp = widthDp))

/**
 * Applies a scheduled announcement onto [settings], so the Announcements tab and the output show
 * exactly what was saved into the service order.
 *
 * Pulled out of the two composable lambdas that used to carry it — going live with a scheduled
 * announcement, and selecting its row — which held **byte-identical** 27-field copies. Every field
 * of `AnnouncementsSettings` comes from the item, so a field added to one side and forgotten on the
 * other silently drops it; and a mis-typed pairing (`targetMinute = item.targetSecond`) puts the
 * wrong countdown on screen with nothing to see until it is live.
 */
internal fun withAnnouncementFrom(settings: AppSettings, item: ScheduleItem.AnnouncementItem): AppSettings =
    settings.copy(
        announcementsSettings = settings.announcementsSettings.copy(
            text                = item.text,
            textColor           = item.textColor,
            backgroundColor     = item.backgroundColor,
            fontSize            = item.fontSize,
            fontType            = item.fontType,
            bold                = item.bold,
            italic              = item.italic,
            underline           = item.underline,
            shadow              = item.shadow,
            shadowColor         = item.shadowColor,
            shadowSize          = item.shadowSize,
            shadowOpacity       = item.shadowOpacity,
            horizontalAlignment = item.horizontalAlignment,
            position            = item.position,
            animationType       = item.animationType,
            animationDuration   = item.animationDuration,
            loopCount           = item.loopCount,
            timerHours          = item.timerHours,
            timerMinutes        = item.timerMinutes,
            timerSeconds        = item.timerSeconds,
            timerTextColor      = item.timerTextColor,
            timerExpiredText    = item.timerExpiredText,
            timerMode           = item.timerMode,
            targetHour          = item.targetHour,
            targetMinute        = item.targetMinute,
            targetSecond        = item.targetSecond,
            liveClockFormat     = item.liveClockFormat
        )
    )

/**
 * The bible files the lookup engine indexes, as a stable re-index key.
 *
 * Sorted because it is a *set*: the engine restarts — and re-indexes — whenever this changes, so
 * swapping primary↔secondary must produce the same key and leave a running engine alone, while
 * genuinely switching to another translation must produce a different one. Sorting is what makes
 * "same bibles, different order" compare equal.
 *
 * File names only, which is why the call site keys the effect on the storage directory separately:
 * moving to another folder holding the same names does not change this.
 */
internal fun engineBibleFiles(bibleSettings: BibleSettings): List<String> =
    bibleSettings.translationList().map { it.fileName }.sorted()

/**
 * Whether the Bible Lookup Engine should be running.
 *
 * All three have to hold: the engine reads its audio from the speech-to-text feed, so it is useless
 * without one; it is opt-in; and with no bibles to index there is nothing to match against. The
 * else-branch at the call site stops the engine and expires any references already detected, so
 * getting this wrong either leaves stale verses staged or silently stops auto-follow mid-service.
 */
internal fun shouldRunBibleEngine(
    sttConnected: Boolean,
    engineEnabled: Boolean,
    engineBibles: List<String>,
): Boolean = sttConnected && engineEnabled && engineBibles.isNotEmpty()

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
internal suspend fun decodeSlideBitmaps(slideFiles: List<File>, index: Int): Pair<ImageBitmap?, ImageBitmap?> {
    val current = slideFiles.getOrNull(index)?.let { f ->
        withContext(Dispatchers.IO) {
            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
        }
    }
    val next = slideFiles.getOrNull(index + 1)?.let { f ->
        withContext(Dispatchers.IO) {
            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
        }
    }
    return current to next
}

/**
 * Whether the main window, rather than `BibleTab`, must resolve a clicked schedule verse.
 *
 * Same window as [shouldMainHandleAutoFollow] and the same reasoning, minus the live-content
 * condition: an item the operator clicked is a request to show that verse whatever is on screen
 * now. [bibleTabIndex] is `-1` when the tab is hidden — and a hidden Bible tab is precisely the
 * case where `selectTab` declines to switch, so nothing else would ever resolve the item.
 */
internal fun shouldMainResolveScheduleVerse(
    activeTabIndex: Int,
    bibleTabIndex: Int,
): Boolean = activeTabIndex != bibleTabIndex

/**
 * Whether the main window, rather than the Bible tab, should apply an auto-follow detection.
 *
 * `BibleTab` sits inside `AnimatedContent` and leaves the composition when the operator switches
 * away, taking its own auto-follow handler — and the history, statistics and training-log writes
 * that go with it — with it. This is the stand-in for exactly that window, so both conditions are
 * about not doing the wrong thing while it is gone:
 *
 * - While the Bible tab *is* the active one it owns the detection; handling it here as well would
 *   put the verse live twice and log it twice.
 * - Only when Bible is already the live content. Auto-follow keeps a passage in step with the
 *   speaker; it must never take the screen away from a song or a slide on its own.
 *
 * [bibleTabIndex] is `-1` when the Bible tab is hidden altogether, which can never equal a real
 * [activeTabIndex] — so with the tab hidden the main window always handles it, there being no
 * `BibleTab` in the composition to defer to.
 */
internal fun shouldMainHandleAutoFollow(
    activeTabIndex: Int,
    bibleTabIndex: Int,
    presentingMode: Presenting,
): Boolean = activeTabIndex != bibleTabIndex && presentingMode == Presenting.BIBLE

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
 * The verses to put on screen for a remote "select bible verse" request.
 *
 * Two different situations, and the fallback is the one that matters. [resolved] is what this
 * machine's own bibles made of the reference; when they made nothing — the phone is showing a
 * translation that is not installed here, or names the book differently — the request carries its
 * own [SelectBibleVerseRequest.verseText], and showing that is far better than showing nothing at
 * all. The bible metadata is still this instance's, since that is what the styling is keyed to.
 *
 * When it did resolve, the request's [SelectBibleVerseRequest.verseRange] is stamped onto every
 * verse: the local lookup knows the verses but not the span the client asked for, and the range is
 * what the reference line renders from, so dropping it turns "John 3:16-18" into "John 3:16".
 */
internal fun remoteSelectedVerses(
    resolved: List<SelectedVerse>,
    request: SelectBibleVerseRequest,
    translationFileName: String,
    bibleAbbreviation: String,
    bibleName: String,
): List<SelectedVerse> =
    if (resolved.isNotEmpty()) {
        resolved.map { it.copy(verseRange = request.verseRange) }
    } else {
        listOf(
            SelectedVerse(
                translationFileName = translationFileName,
                bibleAbbreviation = bibleAbbreviation,
                bibleName = bibleName,
                bookName = request.bookName,
                chapter = request.chapter,
                verseNumber = request.verseNumber,
                verseText = request.verseText,
                verseRange = request.verseRange,
            ),
        )
    }

/**
 * Whether this instance should mirror the primary's content over Instance Link.
 *
 * Both halves are load-bearing, and the role half is the one that is easy to lose: a **Controller**
 * is also connected, but it drives the primary rather than following it, so it must keep browsing
 * its own local songs, bibles and schedule. Mirroring in that role would replace the operator's own
 * library with the far end's, mid-service, on the machine that is meant to be in charge.
 *
 * Shared by the songs, bible and schedule mirrors so all three follow — and stop following —
 * together; they previously spelled this out three times over.
 */
internal fun shouldMirrorFromPrimary(
    status: InstanceLinkStatus,
    role: InstanceLinkRole,
): Boolean = status == InstanceLinkStatus.CONNECTED && role == InstanceLinkRole.CONTROLLED

/**
 * The width a collapsible side panel actually renders at, in pixels.
 *
 * Order matters: the cap is applied to the requested width *before* the collapse fraction scales it.
 * Capping afterwards would make a panel that is only wide because the window shrank animate open
 * from its old, too-large width and overshoot past the resize handles on the way.
 *
 * [visibleFraction] runs 0f (collapsed) to 1f (open) and is driven by an `Animatable`, which can
 * overshoot slightly below zero mid-spring — hence the floor, since a negative width is not a legal
 * measurement constraint.
 */
internal fun panelRenderWidthPx(requestedPx: Float, capPx: Float, visibleFraction: Float): Int =
    (requestedPx.coerceAtMost(capPx) * visibleFraction).roundToInt().coerceAtLeast(0)

internal fun sttUrlToPersist(settings: AppSettings, sttConnected: Boolean): String? {
    if (!sttConnected) return null
    val url = settings.sttSettings.serverUrl
    return if (settings.sttSettings.lastConnectedUrl != url) url else null
}

internal fun withSttLastConnectedUrl(settings: AppSettings, url: String): AppSettings =
    settings.copy(sttSettings = settings.sttSettings.copy(lastConnectedUrl = url))

internal fun stageMonitorScreenIndices(screenAssignments: List<ScreenAssignment>): List<Int> =
    screenAssignments.indices.filter { screenAssignments[it].displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR }

internal fun resolveSelectedConnectionId(currentId: String?, connections: List<CompanionSatelliteSettings>): String? =
    if (connections.any { it.id == currentId }) currentId else connections.firstOrNull()?.id

internal fun resolveBookIndex(bookNames: List<String>, requestedBookName: String): Int =
    bookNames.indexOfFirst { it.equals(requestedBookName, ignoreCase = true) }

internal fun parseVerseRangeEnd(verseRange: String, verseNumber: Int): Int? {
    val rangeNums = verseRange
        .takeIf { it.isNotBlank() }
        ?.split(",", "-")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.isNotEmpty() }
    return rangeNums?.max()?.takeIf { it > verseNumber }
}

internal fun retrySecondsLeft(nextRetryAtMs: Long?, nowMs: Long): Long? =
    nextRetryAtMs?.let { ((it - nowMs) / MILLIS_PER_SECOND).coerceAtLeast(0) }

internal fun findLottiePresetFile(files: List<File>?, presetLabel: String, presetId: String): File? =
    files?.find { it.nameWithoutExtension == presetLabel || it.nameWithoutExtension == presetId }

internal fun visibleTabCount(hiddenTabs: Set<String>): Int =
    Tabs.entries.count { it != Tabs.CROSSWORD && it.name !in hiddenTabs }

internal fun isOnlyVisibleTab(tab: Tabs, hiddenTabs: Set<String>, visibleCount: Int): Boolean =
    tab.name !in hiddenTabs && visibleCount == 1

internal fun toggleHiddenTabs(hiddenTabs: Set<String>, tab: Tabs): Set<String> =
    if (tab.name !in hiddenTabs) hiddenTabs + tab.name else hiddenTabs - tab.name

/** The schedule toolbar's own version of [toggleHiddenTabs]; unlike tabs, hiding them all is allowed. */
internal fun toggleHiddenScheduleButton(hidden: Set<String>, button: ScheduleToolbarButton): Set<String> =
    if (button.name !in hidden) hidden + button.name else hidden - button.name

/**
 * Whether the hidden audio player should be hosted here rather than by the Media tab.
 *
 * The two players are mutually exclusive — only one decoder may run at a time — so this and
 * [shouldHostBackgroundVideo] must never both be true. Both take the media state as nullable, so
 * "there is no media view model" is decided here rather than left as a branch at the call site.
 */
internal fun shouldHostBackgroundAudio(isAudioFile: Boolean?, isPlaying: Boolean?, currentTab: Tabs): Boolean =
    isAudioFile == true && isPlaying == true && currentTab != Tabs.MEDIA

/**
 * Whether the off-tab video decoder should be hosted here. Loaded rather than playing: it exists to
 * keep a frame on screen while the operator is elsewhere, which a paused video still needs.
 */
internal fun shouldHostBackgroundVideo(isAudioFile: Boolean?, isLoaded: Boolean?, currentTab: Tabs): Boolean =
    isAudioFile == false && isLoaded == true && currentTab != Tabs.MEDIA

/**
 * Whether a section index arriving from elsewhere — a phone, a linked instance — should move the
 * Songs tab's own selection.
 *
 * Only while songs are what is live, and only when it is actually a different section: following an
 * index the tab already sits on would write the selection back over itself on every emission.
 */
internal fun shouldFollowRemoteSection(
    presentingMode: Presenting,
    selectedSectionIndex: Int,
    incomingSectionIndex: Int,
): Boolean = presentingMode == Presenting.LYRICS && selectedSectionIndex != incomingSectionIndex

/**
 * Whether a bible announced by the primary invalidates the cached copy. Either signal counts —
 * primary or secondary — because a full replica re-downloads both.
 */
internal fun shouldInvalidateBibleCache(bibleUpdatedSignal: Int, secondaryBibleUpdatedSignal: Int): Boolean =
    bibleUpdatedSignal > 0 || secondaryBibleUpdatedSignal > 0

/** Whether a slide index names a slide that exists. */
internal fun isValidSlideIndex(index: Int, slideCount: Int): Boolean = index in 0 until slideCount

/** Whether a scheduled picture item still points at a folder worth loading. */
internal fun isLoadablePictureFolder(folder: File): Boolean = folder.exists() && folder.isDirectory

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
 * The book id for a resolved book index, or 0 when the book was not found or the bible cannot name
 * it. Zero is the "no book" value the verse payload carries.
 */
internal fun resolveBookIdOrZero(bookIndex: Int, bookIdAt: (Int) -> Int?): Int =
    if (bookIndex >= 0) bookIdAt(bookIndex) ?: 0 else 0

/**
 * Whether a remote picture selection names a different folder than the one on screen, in which case
 * that folder has to be loaded before the selection means anything.
 */
internal fun shouldSwitchPictureFolder(requestedFolderId: String, activeFolderId: String?): Boolean =
    requestedFolderId != activeFolderId

/** Whether a file resolved from the server's map is one that can actually be shown. */
internal fun isUsableImageFile(file: File?): Boolean = file != null && file.exists()

/**
 * Which stored layout a window's placement reads from. Floating is the windowed one; every other
 * placement — maximized and fullscreen alike — shares the maximized layout, so a fullscreen window
 * does not silently start editing the windowed sizes.
 */
internal fun isMaximizedPlacement(placement: WindowPlacement?): Boolean =
    placement != WindowPlacement.Floating

/**
 * A panel width brought back inside its cap.
 *
 * Applied on every recomposition during a drag rather than once on release: leaving a width beyond
 * its cap means the next drag applies its delta to a stale base, and the panel jumps instead of
 * tracking the cursor.
 */
internal fun clampPanelWidth(currentPx: Float, capPx: Float): Float =
    if (currentPx > capPx) capPx else currentPx

/**
 * Whether a collapsible panel still has to be composed. A panel that is collapsed but mid-animation
 * is still on screen, so it is composed until the animation has actually run out.
 */
internal fun isPanelRendered(collapsed: Boolean, visibleFraction: Float): Boolean =
    !collapsed || visibleFraction > 0f

/** Whether the link status is one worth offering a disconnect for — anything but fully detached. */
internal fun canDisconnectInstanceLink(status: InstanceLinkStatus): Boolean =
    status != InstanceLinkStatus.DISCONNECTED

internal fun stableFileId(file: File): String = file.absolutePath.hashCode().toUInt().toString(HEX_RADIX)

internal fun tabForScheduleItem(item: ScheduleItem): Tabs? = when (item) {
    is ScheduleItem.SongItem -> Tabs.SONGS
    is ScheduleItem.BibleVerseItem -> Tabs.BIBLE
    is ScheduleItem.LabelItem -> null
    is ScheduleItem.PictureItem -> Tabs.PICTURES
    is ScheduleItem.PresentationItem -> Tabs.PRESENTATION
    is ScheduleItem.MediaItem -> Tabs.MEDIA
    is ScheduleItem.LowerThirdItem -> Tabs.LOWER_THIRD
    is ScheduleItem.AnnouncementItem -> Tabs.ANNOUNCEMENTS
    is ScheduleItem.WebsiteItem -> Tabs.WEB
    is ScheduleItem.SceneItem -> Tabs.CANVAS
    is ScheduleItem.DictionaryItem -> Tabs.DICTIONARY
}
