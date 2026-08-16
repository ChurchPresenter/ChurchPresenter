package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.data.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.data.settings.InstanceLinkSettings
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.data.settings.OBSSettings
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckResult
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.data.settings.ServerSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.app.churchpresenter.data.Language
import java.io.File
import kotlin.math.roundToInt

private const val DEFAULT_FADE_OUT_MS = 500
private const val DEV_WINDOW_BASE_OFFSET_DP = 40
private const val DEV_WINDOW_STEP_DP = 48
private const val MILLIS_PER_SECOND_F = 1000f
private const val MILLIS_PER_SECOND_L = 1000L

/**
 * The decisions `main.kt` makes at startup, held apart from the entry point that makes them.
 *
 * Everything here is pure: no windows, no sockets, no Compose. What is left in `main.kt` opens real
 * AWT windows and binds real ports, which a headless test cannot do — so the rules those paths obey
 * live here instead, where they can be stated and checked directly.
 */

/**
 * Whether skiko should be pinned to Metal.
 *
 * Only on macOS, where leaving skiko to choose falls back to OpenGL and crashes on some machines.
 * Matched on the name rather than a platform enum because that is what the property carries.
 */
internal fun shouldForceMetalRenderer(osName: String): Boolean =
    osName.lowercase().contains("mac")

/** The port the single-instance lock binds, honouring the override a second dev instance sets. */
internal fun singleInstanceLockPort(override: String?, default: Int): Int =
    override?.toIntOrNull() ?: default

/**
 * The language to start in: the saved one when it is still a language this build has, English when
 * it is not — a settings file naming a language since removed must not stop the app starting.
 */
internal fun resolveStartupLanguage(savedCode: String): Language =
    Language.entries.find { it.code == savedCode } ?: Language.ENGLISH

/** How many DeckLink outputs are available, which is none at all when the driver is not present. */
internal fun deckLinkOutputCount(available: Boolean, deviceCount: () -> Int): Int =
    if (available) deviceCount() else 0

/**
 * Whether a Companion connection should be brought up on this pass.
 *
 * Three separate reasons, and the middle one is the only one that is a setting: something is
 * already live for it, the operator asked for it to auto-connect, or they have just edited it —
 * an edit is an explicit action, so it connects even with auto-connect off. A connection merely
 * seen for the first time at startup does none of these, which keeps startup opt-in.
 */
internal fun shouldConnectCompanion(
    hasLiveSlot: Boolean,
    autoConnect: Boolean,
    lastSeen: CompanionSatelliteSettings?,
    current: CompanionSatelliteSettings,
): Boolean = hasLiveSlot || autoConnect || (lastSeen != null && lastSeen != current)

/** Whether a connection needs a device id minted before it can be used — Companion rejects a blank one. */
internal fun needsGeneratedDeviceId(connection: CompanionSatelliteSettings): Boolean =
    connection.deviceId.isBlank()

/** [connections] with [id]'s device id replaced, leaving every other connection alone. */
internal fun withGeneratedDeviceId(
    connections: List<CompanionSatelliteSettings>,
    id: String,
    deviceId: String,
): List<CompanionSatelliteSettings> =
    connections.map { if (it.id == id) it.copy(deviceId = deviceId) else it }

/** Whether the instance link should dial out on its own: switched on, set to, and actually addressed. */
internal fun shouldAutoConnectInstanceLink(link: InstanceLinkSettings): Boolean =
    link.enabled && link.autoConnect && link.primaryHost.isNotBlank() && link.primaryPort > 0

/**
 * Whether a live link should be dropped. Switching the link off has to disconnect it now rather
 * than leave it running until the next launch.
 */
internal fun shouldDisconnectInstanceLink(link: InstanceLinkSettings): Boolean = !link.enabled

/** Whether the operator's connect/disconnect intent is a change worth persisting. */
internal fun instanceLinkEnabledChanged(current: InstanceLinkSettings, enabled: Boolean): Boolean =
    current.enabled != enabled

/**
 * The key remote callers must present, which is none at all when the operator has not switched key
 * checking on.
 *
 * Read in two places — the Q&A admin panel and the presentation remote — which is why it is one
 * function rather than the same expression written twice: two copies can disagree about whether a
 * key is required, and only one of the two screens would then refuse callers.
 */
internal fun activeApiKey(settings: ServerSettings): String =
    if (settings.apiKeyEnabled) settings.apiKey else ""

/** Whether the tunnel is up. */
internal fun isTunnelConnected(status: TunnelStatus): Boolean = status is TunnelStatus.Connected

/**
 * Whether the tunnel has just gone down, as opposed to being down all along.
 *
 * Edge-triggered on purpose: the URLs handed out over the tunnel are only worth clearing at the
 * moment it drops. Treating "is not connected" as the signal would clear them repeatedly, including
 * before one was ever established.
 */
internal fun tunnelJustDropped(previouslyConnected: Boolean, isConnected: Boolean): Boolean =
    previouslyConnected && !isConnected

/** The browser-source output configured at [index], or an unconfigured one when there is none. */
internal fun browserSourceOutputAt(outputs: List<ScreenAssignment>, index: Int): ScreenAssignment =
    outputs.getOrNull(index) ?: ScreenAssignment()

/** Whether media is what is on the output, which is what connected phones are told. */
internal fun isMediaLive(presentingMode: Presenting): Boolean = presentingMode == Presenting.MEDIA

/**
 * The song position to announce, or none when songs are not what is live.
 *
 * A follower given a position while something else is live would jump to a section of a song nobody
 * is singing, so the mode is checked before the index is read rather than after.
 */
internal fun livePositionOrNull(source: Presenting, forMode: Presenting, index: Int): Int? =
    if (source == forMode) index else null

/** A field worth sending, or nothing — an empty string and "not set" mean the same thing on the wire. */
internal fun nullIfEmpty(value: String): String? = value.ifEmpty { null }

/**
 * The canonical verse reference to announce, or none.
 *
 * Only for scripture, and only once the verse names a book the loaded bible actually knows: the code
 * is resolved through that bible, so a verse from a translation that is no longer loaded — or a
 * partially-filled verse mid-selection — has no code rather than a wrong one.
 */
internal fun <T> liveVerseCode(
    source: Presenting,
    bookName: String,
    chapter: Int,
    verseNumber: Int,
    bookIdByName: (String) -> Int?,
    codeReference: (bookId: Int, chapter: Int, verse: Int) -> T?,
): T? {
    if (source != Presenting.BIBLE) return null
    if (bookName.isEmpty()) return null
    val bookId = bookIdByName(bookName) ?: return null
    return codeReference(bookId, chapter, verseNumber)
}

/** Whether OBS should be connected to, as opposed to disconnected from. */
internal fun shouldConnectObs(settings: OBSSettings): Boolean = settings.enabled

/**
 * Whether a slide announced by the primary is one that can actually be fetched.
 *
 * The snapshot sent on connect always carries this event, with an empty id when the primary has no
 * presentation open — asking for that slide's bytes would be a request for nothing, answered with a
 * 404 and logged as a failed mirror.
 */
internal fun hasFetchableSlide(slideId: String): Boolean = slideId.isNotBlank()

/**
 * Whether the mirrored background cache has to be emptied before fetching.
 *
 * The per-file check is "does this exist locally", so a background the primary has replaced under
 * the same name would otherwise never be re-downloaded — the stale copy would satisfy the check
 * forever.
 */
internal fun shouldInvalidateBackgroundCache(backgroundsUpdatedSignal: Int): Boolean =
    backgroundsUpdatedSignal > 0

/**
 * The settings the output should actually render with.
 *
 * Only the rendering paths see the mirrored backgrounds; editing and persistence keep using this
 * instance's own, so a follower never saves the primary's backgrounds over its own configuration.
 */
internal fun withMirroredBackgrounds(
    settings: AppSettings,
    mirrored: BackgroundSettings?,
): AppSettings = if (mirrored == null) settings else settings.copy(backgroundSettings = mirrored)

/** Whether this is the first update check this install has ever run. */
internal fun isFirstEverUpdateCheck(lastCheckTimestamp: Long): Boolean = lastCheckTimestamp == 0L

/**
 * Whether an update check's outcome should be put in front of the operator.
 *
 * The first check ever is shown whatever it found — that is the one chance to ask how often they
 * want checking done. Every check after it only interrupts when there is actually an update, so a
 * routine "you are up to date" never appears unasked.
 */
internal fun shouldShowUpdateResult(firstEverCheck: Boolean, result: UpdateCheckResult): Boolean =
    firstEverCheck || result is UpdateCheckResult.Available

/**
 * Whether the window should be restored to the position and size it was left at.
 *
 * Only a floating window has its own geometry worth restoring, and only once it has actually been
 * saved: a negative coordinate is the "never saved" value, and restoring to it would put the window
 * off-screen.
 */
internal fun shouldRestoreWindowGeometry(isFloating: Boolean, savedX: Int): Boolean =
    isFloating && savedX >= 0

/** Whether media is what the presentation-live flag should report. */
internal fun isPresentationLive(presentingMode: Presenting): Boolean =
    presentingMode == Presenting.PRESENTATION

/**
 * The line to select for a section chosen remotely, or none.
 *
 * A remote caller says -1 when it means "the whole section" rather than a line within it, so any
 * negative index is normalised to that rather than passed through as a position.
 */
internal fun remoteSongLineIndex(requestedLineIndex: Int): Int =
    if (requestedLineIndex >= 0) requestedLineIndex else -1

/** Whether taking a song section live also has to switch the output over to lyrics. */
internal fun shouldSwitchToLyrics(presentingMode: Presenting): Boolean =
    presentingMode != Presenting.LYRICS

/**
 * Whether a lower-third sequence finishing should clear the output.
 *
 * Only when the lower third is still what is on screen: the sequence runs on its own clock, so by
 * the time it ends the operator may have moved on, and clearing then would blank their new content.
 */
internal fun shouldClearAfterLowerThird(presentingMode: Presenting): Boolean =
    presentingMode == Presenting.LOWER_THIRD

/** Whether a section change is worth telling connected phones about — only while songs are live. */
internal fun shouldBroadcastSongSection(presentingMode: Presenting): Boolean =
    presentingMode == Presenting.LYRICS

/** Whether the output going empty is worth announcing. */
internal fun shouldBroadcastDisplayCleared(presentingMode: Presenting): Boolean =
    presentingMode == Presenting.NONE

/**
 * Whether this instance is in a position to drive another one.
 *
 * Both halves matter: a link that is merely configured as Controller but not connected has nothing
 * to send to, and a connected Controlled follower must never send — it receives.
 */
internal fun isControllerConnected(status: InstanceLinkStatus, role: InstanceLinkRole): Boolean =
    status == InstanceLinkStatus.CONNECTED && role == InstanceLinkRole.CONTROLLER

/**
 * The kind of activity a no-approval remote action is reported as.
 *
 * These arrive as strings over the wire, so an unrecognised one has to become something rather than
 * nothing: the toast exists so the operator can see what a remote client just did and block them if
 * it was not wanted, and an action that produced no toast at all would be the one worth seeing.
 */
internal fun remoteActionType(actionType: String): RemoteEventType = when (actionType) {
    "present" -> RemoteEventType.PRESENT
    "upload" -> RemoteEventType.UPLOAD
    "clear" -> RemoteEventType.CLEAR
    else -> RemoteEventType.PRESENT
}

/** The floor every transition is held to, below which a fade reads as a flicker. */
internal const val MIN_TRANSITION_MS = 100

/**
 * How long a mode-level crossfade runs: the longer of the two that are switched on.
 *
 * One duration has to serve both, because a crossfade between scripture and a song is a single
 * transition — taking the shorter of the two would cut it off part-way.
 */
internal fun modeCrossfadeDuration(bible: BibleSettings, song: SongSettings): Int = maxOf(
    if (bible.crossfade) bible.transitionDuration.toInt() else 0,
    if (song.crossfade) song.transitionDuration.toInt() else 0,
).coerceAtLeast(MIN_TRANSITION_MS)

/** Whether any output is pinned to [mode], and so is still showing it. */
internal fun isAnyScreenLockedTo(locks: Map<Int, Presenting>, mode: Presenting): Boolean =
    locks.values.any { it == mode }

/**
 * Whether clearing the output should fade it out first.
 *
 * Not when a screen is locked to what is being cleared: that screen goes on showing the content, so
 * fading the shared alpha would dim it on a display nobody asked to clear. Only scripture and songs
 * fade at all; everything else clears instantly.
 */
internal fun shouldFadeOnClear(
    mode: Presenting,
    anyScreenLocked: Boolean,
    bible: BibleSettings,
    song: SongSettings,
): Boolean = !anyScreenLocked && when (mode) {
    Presenting.BIBLE -> bible.fadeOut
    Presenting.LYRICS -> song.fadeOut
    else -> false
}

/** How long that fade-out runs, per content type, never below [MIN_TRANSITION_MS]. */
internal fun fadeOutDuration(mode: Presenting, bible: BibleSettings, song: SongSettings): Int = when (mode) {
    Presenting.BIBLE -> bible.transitionDuration.toInt()
    Presenting.LYRICS -> song.transitionDuration.toInt()
    else -> DEFAULT_FADE_OUT_MS
}.coerceAtLeast(MIN_TRANSITION_MS)

/** The shortest an announcement stays up, however fast the speed slider is wound. */
internal const val MIN_ANNOUNCEMENT_DISPLAY_MS = 500L

/** Whether an announcement fades between states, as opposed to cutting or sliding. */
internal fun isFadeAnnouncement(animationType: String): Boolean =
    animationType == Constants.ANIMATION_FADE

/**
 * Whether the announcement slides in rather than being shown by this code at all.
 *
 * A directional slide is animated by the presenter itself, so here the text is simply swapped —
 * running a fade over it as well would fight the animation already in flight.
 */
internal fun isSlidingAnnouncement(animationType: String): Boolean =
    animationType != Constants.ANIMATION_FADE && animationType != Constants.ANIMATION_NONE

/**
 * Whether clearing an announcement should fade it away.
 *
 * Only when something was actually on screen: fading out from nothing spends the animation's length
 * showing an empty screen before the operator's next content can appear.
 */
internal fun shouldFadeOutAnnouncement(isFade: Boolean, wasEmpty: Boolean): Boolean =
    isFade && !wasEmpty

/** Whether the announcement clears itself after a set number of loops, rather than staying up. */
internal fun isFiniteAnnouncementLoop(loopCount: Int): Boolean = loopCount > 0

/**
 * How long an announcement stays up in total.
 *
 * The speed slider reads the other way round — a higher value means faster — so the configured
 * duration is subtracted from the slider's span rather than used directly.
 */
internal fun announcementDisplayMs(sliderSpan: Long, animationDuration: Long, loopCount: Int): Long =
    (sliderSpan - animationDuration).coerceAtLeast(MIN_ANNOUNCEMENT_DISPLAY_MS) * loopCount

/**
 * Whether the bundled KJV has to be written out and made primary.
 *
 * Both halves matter: a folder chosen but no translation picked yet is a half-finished setup the
 * operator is in the middle of, and dropping a Bible into it would pick for them.
 */
internal fun shouldBundleDefaultBible(settings: BibleSettings): Boolean =
    settings.storageDirectory.isEmpty() && settings.primaryBible.isEmpty()

/**
 * Makes sure [dir] is a directory that can be written to, and names the problem when it cannot be.
 *
 * The bundling code used to call `mkdirs()` and ignore what it answered, so a folder that could not
 * be created surfaced a sentence later as `FileNotFoundException: …/Bibles/kjv1769.spb (No such
 * file or directory)` — a message about a file, for a problem with its parent, which reads as a
 * missing resource in the app rather than a home directory the process cannot write into.
 *
 * The reason is a fixed phrase, never the path: it is reported to the crash service, and a user's
 * home directory carries their name.
 *
 * @return null when the directory is ready, otherwise why it is not.
 */
internal fun bundledBibleDirProblem(dir: File): String? = when {
    dir.isDirectory -> if (dir.canWrite()) null else "not writable"
    dir.exists() -> "occupied by a file"
    dir.mkdirs() -> null
    else -> "could not be created"
}

/**
 * Why the bundled Bible cannot be installed into [dir], or null when it can.
 *
 * A folder that cannot be written to only blocks the bundle when [fileName] is not already sitting
 * in it. A read-only Bibles folder holding the copy from an earlier launch — a managed install, or
 * one locked down after the fact and whose settings were later reset — is a working setup, and
 * skipping it would throw that configuration away and send the user to the setup wizard instead.
 * `canWrite()` on a directory is unreliable on Windows besides, so this branch can fire spuriously.
 *
 * The reason is a fixed phrase, never the path: it is reported to the crash service, and a user's
 * home directory carries their name.
 */
internal fun bundledBibleSkipReason(dir: File, fileName: String): String? {
    val problem = bundledBibleDirProblem(dir) ?: return null
    return if (File(dir, fileName).isFile) null else problem
}

/** Whether the licence has already been accepted, at this build's version of it or a later one. */
internal fun isEulaAccepted(acceptedVersion: Int, currentVersion: Int): Boolean =
    acceptedVersion >= currentVersion

/**
 * Whether first-run setup should be offered.
 *
 * Not once it has been dismissed, and not to an install that already has both a Bible and a song
 * folder — that is a working setup, whatever the flag says, and interrupting it would be noise.
 */
internal fun shouldShowSetupWizard(settings: AppSettings): Boolean {
    val bibleReady = settings.bibleSettings.primaryBible.isNotEmpty()
    val songsReady = settings.songSettings.storageDirectory.isNotEmpty()
    return !settings.setupWizardShown && !(bibleReady && songsReady)
}

/**
 * Whether the Developer menu is shown.
 *
 * Always in a dev build, and in a packaged one only when deliberately asked for — the forced-window
 * flag, or the secret keypress, which unlocks it for that session alone.
 */
internal fun shouldShowDeveloperMenu(
    isRelease: Boolean,
    forceDevWindow: Boolean,
    unlocked: Boolean,
): Boolean = !isRelease || forceDevWindow || unlocked

/**
 * Whether a queued remote request is covered by the decision the operator just made.
 *
 * Allow/block apply to a client, not to the one request on screen, so every other request already
 * queued from that client is settled the same way rather than asked about again. A blank id is the
 * unattributable case, and takes the whole queue with it: there is no client to ask about next.
 */
internal fun remoteEventTargetsClient(eventClientId: String, decidedClientId: String): Boolean =
    eventClientId == decidedClientId || decidedClientId.isBlank()

/** Whether the client awaiting approval is another instance following this one. */
internal fun isInstanceLinkFollowerClient(clientId: String, followers: Set<String>): Boolean =
    clientId.isNotBlank() && clientId in followers

/**
 * Whether generated lower thirds have somewhere to be written.
 *
 * Checked against the filesystem rather than the setting alone: a folder configured once and since
 * moved or deleted would otherwise send the generator to a path it cannot save into.
 */
internal fun isUsableOutputDir(path: String): Boolean = path.isNotEmpty() && File(path).isDirectory

/** The URL a follower streams a mirrored media item from, carrying the key when one is set. */
internal fun instanceLinkMediaStreamUrl(
    host: String,
    port: Int,
    apiKey: String,
    itemId: String,
): String {
    val keyParam = if (apiKey.isNotEmpty()) "?${Constants.QUERY_PARAM_API_KEY}=$apiKey" else ""
    return "http://$host:$port${Constants.ENDPOINT_MEDIA_STREAM}/$itemId$keyParam"
}

/** Whether going live with [mode] should also raise the output windows — clearing must not. */
internal fun shouldShowPresenterWindowFor(mode: Presenting): Boolean = mode != Presenting.NONE

/** Whether the link is anything other than fully down, connecting included. */
internal fun isInstanceLinkActive(status: InstanceLinkStatus): Boolean =
    status != InstanceLinkStatus.DISCONNECTED

/** Whether this output draws the configured background, which is a separate switch per layout. */
internal fun showsOutputBackground(assignment: ScreenAssignment): Boolean =
    if (assignment.isLowerThird) assignment.showLowerThirdBackground
    else assignment.showFullscreenBackground

/** What an output is showing: its own lock when it has one, otherwise whatever is live. */
internal fun effectiveOutputMode(
    locks: Map<Int, Presenting>,
    index: Int,
    presentingMode: Presenting,
): Presenting = locks[index] ?: presentingMode

/**
 * Whether a mode change on this output crossfades rather than cuts.
 *
 * Only between two pieces of content: crossfading from or to nothing is a fade, which the per-type
 * fade settings own, and running both would fade twice over the same moment.
 */
internal fun isScreenCrossfadeActive(
    bible: BibleSettings,
    song: SongSettings,
    mode: Presenting,
    previousMode: Presenting,
): Boolean = (bible.crossfade || song.crossfade) &&
    mode != Presenting.NONE && previousMode != Presenting.NONE

/**
 * The URL the on-screen Q&A QR code points at.
 *
 * The tunnel URL when there is one, so a phone on mobile data can reach it; the LAN address
 * otherwise.
 */
internal fun qaQrCodeUrl(tunnelUrl: String, serverUrl: String): String =
    "${tunnelUrl.ifEmpty { serverUrl }}/qa"

/**
 * Whether output windows have to fall back to ordinary windows on this machine.
 *
 * Only when there is genuinely nowhere else to put them — no second display and no DeckLink device
 * — and only in a build that asked for it, so a release install with one monitor shows nothing
 * extra rather than a stray window over the operator's screen.
 */
internal fun isDevWindowedFallback(
    isRelease: Boolean,
    forceDevWindow: Boolean,
    realWindowCount: Int,
): Boolean = (!isRelease || forceDevWindow) && realWindowCount == 0

/** How many fallback windows to open — at least one whenever they are used at all. */
internal fun devFallbackWindowCount(devWindowedFallback: Boolean, configured: Int): Int =
    if (devWindowedFallback) configured.coerceAtLeast(1) else 0

/** How far each extra fallback window is cascaded, so several do not stack exactly on each other. */
internal fun devFallbackWindowOffsetDp(index: Int): Int = DEV_WINDOW_BASE_OFFSET_DP + index * DEV_WINDOW_STEP_DP

/** Whether this output's picture goes out over SDI rather than to a display. */
internal fun isDeckLinkPrimaryOutput(assignment: ScreenAssignment): Boolean =
    assignment.targetType == Constants.TARGET_TYPE_DECKLINK

/** Whether this output's key signal is aimed at a DeckLink device rather than a display. */
internal fun isDeckLinkKeyOutput(assignment: ScreenAssignment): Boolean =
    assignment.keyTargetType == Constants.TARGET_TYPE_DECKLINK

/**
 * Whether this output's key signal goes to a DeckLink device that has actually been chosen.
 *
 * The call sites this replaced also tested `keyTargetDisplay >= 0` alongside [hasKeyOutput], which
 * is the very expression [hasKeyOutput] is defined as — so that clause could never fail once the
 * first had passed, and is dropped rather than carried as a condition no input can reach.
 */
internal fun hasDeckLinkKeyOutput(assignment: ScreenAssignment): Boolean =
    assignment.hasKeyOutput && isDeckLinkKeyOutput(assignment)

/** Whether this output's key signal goes to an ordinary display instead. */
internal fun hasScreenKeyOutput(assignment: ScreenAssignment): Boolean =
    assignment.hasKeyOutput && assignment.keyTargetType == Constants.TARGET_TYPE_SCREEN

/**
 * Which display a key output lands on.
 *
 * Matched on the bounds saved with the assignment first, because display indices are reordered by
 * the OS when monitors are plugged or unplugged; the saved index is only the fallback for a layout
 * whose bounds no longer match anything.
 */
internal fun keyOutputScreenIndex(matchedByBounds: Int?, savedIndex: Int): Int =
    matchedByBounds ?: savedIndex

/** How long a Lottie composition runs, from its own frame count and rate. Never zero. */
internal fun lottieCompositionDurationMs(durationFrames: Float, frameRate: Float): Long =
    ((durationFrames / frameRate) * MILLIS_PER_SECOND_F).toLong().coerceAtLeast(1L)

/** How long pre-rendered frames run, from how many there are and the rate they were rendered at. */
internal fun lottiePrerenderDurationMs(frameCount: Int, fps: Int): Long =
    (frameCount * MILLIS_PER_SECOND_L / fps).coerceAtLeast(1L)

/**
 * Whether the clip holds on a frame partway through.
 *
 * The frame is stored as a fraction of the clip, so a value outside 0..1 names no frame in it and
 * is treated as no hold at all rather than clamped to the start or the end.
 */
internal fun lottieHasPause(pauseAtFrame: Boolean, pauseFrame: Float): Boolean =
    pauseAtFrame && pauseFrame in 0f..1f

/** When the hold starts, or -1 when there is none — an instant no elapsed time ever reaches. */
internal fun lottiePauseAtMs(totalDurationMs: Long, pauseFrame: Float, hasPause: Boolean): Long =
    if (hasPause) (totalDurationMs * pauseFrame).toLong() else -1L

/** The clip's whole length on the wall clock, the hold included. */
internal fun lottieGrandTotalMs(
    totalDurationMs: Long,
    hasPause: Boolean,
    pauseDurationMs: Long,
): Long = totalDurationMs + if (hasPause) pauseDurationMs else 0L

/**
 * How far through the clip [elapsedMs] is, as a fraction.
 *
 * Three stretches when the clip holds: play up to the hold, sit on it, then play what is left over
 * whatever time remains. The last stretch is re-scaled rather than resumed at the original rate,
 * because the hold has already consumed wall-clock time the clip's own timeline does not know
 * about — playing on at the old rate would run past the end.
 */
internal fun lottieProgressAt(
    elapsedMs: Long,
    totalDurationMs: Long,
    hasPause: Boolean,
    pauseFrame: Float,
    pauseAtMs: Long,
    pauseDurationMs: Long,
): Float {
    if (!hasPause) return (elapsedMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
    return when {
        elapsedMs < pauseAtMs -> (elapsedMs.toFloat() / totalDurationMs).coerceIn(0f, pauseFrame)
        elapsedMs < pauseAtMs + pauseDurationMs -> pauseFrame
        else -> {
            val postElapsed = elapsedMs - pauseAtMs - pauseDurationMs
            val postTotalMs = (totalDurationMs - pauseAtMs).coerceAtLeast(1L)
            (pauseFrame + (postElapsed.toFloat() / postTotalMs) * (1f - pauseFrame)).coerceIn(0f, 1f)
        }
    }
}

/** Which pre-rendered frame a fraction of the way through the clip lands on. */
internal fun lottieFrameIndexFor(progress: Float, frameCount: Int): Int =
    (progress * (frameCount - 1)).roundToInt().coerceIn(0, frameCount - 1)

/**
 * The identifier the open-ping carries, or none.
 *
 * An opted-out install still pings — the count of churches opening the app is what the map is for —
 * but carries nothing that follows it between launches. Taken as a function rather than a value
 * because minting the id writes it to disk: computing one for an opted-out install and discarding
 * it would leave the very identifier that was opted out of sitting in their home directory.
 */
internal fun analyticsInstallId(enabled: Boolean, installId: () -> String): String? =
    if (enabled) installId() else null

/** The positions in [all] that are not [primary] — the displays an output may be opened on. */
internal fun <T> nonPrimaryIndices(all: List<T>, primary: T): List<Int> =
    all.indices.filter { all[it] != primary }

/** How many real outputs there are to drive: every non-primary display, plus every SDI device. */
internal fun presenterWindowCount(nonPrimaryScreens: Int, deckLinkDevices: Int): Int =
    nonPrimaryScreens + deckLinkDevices

/** Whether the slot at [index] is one of the fallback windows rather than a real output. */
internal fun isFallbackWindowSlot(
    devWindowedFallback: Boolean,
    index: Int,
    realWindowCount: Int,
): Boolean = devWindowedFallback && index >= realWindowCount

/** Which fallback window a slot is, counting from the first one after the real outputs. */
internal fun fallbackSlotIndex(index: Int, realWindowCount: Int): Int = index - realWindowCount

/**
 * Whether the mirrored picture cache has to be emptied.
 *
 * The counterpart to [shouldInvalidateBackgroundCache]: cached pictures are keyed by folder and
 * position only, so an image the primary has replaced at the same position would be served from the
 * stale copy forever. Zero is the value the flow replays on subscribe, which announces nothing.
 */
internal fun shouldInvalidatePictureCache(picturesUpdatedSignal: Int): Boolean =
    picturesUpdatedSignal != 0

/**
 * Whether a clear signal is a fresh one rather than the value replayed on subscribe.
 *
 * The flow replays its current value to every new subscriber — including on re-subscribe when the
 * role changes — so acting on equality would clear the output every time the link was reconfigured.
 */
internal fun isFreshClearSignal(signal: Int, lastSeen: Int): Boolean = signal != lastSeen

/**
 * Whether media going away is worth one last broadcast.
 *
 * Only on the edge: connected phones need one "nothing loaded" to drop their now-playing view, but
 * repeating it on every poll of an idle app would be a message every half-second forever.
 */
internal fun shouldBroadcastMediaCleared(isLoaded: Boolean, wasLoaded: Boolean): Boolean =
    !isLoaded && wasLoaded

/** How much of a remote question is shown in the approval prompt. */
internal const val MAX_REMOTE_EVENT_TITLE = 80

/** The question text put in front of the operator, cut to what the prompt can show. */
internal fun remoteEventTitle(text: String): String = text.take(MAX_REMOTE_EVENT_TITLE)

/** Whether a follower is allowed to push items into this instance's schedule. */
internal fun canPushToSchedule(link: InstanceLinkSettings): Boolean = link.allowPushToSchedule

/** Where a translation lives, as an absolute path, so a follower can be handed the file itself. */
internal fun bibleFilePath(storageDirectory: String, translation: String): String =
    File(storageDirectory, translation).absolutePath

/** Whether an index names a display that is actually attached. */
internal fun isScreenIndexValid(index: Int, screenCount: Int): Boolean = index in 0 until screenCount

/** Whether this output has no display chosen for it at all. */
internal fun hasNoPrimaryTarget(assignment: ScreenAssignment): Boolean =
    assignment.targetDisplay == Constants.KEY_TARGET_NONE

/**
 * Which display this output opens on, or none when nothing usable is left.
 *
 * Three attempts in order of how much they can be trusted: the bounds saved with the assignment,
 * which survive the OS reordering display indices; the saved index, but only if it still names an
 * attached display; and finally this output's own position in the list of non-primary displays, so
 * a configuration written on a different machine still lands somewhere rather than nowhere.
 */
internal fun primaryOutputScreenIndex(
    matchedByBounds: Int?,
    savedDisplay: Int,
    screenCount: Int,
    positionalFallback: Int?,
): Int? = matchedByBounds
    ?: savedDisplay.takeIf { isScreenIndexValid(it, screenCount) }
    ?: positionalFallback

/** The smallest the main window may open at, whatever was saved. */
internal const val MIN_MAIN_WINDOW_WIDTH = 800
internal const val MIN_MAIN_WINDOW_HEIGHT = 600

/**
 * The size the main window opens at.
 *
 * Only a floating window has its own size worth restoring; maximized and fullscreen take the
 * primary display's, so a saved size from a monitor since unplugged cannot strand the window at a
 * shape the current display cannot show.
 *
 * A restored floating size is clamped at both ends, because it is written back verbatim on exit and
 * nothing else checks it. Too large is the monitor case the paragraph above only covers for
 * maximized windows: a window sized on a 4K display and reopened on a laptop panel would otherwise
 * open with its own edges past the screen, and a window has to be draggable by an edge to be
 * recoverable. Too small is the same trap from the other side — the window can be dragged down to
 * nothing, and that nothing is what it reopens as.
 *
 * The lower bound wins ties: on a display smaller than [MIN_MAIN_WINDOW_WIDTH] the window is opened
 * too big for the screen rather than too small to use, since an oversized window can still be moved
 * and resized while a collapsed one cannot.
 */
internal fun startupWindowSize(
    isFloating: Boolean,
    savedWidth: Int,
    savedHeight: Int,
    primaryWidth: Int,
    primaryHeight: Int,
): Pair<Int, Int> {
    if (!isFloating) return primaryWidth to primaryHeight
    val width = savedWidth.coerceIn(MIN_MAIN_WINDOW_WIDTH, maxOf(primaryWidth, MIN_MAIN_WINDOW_WIDTH))
    val height = savedHeight.coerceIn(MIN_MAIN_WINDOW_HEIGHT, maxOf(primaryHeight, MIN_MAIN_WINDOW_HEIGHT))
    return width to height
}

/**
 * The position the main window opens at.
 *
 * The primary display's own origin is the fallback rather than 0,0 — on a multi-monitor desktop the
 * origin can belong to a different display, which would open the window on the wrong screen.
 */
internal fun startupWindowPosition(
    restoreGeometry: Boolean,
    savedX: Int,
    savedY: Int,
    primaryX: Int,
    primaryY: Int,
): Pair<Int, Int> = if (restoreGeometry) savedX to savedY else primaryX to primaryY
