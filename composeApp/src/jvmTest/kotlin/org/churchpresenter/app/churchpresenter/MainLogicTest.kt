package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.settings.InstanceLinkSettings
import org.churchpresenter.settings.OBSSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.ServerSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckResult
import org.churchpresenter.app.churchpresenter.utils.UpdateInfo
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules the entry point applies at startup, checked without starting one.
 */
@Suppress("LargeClass")
class MainLogicTest {

    // ── Renderer ────────────────────────────────────────────────────────────────

    @Test
    fun `macOS is pinned to Metal`() {
        // Left to choose, skiko falls back to OpenGL there and crashes on some machines.
        assertTrue(shouldForceMetalRenderer("Mac OS X"))
        assertTrue(shouldForceMetalRenderer("macOS"))
    }

    @Test
    fun `every other platform chooses for itself`() {
        assertFalse(shouldForceMetalRenderer("Windows 11"))
        assertFalse(shouldForceMetalRenderer("Linux"))
        assertFalse(shouldForceMetalRenderer(""))
    }

    // ── The single-instance lock ────────────────────────────────────────────────

    @Test
    fun `the lock uses its own port when nothing overrides it`() {
        assertEquals(47632, singleInstanceLockPort(override = null, default = 47632))
    }

    @Test
    fun `a second dev instance can be given its own port`() {
        assertEquals(47633, singleInstanceLockPort(override = "47633", default = 47632))
    }

    @Test
    fun `an unreadable override falls back rather than failing to start`() {
        assertEquals(47632, singleInstanceLockPort(override = "not-a-port", default = 47632))
        assertEquals(47632, singleInstanceLockPort(override = "", default = 47632))
    }

    // ── Language ────────────────────────────────────────────────────────────────

    @Test
    fun `the saved language is the one started in`() {
        assertEquals(Language.ENGLISH, resolveStartupLanguage("en"))
        Language.entries.forEach { assertEquals(it, resolveStartupLanguage(it.code)) }
    }

    @Test
    fun `a language this build no longer has falls back to english`() {
        // A settings file naming a removed language must not stop the app starting.
        assertEquals(Language.ENGLISH, resolveStartupLanguage("xx"))
        assertEquals(Language.ENGLISH, resolveStartupLanguage(""))
    }

    // ── DeckLink ────────────────────────────────────────────────────────────────

    @Test
    fun `outputs are counted only when the driver is there`() {
        assertEquals(3, deckLinkOutputCount(available = true) { 3 })
    }

    @Test
    fun `no driver means no outputs, and the count is never asked for`() {
        var asked = false
        assertEquals(0, deckLinkOutputCount(available = false) { asked = true; 3 })
        assertFalse(asked, "listing devices without the driver is what crashes")
    }

    // ── Companion connections ───────────────────────────────────────────────────

    private fun connection(id: String = "c1", autoConnect: Boolean = false, deviceId: String = "d1") =
        CompanionSatelliteSettings(id = id, host = "10.0.0.2", autoConnect = autoConnect, deviceId = deviceId)

    @Test
    fun `a connection with something already live is brought up`() {
        val c = connection()
        assertTrue(shouldConnectCompanion(hasLiveSlot = true, autoConnect = false, lastSeen = c, current = c))
    }

    @Test
    fun `a connection set to auto-connect is brought up`() {
        val c = connection(autoConnect = true)
        assertTrue(shouldConnectCompanion(hasLiveSlot = false, autoConnect = true, lastSeen = null, current = c))
    }

    @Test
    fun `a connection just edited is brought up even with auto-connect off`() {
        // An edit is an explicit action, so it connects; that is what separates it from startup.
        val before = connection(deviceId = "d1")
        val after = before.copy(deviceId = "d2")
        assertTrue(shouldConnectCompanion(hasLiveSlot = false, autoConnect = false, lastSeen = before, current = after))
    }

    @Test
    fun `a connection seen for the first time at startup is left alone`() {
        // Keeps startup opt-in: never seen before, nothing live, auto-connect off.
        val c = connection()
        assertFalse(shouldConnectCompanion(hasLiveSlot = false, autoConnect = false, lastSeen = null, current = c))
    }

    @Test
    fun `a connection seen before and unchanged is left alone`() {
        val c = connection()
        assertFalse(shouldConnectCompanion(hasLiveSlot = false, autoConnect = false, lastSeen = c, current = c))
    }

    @Test
    fun `a blank device id has to be minted before use`() {
        assertTrue(needsGeneratedDeviceId(connection(deviceId = "")))
        assertTrue(needsGeneratedDeviceId(connection(deviceId = "   ")))
        assertFalse(needsGeneratedDeviceId(connection(deviceId = "d1")))
    }

    @Test
    fun `minting a device id leaves every other connection alone`() {
        val all = listOf(connection(id = "a", deviceId = ""), connection(id = "b", deviceId = "keep"))

        val updated = withGeneratedDeviceId(all, id = "a", deviceId = "minted")

        assertEquals("minted", updated.first { it.id == "a" }.deviceId)
        assertEquals("keep", updated.first { it.id == "b" }.deviceId)
    }

    @Test
    fun `minting against an id that is not there changes nothing`() {
        val all = listOf(connection(id = "a", deviceId = "keep"))
        assertEquals(all, withGeneratedDeviceId(all, id = "missing", deviceId = "minted"))
    }

    // ── The instance link ───────────────────────────────────────────────────────

    private fun link(
        enabled: Boolean = true,
        autoConnect: Boolean = true,
        host: String = "10.0.0.9",
        port: Int = 8080,
    ) = InstanceLinkSettings(enabled = enabled, autoConnect = autoConnect, primaryHost = host, primaryPort = port)

    @Test
    fun `a link that is on, set to, and addressed dials out`() {
        assertTrue(shouldAutoConnectInstanceLink(link()))
    }

    @Test
    fun `a link missing any one of those does not`() {
        assertFalse(shouldAutoConnectInstanceLink(link(enabled = false)))
        assertFalse(shouldAutoConnectInstanceLink(link(autoConnect = false)))
        assertFalse(shouldAutoConnectInstanceLink(link(host = "")))
        assertFalse(shouldAutoConnectInstanceLink(link(port = 0)))
    }

    @Test
    fun `switching the link off drops it now rather than at the next launch`() {
        assertTrue(shouldDisconnectInstanceLink(link(enabled = false)))
        assertFalse(shouldDisconnectInstanceLink(link(enabled = true)))
    }

    @Test
    fun `only a real change of intent is persisted`() {
        assertTrue(instanceLinkEnabledChanged(link(enabled = false), enabled = true))
        assertFalse(instanceLinkEnabledChanged(link(enabled = true), enabled = true))
    }

    // ── The key remote callers must present ─────────────────────────────────────

    @Test
    fun `a key is required only once key checking is switched on`() {
        assertEquals("s3cret", activeApiKey(ServerSettings(apiKeyEnabled = true, apiKey = "s3cret")))
    }

    @Test
    fun `with key checking off no key is required, whatever is stored`() {
        // The stored key survives being switched off, so the flag has to be what decides.
        assertEquals("", activeApiKey(ServerSettings(apiKeyEnabled = false, apiKey = "s3cret")))
    }

    // ── The tunnel ──────────────────────────────────────────────────────────────

    @Test
    fun `only a connected tunnel counts as up`() {
        assertTrue(isTunnelConnected(TunnelStatus.Connected("https://x.trycloudflare.com")))
        assertFalse(isTunnelConnected(TunnelStatus.Idle))
        assertFalse(isTunnelConnected(TunnelStatus.Starting))
        assertFalse(isTunnelConnected(TunnelStatus.Downloading))
        assertFalse(isTunnelConnected(TunnelStatus.Error("gone")))
    }

    @Test
    fun `the drop is the moment it goes down, not every moment after`() {
        assertTrue(tunnelJustDropped(previouslyConnected = true, isConnected = false))
        assertFalse(tunnelJustDropped(previouslyConnected = false, isConnected = false))
        assertFalse(tunnelJustDropped(previouslyConnected = true, isConnected = true))
        assertFalse(tunnelJustDropped(previouslyConnected = false, isConnected = true))
    }

    // ── Browser source outputs ──────────────────────────────────────────────────

    @Test
    fun `a configured browser source output is used`() {
        val configured = ScreenAssignment(displayMode = "browser_source")
        assertEquals(configured, browserSourceOutputAt(listOf(configured), 0))
    }

    @Test
    fun `an output that was never configured falls back rather than failing`() {
        assertEquals(ScreenAssignment(), browserSourceOutputAt(emptyList(), 0))
        assertEquals(ScreenAssignment(), browserSourceOutputAt(listOf(ScreenAssignment()), 3))
    }

    // ── What a follower is told is live ─────────────────────────────────────────

    @Test
    fun `media is reported live only while media is what is on the output`() {
        assertTrue(isMediaLive(Presenting.MEDIA))
        assertFalse(isMediaLive(Presenting.LYRICS))
        assertFalse(isMediaLive(Presenting.NONE))
    }

    @Test
    fun `a position is announced only for the content it belongs to`() {
        assertEquals(4, livePositionOrNull(Presenting.LYRICS, Presenting.LYRICS, 4))
        assertEquals(null, livePositionOrNull(Presenting.BIBLE, Presenting.LYRICS, 4))
    }

    // ── What goes out on the wire ───────────────────────────────────────────────

    @Test
    fun `an empty field is sent as nothing rather than as blank`() {
        assertEquals(null, nullIfEmpty(""))
        assertEquals("a value", nullIfEmpty("a value"))
    }

    @Test
    fun `a verse reference is resolved through the loaded bible`() {
        val code = liveVerseCode(
            source = Presenting.BIBLE,
            bookName = "John", chapter = 3, verseNumber = 16,
            bookIdByName = { 43 },
            codeReference = { bookId, chapter, verse -> Triple(bookId, chapter, verse) },
        )
        assertEquals(Triple(43, 3, 16), code)
    }

    @Test
    fun `no reference is announced when scripture is not what is live`() {
        val code = liveVerseCode(
            source = Presenting.LYRICS,
            bookName = "John", chapter = 3, verseNumber = 16,
            bookIdByName = { error("must not be consulted when scripture is not live") },
            codeReference = { _, _, _ -> error("must not be consulted") },
        )
        assertEquals(null, code)
    }

    @Test
    fun `a half-filled verse has no reference rather than a wrong one`() {
        // Mid-selection the book can still be empty; resolving that would name the wrong passage.
        val code = liveVerseCode(
            source = Presenting.BIBLE,
            bookName = "", chapter = 3, verseNumber = 16,
            bookIdByName = { error("must not be consulted without a book") },
            codeReference = { _, _, _ -> error("must not be consulted") },
        )
        assertEquals(null, code)
    }

    @Test
    fun `a book the loaded bible does not know has no reference`() {
        val code = liveVerseCode(
            source = Presenting.BIBLE,
            bookName = "Some Other Book", chapter = 1, verseNumber = 1,
            bookIdByName = { null },
            codeReference = { _, _, _ -> error("must not be consulted for an unknown book") },
        )
        assertEquals(null, code)
    }

    // ── OBS ─────────────────────────────────────────────────────────────────────

    @Test
    fun `obs is connected only while the integration is switched on`() {
        assertTrue(shouldConnectObs(OBSSettings(enabled = true)))
        assertFalse(shouldConnectObs(OBSSettings(enabled = false)))
    }

    // ── Mirroring the primary ───────────────────────────────────────────────────

    @Test
    fun `a slide the primary actually has is fetched`() {
        assertTrue(hasFetchableSlide("deck-1"))
    }

    @Test
    fun `the empty slide in a connect snapshot is not fetched`() {
        // The snapshot always carries this event; an empty id means no deck is open, and asking
        // for it would 404 and be logged as a failed mirror.
        assertFalse(hasFetchableSlide(""))
        assertFalse(hasFetchableSlide("   "))
    }

    @Test
    fun `an announced background change empties the cache first`() {
        // The per-file check is "does this exist locally", so a replaced background under the same
        // name would otherwise satisfy it forever.
        assertTrue(shouldInvalidateBackgroundCache(1))
        assertFalse(shouldInvalidateBackgroundCache(0))
    }

    @Test
    fun `mirrored backgrounds are used for rendering`() {
        val mirrored = BackgroundSettings(defaultBackgroundColor = "#123456")
        val effective = withMirroredBackgrounds(AppSettings(), mirrored)

        assertEquals(mirrored, effective.backgroundSettings)
    }

    @Test
    fun `without a mirror the instance keeps its own backgrounds`() {
        // The follower must never persist the primary's backgrounds over its own configuration.
        val own = AppSettings()
        assertEquals(own, withMirroredBackgrounds(own, null))
    }

    // ── Update checks ───────────────────────────────────────────────────────────

    private fun available() = UpdateCheckResult.Available(
        UpdateInfo(latestVersion = "1.2.3", releaseUrl = "https://example.invalid", releaseNotes = ""),
    )

    @Test
    fun `an install that has never checked is recognised`() {
        assertTrue(isFirstEverUpdateCheck(0L))
        assertFalse(isFirstEverUpdateCheck(1L))
    }

    @Test
    fun `the very first check is shown whatever it found`() {
        // That is the one chance to ask how often the operator wants checking done.
        assertTrue(shouldShowUpdateResult(firstEverCheck = true, result = UpdateCheckResult.UpToDate))
        assertTrue(shouldShowUpdateResult(firstEverCheck = true, result = available()))
    }

    @Test
    fun `later checks interrupt only when there is something to install`() {
        assertTrue(shouldShowUpdateResult(firstEverCheck = false, result = available()))
        assertFalse(
            shouldShowUpdateResult(firstEverCheck = false, result = UpdateCheckResult.UpToDate),
            "a routine up-to-date result must not appear unasked",
        )
    }

    // ── Window placement ────────────────────────────────────────────────────────

    @Test
    fun `a floating window is restored where it was left`() {
        assertTrue(shouldRestoreWindowGeometry(isFloating = true, savedX = 0))
        assertTrue(shouldRestoreWindowGeometry(isFloating = true, savedX = 640))
    }

    @Test
    fun `a maximized window is not restored to a floating geometry`() {
        assertFalse(shouldRestoreWindowGeometry(isFloating = false, savedX = 640))
    }

    @Test
    fun `geometry that was never saved is not restored`() {
        // A negative coordinate is the never-saved value; honouring it puts the window off-screen.
        assertFalse(shouldRestoreWindowGeometry(isFloating = true, savedX = -1))
    }

    // ── What the presentation-live flag reports ─────────────────────────────────

    @Test
    fun `a presentation is reported live only while it is on the output`() {
        assertTrue(isPresentationLive(Presenting.PRESENTATION))
        assertFalse(isPresentationLive(Presenting.LYRICS))
        assertFalse(isPresentationLive(Presenting.NONE))
    }

    // ── Acting on a remote request ──────────────────────────────────────────────

    @Test
    fun `a line chosen remotely is used as given`() {
        assertEquals(0, remoteSongLineIndex(0))
        assertEquals(3, remoteSongLineIndex(3))
    }

    @Test
    fun `any negative line means the whole section`() {
        // Callers say -1 for "the section, not a line in it"; other negatives must mean the same
        // rather than being passed through as a position.
        assertEquals(-1, remoteSongLineIndex(-1))
        assertEquals(-1, remoteSongLineIndex(-7))
    }

    @Test
    fun `taking a section live switches the output over when something else is on it`() {
        assertTrue(shouldSwitchToLyrics(Presenting.BIBLE))
        assertTrue(shouldSwitchToLyrics(Presenting.NONE))
        assertFalse(shouldSwitchToLyrics(Presenting.LYRICS), "already lyrics, so just change section")
    }

    @Test
    fun `a finished lower third clears only while it is still on screen`() {
        // The sequence runs on its own clock; by the time it ends the operator may have moved on,
        // and clearing then would blank whatever they moved to.
        assertTrue(shouldClearAfterLowerThird(Presenting.LOWER_THIRD))
        assertFalse(shouldClearAfterLowerThird(Presenting.LYRICS))
        assertFalse(shouldClearAfterLowerThird(Presenting.NONE))
    }

    @Test
    fun `a section change is announced only while songs are live`() {
        assertTrue(shouldBroadcastSongSection(Presenting.LYRICS))
        assertFalse(shouldBroadcastSongSection(Presenting.BIBLE))
    }

    @Test
    fun `an emptied output is announced only when it is actually empty`() {
        assertTrue(shouldBroadcastDisplayCleared(Presenting.NONE))
        assertFalse(shouldBroadcastDisplayCleared(Presenting.LYRICS))
    }

    // ── Driving another instance ────────────────────────────────────────────────

    @Test
    fun `a connected controller may drive the other instance`() {
        assertTrue(isControllerConnected(InstanceLinkStatus.CONNECTED, InstanceLinkRole.CONTROLLER))
    }

    @Test
    fun `a controller that is not connected has nothing to drive`() {
        assertFalse(isControllerConnected(InstanceLinkStatus.DISCONNECTED, InstanceLinkRole.CONTROLLER))
        assertFalse(isControllerConnected(InstanceLinkStatus.CONNECTING, InstanceLinkRole.CONTROLLER))
        assertFalse(isControllerConnected(InstanceLinkStatus.ERROR, InstanceLinkRole.CONTROLLER))
    }

    @Test
    fun `a follower never drives, however well connected`() {
        // It receives; sending from here would fight the primary for the output.
        assertFalse(isControllerConnected(InstanceLinkStatus.CONNECTED, InstanceLinkRole.CONTROLLED))
    }

    // ── What a remote client just did ───────────────────────────────────────────

    @Test
    fun `each no-approval action is reported as its own kind`() {
        assertEquals(RemoteEventType.PRESENT, remoteActionType("present"))
        assertEquals(RemoteEventType.UPLOAD, remoteActionType("upload"))
        assertEquals(RemoteEventType.CLEAR, remoteActionType("clear"))
    }

    @Test
    fun `an action nobody recognises is still reported`() {
        // The toast is how the operator sees what a remote client did and blocks them if it was
        // unwanted — an action that produced no toast is the one worth seeing.
        assertEquals(RemoteEventType.PRESENT, remoteActionType("something-new"))
        assertEquals(RemoteEventType.PRESENT, remoteActionType(""))
    }

    // ── Transitions on the output ───────────────────────────────────────────────

    @Test
    fun `the crossfade takes the longer of the two that are on`() {
        // One duration serves both, because a crossfade from scripture to a song is a single
        // transition — the shorter of the two would cut it off part-way.
        val duration = modeCrossfadeDuration(
            BibleSettings(crossfade = true, transitionDuration = 400f),
            SongSettings(crossfade = true, transitionDuration = 900f),
        )
        assertEquals(900, duration)
    }

    @Test
    fun `a crossfade that is switched off contributes nothing`() {
        val duration = modeCrossfadeDuration(
            BibleSettings(crossfade = false, transitionDuration = 5000f),
            SongSettings(crossfade = true, transitionDuration = 400f),
        )
        assertEquals(400, duration, "the disabled one must not set the length")
    }

    @Test
    fun `with both off the transition still has a floor`() {
        // Below this a fade reads as a flicker rather than a transition.
        val duration = modeCrossfadeDuration(
            BibleSettings(crossfade = false), SongSettings(crossfade = false),
        )
        assertEquals(MIN_TRANSITION_MS, duration)
    }

    @Test
    fun `a screen pinned to the mode being cleared is noticed`() {
        assertTrue(isAnyScreenLockedTo(mapOf(0 to Presenting.LYRICS), Presenting.LYRICS))
        assertFalse(isAnyScreenLockedTo(mapOf(0 to Presenting.BIBLE), Presenting.LYRICS))
        assertFalse(isAnyScreenLockedTo(emptyMap(), Presenting.LYRICS))
    }

    @Test
    fun `clearing scripture or a song fades it out first`() {
        assertTrue(
            shouldFadeOnClear(Presenting.BIBLE, false, BibleSettings(fadeOut = true), SongSettings()),
        )
        assertTrue(
            shouldFadeOnClear(Presenting.LYRICS, false, BibleSettings(), SongSettings(fadeOut = true)),
        )
    }

    @Test
    fun `nothing fades while a screen is still showing it`() {
        // That display was not asked to clear, and the alpha is shared — fading would dim it there.
        assertFalse(
            shouldFadeOnClear(Presenting.LYRICS, true, BibleSettings(), SongSettings(fadeOut = true)),
        )
    }

    @Test
    fun `content with no fade of its own clears instantly`() {
        assertFalse(
            shouldFadeOnClear(Presenting.PICTURES, false, BibleSettings(fadeOut = true), SongSettings(fadeOut = true)),
        )
        assertFalse(
            shouldFadeOnClear(Presenting.BIBLE, false, BibleSettings(fadeOut = false), SongSettings()),
        )
    }

    @Test
    fun `each content type fades for its own configured time`() {
        assertEquals(
            700,
            fadeOutDuration(Presenting.BIBLE, BibleSettings(transitionDuration = 700f), SongSettings()),
        )
        assertEquals(
            300,
            fadeOutDuration(Presenting.LYRICS, BibleSettings(), SongSettings(transitionDuration = 300f)),
        )
    }

    @Test
    fun `anything else falls back, and nothing goes below the floor`() {
        assertEquals(500, fadeOutDuration(Presenting.MEDIA, BibleSettings(), SongSettings()))
        assertEquals(
            MIN_TRANSITION_MS,
            fadeOutDuration(Presenting.BIBLE, BibleSettings(transitionDuration = 10f), SongSettings()),
        )
    }

    // ── Announcements ───────────────────────────────────────────────────────────

    @Test
    fun `fade is told apart from cutting and sliding`() {
        assertTrue(isFadeAnnouncement(Constants.ANIMATION_FADE))
        assertFalse(isFadeAnnouncement(Constants.ANIMATION_NONE))
        assertFalse(isFadeAnnouncement("SLIDE_LEFT"))
    }

    @Test
    fun `a sliding announcement is left to the presenter to animate`() {
        // Running a fade here as well would fight the animation already in flight.
        assertTrue(isSlidingAnnouncement("SLIDE_LEFT"))
        assertTrue(isSlidingAnnouncement("SCROLL_UP"))
        assertFalse(isSlidingAnnouncement(Constants.ANIMATION_FADE))
        assertFalse(isSlidingAnnouncement(Constants.ANIMATION_NONE))
    }

    @Test
    fun `clearing fades out only when something was on screen`() {
        assertTrue(shouldFadeOutAnnouncement(isFade = true, wasEmpty = false))
    }

    @Test
    fun `fading out from an empty screen is skipped`() {
        // It would otherwise spend the animation's length showing nothing before the next content.
        assertFalse(shouldFadeOutAnnouncement(isFade = true, wasEmpty = true))
        assertFalse(shouldFadeOutAnnouncement(isFade = false, wasEmpty = false))
    }

    @Test
    fun `a loop count clears itself, and none stays up`() {
        assertTrue(isFiniteAnnouncementLoop(1))
        assertTrue(isFiniteAnnouncementLoop(5))
        assertFalse(isFiniteAnnouncementLoop(0), "zero means stay up until stopped by hand")
    }

    @Test
    fun `the speed slider reads the other way round`() {
        // A higher configured value means faster, so it is subtracted from the slider's span.
        assertEquals(20_500L, announcementDisplayMs(sliderSpan = 30_500L, animationDuration = 10_000L, loopCount = 1))
    }

    @Test
    fun `each loop adds its own time on screen`() {
        assertEquals(61_000L, announcementDisplayMs(sliderSpan = 30_500L, animationDuration = 0L, loopCount = 2))
    }

    @Test
    fun `an announcement is never on screen for less than the floor`() {
        assertEquals(
            MIN_ANNOUNCEMENT_DISPLAY_MS,
            announcementDisplayMs(sliderSpan = 30_500L, animationDuration = 99_999L, loopCount = 1),
        )
    }

    // ── First run ───────────────────────────────────────────────────────────────

    @Test
    fun `an install with no bible at all gets the bundled one`() {
        assertTrue(shouldBundleDefaultBible(BibleSettings()))
    }

    @Test
    fun `a setup already part-way through is left alone`() {
        // A folder chosen but no translation picked yet is a choice the operator is mid-way through.
        assertFalse(shouldBundleDefaultBible(BibleSettings(storageDirectory = "/bibles")))
        assertFalse(shouldBundleDefaultBible(BibleSettings(primaryBible = "kjv1769.spb")))
    }

    @Test
    fun `a bibles folder that can be created is reported as ready`() {
        val dir = Files.createTempDirectory("cp-bundle-dir").toFile()
        try {
            val nested = File(dir, "Bibles")

            assertNull(bundledBibleDirProblem(nested), "a fresh folder should be created")
            assertTrue(nested.isDirectory)
            assertNull(bundledBibleDirProblem(nested), "and asking again must accept the one made")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a bibles path occupied by a file is named, not left to fail at the write`() {
        // The reported failure surfaced as FileNotFoundException about kjv1769.spb, which reads as a
        // missing app resource rather than a folder that could not be made.
        val dir = Files.createTempDirectory("cp-bundle-dir").toFile()
        try {
            val occupied = File(dir, "Bibles").apply { writeText("not a folder") }

            assertEquals("occupied by a file", bundledBibleDirProblem(occupied))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a bibles folder that cannot be created is named`() {
        val dir = Files.createTempDirectory("cp-bundle-dir").toFile()
        try {
            // A directory cannot be made below a plain file, whatever the filesystem.
            val blocked = File(File(dir, "wall").apply { writeText("x") }, "Bibles")

            assertEquals("could not be created", bundledBibleDirProblem(blocked))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a read-only bibles folder that already holds the bible is used, not skipped`() {
        // Losing a working configuration is worse than the problem being guarded against: the copy
        // is already there, so nothing needs writing and the settings must still be pointed at it.
        val dir = Files.createTempDirectory("cp-bundle-dir").toFile()
        try {
            val bibles = File(dir, "Bibles").apply { mkdirs() }
            File(bibles, "kjv1769.spb").writeText("bible")
            // Windows ignores setWritable on a directory (it is a DOS attribute, not a permission),
            // which is the same reason the "not writable" branch can fire spuriously there. Nothing
            // to assert on a platform that cannot produce the state.
            bibles.setWritable(false)
            if (bibles.canWrite()) return

            assertEquals("not writable", bundledBibleDirProblem(bibles), "the folder is still unwritable")
            assertNull(
                bundledBibleSkipReason(bibles, "kjv1769.spb"),
                "but an existing copy means there is nothing to write, so the bundle is not skipped",
            )
        } finally {
            File(dir, "Bibles").setWritable(true)
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a bibles folder that cannot be made is skipped when it holds no bible`() {
        val dir = Files.createTempDirectory("cp-bundle-dir").toFile()
        try {
            val blocked = File(File(dir, "wall").apply { writeText("x") }, "Bibles")

            assertEquals("could not be created", bundledBibleSkipReason(blocked, "kjv1769.spb"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the licence counts as accepted at this version or a later one`() {
        assertTrue(isEulaAccepted(acceptedVersion = 1, currentVersion = 1))
        assertTrue(isEulaAccepted(acceptedVersion = 2, currentVersion = 1))
    }

    @Test
    fun `a licence never accepted, or accepted at an older version, is asked again`() {
        assertFalse(isEulaAccepted(acceptedVersion = 0, currentVersion = 1))
        assertFalse(isEulaAccepted(acceptedVersion = 1, currentVersion = 2))
    }

    @Test
    fun `a fresh install is offered the setup wizard`() {
        assertTrue(shouldShowSetupWizard(AppSettings()))
    }

    @Test
    fun `an install that already works is not interrupted by the wizard`() {
        // Both a bible and a song folder means a working setup, whatever the flag says.
        val ready = AppSettings(
            bibleSettings = BibleSettings(primaryBible = "kjv1769.spb"),
            songSettings = SongSettings(storageDirectory = "/songs"),
        )
        assertFalse(shouldShowSetupWizard(ready))
    }

    @Test
    fun `a half-configured install is still offered the wizard`() {
        val bibleOnly = AppSettings(bibleSettings = BibleSettings(primaryBible = "kjv1769.spb"))
        assertTrue(shouldShowSetupWizard(bibleOnly))
        val songsOnly = AppSettings(songSettings = SongSettings(storageDirectory = "/songs"))
        assertTrue(shouldShowSetupWizard(songsOnly))
    }

    @Test
    fun `once dismissed the wizard stays dismissed`() {
        assertFalse(shouldShowSetupWizard(AppSettings(setupWizardShown = true)))
    }

    // ── The Developer menu ──────────────────────────────────────────────────────

    @Test
    fun `a dev build always has the developer menu`() {
        assertTrue(shouldShowDeveloperMenu(isRelease = false, forceDevWindow = false, unlocked = false))
    }

    @Test
    fun `a packaged build hides it until it is deliberately asked for`() {
        assertFalse(shouldShowDeveloperMenu(isRelease = true, forceDevWindow = false, unlocked = false))
        assertTrue(shouldShowDeveloperMenu(isRelease = true, forceDevWindow = true, unlocked = false))
        assertTrue(shouldShowDeveloperMenu(isRelease = true, forceDevWindow = false, unlocked = true))
    }

    // ── Remote approval queue ───────────────────────────────────────────────────

    @Test
    fun `allowing a client settles every request already queued from it`() {
        // Allow/block apply to the client, not to the one request the dialog happens to be showing.
        assertTrue(remoteEventTargetsClient(eventClientId = "phone-1", decidedClientId = "phone-1"))
        assertFalse(remoteEventTargetsClient(eventClientId = "phone-2", decidedClientId = "phone-1"))
    }

    @Test
    fun `an unattributable decision takes the whole queue with it`() {
        // There is no client to ask about next, so leaving entries queued would strand them.
        assertTrue(remoteEventTargetsClient(eventClientId = "phone-1", decidedClientId = ""))
        assertTrue(remoteEventTargetsClient(eventClientId = "", decidedClientId = ""))
    }

    @Test
    fun `a following instance is recognised among the clients asking`() {
        assertTrue(isInstanceLinkFollowerClient("overflow-room", setOf("overflow-room", "phone-1")))
        assertFalse(isInstanceLinkFollowerClient("phone-1", setOf("overflow-room")))
    }

    @Test
    fun `an unidentified client is never taken for a follower`() {
        assertFalse(isInstanceLinkFollowerClient("", setOf("overflow-room")))
        assertFalse(isInstanceLinkFollowerClient("", emptySet()))
    }

    // ── Lower-third output folder ───────────────────────────────────────────────

    @Test
    fun `a real folder is somewhere generated lower thirds can be written`() {
        val dir = Files.createTempDirectory("cp-main-logic-output").toFile()
        try {
            assertTrue(isUsableOutputDir(dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an unset or vanished folder is not`() {
        // A folder configured once and since moved would otherwise send the generator nowhere.
        val dir = Files.createTempDirectory("cp-main-logic-output").toFile()
        try {
            assertFalse(isUsableOutputDir(""))
            assertFalse(isUsableOutputDir(File(dir, "gone").absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a file where a folder should be is refused`() {
        val dir = Files.createTempDirectory("cp-main-logic-output").toFile()
        try {
            val file = File(dir, "not-a-folder.json").apply { writeText("{}") }
            assertFalse(isUsableOutputDir(file.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── Mirrored media ──────────────────────────────────────────────────────────

    @Test
    fun `a follower streams mirrored media from the primary`() {
        assertEquals(
            "http://192.168.1.10:8080/api/media/stream/item-7",
            instanceLinkMediaStreamUrl("192.168.1.10", 8080, apiKey = "", itemId = "item-7"),
        )
    }

    @Test
    fun `the key travels with the request when the primary requires one`() {
        assertEquals(
            "http://192.168.1.10:8080/api/media/stream/item-7?apiKey=s3cret",
            instanceLinkMediaStreamUrl("192.168.1.10", 8080, apiKey = "s3cret", itemId = "item-7"),
        )
    }

    // ── Going live ──────────────────────────────────────────────────────────────

    @Test
    fun `going live raises the output windows, clearing does not`() {
        Presenting.entries.filter { it != Presenting.NONE }
            .forEach { assertTrue(shouldShowPresenterWindowFor(it), it.name) }
        assertFalse(shouldShowPresenterWindowFor(Presenting.NONE))
    }

    @Test
    fun `a link that is connecting is not treated as down`() {
        assertTrue(isInstanceLinkActive(InstanceLinkStatus.CONNECTED))
        assertTrue(isInstanceLinkActive(InstanceLinkStatus.CONNECTING))
        assertFalse(isInstanceLinkActive(InstanceLinkStatus.DISCONNECTED))
    }

    // ── Per-output rendering ────────────────────────────────────────────────────

    @Test
    fun `each layout has its own background switch`() {
        val lowerThird = ScreenAssignment(
            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            showLowerThirdBackground = false,
            showFullscreenBackground = true,
        )
        assertFalse(showsOutputBackground(lowerThird), "the fullscreen switch must not stand in for it")

        val fullscreen = ScreenAssignment(
            displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
            showLowerThirdBackground = false,
            showFullscreenBackground = true,
        )
        assertTrue(showsOutputBackground(fullscreen))
    }

    @Test
    fun `an output locked to a tab goes on showing it`() {
        val locks = mapOf(0 to Presenting.LYRICS)
        assertEquals(Presenting.LYRICS, effectiveOutputMode(locks, 0, Presenting.BIBLE))
    }

    @Test
    fun `an unlocked output follows whatever is live`() {
        assertEquals(Presenting.BIBLE, effectiveOutputMode(mapOf(0 to Presenting.LYRICS), 1, Presenting.BIBLE))
        assertEquals(Presenting.BIBLE, effectiveOutputMode(emptyMap(), 0, Presenting.BIBLE))
    }

    @Test
    fun `switching between two pieces of content crossfades`() {
        assertTrue(
            isScreenCrossfadeActive(
                BibleSettings(crossfade = true), SongSettings(crossfade = false),
                Presenting.LYRICS, Presenting.BIBLE,
            )
        )
        assertTrue(
            isScreenCrossfadeActive(
                BibleSettings(crossfade = false), SongSettings(crossfade = true),
                Presenting.LYRICS, Presenting.BIBLE,
            )
        )
    }

    @Test
    fun `coming from or going to an empty screen is a fade, not a crossfade`() {
        // The per-type fade settings own that moment; running both would fade twice over it.
        val on = BibleSettings(crossfade = true)
        assertFalse(isScreenCrossfadeActive(on, SongSettings(), Presenting.BIBLE, Presenting.NONE))
        assertFalse(isScreenCrossfadeActive(on, SongSettings(), Presenting.NONE, Presenting.BIBLE))
    }

    @Test
    fun `crossfade switched off everywhere cuts`() {
        assertFalse(
            isScreenCrossfadeActive(BibleSettings(), SongSettings(), Presenting.LYRICS, Presenting.BIBLE)
        )
    }

    @Test
    fun `the QA code points at the tunnel when there is one`() {
        // Only the tunnel URL is reachable from a phone that is not on the venue's WiFi.
        assertEquals(
            "https://abc.trycloudflare.com/qa",
            qaQrCodeUrl("https://abc.trycloudflare.com", "http://10.0.0.5:8080"),
        )
    }

    @Test
    fun `it falls back to the LAN address otherwise`() {
        assertEquals("http://10.0.0.5:8080/qa", qaQrCodeUrl("", "http://10.0.0.5:8080"))
    }

    // ── The dev fallback windows ────────────────────────────────────────────────

    @Test
    fun `a dev machine with nowhere to output falls back to a window`() {
        assertTrue(isDevWindowedFallback(isRelease = false, forceDevWindow = false, realWindowCount = 0))
        assertTrue(isDevWindowedFallback(isRelease = true, forceDevWindow = true, realWindowCount = 0))
    }

    @Test
    fun `a real output is always preferred to the fallback`() {
        assertFalse(isDevWindowedFallback(isRelease = false, forceDevWindow = false, realWindowCount = 1))
        assertFalse(isDevWindowedFallback(isRelease = true, forceDevWindow = true, realWindowCount = 2))
    }

    @Test
    fun `a packaged build that did not ask for it gets no stray window`() {
        assertFalse(isDevWindowedFallback(isRelease = true, forceDevWindow = false, realWindowCount = 0))
    }

    @Test
    fun `the fallback opens at least one window, however it is configured`() {
        assertEquals(1, devFallbackWindowCount(devWindowedFallback = true, configured = 0))
        assertEquals(1, devFallbackWindowCount(devWindowedFallback = true, configured = 1))
        assertEquals(3, devFallbackWindowCount(devWindowedFallback = true, configured = 3))
    }

    @Test
    fun `no fallback means no windows at all`() {
        assertEquals(0, devFallbackWindowCount(devWindowedFallback = false, configured = 3))
    }

    @Test
    fun `several fallback windows are cascaded rather than stacked`() {
        assertEquals(40, devFallbackWindowOffsetDp(0))
        assertTrue(devFallbackWindowOffsetDp(1) > devFallbackWindowOffsetDp(0))
        assertTrue(devFallbackWindowOffsetDp(2) > devFallbackWindowOffsetDp(1))
    }

    // ── DeckLink and key outputs ────────────────────────────────────────────────

    @Test
    fun `an output aimed at SDI is told apart from one aimed at a display`() {
        assertTrue(isDeckLinkPrimaryOutput(ScreenAssignment(targetType = Constants.TARGET_TYPE_DECKLINK)))
        assertFalse(isDeckLinkPrimaryOutput(ScreenAssignment(targetType = Constants.TARGET_TYPE_SCREEN)))
    }

    @Test
    fun `a key output on SDI needs a device actually chosen`() {
        val chosen = ScreenAssignment(
            keyTargetType = Constants.TARGET_TYPE_DECKLINK, keyTargetDisplay = 0,
        )
        assertTrue(hasDeckLinkKeyOutput(chosen))
    }

    @Test
    fun `a key output left unconfigured drives nothing`() {
        // KEY_TARGET_NONE is what hasKeyOutput reads as off — nothing may be pushed to a device.
        val off = ScreenAssignment(keyTargetType = Constants.TARGET_TYPE_DECKLINK)
        assertFalse(off.hasKeyOutput, "the default must be off")
        assertFalse(hasDeckLinkKeyOutput(off))
        assertFalse(hasScreenKeyOutput(off))
    }

    @Test
    fun `a key output on a display is not taken for an SDI one`() {
        val onScreen = ScreenAssignment(keyTargetType = Constants.TARGET_TYPE_SCREEN, keyTargetDisplay = 1)
        assertTrue(hasScreenKeyOutput(onScreen))
        assertFalse(hasDeckLinkKeyOutput(onScreen))
        assertFalse(isDeckLinkKeyOutput(onScreen))
    }

    @Test
    fun `an SDI key output is not also driven as a display one`() {
        // Both windows are spawned from the same assignment, so exactly one may claim it.
        val onDeckLink = ScreenAssignment(keyTargetType = Constants.TARGET_TYPE_DECKLINK, keyTargetDisplay = 0)
        assertTrue(hasDeckLinkKeyOutput(onDeckLink))
        assertFalse(hasScreenKeyOutput(onDeckLink))
    }

    @Test
    fun `a key output is placed by its saved bounds, not its saved index`() {
        // Display indices are reordered by the OS when monitors are plugged or unplugged.
        assertEquals(2, keyOutputScreenIndex(matchedByBounds = 2, savedIndex = 0))
    }

    @Test
    fun `the saved index is the fallback when the bounds match nothing`() {
        assertEquals(0, keyOutputScreenIndex(matchedByBounds = null, savedIndex = 0))
    }

    // ── The lower-third playback clock ──────────────────────────────────────────

    @Test
    fun `a composition runs for its own frame count at its own rate`() {
        assertEquals(2_000L, lottieCompositionDurationMs(durationFrames = 60f, frameRate = 30f))
    }

    @Test
    fun `pre-rendered frames run for as long as they were rendered to last`() {
        assertEquals(2_000L, lottiePrerenderDurationMs(frameCount = 50, fps = 25))
    }

    @Test
    fun `no clip is ever zero-length`() {
        // A zero total would divide by zero on the very first progress reading.
        assertEquals(1L, lottieCompositionDurationMs(durationFrames = 0f, frameRate = 30f))
        assertEquals(1L, lottiePrerenderDurationMs(frameCount = 0, fps = 25))
    }

    @Test
    fun `a hold is only honoured on a frame the clip actually has`() {
        assertTrue(lottieHasPause(pauseAtFrame = true, pauseFrame = 0.5f))
        assertTrue(lottieHasPause(pauseAtFrame = true, pauseFrame = 0f))
        assertTrue(lottieHasPause(pauseAtFrame = true, pauseFrame = 1f))
    }

    @Test
    fun `a hold outside the clip is dropped rather than clamped`() {
        assertFalse(lottieHasPause(pauseAtFrame = true, pauseFrame = 1.5f))
        assertFalse(lottieHasPause(pauseAtFrame = true, pauseFrame = -0.1f))
        assertFalse(lottieHasPause(pauseAtFrame = false, pauseFrame = 0.5f))
    }

    @Test
    fun `the hold starts partway through, and never at all without one`() {
        assertEquals(1_000L, lottiePauseAtMs(totalDurationMs = 2_000L, pauseFrame = 0.5f, hasPause = true))
        // -1 is an instant no elapsed time ever reaches, so the plateau can never be entered.
        assertEquals(-1L, lottiePauseAtMs(totalDurationMs = 2_000L, pauseFrame = 0.5f, hasPause = false))
    }

    @Test
    fun `the hold adds to the clip's length on the wall clock`() {
        assertEquals(5_000L, lottieGrandTotalMs(2_000L, hasPause = true, pauseDurationMs = 3_000L))
        assertEquals(2_000L, lottieGrandTotalMs(2_000L, hasPause = false, pauseDurationMs = 3_000L))
    }

    @Test
    fun `without a hold the clip plays straight through`() {
        val progress = { ms: Long -> lottieProgressAt(ms, 2_000L, false, 0f, -1L, 0L) }
        assertEquals(0f, progress(0L))
        assertEquals(0.5f, progress(1_000L))
        assertEquals(1f, progress(2_000L))
    }

    @Test
    fun `running past the end stays at the end`() {
        assertEquals(1f, lottieProgressAt(9_999L, 2_000L, false, 0f, -1L, 0L))
    }

    @Test
    fun `a held clip plays up to the hold, sits on it, then plays out`() {
        // 2s clip holding at the halfway frame for 3s: 5s of wall clock in three stretches.
        val progress = { ms: Long -> lottieProgressAt(ms, 2_000L, true, 0.5f, 1_000L, 3_000L) }
        assertEquals(0.25f, progress(500L), "before the hold, at the clip's own rate")
        assertEquals(0.5f, progress(1_000L), "the hold begins")
        assertEquals(0.5f, progress(2_500L), "still on the same frame midway through the hold")
        assertEquals(0.5f, progress(3_999L), "still held right up to the end of it")
        assertEquals(1f, progress(5_000L), "and reaches the last frame exactly as the wall clock does")
    }

    @Test
    fun `the stretch after a hold is re-scaled, not resumed at the old rate`() {
        // The hold has consumed wall-clock time the clip's own timeline knows nothing about, so
        // playing on at the original rate would run past the end well before the clock did.
        val progress = { ms: Long -> lottieProgressAt(ms, 2_000L, true, 0.5f, 1_000L, 3_000L) }
        assertEquals(0.75f, progress(4_500L), "halfway through what is left, halfway through the rest")
    }

    @Test
    fun `the stretch before a hold never overruns the held frame`() {
        // The hold's instant is stored separately from its fraction, so a rounded or edited pair
        // that disagree must still stop on the frame the hold names rather than sail past it.
        assertEquals(0.1f, lottieProgressAt(500L, 2_000L, true, 0.1f, 1_000L, 3_000L))
    }

    @Test
    fun `progress lands on a real frame at both ends`() {
        assertEquals(0, lottieFrameIndexFor(0f, frameCount = 50))
        assertEquals(49, lottieFrameIndexFor(1f, frameCount = 50))
        assertEquals(25, lottieFrameIndexFor(0.5f, frameCount = 51))
    }

    @Test
    fun `a frame index is never off the end of the frames that exist`() {
        assertEquals(49, lottieFrameIndexFor(1.5f, frameCount = 50))
        assertEquals(0, lottieFrameIndexFor(-0.5f, frameCount = 50))
        assertEquals(0, lottieFrameIndexFor(0.5f, frameCount = 1))
    }

    // ── The open ping ───────────────────────────────────────────────────────────

    @Test
    fun `an opted-in install is identified across launches`() {
        assertEquals("install-abc", analyticsInstallId(enabled = true) { "install-abc" })
    }

    @Test
    fun `an opted-out install is never given an id to mint`() {
        // Minting one writes it to disk, so the very id opted out of must not be computed at all.
        var minted = false
        assertNull(analyticsInstallId(enabled = false) { minted = true; "install-abc" })
        assertFalse(minted, "the id must not be created only to be thrown away")
    }

    // ── Counting the outputs ────────────────────────────────────────────────────

    @Test
    fun `every display but the primary can take an output`() {
        assertEquals(listOf(0, 2), nonPrimaryIndices(listOf("left", "builtin", "right"), "builtin"))
    }

    @Test
    fun `a machine with only the primary display has nowhere to put one`() {
        assertEquals(emptyList(), nonPrimaryIndices(listOf("builtin"), "builtin"))
    }

    @Test
    fun `displays and SDI devices are both outputs to drive`() {
        assertEquals(3, presenterWindowCount(nonPrimaryScreens = 2, deckLinkDevices = 1))
        assertEquals(0, presenterWindowCount(nonPrimaryScreens = 0, deckLinkDevices = 0))
    }

    @Test
    fun `the slots past the real outputs are the fallback windows`() {
        assertFalse(isFallbackWindowSlot(devWindowedFallback = true, index = 0, realWindowCount = 1))
        assertTrue(isFallbackWindowSlot(devWindowedFallback = true, index = 1, realWindowCount = 1))
        assertEquals(0, fallbackSlotIndex(index = 1, realWindowCount = 1))
        assertEquals(2, fallbackSlotIndex(index = 3, realWindowCount = 1))
    }

    @Test
    fun `without the fallback no slot is ever one of its windows`() {
        assertFalse(isFallbackWindowSlot(devWindowedFallback = false, index = 5, realWindowCount = 1))
    }

    // ── Mirrored caches and signals ─────────────────────────────────────────────

    @Test
    fun `a picture change on the primary empties the mirrored cache`() {
        assertTrue(shouldInvalidatePictureCache(1))
        assertTrue(shouldInvalidatePictureCache(7))
    }

    @Test
    fun `the value replayed on subscribe announces nothing`() {
        assertFalse(shouldInvalidatePictureCache(0))
    }

    @Test
    fun `only a signal that moved is a fresh clear`() {
        assertTrue(isFreshClearSignal(signal = 4, lastSeen = 3))
        assertFalse(isFreshClearSignal(signal = 3, lastSeen = 3), "re-subscribing must not clear")
    }

    // ── Media broadcasts ────────────────────────────────────────────────────────

    @Test
    fun `media going away is announced exactly once`() {
        assertTrue(shouldBroadcastMediaCleared(isLoaded = false, wasLoaded = true))
    }

    @Test
    fun `an idle app does not repeat itself every poll`() {
        assertFalse(shouldBroadcastMediaCleared(isLoaded = false, wasLoaded = false))
        assertFalse(shouldBroadcastMediaCleared(isLoaded = true, wasLoaded = true))
    }

    // ── Remote prompts ──────────────────────────────────────────────────────────

    @Test
    fun `a long question is cut to what the prompt can show`() {
        val long = "q".repeat(200)
        assertEquals(MAX_REMOTE_EVENT_TITLE, remoteEventTitle(long).length)
    }

    @Test
    fun `a short question is shown whole`() {
        assertEquals("Where is the coffee?", remoteEventTitle("Where is the coffee?"))
        assertEquals("", remoteEventTitle(""))
    }

    @Test
    fun `pushing to the schedule is off unless allowed`() {
        assertFalse(canPushToSchedule(InstanceLinkSettings()))
        assertTrue(canPushToSchedule(InstanceLinkSettings(allowPushToSchedule = true)))
    }

    @Test
    fun `a follower is handed a translation's absolute path`() {
        val dir = Files.createTempDirectory("cp-main-logic-bibles").toFile()
        try {
            val expected = File(dir, "kjv1769.spb").absolutePath
            assertEquals(expected, bibleFilePath(dir.absolutePath, "kjv1769.spb"))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── Placing an output on a display ──────────────────────────────────────────

    @Test
    fun `an index names an attached display, or it does not`() {
        assertTrue(isScreenIndexValid(0, screenCount = 2))
        assertTrue(isScreenIndexValid(1, screenCount = 2))
        assertFalse(isScreenIndexValid(2, screenCount = 2))
        assertFalse(isScreenIndexValid(-1, screenCount = 2))
        assertFalse(isScreenIndexValid(0, screenCount = 0))
    }

    @Test
    fun `an output explicitly set to none is skipped`() {
        assertTrue(hasNoPrimaryTarget(ScreenAssignment(targetDisplay = Constants.KEY_TARGET_NONE)))
    }

    @Test
    fun `an output left on auto is not taken for one turned off`() {
        // -1 is "resolve at runtime", which is the default and must still open a window.
        assertFalse(hasNoPrimaryTarget(ScreenAssignment()), "auto is the default, not none")
        assertFalse(hasNoPrimaryTarget(ScreenAssignment(targetDisplay = 0)))
    }

    @Test
    fun `saved bounds beat a saved index`() {
        // The OS reorders display indices when monitors are plugged or unplugged; bounds survive it.
        assertEquals(
            2,
            primaryOutputScreenIndex(
                matchedByBounds = 2, savedDisplay = 0, screenCount = 3, positionalFallback = 1,
            ),
        )
    }

    @Test
    fun `the saved index is used when it still names an attached display`() {
        assertEquals(
            1,
            primaryOutputScreenIndex(
                matchedByBounds = null, savedDisplay = 1, screenCount = 3, positionalFallback = 2,
            ),
        )
    }

    @Test
    fun `a saved index for a display no longer attached falls back to position`() {
        // A configuration written on a different machine still lands somewhere rather than nowhere.
        assertEquals(
            2,
            primaryOutputScreenIndex(
                matchedByBounds = null, savedDisplay = 9, screenCount = 3, positionalFallback = 2,
            ),
        )
    }

    @Test
    fun `an output with nothing usable left opens no window`() {
        assertNull(
            primaryOutputScreenIndex(
                matchedByBounds = null, savedDisplay = 9, screenCount = 3, positionalFallback = null,
            ),
        )
    }

    // ── The main window's own geometry ──────────────────────────────────────────

    @Test
    fun `a floating window reopens at the size it was left`() {
        assertEquals(
            1280 to 800,
            startupWindowSize(
                isFloating = true, savedWidth = 1280, savedHeight = 800,
                primaryWidth = 3840, primaryHeight = 2160,
            ),
        )
    }

    @Test
    fun `a maximized window takes the primary display's size instead`() {
        // A size saved on a monitor since unplugged must not strand it at a shape nothing can show.
        assertEquals(
            3840 to 2160,
            startupWindowSize(
                isFloating = false, savedWidth = 1280, savedHeight = 800,
                primaryWidth = 3840, primaryHeight = 2160,
            ),
        )
    }

    @Test
    fun `a floating size saved on a bigger monitor is brought back onto this one`() {
        // Sized on a 4K display, reopened on a laptop panel. Unclamped, the window opens with its
        // own edges past the screen, and an edge you cannot reach is an edge you cannot drag.
        assertEquals(
            1920 to 1080,
            startupWindowSize(
                isFloating = true, savedWidth = 3840, savedHeight = 2160,
                primaryWidth = 1920, primaryHeight = 1080,
            ),
        )
    }

    @Test
    fun `a floating window dragged down to nothing does not reopen at nothing`() {
        // The saved size is written back verbatim on exit, so without a floor the collapse persists
        // across restarts and there is no window left to fix it with.
        assertEquals(
            MIN_MAIN_WINDOW_WIDTH to MIN_MAIN_WINDOW_HEIGHT,
            startupWindowSize(
                isFloating = true, savedWidth = 20, savedHeight = 10,
                primaryWidth = 1920, primaryHeight = 1080,
            ),
        )
    }

    @Test
    fun `on a display smaller than the floor the window opens too big rather than too small`() {
        // An oversized window can still be moved and resized; a collapsed one cannot, so the lower
        // bound wins the tie.
        assertEquals(
            MIN_MAIN_WINDOW_WIDTH to MIN_MAIN_WINDOW_HEIGHT,
            startupWindowSize(
                isFloating = true, savedWidth = 100, savedHeight = 100,
                primaryWidth = 640, primaryHeight = 480,
            ),
        )
    }

    @Test
    fun `a floating size that already fits is left exactly as it was`() {
        assertEquals(
            1600 to 900,
            startupWindowSize(
                isFloating = true, savedWidth = 1600, savedHeight = 900,
                primaryWidth = 1920, primaryHeight = 1080,
            ),
        )
    }

    @Test
    fun `a window with geometry worth restoring reopens where it was`() {
        assertEquals(
            120 to 60,
            startupWindowPosition(
                restoreGeometry = true, savedX = 120, savedY = 60, primaryX = -1920, primaryY = 0,
            ),
        )
    }

    @Test
    fun `otherwise it opens on the primary display, not at the desktop origin`() {
        // On a multi-monitor desktop the origin can belong to a different display entirely.
        assertEquals(
            -1920 to 0,
            startupWindowPosition(
                restoreGeometry = false, savedX = 120, savedY = 60, primaryX = -1920, primaryY = 0,
            ),
        )
    }
}
