package org.churchpresenter.media.utils

import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FollowerMediaUrlTest {

    private val tempDir: File = Files.createTempDirectory("cp-follower-media-url").toFile()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
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
