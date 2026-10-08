@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.obs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the Scene Mappings card: the default scene and one box per presenting mode.
 *
 * The twelve mode boxes all write into a single `Map<String, String>` keyed by the presenting mode's
 * enum name, each through its own copy of the same callback. Two failures live in that arrangement —
 * a box writing under a neighbour's key, and a box replacing the map rather than adding to it — so
 * every test below asserts the whole map, not just the entry it set.
 *
 * The blank case is a real branch rather than a no-op: clearing a box **removes** its key instead of
 * storing an empty string, which is what keeps a cleared mapping from being sent to OBS as a scene
 * named "".
 */
class OBSSettingsTabScenesTest {

    // ── The card itself ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the mapping card appears only once OBS is enabled`() {
        obsTab { _, _ ->
            onNodeWithText(ObsLabel.SECTION_MAPPINGS).assertDoesNotExist()
        }
        obsTab(initial = obsEnabled()) { _, _ ->
            onNodeWithText(ObsLabel.SECTION_MAPPINGS).assertExists()
        }
    }

    @Test
    fun `every presenting mode gets a caption and a box`() {
        obsTab(initial = obsEnabled()) { _, _ ->
            for ((_, label) in obsSceneModes) {
                onAllNodesWithText(label).assertCountEquals(1)
            }
            // Host, port, password, default scene and one box per mode.
            obsFields().assertCountEquals(4 + obsSceneModes.size)
        }
    }

    /**
     * **The dictionary cannot be mapped to an OBS scene.** `Presenting` declares thirteen modes and
     * this card offers twelve: `DICTIONARY` has no row, so presenting a Strong's entry leaves OBS on
     * whatever scene it was already on rather than switching.
     *
     * That reads as an oversight — the dictionary is a full presenting mode with its own presenter,
     * and the Stage Monitor tab does give it a content type — but it is what ships, so it is pinned
     * here rather than asserted away. Adding the row (and its `obs_mode_*` string) will fail this
     * test, which is the point: the new row then needs its own coverage like every other mode.
     *
     * Messages and props have no row either, by design: props sit over whatever is on air and never
     * change the scene, and a message takes the default scene like any unmapped mode.
     */
    @Test
    fun `every presenting mode except the dictionary, messages and props can be mapped`() {
        val mapped = obsSceneModes.map { it.first }.toSet()
        assertEquals(
            setOf(Presenting.DICTIONARY, Presenting.MESSAGE, Presenting.PROPS),
            Presenting.entries.toSet() - mapped,
            "only these may be missing a scene row; a newly unmapped mode is a regression",
        )
    }

    // ── The default scene ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the default scene stores what is typed`() {
        obsTab(initial = obsEnabled()) { get, _ ->
            assertEquals("", get().obsSettings.defaultScene, "no default scene out of the box")

            obsFieldShowing("").let { } // several boxes are blank; the default scene is found by caption
            sceneOrDefaultBox().performTextReplacement("Wide Shot")
            waitForIdle()

            assertEquals("Wide Shot", get().obsSettings.defaultScene, "the typed scene must be stored")
            assertTrue(get().obsSettings.sceneMappings.isEmpty(), "and no mapping may be created by it")
        }
    }

    @Test
    fun `a stored default scene is rendered`() {
        obsTab(initial = obsEnabled { copy(defaultScene = "Wide Shot") }) { _, _ ->
            assertObsFieldShows("Wide Shot", "the default scene box")
        }
    }

    /** The default scene box sits directly under the "Default Scene" caption, above the mode rows. */
    private fun ComposeUiTest.sceneOrDefaultBox() =
        onNode(
            androidx.compose.ui.test.hasSetTextAction() and
                SemanticsMatcher("is the default-scene box") { node ->
                    val caption = onAllNodesWithText(ObsLabel.DEFAULT_SCENE)
                        .fetchSemanticsNodes(atLeastOneRootRequired = false)
                        .minByOrNull { it.boundsInRoot.top } ?: return@SemanticsMatcher false
                    node.boundsInRoot.left >= caption.boundsInRoot.right &&
                        node.boundsInRoot.top < caption.boundsInRoot.bottom &&
                        node.boundsInRoot.bottom > caption.boundsInRoot.top
                },
        )

    // ── The mode mappings ───────────────────────────────────────────────────────────────────────

    @Test
    fun `each mode stores its scene under its own key`() {
        for ((mode, _) in obsSceneModes) {
            obsTab(initial = obsEnabled()) { get, _ ->
                setScene(mode, "Scene For ${mode.name}")

                assertEquals(
                    mapOf(mode.name to "Scene For ${mode.name}"),
                    get().obsSettings.sceneMappings,
                    "$mode must write exactly one entry, under its own name",
                )
            }
        }
    }

    @Test
    fun `mapping one mode leaves the others alone`() {
        obsTab(initial = obsEnabled { copy(sceneMappings = mapOf(Presenting.BIBLE.name to "Bible Scene")) }) { get, _ ->
            setScene(Presenting.LYRICS, "Songs Scene")

            assertEquals(
                mapOf(Presenting.BIBLE.name to "Bible Scene", Presenting.LYRICS.name to "Songs Scene"),
                get().obsSettings.sceneMappings,
                "the existing mapping must survive a new one being added",
            )
        }
    }

    @Test
    fun `a stored mapping is rendered in its own box`() {
        obsTab(
            initial = obsEnabled {
                copy(sceneMappings = mapOf(Presenting.MEDIA.name to "Media Scene"))
            },
        ) { _, _ ->
            assertObsFieldShows("Media Scene", "the Media box")
            onNodeWithText("Media Scene").assertExists()
        }
    }

    /**
     * Clearing a box removes the key rather than storing a blank scene — otherwise the app would ask
     * OBS to switch to a scene with no name.
     */
    @Test
    fun `clearing a mapping removes its key rather than storing a blank`() {
        obsTab(
            initial = obsEnabled {
                copy(
                    sceneMappings = mapOf(
                        Presenting.BIBLE.name to "Bible Scene",
                        Presenting.LYRICS.name to "Songs Scene",
                    ),
                )
            },
        ) { get, _ ->
            setScene(Presenting.BIBLE, "")

            assertEquals(
                mapOf(Presenting.LYRICS.name to "Songs Scene"),
                get().obsSettings.sceneMappings,
                "the cleared key must be gone, not present with a blank value",
            )
        }
    }

    /**
     * The right-hand column is a second copy of the same callback, written out separately in the tab
     * rather than shared, so its blank-removes-the-key branch is genuinely different code from the
     * left column's. Songs is the right-hand box of the first row.
     */
    @Test
    fun `clearing a right-column mapping removes its key too`() {
        obsTab(
            initial = obsEnabled {
                copy(
                    sceneMappings = mapOf(
                        Presenting.BIBLE.name to "Bible Scene",
                        Presenting.LYRICS.name to "Songs Scene",
                    ),
                )
            },
        ) { get, _ ->
            setScene(Presenting.LYRICS, "")

            assertEquals(
                mapOf(Presenting.BIBLE.name to "Bible Scene"),
                get().obsSettings.sceneMappings,
                "the right column must remove its key rather than store a blank",
            )
        }
    }

    @Test
    fun `a whitespace-only scene is treated as blank`() {
        obsTab(initial = obsEnabled { copy(sceneMappings = mapOf(Presenting.BIBLE.name to "Bible Scene")) }) { get, _ ->
            setScene(Presenting.BIBLE, "   ")

            assertEquals(
                emptyMap(),
                get().obsSettings.sceneMappings,
                "whitespace is not a scene name, so the key must be removed",
            )
        }
    }

    @Test
    fun `mappings survive being set one after another`() {
        obsTab(initial = obsEnabled()) { get, _ ->
            setScene(Presenting.BIBLE, "One")
            setScene(Presenting.LYRICS, "Two")
            setScene(Presenting.QA, "Three")

            assertEquals(
                mapOf(
                    Presenting.BIBLE.name to "One",
                    Presenting.LYRICS.name to "Two",
                    Presenting.QA.name to "Three",
                ),
                get().obsSettings.sceneMappings,
                "each write must add to the map rather than replace it",
            )
        }
    }

    /**
     * The mode list has an even number of entries, so the tab's `pair.size == 2` check is always
     * true and its `else` branch — the spacer that would balance an odd last row — never runs. Pinned
     * so that adding a thirteenth mode fails here and the branch gets a test rather than going live
     * untried.
     */
    @Test
    fun `the mode list is even, so no row is ever half empty`() {
        assertEquals(
            0,
            obsSceneModes.size % 2,
            "an odd mode count would render a half-empty row the tests have never exercised",
        )
    }
}
