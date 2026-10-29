@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.churchpresenter.profiles.ProfilesSettingsTab
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.OutputLook
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProfileSet
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SlideLook
import org.churchpresenter.settings.withLinksResolved
import kotlin.math.abs
import kotlin.test.Test

/**
 * Settings → Profiles across the building: one profile per screen, a profile linked to another, one
 * translation styled on its own, the preview's handles and text boxes, and profile sets.
 */
class ProfilesDemoTour {

    @Test
    fun profiles() = recordTour(
        "profiles",
        tab = "Output Profiles",
        liveLabel = "Every screen its own look",
        steps = 18,
        heading = "" to "",
        settings = ::building,
        panel = { settings, change ->
            Box(Modifier.size(PROFILES_W.dp, PROFILES_H.dp)) { ProfilesSettingsTab(settings, change) }
        },
    ) { profilesTour() }

    private fun DemoTour.profilesTour() {
        say("intro")
        intro("One look for every screen in the building.")
        say("why")
        caption("A profile per screen", "Sanctuary, overflow, livestream, stage")
        say("open")
        step("Settings", "Then Profiles", find(hasContentDescription("Settings", substring = true)), numbered = true) {}
        openPanel("Settings — Profiles", PROFILES_W, PROFILES_H)
        menusInPlace = true
        basics()
        linked()
        styleOne()
        onThePreview()
        closePanel()
        sets()
        say("outro")
        outro()
    }

    /** The list, what a profile is to its screens, and the Outputs and Content pages. */
    private fun DemoTour.basics() {
        say("list")
        panelNote("Profiles", "Which screen uses each", panelItem(hasText("PROFILES")), numbered = true)
        say("same")
        panelNote("One profile, the same look", "Change it once, both screens follow", row(SANCTUARY))
        say("menu")
        panelNote("New profile", "Right-click for more", panelItem(hasContentDescription(NEW_PROFILE)), numbered = true)
        say("outputs")
        panelNote("Outputs", "Screens, Browser Source, NDI, OMT", rail("Outputs"), click = true, numbered = true)
        say("content")
        panelNote("Content & sources", "What it shows, which Bibles", rail(CONTENT), click = true, numbered = true)
    }

    /** Overflow room following Sanctuary: the master, the follower, what it keeps, and unlinking. */
    private fun DemoTour.linked() {
        say("linked-intro")
        panelNote("Linked profiles", "Sanctuary, with a smaller font", row(OVERFLOW), numbered = true)
        say("master")
        quietPanelClick(hasText(SANCTUARY))
        quietPanelClick(hasText("General"))
        point("The master", "Followed by Overflow room", hasText("Linked profiles"))
        say("linked")
        panelNote("Linked to Sanctuary", "Keeps only what it changes", row(OVERFLOW), click = true)
        say("differ")
        point("Different from Sanctuary", "Revert to follow it again", hasText("Different from Sanctuary", substring = true))
        say("unlink")
        point("Unlink", "A standalone copy", hasText("Unlink"))
        say("sections")
        point("Sections", "Each can follow its own master", hasText("Sections"))
        say("only-changes")
        quietPanelClick(hasText("Bible"))
        point("Only changes", "Just what this profile changes", hasText("Only changes"))
    }

    /** Applies to: every translation, then KJV on its own; and one song language. */
    private fun DemoTour.styleOne() {
        quietPanelClick(hasText(SANCTUARY))
        say("applies-page")
        panelNote("Bible", "Style every translation", rail("Bible"), click = true, numbered = true)
        say("applies")
        point("Applies to: All", "Every translation", hasText("Applies to"))
        say("one")
        val kjv = runCatching { seek(hasText("KJV") and hasClickAction()) }.getOrNull()
        if (kjv != null) {
            panelNote("Just KJV", "A size or color of its own", kjv, click = true, numbered = true)
        } else {
            caption("Just KJV", "A size or color of its own")
        }
        say("songs-page")
        panelNote("Songs", "One language on its own", rail("Songs"), click = true, numbered = true)
        say("songs-language")
        point("Applies to", "One song language", hasText("Applies to"))
    }

    /** Adjust on preview, moving one element, the large preview, text boxes and the defaults list. */
    private fun DemoTour.onThePreview() {
        quietPanelClick(hasText("Bible"))
        say("adjust")
        panelNote("Adjust on preview", "Drag it on the picture", seek(hasText("Adjust on preview")), click = true, numbered = true)
        say("element")
        point("Move one element", "Reset positions puts it back", hasText("Reset positions"))
        say("larger")
        point("Larger", "The preview across the window", hasText("Larger"))
        say("boxes")
        quietPanelClick(hasText("Advanced"))
        point("Text boxes", "Drag and resize, text fits", hasText("Text boxes") or hasText("Text box"))
        say("box-options")
        boxOn()
        point("Keep clear, snap to guides", "As you move them", hasText("Snap to guides") or hasText("Keep clear of other boxes"))
        say("defaults")
        point("Different from defaults", "Revert any change", hasText("Different from defaults"))
    }

    /** Switches on the Bible text's own box: the switch level with the Text box row. */
    private fun DemoTour.boxOn() {
        val row = runCatching { seek(hasText("Text box")) }.getOrNull() ?: return
        panelItems(isToggleable())
            .minByOrNull { abs(it.bounds.center.y - row.bounds.center.y) }
            ?.takeIf { abs(it.bounds.center.y - row.bounds.center.y) < dp(BOX_ROW_DP) }
            ?.node?.performClick()
        ui.waitForIdle()
        giveKeyboardBack()
        pause(0.3)
    }

    /** The preview header's profile sets: switched on, saving one, and putting one back. */
    private fun DemoTour.sets() {
        giveKeyboardBack()
        say("sets-intro")
        caption("Profile sets", "Every output's profile, in one click")
        say("sets-menu")
        step("Preview Settings", "The preview header", find(control(PREVIEW_SETTINGS)), numbered = true)
        say("sets-switch")
        val switch = runCatching { menuItem(SHOW_SETS) }.getOrNull()
        if (switch != null) {
            step(SHOW_SETS, "Puts the button in the header", switch, numbered = false)
        } else {
            caption(SHOW_SETS, "Puts the button in the header")
        }
        closeMenu()
        say("sets-open")
        step("Profile sets", "Save current as a set", find(control(PROFILE_SETS)), numbered = true)
        say("sets-save")
        runCatching { note("Save current as a set", "Every output, as it is now", menuItem("Save current as a set")) }
            .onFailure { caption("Save current as a set", "Every output, as it is now") }
        say("sets-apply")
        runCatching { step("Youth night", "Every output switched back", menuItem(YOUTH_SET), numbered = true) }
            .onFailure { caption("Youth night", "Every output switched back") }
        closeMenu()
    }

    /** A profile's row in the list down the left. */
    private fun DemoTour.row(name: String) = panelItems(hasText(name)).minBy { it.bounds.left }

    private fun control(label: String) = hasContentDescription(label) or hasText(label)

    /**
     * Closes an open drop-down with a click outside it, on the Bible tab already open: the click only
     * dismisses, so the next one reaches what it is aimed at.
     */
    private fun DemoTour.closeMenu() {
        runCatching { quietClick(tab("Bible")) }
        giveKeyboardBack()
        pause(0.3)
    }
}

/**
 * The demo library across a building: Sanctuary on screen 1 and the master of Overflow room on
 * screen 2, which keeps KJV at a smaller size and Q&A off; the livestream as a lower third on screen
 * 3; a stage monitor nothing uses yet; and two sets of which profile each screen uses.
 */
private fun building(s: AppSettings): AppSettings {
    val proj = s.projectionSettings
    val bible = s.bibleSettings.copy(
        translations = s.bibleSettings.translations.map {
            if (KJV_FILE in it.fileName.lowercase()) it.copy(customAbbreviation = "KJV") else it
        },
    )
    val kjv = bible.translations.firstOrNull { KJV_FILE in it.fileName.lowercase() }?.fileName ?: "kjv.spb"
    val sanctuary = OutputProfile(id = "sanctuary", name = SANCTUARY, bibleSettings = bible)
    val overflow = OutputProfile(
        id = "overflow",
        name = OVERFLOW,
        parentId = sanctuary.id,
        bibleSettings = bible.copy(
            translations = bible.translations.map {
                if (it.fileName == kjv) it.copy(textFontSize = OVERFLOW_KJV_SIZE) else it
            },
        ),
        overrides = setOf("bibleSettings.translations[$kjv].textFontSize", "look.slide.qa"),
        look = OutputLook(slide = SlideLook(qa = false)),
    )
    val livestream = OutputProfile(
        id = "livestream",
        name = "Livestream",
        displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
    )
    val stage = OutputProfile(id = "stage", name = "Stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)
    val sunday = ProfileSet(
        id = "set1",
        name = "Sunday service",
        profiles = mapOf("screen:0" to sanctuary.id, "screen:1" to overflow.id, "screen:2" to livestream.id),
    )
    val youth = ProfileSet(
        id = "set2",
        name = YOUTH_SET,
        profiles = mapOf("screen:0" to sanctuary.id, "screen:1" to stage.id, "screen:2" to livestream.id),
    )
    return s.copy(
        projectionSettings = proj.copy(
            devWindowCount = 3,
            outputProfiles = listOf(sanctuary, overflow, livestream, stage),
            screenAssignments = listOf(
                ScreenAssignment(activeProfileId = sanctuary.id),
                ScreenAssignment(activeProfileId = overflow.id),
                ScreenAssignment(activeProfileId = livestream.id),
            ),
            profileSets = listOf(sunday, youth),
            showProfileSetsButton = false,
        ).withLinksResolved(),
    )
}

private const val SANCTUARY = "Sanctuary"
private const val OVERFLOW = "Overflow room"
private const val YOUTH_SET = "Youth night"
private const val CONTENT = "Content & sources"
private const val NEW_PROFILE = "New profile"
private const val PREVIEW_SETTINGS = "Preview Settings"
private const val PROFILE_SETS = "Profile sets"
private const val SHOW_SETS = "Show profile sets button"
private const val OVERFLOW_KJV_SIZE = 50
private const val KJV_FILE = "kjv"
private const val BOX_ROW_DP = 40f

// The real Settings window opens at this size (OptionsDialog).
private const val PROFILES_W = 1400f
private const val PROFILES_H = 900f
