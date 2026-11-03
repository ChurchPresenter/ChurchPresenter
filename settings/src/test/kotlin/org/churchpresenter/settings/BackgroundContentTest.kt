package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Set as Background: a picture or clip saved as the Default, Songs or Bible background. */
class BackgroundContentTest {

    private val picture = "/church/backgrounds/sunrise.jpg"
    private val clip = "/church/backgrounds/waves.mp4"

    private fun settings(vararg profiles: OutputProfile) = AppSettings(
        projectionSettings = ProjectionSettings(outputProfiles = profiles.toList()),
    )

    private fun AppSettings.profile(id: String) = projectionSettings.outputProfiles.single { it.id == id }

    private fun AppSettings.shownBy(id: String): BackgroundSettings = profile(id).let {
        resolveBackgroundSurfaces(backgroundSettings, it.backgroundSettings, it.backgroundOverrides)
    }

    // ── Every output ────────────────────────────────────────────────────────────

    @Test
    fun `a picture for every output is the Background tab's`() {
        val out = settings(OutputProfile(id = "main"))
            .withBackgroundSet(picture, false, BackgroundContent.DEFAULT, null)

        assertEquals(Constants.BACKGROUND_IMAGE, out.backgroundSettings.defaultBackgroundType)
        assertEquals(picture, out.backgroundSettings.defaultBackgroundImage)
        assertTrue(out.profile("main").backgroundOverrides.isEmpty(), "a profile that follows keeps following")
    }

    @Test
    fun `a clip for every output is a video background`() {
        val out = settings().withBackgroundSet(clip, true, BackgroundContent.DEFAULT, null)

        assertEquals(Constants.BACKGROUND_VIDEO, out.backgroundSettings.defaultBackgroundType)
        assertEquals(clip, out.backgroundSettings.defaultBackgroundVideo)
    }

    @Test
    fun `every output reaches a profile that keeps its own copy`() {
        val own = OutputProfile(
            id = "stream",
            backgroundOverrides = setOf(BackgroundSurface.SONG.name),
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            ),
        )
        val out = settings(own, OutputProfile(id = "main"))
            .withBackgroundSet(picture, false, BackgroundContent.SONG, null)

        assertEquals(picture, out.backgroundSettings.songBackground.backgroundImage)
        assertEquals(picture, out.shownBy("stream").songBackground.backgroundImage)
        assertEquals(Constants.BACKGROUND_IMAGE, out.shownBy("stream").songBackground.backgroundType)
        assertEquals(picture, out.shownBy("main").songBackground.backgroundImage)
    }

    @Test
    fun `Songs and Bible stop following Default once they have their own`() {
        val following = BackgroundSettings(
            songBackground = BackgroundConfig(
                backgroundType = Constants.BACKGROUND_DEFAULT,
                ownBackgroundType = "Color",
            ),
        )
        val out = AppSettings(backgroundSettings = following)
            .withBackgroundSet(picture, false, BackgroundContent.SONG, null)
            .withBackgroundSet(clip, true, BackgroundContent.BIBLE, null)

        with(out.backgroundSettings) {
            assertEquals(Constants.BACKGROUND_IMAGE, songBackground.backgroundType)
            assertEquals("", songBackground.ownBackgroundType)
            assertEquals(Constants.BACKGROUND_VIDEO, bibleBackground.backgroundType)
            assertEquals(clip, bibleBackground.backgroundVideo)
            assertEquals(Constants.BACKGROUND_COLOR, defaultBackgroundType, "Default is left alone")
        }
    }

    // ── One profile ─────────────────────────────────────────────────────────────

    @Test
    fun `one profile keeps it as its own, and the Background tab is left alone`() {
        val out = settings(OutputProfile(id = "main"), OutputProfile(id = "lobby"))
            .withBackgroundSet(picture, false, BackgroundContent.BIBLE, "lobby")

        assertTrue(BackgroundSurface.BIBLE.name in out.profile("lobby").backgroundOverrides)
        assertEquals(picture, out.shownBy("lobby").bibleBackground.backgroundImage)
        assertEquals("", out.backgroundSettings.bibleBackground.backgroundImage)
        assertEquals("", out.shownBy("main").bibleBackground.backgroundImage, "another profile does not change")
    }

    @Test
    fun `a profile taking a surface it followed keeps the look it showed`() {
        val house = BackgroundSettings(defaultBackgroundOpacity = 0.6f, defaultBackgroundDim = 30)
        val out = AppSettings(
            backgroundSettings = house,
            projectionSettings = ProjectionSettings(outputProfiles = listOf(OutputProfile(id = "main"))),
        ).withBackgroundSet(picture, false, BackgroundContent.DEFAULT, "main")

        val shown = out.shownBy("main")
        assertEquals(picture, shown.defaultBackgroundImage)
        assertEquals(0.6f, shown.defaultBackgroundOpacity, "only the picture changes")
        assertEquals(30, shown.defaultBackgroundDim)
    }

    @Test
    fun `a linked profile keeps it as a value of its own`() {
        val master = OutputProfile(id = "master")
        val linked = ProjectionSettings(outputProfiles = listOf(master))
            .createLinkedProfile("master", OutputProfile(id = "follower"))
        val out = AppSettings(projectionSettings = linked)
            .withBackgroundSet(picture, false, BackgroundContent.SONG, "follower")

        val follower = out.profile("follower")
        assertTrue(follower.overrides.any { it.startsWith("backgroundSettings.songBackground") })
        assertEquals(picture, out.shownBy("follower").songBackground.backgroundImage)

        // Editing the master afterwards brings the follower back in line except where it has its own.
        val edited = out.projectionSettings.editProfile("master") { it.copy(songMode = Constants.SONG_LANG_OFF) }
        val after = out.copy(projectionSettings = edited)
        assertEquals(picture, after.shownBy("follower").songBackground.backgroundImage)
    }

    @Test
    fun `an unknown profile changes nothing`() {
        val before = settings(OutputProfile(id = "main"))
        assertSame(before, before.withBackgroundSet(picture, false, BackgroundContent.DEFAULT, "gone"))
    }

    // ── What is set already ─────────────────────────────────────────────────────

    @Test
    fun `a picture set as a background is that background`() {
        val out = settings(OutputProfile(id = "main"), OutputProfile(id = "lobby"))
            .withBackgroundSet(picture, false, BackgroundContent.SONG, null)
            .withBackgroundSet(clip, true, BackgroundContent.DEFAULT, "lobby")

        assertTrue(out.backgroundShows(picture, BackgroundContent.SONG, null))
        assertTrue(out.backgroundShows(picture, BackgroundContent.SONG, "main"), "a following profile shows it too")
        assertFalse(out.backgroundShows(picture, BackgroundContent.BIBLE, null))
        assertTrue(out.backgroundShows(clip, BackgroundContent.DEFAULT, "lobby"))
        assertFalse(out.backgroundShows(clip, BackgroundContent.DEFAULT, null))
        assertFalse(out.backgroundShows(clip, BackgroundContent.DEFAULT, "gone"))
    }

    @Test
    fun `a surface of another type shows no file`() {
        val out = settings().withBackgroundSet(picture, false, BackgroundContent.BIBLE, null)
        val colored = out.copy(
            backgroundSettings = out.backgroundSettings.copy(
                bibleBackground = out.backgroundSettings.bibleBackground.copy(
                    backgroundType = Constants.BACKGROUND_COLOR,
                ),
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
            ),
        )
        assertFalse(colored.backgroundShows(picture, BackgroundContent.BIBLE, null))
        assertFalse(colored.backgroundShows(picture, BackgroundContent.DEFAULT, null))
        val video = settings().withBackgroundSet(clip, true, BackgroundContent.SONG, null)
        assertTrue(video.backgroundShows(clip, BackgroundContent.SONG, null))
        assertFalse(video.backgroundShows(picture, BackgroundContent.SONG, null))
    }
}
