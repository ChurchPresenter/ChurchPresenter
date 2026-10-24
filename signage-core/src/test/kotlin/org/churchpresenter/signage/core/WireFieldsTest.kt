package org.churchpresenter.signage.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every field of the shared fixtures and of each message reads back as the value it carries, so a
 * renamed or mistyped field fails here rather than in the other repository.
 */
class WireFieldsTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    @Test
    fun theSharedManifestCarriesEachValue() {
        val manifest = SignageJson.decodeFromString(Manifest.serializer(), fixture("manifest-v1.json"))
        assertEquals("cp-7f3a", manifest.ownerId)
        assertEquals(1080 to 1920, manifest.screen.width to manifest.screen.height)
        assertEquals(listOf("Welcome", "Events"), manifest.playlists.map { it.name })
        val (picture, video) = manifest.playlists[0].items
        assertEquals(MediaKind.IMAGE to 8_000L, picture.kind to picture.durationMs)
        assertEquals(Transition.CROSSFADE to 800L, picture.transition to picture.transitionMs)
        assertEquals(MediaKind.VIDEO to null, video.kind to video.durationMs)
        assertEquals("Grace Church", manifest.idleCard.title)
        assertEquals("3".repeat(64), manifest.idleCard.logoSha256)
        assertEquals("welcome", manifest.defaultPlaylistId)
        assertEquals(false, manifest.diagnostics)
    }

    @Test
    fun theSharedReleaseIndexCarriesEachValue() {
        val release = SignageJson.decodeFromString(SignageRelease.serializer(), fixture("signage-release-v1.json"))
        assertEquals(Triple("0.2.0", 1, 1), Triple(release.version, release.protocolMin, release.protocolMax))
        val arm = release.assets.getValue(Platform.LINUX_ARM64)
        assertEquals("churchpresenter-signage-linux-arm64.tar.gz", arm.name)
        assertEquals(61_234_567L to "1".repeat(64), arm.size to arm.sha256)
    }

    @Test
    fun playerMessagesCarryEachValue() {
        val hello = PlayerMessage.Hello(
            "p", "26.0.9", 1, 2, "m", 1920, 1080, Platform.LINUX_X64, "4821", listOf(Features.WIPE),
        )
        assertEquals("p" to "26.0.9", hello.playerId to hello.agentVersion)
        assertEquals(1 to 2, hello.protocolMin to hello.protocolMax)
        assertEquals("m" to "4821", hello.manifestVersion to hello.pairingCode)
        assertEquals(1920 to 1080, hello.screenWidth to hello.screenHeight)
        assertEquals(Platform.LINUX_X64 to listOf("wipe"), hello.platform to hello.features)

        val status = PlayerMessage.Status("welcome", "a".repeat(64), "schedule", 5, 6, CecState.WORKING, listOf("e"))
        assertEquals("welcome" to "a".repeat(64), status.showingPlaylistId to status.showingItemSha256)
        assertEquals(Triple("schedule", 5L, 6L), Triple(status.reason, status.lastSyncMs, status.freeDiskBytes))
        assertEquals(CecState.WORKING to listOf("e"), status.cec to status.errors)

        val preview = PlayerMessage.Preview("/9j/", 480, 270, 7)
        assertEquals("/9j/" to 7L, preview.jpegBase64 to preview.takenAtMs)
        assertEquals(480 to 270, preview.width to preview.height)

        val progress = PlayerMessage.UpdateProgress("26.0.9", UpdateState.FAILED, "why")
        assertEquals("26.0.9" to UpdateState.FAILED, progress.version to progress.state)
        assertEquals("why", progress.message)
    }

    @Test
    fun serverMessagesCarryEachValue() {
        assertEquals(2, ServerMessage.Unsupported(2).serverProtocol)
        assertEquals("4821", ServerMessage.PairingPending("4821").code)
        val paired = ServerMessage.Paired("t", "o", "Main PC")
        assertEquals(Triple("t", "o", "Main PC"), Triple(paired.token, paired.ownerId, paired.ownerName))
        assertEquals("v", ServerMessage.ManifestChanged("v").version)
        val override = ScreenOverride(OverrideAction.Play("welcome"), Until.Time(5), 9)
        assertEquals(OverrideAction.Play("welcome"), override.action)
        assertEquals(Until.Time(5) to 9L, override.until to override.setAtMs)
        assertEquals(override, ServerMessage.SetOverride(override).override)
        assertEquals(5, ServerMessage.Identify(5).seconds)
        assertEquals(320, ServerMessage.PreviewRequest(320).maxWidth)
        val offer = ServerMessage.UpdateAvailable("26.0.9", 1, 2, "s", "/p", "{}", "sig")
        assertEquals(Triple("26.0.9", 1, 2), Triple(offer.agentVersion, offer.protocolMin, offer.protocolMax))
        assertEquals(Triple("s", "/p", "{}"), Triple(offer.sha256, offer.path, offer.release))
        assertEquals("sig", offer.releaseSignature)
    }

    @Test
    fun aDecisionCarriesWhatToShowAndWhy() {
        val manifest = SignageJson.decodeFromString(Manifest.serializer(), fixture("manifest-v1.json"))
        // A Monday morning in Chicago: no block runs, so the default playlist plays.
        val decision = decide(manifest, null, Instant.parse("2026-10-19T15:00:00Z")) { true }
        assertEquals(Show.PlaylistShow("welcome"), decision.show)
        assertEquals("welcome", (decision.show as Show.PlaylistShow).playlistId)
        assertEquals(TvPower.ON to Reason.DEFAULT, decision.tv to decision.reason)
    }
}
