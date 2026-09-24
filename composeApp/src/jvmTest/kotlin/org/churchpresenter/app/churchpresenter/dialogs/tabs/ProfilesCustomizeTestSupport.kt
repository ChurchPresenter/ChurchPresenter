@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants

/**
 * Driving the Customize surface on the Profiles tab.
 *
 * Replaces `ProjectionCustomizeTestSupport`, which opened a Customize *dialog* from a row of the
 * Projection tab's grid and had to switch an override on before anything would accept a click. The
 * editor is always on screen now, one profile at a time, and there is no override switch: a profile
 * simply owns its Bible and Song styling. Only backgrounds still follow-or-override, surface by
 * surface, and [BackgroundCustomizePane] draws that as its own row.
 */

/** The size the real dialog opens at, which the editor's fixed-width columns are laid out against. */
private const val DIALOG_WIDTH = 1400f
private const val DIALOG_HEIGHT = 900f

/**
 * Renders the Profiles tab over [initial] and runs [block], which is handed a `get()` returning the
 * document as it stands after each edit.
 *
 * **Sized deliberately.** The editor spends 260dp on the profile rail, 176dp on the category rail
 * and 426dp on the preview column before a pane gets anything, and its controls are fixed-size
 * cells that a row too narrow *clips rather than shrinks*. A clipped node keeps its semantics, so a
 * finder still matches it and the click lands on nothing — a failure that reads like broken
 * behaviour and is not one.
 */
internal fun profilesTab(
    initial: AppSettings,
    onIdentify: () -> Unit = {},
    block: SkikoComposeUiTest.(get: () -> AppSettings) -> Unit,
) {
    lateinit var doc: MutableState<AppSettings>
    runSkikoComposeUiTest(size = Size(DIALOG_WIDTH, DIALOG_HEIGHT), density = Density(1f)) {
        setContent {
            // Snapshot state, not a plain `var`: the editor reads the document it was handed and
            // writes a new one, so a var Compose cannot observe leaves the tree showing the
            // original for ever. Two edits in a row then both compute from that first snapshot and
            // the second silently discards the first -- which reads as "only the last control
            // works" and is a fault in the harness, not in the editor.
            val state = remember { mutableStateOf(initial) }
            doc = state
            ProfilesSettingsTab(
                settings = state.value,
                onSettingsChange = { update -> state.value = update(state.value) },
                onIdentify = onIdentify,
            )
        }
        block { doc.value }
    }
}

/** A document with one profile in [mode], whose styling starts from [bible] and [song]. */
internal fun profileDocument(
    mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
    bible: BibleSettings = BibleSettings(),
    song: SongSettings = SongSettings(),
    profile: OutputProfile = OutputProfile(),
): AppSettings = AppSettings(
    bibleSettings = bible,
    songSettings = song,
    projectionSettings = ProjectionSettings(
        outputProfiles = listOf(
            profile.copy(
                id = profile.id.ifBlank { PROFILE_ID },
                name = profile.name.ifBlank { "Main" },
                displayMode = mode,
                bibleSettings = bible,
                songSettings = song,
            ),
        ),
    ),
)

internal const val PROFILE_ID = "main"

/** The profile being edited. */
internal fun AppSettings.profile(id: String = PROFILE_ID): OutputProfile =
    projectionSettings.outputProfiles.first { it.id == id }

/**
 * What an output on that profile actually renders with.
 *
 * Assertions go through this rather than the profile's stored copy wherever the question is "what
 * does the screen show", because resolution is where a profile's styling is merged over the
 * document's content — see [resolvedFor].
 */
internal fun AppSettings.asRendered(id: String = PROFILE_ID): AppSettings = resolvedFor(profile(id))

/** The profile's own Songs styling, as rendered. */
internal fun AppSettings.song(id: String = PROFILE_ID): SongSettings = asRendered(id).songSettings

/** The profile's own Bible styling, as rendered. */
internal fun AppSettings.bible(id: String = PROFILE_ID): BibleSettings = asRendered(id).bibleSettings

/** Selects [pane] on the category rail, and chips [element] when one is named. */
internal fun SkikoComposeUiTest.openCustomizePane(
    pane: CustomizePane,
    element: CustomizeElement? = null,
) {
    onNodeWithTag(railTag(pane.name)).performClick()
    waitForIdle()
    if (element != null) openElement(element)
}

/** Chips [element] in the pane already open. */
internal fun SkikoComposeUiTest.openElement(element: CustomizeElement) {
    onNodeWithTag(elementChipTag(element.name)).performClick()
    waitForIdle()
}

/**
 * Opens a background surface, taking it over from the Background tab unless [own] is false.
 *
 * A surface that is following is drawn dimmed under a blanket that swallows clicks, so a test that
 * drives any control below has to take it over first, exactly as an operator does. Tests that only
 * assert on what is *rendered* leave [own] alone and see the inherited values.
 */
internal fun SkikoComposeUiTest.openBackgroundSurface(
    element: CustomizeElement = CustomizeElement.BACKGROUND_SONG,
    own: Boolean = true,
) {
    openCustomizePane(CustomizePane.BACKGROUND, element)
    if (own) takeOverBackground()
}

/** Clicks "Custom" on the open surface's follow row. */
internal fun SkikoComposeUiTest.takeOverBackground() {
    // Not the preview column's "Custom" shape, which carries the same word.
    onNode(hasText(BACKGROUND_OWN) and !hasTestTag(previewShapeTag("CUSTOM"))).performScrollTo().performClick()
    waitForIdle()
}

/** Hands the open surface back to the Background tab. */
internal fun SkikoComposeUiTest.followBackground() {
    onNodeWithText(BACKGROUND_FOLLOW).performScrollTo().performClick()
    waitForIdle()
}

internal const val BACKGROUND_OWN = "Custom"
internal const val BACKGROUND_FOLLOW = "Follow Background tab"

/** The background config this profile draws for [scope], after resolution. */
internal fun AppSettings.backgroundFor(
    scope: BackgroundScope,
    id: String = PROFILE_ID,
): BackgroundConfig = asRendered(id).backgroundSettings.configFor(scope)

/** Whether [scope] is this profile's own rather than the Background tab's. */
internal fun AppSettings.overrides(scope: BackgroundScope, id: String = PROFILE_ID): Boolean =
    scope.name in profile(id).backgroundOverrides

/**
 * Picks [option] from a `ChoiceControl`, whose segments are plain labelled buttons.
 *
 * [nth] because a lower-third profile draws two such rows carrying the same words — the band's type
 * row and, below it, the wash above the band, which uses the band's own labels on purpose. They
 * come out in composition order, so 0 is the band's and 1 is the wash's.
 *
 * [scroll] off for a control on the strip under the preview: only the pane column scrolls, and
 * `performScrollTo` fails outright on a node with no scrollable ancestor rather than doing nothing.
 */
internal fun SkikoComposeUiTest.chooseSegment(option: String, scroll: Boolean = true, nth: Int = 0) {
    val node = onAllNodesWithText(option)[nth]
    if (scroll) node.performScrollTo()
    node.performClick()
    waitForIdle()
}
