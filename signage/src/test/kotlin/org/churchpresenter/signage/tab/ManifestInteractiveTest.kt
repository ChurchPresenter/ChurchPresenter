package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.KioskPin
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlaylistItem
import org.churchpresenter.signage.hub.PairedScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A touch page and the screen's touchscreen settings in its manifest: the page goes only to a player
 * that can show it, with its reset time kept in bounds and its sites, and the PIN (as its hash) and
 * the corner gesture with the screen.
 */
class ManifestInteractiveTest {
    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_ARM64, "26.0.41", 1920, 1080, 0)
    private val signup = LinkPlaylist(
        "link:s", "Sign-up", "https://signup.example.org", LinkKind.WEB,
        refreshMinutes = 10, smooth = true, interactive = InteractiveLink(5, listOf("pay.example.com")),
    )
    private val rotation = RotationPlaylist("rotation:r", "Mornings", listOf(RotationStep("link:s", 30)))

    private fun build(features: List<String>, plan: ScreenPlan = ScreenPlan()) = buildManifest(
        screen = screen,
        ownerId = "owner",
        settings = SignageSettings(screens = mapOf("p" to plan), added = listOf(signup, rotation)),
        playlists = listOf(HashedPlaylist(signup), HashedPlaylist(rotation)),
        timeZone = "UTC",
        features = features,
    ).first

    @Test
    fun aTouchPageGoesOnlyToAPlayerThatCanShowIt() {
        assertTrue(build(listOf(Features.WEB, Features.ROTATION)).playlists.none { it.id == "link:s" })
        val manifest = build(listOf(Features.WEB, Features.ROTATION, Features.INTERACTIVE))
        val page = manifest.playlists.single { it.id == "link:s" }.items.single()
        assertEquals(
            PlaylistItem(
                kind = MediaKind.WEB,
                url = "https://signup.example.org",
                interactive = true,
                // Five seconds is below the least a page waits: it waits that least instead.
                idleResetMs = InteractiveLink.IDLE_RESET_SECONDS.first * 1_000L,
                allowedHosts = listOf("pay.example.com"),
            ),
            page,
        )
        assertNull(page.refreshMs, "a touch page never reloads under someone's hands")
        assertNull(page.maxFps)
        assertTrue(manifest.playlists.single { it.id == "rotation:r" }.items.single().interactive)
    }

    @Test
    fun theScreensPinAndGestureGoWithIt() {
        val hash = KioskPin.hash("2468")
        val screen = build(
            listOf(Features.WEB),
            ScreenPlan(kioskPinHash = hash, kioskGesture = false, kioskGesturePlaylistId = "link:s"),
        ).screen
        assertEquals(hash, screen.kioskPinHash)
        assertFalse(screen.kioskGesture)
        assertEquals("link:s", screen.kioskGesturePlaylistId)
        val plain = build(listOf(Features.WEB)).screen
        assertNull(plain.kioskPinHash)
        assertTrue(plain.kioskGesture)
    }

    @Test
    fun theSitesTypedAreSplitAndTheTouchPlaylistsFound() {
        assertEquals(
            listOf("pay.example.com", "maps.example.org", "x.example"),
            InteractiveLink.hostsFrom(" pay.example.com, maps.example.org\nx.example ,, "),
        )
        val folder = SignageFolderState(
            listOf(
                PlaylistSummary("link:s", "Sign-up", kind = SourceKind.WEB),
                PlaylistSummary("rotation:r", "Mornings", kind = SourceKind.ROTATION),
                PlaylistSummary("folder:", "Welcome"),
            ),
            SignageSettings(added = listOf(signup, rotation)),
            ready = true,
        )
        assertEquals(listOf("link:s", "rotation:r"), touchPlaylists(folder).map { it.id })
    }

    @Test
    fun aFullAddressPastedAfterThePrefillReplacesIt() {
        assertEquals("https://example.org/a", withoutPrefixTwice("https://www.https://example.org/a"))
        assertEquals("https://example.org/a", withoutPrefixTwice("https://example.org/ahttps://www."))
        assertEquals("https://www.example.org", withoutPrefixTwice("https://www.www.example.org"))
        assertEquals("https://www.example.org", withoutPrefixTwice("https://www.example.org"))
        assertEquals("https://www.", withoutPrefixTwice("https://www."))
        assertFalse(isLinkAddress("https://www."), "the prefill alone is no address")
        assertTrue(isLinkAddress("https://www.example.org"))
        assertEquals("rtsp://camera/live", withoutPrefixTwice("rtsp://camera/live"), "no prefill, left as typed")
        assertEquals("abchttps://www.", withoutPrefixTwice("abchttps://www."), "no whole address: left as typed")
    }

    @Test
    fun whatIsNoAddressSaysSoAndIsGuessedAWebPage() {
        listOf("example.org", "https://", "mailto:office@example.org", "http://bad host/", "")
            .forEach { assertFalse(isLinkAddress(it), it) }
        // Half-typed or odd addresses are taken for web pages, never for streams.
        listOf("example.org", "mailto:office@example.org", "https://www.", "http://bad host/")
            .forEach { assertEquals(LinkKind.WEB, LinkKind.guess(it), it) }
    }
}
