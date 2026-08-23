package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.companionserver.InstanceLinkStatus

/**
 * The four decisions that separate the two Instance Link roles: whether this instance mirrors the
 * primary's output, sources content from it, replaces its backgrounds with it, and where it plays a
 * schedule item's media from.
 *
 * These used to be conditions inlined in `main.kt`'s root composable, unreachable from any test, and
 * they were wrong: everything that drives the *presenter* was gated on the connection alone, so a
 * Controller — which is defined as staying independent — mirrored the primary anyway. That is a
 * feedback loop rather than a cosmetic slip. A Controller going live sets its own presenter *and*
 * sends the command; the primary then broadcasts the resulting state back, and the Controller
 * overwrote what it had just put up with the primary's version of it. The primary's connect snapshot
 * replays its live state to every client, so it happened at connect time too, before the operator
 * touched anything.
 *
 * Hence the shape of this suite: every predicate is asserted over the **whole** status × role
 * product rather than at the one or two points a bug was found, because the property that matters is
 * the negative one — a Controller never follows, under any status.
 */
class InstanceLinkRoleGatingTest {

    private val tempDir: File = Files.createTempDirectory("cp-instance-link-role").toFile()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `only a Controlled instance mirrors the primary's output`() {
        assertTrue(shouldMirrorRemoteOutput(InstanceLinkRole.CONTROLLED))
        assertFalse(shouldMirrorRemoteOutput(InstanceLinkRole.CONTROLLER))
    }

    @Test
    fun `remote content is sourced only while a Controlled instance is connected`() {
        for (status in InstanceLinkStatus.entries) {
            for (role in InstanceLinkRole.entries) {
                val expected = status == InstanceLinkStatus.CONNECTED && role == InstanceLinkRole.CONTROLLED
                assertEquals(
                    expected,
                    shouldUseRemoteContent(status, role),
                    "shouldUseRemoteContent($status, $role)"
                )
            }
        }
    }

    @Test
    fun `a Controller never sources remote content, whatever the connection is doing`() {
        for (status in InstanceLinkStatus.entries) {
            assertFalse(
                shouldUseRemoteContent(status, InstanceLinkRole.CONTROLLER),
                "a Controller must keep its own content while $status"
            )
        }
    }

    @Test
    fun `backgrounds are mirrored only on an explicit opt-in`() {
        assertTrue(
            shouldMirrorRemoteBackgrounds(
                InstanceLinkStatus.CONNECTED, InstanceLinkRole.CONTROLLED, mirrorBackgrounds = true
            )
        )
        // Off by default — backgrounds are usually venue-specific, so a follower keeps its own.
        assertFalse(
            shouldMirrorRemoteBackgrounds(
                InstanceLinkStatus.CONNECTED, InstanceLinkRole.CONTROLLED, mirrorBackgrounds = false
            )
        )
    }

    @Test
    fun `the backgrounds opt-in cannot override the role or the connection`() {
        for (status in InstanceLinkStatus.entries) {
            for (role in InstanceLinkRole.entries) {
                val expected = status == InstanceLinkStatus.CONNECTED && role == InstanceLinkRole.CONTROLLED
                assertEquals(
                    expected,
                    shouldMirrorRemoteBackgrounds(status, role, mirrorBackgrounds = true),
                    "shouldMirrorRemoteBackgrounds($status, $role, opted in)"
                )
            }
        }
    }

    @Test
    fun `media that exists on this machine is played from disk, not streamed from the primary`() {
        // Built from the real temp dir rather than a POSIX literal: a "/tmp/..." string is not a
        // path on Windows, so exists() would answer false there for the wrong reason and the test
        // would pass while asserting nothing.
        val local = File(tempDir, "sermon-bumper.mp4").apply { writeBytes(ByteArray(1)) }
        assertEquals(
            local.absolutePath,
            followerMediaUrl(
                mediaType = Constants.MEDIA_TYPE_LOCAL,
                localUrl = local.absolutePath,
                remoteStreamUrl = "http://primary:8080/media/abc"
            )
        )
    }

    @Test
    fun `media that exists only on the primary's disk is streamed from there`() {
        val missing = File(tempDir, "not-mounted-here.mp4")
        assertFalse(missing.exists(), "fixture must not exist for this branch to be the one under test")
        assertEquals(
            "http://primary:8080/media/abc",
            followerMediaUrl(
                mediaType = Constants.MEDIA_TYPE_LOCAL,
                localUrl = missing.absolutePath,
                remoteStreamUrl = "http://primary:8080/media/abc"
            )
        )
    }

    @Test
    fun `a missing file with nowhere to stream from keeps its own path`() {
        // No link (or a Controller, which is handed no stream URL at all): the player must fail on
        // the real path the operator configured, not on a silently substituted one.
        val missing = File(tempDir, "gone.mp4")
        assertEquals(
            missing.absolutePath,
            followerMediaUrl(
                mediaType = Constants.MEDIA_TYPE_LOCAL,
                localUrl = missing.absolutePath,
                remoteStreamUrl = null
            )
        )
    }

    @Test
    fun `a URL media item is never rewritten to the primary's stream`() {
        // Already reachable from anywhere — routing it through the primary would add a hop and pin
        // the follower's playback to the primary staying up.
        val url = "rtsp://camera.local/stream1"
        assertEquals(
            url,
            followerMediaUrl(
                mediaType = Constants.MEDIA_TYPE_URL,
                localUrl = url,
                remoteStreamUrl = "http://primary:8080/media/abc"
            )
        )
    }
}
