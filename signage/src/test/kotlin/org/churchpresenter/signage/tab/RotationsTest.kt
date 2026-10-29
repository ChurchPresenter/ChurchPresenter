package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.BlockAction
import org.churchpresenter.signage.core.ClockTime
import org.churchpresenter.signage.core.Day
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlaylistItem
import org.churchpresenter.signage.core.ScheduleBlock
import org.churchpresenter.signage.hub.PairedScreen
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rotations and schedules: how they are kept, what removing an entry does to them, and what a screen is sent. */
@OptIn(ExperimentalPathApi::class)
class RotationsTest {
    private val dir = createTempDirectory("signage-rotations")

    @AfterTest
    fun cleanUp() = dir.deleteRecursively()

    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_ARM64, "26.0.32", 1920, 1080, 0)
    private val picture = FolderFile(Path("/s/a.jpg"), MediaKind.IMAGE, 1, 1)
    private val clip = FolderFile(Path("/s/b.mp4"), MediaKind.VIDEO, 1, 1)
    private val welcome = FolderFile(Path("/pictures/welcome.png"), MediaKind.IMAGE, 1, 1)
    private val weather = LinkPlaylist("link:w", "Weather", "https://weather.example.org", LinkKind.WEB)
    private val rotation = RotationPlaylist(
        "rotation:1",
        "Lobby evenings",
        listOf(
            RotationStep("link:w", 45),
            RotationStep("folder:"),
            RotationStep("file:w", 15),
            RotationStep("file:w"),
            RotationStep("gone"),
        ),
    )
    private val playlists = listOf(
        HashedPlaylist(
            FolderPlaylist("folder:", "Signage", listOf(picture, clip), "/s", pictureSeconds = 7),
            mapOf(picture to "a", clip to "b"),
        ),
        HashedPlaylist(
            FilePlaylist("file:w", "Welcome", welcome.path.toString(), MediaKind.IMAGE),
            mapOf(welcome to "w"),
        ),
        HashedPlaylist(weather),
        HashedPlaylist(rotation),
    )

    private fun build(features: List<String>, schedule: List<ScheduleBlock> = emptyList()) = buildManifest(
        screen = screen,
        ownerId = "owner",
        settings = SignageSettings(screens = mapOf("p" to ScreenPlan("rotation:1", schedule))),
        playlists = playlists,
        timeZone = "UTC",
        features = features,
    ).first

    @Test
    fun aRotationIsItsStepsInTurnWithTheirTimes() {
        val manifest = build(listOf(Features.WEB, Features.ROTATION))
        val items = manifest.playlists.single { it.id == "rotation:1" }.items
        assertEquals(
            listOf(
                PlaylistItem(kind = MediaKind.WEB, url = weather.url, durationMs = 45_000),
                PlaylistItem("a", MediaKind.IMAGE, durationMs = 7_000),
                PlaylistItem("b", MediaKind.VIDEO),
                PlaylistItem("w", MediaKind.IMAGE, durationMs = 15_000),
                PlaylistItem("w", MediaKind.IMAGE, durationMs = 10_000),
            ),
            items,
        )
        assertEquals("rotation:1", manifest.defaultPlaylistId)
    }

    @Test
    fun aPlayerThatCannotRotateLinksIsSentTheRotationsFilesAlone() {
        val items = build(listOf(Features.WEB)).playlists.single { it.id == "rotation:1" }.items
        assertTrue(items.none { it.kind.isLink })
        assertEquals(4, items.size)
    }

    @Test
    fun aScheduleIsSentWithoutTheTimesTheScreenCannotPlay() {
        val week = setOf(Day.MON, Day.TUE)
        fun block(id: String, action: BlockAction) =
            ScheduleBlock(id, week, ClockTime.parse("08:00"), ClockTime.parse("17:00"), action)
        val schedule = listOf(
            block("r", BlockAction.Play("rotation:1")),
            block("gone", BlockAction.Play("folder:Gone")),
            block("off", BlockAction.TvOff),
            block("service", BlockAction.Service),
        )
        assertEquals(listOf("r", "off", "service"), build(listOf(Features.ROTATION), schedule).schedule.map { it.id })
    }

    @Test
    fun removingAnEntryTakesItOutOfEveryRotationAndKeepsTheSchedules() {
        val block =
            ScheduleBlock("b", setOf(Day.SUN), ClockTime.parse("09:00"), ClockTime.parse("12:00"), BlockAction.TvOff)
        val settings = SignageSettings(
            screens = mapOf("p" to ScreenPlan("link:w", listOf(block))),
            added = listOf(weather, rotation),
        )
        val after = settings.withoutAdded("link:w")
        val steps = (after.added.single() as RotationPlaylist).steps.map { it.sourceId }
        assertEquals(listOf("folder:", "file:w", "file:w", "gone"), steps)
        assertEquals(ScreenPlan(null, listOf(block)), after.plan("p"))
    }

    @Test
    fun aRotationIsKeptAndListedWithItsStepsAndTime() {
        val store = SignageStore(dir)
        store.saveSettings(SignageSettings(added = listOf(rotation)))
        assertEquals(listOf(rotation), SignageStore(dir).loadSettings().added)
        val summary = HashedPlaylist(rotation).addedSummary()
        assertEquals(Triple(SourceKind.ROTATION, 5, 60), Triple(summary.kind, summary.steps, summary.seconds))
        assertEquals(RotationStep("link:1", 60), defaultStep(PlaylistSummary("link:1", "W", kind = SourceKind.WEB)))
        assertEquals(RotationStep("folder:", null), defaultStep(PlaylistSummary("folder:", "S")))
        assertEquals(RotationStep("file:1", 10), defaultStep(PlaylistSummary("file:1", "P", kind = SourceKind.PICTURE)))
    }
}
