@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import kotlin.math.roundToInt
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
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
            // Advanced, so every row a test reaches for is on screen; Basic hides the rarer ones.
            val state = remember { mutableStateOf(initial.copy(profilesAdvanced = true)) }
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
/**
 * Anything but the preview column's "Different from defaults" card, which repeats a changed value
 * beside its default -- for a check that the page's own control shows it, or no longer does.
 */
internal val outsideDefaultsCard: SemanticsMatcher = !hasAnyAncestor(hasTestTag(DEFAULTS_CARD_TAG))

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
    // A content background is the top group of its own page now; only the default has the
    // Background page to itself.
    val page = when (element) {
        CustomizeElement.BACKGROUND_BIBLE -> CustomizePane.BIBLE
        CustomizeElement.BACKGROUND_SONG -> CustomizePane.SONGS
        else -> pane
    }
    onNodeWithTag(railTag(page.name)).performClick()
    waitForIdle()
    if (element != null && element !in BACKGROUND_ELEMENTS) openElement(element)
}

private val BACKGROUND_ELEMENTS = setOf(
    CustomizeElement.BACKGROUND_DEFAULT,
    CustomizeElement.BACKGROUND_BIBLE,
    CustomizeElement.BACKGROUND_SONG,
)

/** Picks [element] on the open page's Text strip. */
internal fun SkikoComposeUiTest.openElement(element: CustomizeElement) {
    onNodeWithTag(elementChipTag(element.name)).performScrollTo().performClick()
    waitForIdle()
}

/**
 * Opens a background surface -- the Background group of the Bible or Songs page, or the Background
 * page for the default -- and gives the profile its own copy unless [own] is false.
 *
 * A surface still following the level above shows no editor, so a test that drives any control
 * below has to take it over first, exactly as an operator does.
 */
internal fun SkikoComposeUiTest.openBackgroundSurface(
    element: CustomizeElement = CustomizeElement.BACKGROUND_SONG,
    own: Boolean = true,
) {
    openCustomizePane(CustomizePane.BACKGROUND, element)
    if (own) takeOverBackground()
}

/** Clicks "Own" on the open page's Background row. */
internal fun SkikoComposeUiTest.takeOverBackground() {
    onNodeWithTag(BG_OWN_TAG).performScrollTo().performClick()
    waitForIdle()
}

/**
 * Hands the open surface back to the level above: the app's own background for a content surface,
 * the app default on the Background page.
 */
internal fun SkikoComposeUiTest.followBackground() {
    val link = onAllNodesWithTag(BG_APP_DEFAULT_TAG).fetchSemanticsNodes()
    if (link.isNotEmpty()) {
        onNodeWithTag(BG_APP_DEFAULT_TAG).performScrollTo().performClick()
    } else {
        onNodeWithTag(linkTag(BACKGROUND_FOLLOW)).performScrollTo().performClick()
    }
    waitForIdle()
}

/**
 * The [nth] clickable segment reading exactly [label] -- "Top", "Center", "Above verse". Earlier
 * groups come first, so 0 is the Text group's where Position repeats the same words.
 */
internal fun ComposeUiTest.segment(label: String, nth: Int = 0): SemanticsNodeInteraction =
    onAllNodes(hasTextExactly(label) and hasClickAction())[nth]

/** Opens [page] from the section list. */
internal fun ComposeUiTest.openProfilePage(page: ProfilePage) {
    onNodeWithTag(page.navTag()).performClick()
    waitForIdle()
}

/**
 * A content switch on the Content & sources page, by its exact caption, opening the page first if
 * it is not showing.
 */
internal fun ComposeUiTest.contentSwitch(label: String): SemanticsNodeInteraction {
    if (onAllNodesWithTag(contentSwitchTag(label)).fetchSemanticsNodes().isEmpty()) {
        openProfilePage(ProfilePage.Content)
    }
    val node = onNodeWithTag(contentSwitchTag(label))
    if (onAllNodesWithTag(contentSwitchTag(label)).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
    return node
}

/** Opens the preview's shape menu and picks [name] -- a [PreviewShapePreset] name, or `CUSTOM`. */
internal fun ComposeUiTest.pickPreviewShape(name: String) {
    onNodeWithTag(PREVIEW_SHAPE_TRIGGER_TAG).performClick()
    waitForIdle()
    onNodeWithTag(previewShapeTag(name)).performClick()
    waitForIdle()
}

internal const val BACKGROUND_OWN = "Own"
internal const val BACKGROUND_FOLLOW = "Use the app's instead"

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

/**
 * Sets the stepper that shows [readout] -- the number field's digits, its unit dropped -- to
 * [fraction] of [range], the way the slider it replaced was tapped at a fraction of its track.
 */
internal fun ComposeUiTest.setProfileStepper(
    caption: String,
    readout: String,
    fraction: Float,
    range: IntRange = 0..100,
) {
    require(caption.isNotBlank()) { "a stepper is named by the row it sits in" }
    val showing = readout.filter { it.isDigit() || it == '-' }.toInt()
    val to = (range.first + (range.last - range.first) * fraction).roundToInt()
    retypeNumberField(showing = showing, to = to)
}
