@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The header every source type shares: the panel's title, the Name field and the six transform
 * controls (X, Y, W, H, Rotation, Opacity).
 *
 * These are the only controls on the panel that must work for all eleven kinds of [SceneSource], and
 * they are the only ones that go through `updateName`/`updateTransform` — two exhaustive `when`s over
 * the sealed class, each of which rebuilds the source with `copy(...)` per branch. A branch that
 * copied the wrong field, or returned the wrong subtype, would be invisible for ten of the eleven
 * kinds, so both are walked across every kind rather than spot-checked on one.
 *
 * The four position fields are `PropertyFloatField`s: they keep what is typed in local state and only
 * push it to the model on IME Done or focus loss, restoring the last good value if what was typed is
 * not a number. Each of those three outcomes — commit, reject, and the model's own clamping — is a
 * test of its own.
 */
class SourcePropertiesPanelTest {

    /** Ordinals of the header's fields among all editable fields on the panel. */
    private object Field {
        const val NAME = 0
        const val X = 1
        const val Y = 2
        const val W = 3
        const val H = 4
        const val ROTATION = 5
    }

    // ── What the header displays ──────────────────────────────────────────────

    @Test
    fun `the panel titles itself and captions every shared control`() = sourcePanel(Fixture.text()) { _ ->
        listOf(
            Label.PROPERTIES, Label.NAME, Label.TRANSFORM,
            Label.X, Label.Y, Label.W, Label.H, Label.ROTATION, Label.OPACITY,
        ).forEach { caption ->
            onAllNodesWithText(caption).onFirst().assertExists("\"$caption\" must caption the header")
        }
    }

    @Test
    fun `the header is visible, not merely present in the tree`() = sourcePanel(Fixture.text()) { _ ->
        onNodeWithText(Label.PROPERTIES).assertIsDisplayed()
        onNodeWithText(Label.NAME).assertIsDisplayed()
        onNodeWithText(Label.TRANSFORM).assertIsDisplayed()
    }

    @Test
    fun `the header offers exactly six fields before the type-specific ones`() =
        sourcePanel(Fixture.color()) { _ ->
            // A Color source adds no field of its own, so the header's six are the whole panel's.
            // This is what pins the ordinals every other test in this class addresses fields by.
            textFields().assertCountEquals(6)
            assertFieldShows("Backdrop", "the Name field")
        }

    @Test
    fun `the name field shows the source's name`() = sourcePanel(Fixture.image()) { _ ->
        assertFieldShows("Logo", "the Name field")
    }

    @Test
    fun `the position fields show the stored transform to three decimals`() {
        val placed = Fixture.text().copy(
            transform = SourceTransform(x = 0.25f, y = 0.5f, width = 0.125f, height = 0.75f)
        )
        sourcePanel(placed) { _ ->
            assertFieldShows("0.250", "X")
            assertFieldShows("0.500", "Y")
            assertFieldShows("0.125", "W")
            assertFieldShows("0.750", "H")
        }
    }

    @Test
    fun `the rotation input and the opacity read-out show their stored values`() {
        // An Image source is used wherever a read-out is asserted by text: it is the only kind that
        // adds no slider of its own, so a percentage on screen can only have come from the header.
        val turned = Fixture.image().copy(transform = SourceTransform(rotation = 45f, opacity = 0.4f))
        sourcePanel(turned) { _ ->
            assertFieldShows("45", "the rotation input")
            onNodeWithText("40%").assertExists("the opacity slider reads out whole percent")
            onNodeWithText("°").assertExists("the rotation input is suffixed with its unit")
        }
    }

    // ── Name ──────────────────────────────────────────────────────────────────

    @Test
    fun `renaming the source stores the new name and shows it`() = sourcePanel(Fixture.image()) { get ->
        typeField(Field.NAME, "Lower Third")

        assertEquals("Lower Third", get().name, "the Name field writes to the source's name")
        assertEquals("/tmp/logo.png", (get() as SceneSource.ImageSource).filePath, "and to nothing else")
        assertFieldShows("Lower Third", "the Name field after typing")
    }

    @Test
    fun `the name can be cleared`() = sourcePanel(Fixture.image()) { get ->
        typeField(Field.NAME, "")

        assertEquals("", get().name, "an empty name is stored as typed, not rejected")
    }

    @Test
    fun `every kind of source can be renamed, and stays its own kind`() {
        // `updateName` is an eleven-branch `when`; each branch must copy the name onto its own type.
        Fixture.everyKind("rename").forEach { source ->
            sourcePanel(source) { get ->
                typeField(Field.NAME, "Renamed")

                assertEquals("Renamed", get().name, "${source::class.simpleName} must accept a new name")
                assertEquals(
                    source::class, get()::class,
                    "renaming a ${source::class.simpleName} must not change its type",
                )
                assertEquals(source.id, get().id, "and must not change its id")
            }
        }
    }

    // ── Position and size ─────────────────────────────────────────────────────

    @Test
    fun `committing X moves the source horizontally and nothing else`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.X, "0.4")

        assertEquals(
            SourceTransform(x = 0.4f),
            get().transform,
            "X writes only the x of the transform",
        )
        assertFieldShows("0.400", "X after committing")
    }

    @Test
    fun `committing Y moves the source vertically and nothing else`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.Y, "0.6")

        assertEquals(SourceTransform(y = 0.6f), get().transform, "Y writes only the y of the transform")
        assertFieldShows("0.600", "Y after committing")
    }

    @Test
    fun `committing W resizes the source horizontally and nothing else`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.W, "0.5")

        assertEquals(SourceTransform(width = 0.5f), get().transform, "W writes only the width")
        assertFieldShows("0.500", "W after committing")
    }

    @Test
    fun `committing H resizes the source vertically and nothing else`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.H, "0.25")

        assertEquals(SourceTransform(height = 0.25f), get().transform, "H writes only the height")
        assertFieldShows("0.250", "H after committing")
    }

    @Test
    fun `a negative position is accepted, so a source can be parked off-canvas`() =
        sourcePanel(Fixture.text()) { get ->
            commitField(Field.X, "-0.5")

            assertEquals(-0.5f, get().transform.x, "X is not clamped — sources may sit off-screen")
        }

    @Test
    fun `a width of zero is floored so the source cannot vanish`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.W, "0")

        assertEquals(0.01f, get().transform.width, "W is floored at 0.01, not left at zero")
        assertFieldShows("0.010", "W after the floor is applied")
    }

    @Test
    fun `a negative height is floored the same way`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.H, "-3")

        assertEquals(0.01f, get().transform.height, "H is floored at 0.01")
    }

    @Test
    fun `text that is not a number restores the last good value instead of committing`() {
        val placed = Fixture.text().copy(transform = SourceTransform(x = 0.25f))
        sourcePanel(placed) { get ->
            commitField(Field.X, "over there")

            assertEquals(0.25f, get().transform.x, "an unparseable X must leave the transform alone")
            assertFieldShows("0.250", "X after rejecting the typed text")
        }
    }

    @Test
    fun `typing into a position field alone changes nothing until it is committed`() =
        sourcePanel(Fixture.text()) { get ->
            typeField(Field.X, "0.9")

            assertEquals(
                SourceTransform(), get().transform,
                "the field holds what was typed locally; only Done or focus loss commits it",
            )
        }

    @Test
    fun `every kind of source can be moved, and stays its own kind`() {
        // `updateTransform` is the second eleven-branch `when` over the sealed class.
        Fixture.everyKind("move").forEach { source ->
            sourcePanel(source) { get ->
                commitField(Field.X, "0.75")

                assertEquals(
                    0.75f, get().transform.x,
                    "${source::class.simpleName} must accept a new transform",
                )
                assertEquals(
                    source::class, get()::class,
                    "moving a ${source::class.simpleName} must not change its type",
                )
            }
        }
    }

    // ── Rotation ──────────────────────────────────────────────────────────────

    @Test
    fun `committing the rotation input turns the source`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.ROTATION, "90")

        assertEquals(90f, get().transform.rotation, "the rotation input writes the transform's rotation")
        assertFieldShows("90", "the rotation input after committing")
    }

    @Test
    fun `the rotation input accepts a negative angle`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.ROTATION, "-90")

        assertEquals(-90f, get().transform.rotation, "the range runs from -180°")
    }

    @Test
    fun `a rotation past the end of the range is clamped to it`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.ROTATION, "900")

        assertEquals(180f, get().transform.rotation, "the input clamps to the slider's own range")
        assertFieldShows("180", "the rotation input after clamping")
    }

    @Test
    fun `a rotation before the start of the range is clamped to it`() = sourcePanel(Fixture.text()) { get ->
        commitField(Field.ROTATION, "-900")

        assertEquals(-180f, get().transform.rotation, "the range bottoms out at -180°")
    }

    @Test
    fun `text that is not a number leaves the rotation alone`() {
        val turned = Fixture.text().copy(transform = SourceTransform(rotation = 30f))
        sourcePanel(turned) { get ->
            commitField(Field.ROTATION, "sideways")

            assertEquals(30f, get().transform.rotation, "an unparseable angle must not reach the model")
            assertFieldShows("30", "the rotation input after rejecting the typed text")
        }
    }

    @Test
    fun `dragging the rotation slider to its far end turns the source fully clockwise`() =
        sourcePanel(Fixture.image()) { get ->
            tapSliderUnder(Label.ROTATION, fraction = 1f, gapDp = Gap.INPUT)

            assertEquals(180f, get().transform.rotation, "the far end of the track is +180°")
        }

    @Test
    fun `dragging the rotation slider to its near end turns the source fully anticlockwise`() =
        sourcePanel(Fixture.image()) { get ->
            tapSliderUnder(Label.ROTATION, fraction = 0f, gapDp = Gap.INPUT)

            assertEquals(-180f, get().transform.rotation, "the near end of the track is -180°")
        }

    // ── Opacity ───────────────────────────────────────────────────────────────

    @Test
    fun `dragging the opacity slider to its near end makes the source invisible`() =
        sourcePanel(Fixture.image()) { get ->
            tapSliderUnder(Label.OPACITY, fraction = 0f, gapDp = Gap.READOUT)

            assertEquals(0f, get().transform.opacity, "the near end of the track is fully transparent")
            onNodeWithText("0%").assertExists("and the read-out follows immediately")
        }

    @Test
    fun `dragging the opacity slider to its far end makes the source fully opaque`() {
        val faded = Fixture.image().copy(transform = SourceTransform(opacity = 0.2f))
        sourcePanel(faded) { get ->
            tapSliderUnder(Label.OPACITY, fraction = 1f, gapDp = Gap.READOUT)

            assertEquals(1f, get().transform.opacity, "the far end of the track is fully opaque")
            onNodeWithText("100%").assertExists()
        }
    }

    @Test
    fun `a mid-track tap on the opacity slider lands inside the range`() = sourcePanel(Fixture.image()) { get ->
        tapSliderUnder(Label.OPACITY, fraction = 0.5f, gapDp = Gap.READOUT)

        val opacity = get().transform.opacity
        assertTrue(opacity > 0f && opacity < 1f, "a mid-track tap lands between the ends, was $opacity")
        assertEquals(
            1, countOf("${(opacity * 100).toInt()}%"),
            "and the read-out shows exactly the value that was stored",
        )
    }
}
