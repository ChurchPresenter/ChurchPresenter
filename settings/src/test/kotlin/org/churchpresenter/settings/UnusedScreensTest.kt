package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Monitors marked "Don't use": how the mark is read, set and cleared, and that it is persisted. */
class UnusedScreensTest {

    private val foyerKey = screenKey(1920, 0, 1280, 720)
    private val balconyKey = screenKey(3200, 0, 3840, 2160)

    private fun onFoyer(profile: String = "main") = ScreenAssignment(
        targetDisplay = 1,
        targetBoundsX = 1920, targetBoundsY = 0, targetBoundsW = 1280, targetBoundsH = 720,
        activeProfileId = profile,
    )

    private fun onBalcony(profile: String = "main") = ScreenAssignment(
        targetDisplay = 2,
        targetBoundsX = 3200, targetBoundsY = 0, targetBoundsW = 3840, targetBoundsH = 2160,
        activeProfileId = profile,
    )

    private fun keyOnFoyer(base: ScreenAssignment) = base.copy(
        keyTargetDisplay = 1,
        keyTargetBoundsX = 1920, keyTargetBoundsY = 0, keyTargetBoundsW = 1280, keyTargetBoundsH = 720,
    )

    // ── Reading the mark ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a monitor is unused only when its key is marked`() {
        val proj = ProjectionSettings(unusedScreens = listOf(foyerKey))

        assertTrue(proj.isScreenUnused(foyerKey))
        assertFalse(proj.isScreenUnused(balconyKey))
    }

    @Test
    fun `a blank key names no monitor, so it is never unused`() {
        // Even a list that somehow carries a blank entry must not mark every unresolved row unused.
        assertFalse(ProjectionSettings(unusedScreens = listOf("")).isScreenUnused(""))
    }

    @Test
    fun `the bounds overload reads the monitor with those bounds`() {
        val proj = ProjectionSettings(unusedScreens = listOf(foyerKey))

        assertTrue(proj.isScreenUnused(1920, 0, 1280, 720))
        assertFalse(proj.isScreenUnused(3200, 0, 3840, 2160))
        assertFalse(proj.isScreenUnused(Int.MIN_VALUE, Int.MIN_VALUE, 0, 0), "unset bounds name no monitor")
    }

    @Test
    fun `a None row drives nothing`() {
        val none = ScreenAssignment(targetDisplay = Constants.KEY_TARGET_NONE)

        assertTrue(ProjectionSettings().drivesNothing(none))
    }

    @Test
    fun `a row on an unused monitor drives nothing, exactly as a None row`() {
        val proj = ProjectionSettings(unusedScreens = listOf(foyerKey))

        assertTrue(proj.drivesNothing(onFoyer()))
    }

    @Test
    fun `a row on a monitor in use drives it`() {
        val proj = ProjectionSettings(unusedScreens = listOf(foyerKey))

        assertFalse(proj.drivesNothing(onBalcony()))
        assertFalse(proj.drivesNothing(ScreenAssignment(targetDisplay = -1)), "auto still drives a monitor")
    }

    @Test
    fun `a DeckLink row is never on an unused monitor`() {
        val proj = ProjectionSettings(unusedScreens = listOf(foyerKey))

        assertFalse(proj.drivesNothing(onFoyer().copy(targetType = Constants.TARGET_TYPE_DECKLINK)))
    }

    // ── The key's monitor ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a key on a screen is keyed by that screen`() {
        assertEquals(foyerKey, keyOnFoyer(onBalcony()).keyTargetScreenKey)
    }

    @Test
    fun `a key driving nothing, or a DeckLink port, has no screen key`() {
        assertEquals("", onFoyer().keyTargetScreenKey, "the default key target is None")
        assertEquals(
            "",
            keyOnFoyer(onBalcony()).copy(keyTargetType = Constants.TARGET_TYPE_DECKLINK).keyTargetScreenKey,
        )
        assertEquals(
            "",
            keyOnFoyer(onBalcony()).copy(keyTargetDisplay = Constants.KEY_TARGET_NONE).keyTargetScreenKey,
            "a key set to None keeps its old bounds but drives no monitor",
        )
    }

    // ── Marking a monitor unused ────────────────────────────────────────────────────────────────

    @Test
    fun `marking a monitor unused stores its key once`() {
        val proj = ProjectionSettings().withScreenUnused(foyerKey).withScreenUnused(foyerKey)

        assertEquals(listOf(foyerKey), proj.unusedScreens)
    }

    @Test
    fun `marking a monitor unused sets every row on it to None and clears its bounds`() {
        val proj = ProjectionSettings(screenAssignments = listOf(onFoyer("main"), onFoyer("stage")))
            .withScreenUnused(foyerKey)

        for (row in proj.screenAssignments) {
            assertEquals(Constants.KEY_TARGET_NONE, row.targetDisplay)
            assertEquals(Constants.TARGET_TYPE_SCREEN, row.targetType)
            assertEquals(Int.MIN_VALUE, row.targetBoundsX)
            assertEquals(Int.MIN_VALUE, row.targetBoundsY)
            assertEquals(0, row.targetBoundsW)
            assertEquals(0, row.targetBoundsH)
            assertEquals("", row.targetScreenKey, "nothing left in the settings still names the monitor")
        }
        assertEquals(listOf("main", "stage"), proj.screenAssignments.map { it.activeProfileId })
    }

    @Test
    fun `marking a monitor unused takes it off a key that drove it, leaving the row's picture`() {
        val proj = ProjectionSettings(screenAssignments = listOf(keyOnFoyer(onBalcony())))
            .withScreenUnused(foyerKey)

        val row = proj.screenAssignments.single()
        assertEquals(Constants.KEY_TARGET_NONE, row.keyTargetDisplay)
        assertEquals(Constants.TARGET_TYPE_SCREEN, row.keyTargetType)
        assertEquals(Int.MIN_VALUE, row.keyTargetBoundsX)
        assertEquals(Int.MIN_VALUE, row.keyTargetBoundsY)
        assertEquals(0, row.keyTargetBoundsW)
        assertEquals(0, row.keyTargetBoundsH)
        assertEquals(2, row.targetDisplay, "the picture is on another monitor and stays there")
        assertEquals(balconyKey, row.targetScreenKey)
    }

    @Test
    fun `a row whose picture and key are both on the monitor loses both`() {
        val row = ProjectionSettings(screenAssignments = listOf(keyOnFoyer(onFoyer())))
            .withScreenUnused(foyerKey).screenAssignments.single()

        assertEquals(Constants.KEY_TARGET_NONE, row.targetDisplay)
        assertEquals(Constants.KEY_TARGET_NONE, row.keyTargetDisplay)
    }

    @Test
    fun `rows on other monitors are left exactly as they were`() {
        val balcony = keyOnFoyer(onBalcony())
            .copy(keyTargetDisplay = 2, keyTargetBoundsX = 3200, keyTargetBoundsW = 3840)
        val proj = ProjectionSettings(screenAssignments = listOf(onFoyer(), balcony)).withScreenUnused(foyerKey)

        assertEquals(balcony, proj.screenAssignments[1])
    }

    @Test
    fun `marking a blank key changes nothing`() {
        val before = ProjectionSettings(screenAssignments = listOf(onFoyer()))

        assertSame(before, before.withScreenUnused(""))
    }

    // ── Putting it back into use ────────────────────────────────────────────────────────────────

    @Test
    fun `putting a monitor back into use removes only its key`() {
        val proj = ProjectionSettings(unusedScreens = listOf(foyerKey, balconyKey)).withScreenUsed(foyerKey)

        assertEquals(listOf(balconyKey), proj.unusedScreens)
    }

    @Test
    fun `putting back a monitor that was never marked changes nothing`() {
        val before = ProjectionSettings(unusedScreens = listOf(balconyKey))

        assertSame(before, before.withScreenUsed(foyerKey))
    }

    // ── Kept across loads ───────────────────────────────────────────────────────────────────────

    @Test
    fun `repairing profile references leaves the unused monitors alone`() {
        val proj = ProjectionSettings(
            unusedScreens = listOf(foyerKey),
            screenAssignments = listOf(onBalcony(profile = "gone")),
        ).withProfileReferencesRepaired()

        assertEquals(listOf(foyerKey), proj.unusedScreens)
    }

    @Test
    fun `the unused monitors survive a settings round trip`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(unusedScreens = listOf(foyerKey, balconyKey)),
        )

        val restored = json.decodeFromString<AppSettings>(json.encodeToString(settings))

        assertEquals(listOf(foyerKey, balconyKey), restored.projectionSettings.unusedScreens)
    }

    @Test
    fun `a settings file written before the field existed loads with no monitor unused`() {
        val json = Json { ignoreUnknownKeys = true }

        val restored = json.decodeFromString<AppSettings>("""{"projectionSettings":{"screenNames":{}}}""")

        assertEquals(emptyList(), restored.projectionSettings.unusedScreens)
    }
}
