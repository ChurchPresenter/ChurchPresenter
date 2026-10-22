package org.churchpresenter.presenter

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A text layer's transform read from the file and played back, which the band's second-voice
 * colour follows: the easing, the keyframes, the ways a file writes a value, and the matrix.
 */
class LayerMotionTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun assertNear(expected: Float, actual: Float, what: String = "") =
        assertTrue(abs(expected - actual) < 0.01f, "$what: expected $expected, was $actual")

    private fun assertNear(expected: Offset, actual: Offset, what: String = "") {
        assertNear(expected.x, actual.x, "$what x")
        assertNear(expected.y, actual.y, "$what y")
    }

    // ── Easing ──────────────────────────────────────────────────────────────────

    @Test
    fun `a straight curve eases evenly`() {
        assertNear(0.25f, cubicBezier(0.25f, 0f, 0f, 1f, 1f))
        assertNear(0.5f, cubicBezier(0.5f, 0f, 0f, 1f, 1f))
    }

    @Test
    fun `a curve starts at nothing and ends at all of it`() {
        assertEquals(0f, cubicBezier(-0.5f, 0.4f, 0f, 0.2f, 1f))
        assertEquals(1f, cubicBezier(1.5f, 0.4f, 0f, 0.2f, 1f))
    }

    @Test
    fun `an ease-in curve is behind halfway through`() {
        assertTrue(cubicBezier(0.5f, 0.42f, 0f, 1f, 1f) < 0.5f)
    }

    // ── Keyframes ───────────────────────────────────────────────────────────────

    private val track = KeyedTrack(
        listOf(
            Keyframe(time = 10f, value = listOf(0f, 100f)),
            Keyframe(time = 20f, value = listOf(100f, 200f), hold = true),
            Keyframe(time = 30f, value = listOf(50f)),
        ),
    )

    @Test
    fun `before the first keyframe a track holds its first value`() {
        assertEquals(listOf(0f, 100f), track.at(0f))
    }

    @Test
    fun `after the last keyframe a track holds its last value`() {
        assertEquals(listOf(50f), track.at(40f))
    }

    @Test
    fun `between two keyframes a track moves from one to the next`() {
        val half = track.at(15f)

        assertNear(50f, half[0])
        assertNear(150f, half[1])
    }

    @Test
    fun `a held keyframe jumps rather than moving`() {
        assertEquals(listOf(100f, 200f), track.at(25f))
    }

    @Test
    fun `a value the next keyframe leaves out stays where it was`() {
        val short = KeyedTrack(listOf(Keyframe(0f, listOf(0f, 7f)), Keyframe(10f, listOf(10f))))

        assertEquals(7f, short.at(5f)[1])
    }

    // ── Reading a property ──────────────────────────────────────────────────────

    @Test
    fun `a still number and a still array are read as they are`() {
        assertEquals(listOf(45f), readTrack(json("""{"a":0,"k":45}"""))?.at(0f))
        assertEquals(listOf(1f, 2f, 3f), readTrack(json("""{"a":0,"k":[1,2,3]}"""))?.at(99f))
    }

    @Test
    fun `keyframes are read with their handles`() {
        val p = json(
            """{"a":1,"k":[
                {"t":0,"s":[0,0],"o":{"x":[0.42],"y":[0]},"i":{"x":[0.58],"y":[1]}},
                {"t":10,"s":[100,0]}
            ]}""",
        )

        val t = assertNotNull(readTrack(p))

        assertNear(50f, t.at(5f)[0], "an even curve is halfway at halfway")
        assertTrue(t.at(2f)[0] < 20f, "eased in")
    }

    @Test
    fun `an old-style keyframe's end value carries to the next`() {
        val p = json("""{"a":1,"k":[{"t":0,"s":[0],"e":[80]},{"t":10}]}""")

        assertEquals(listOf(80f), readTrack(p)?.at(10f))
    }

    @Test
    fun `a held keyframe in the file is read as held`() {
        val p = json("""{"a":1,"k":[{"t":0,"s":[0],"h":1},{"t":10,"s":[50]}]}""")

        assertEquals(listOf(0f), readTrack(p)?.at(9f))
    }

    @Test
    fun `a position split into x and y is read as one`() {
        val p = json(
            """{"s":true,
                "x":{"a":1,"k":[{"t":0,"s":[0]},{"t":10,"s":[100]}]},
                "y":{"a":0,"k":40}}""",
        )

        val at = assertNotNull(readTrack(p)).at(10f)

        assertEquals(listOf(100f, 40f), at)
    }

    @Test
    fun `what cannot be read is no track`() {
        assertNull(readTrack(json("""{"a":0}""")))
        assertNull(readTrack(json("""{"a":0,"k":"x"}""")))
        assertNull(readTrack(json("""{"a":1,"k":[{"s":[1]}]}""")), "a keyframe with no time")
        assertNull(readTrack(json("""{"s":1,"x":{"a":0,"k":1}}""")), "a split position missing y")
    }

    // ── The layers that move ────────────────────────────────────────────────────

    private fun layer(type: Int, name: String, ks: String) =
        json("""{"ty":$type,"nm":"$name","st":5,"ks":$ks}""")

    private val still =
        """{"a":{"a":0,"k":[0,0]},"p":{"a":0,"k":[10,10]},"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":0}}"""
    private val sliding = """{"p":{"a":1,"k":[{"t":0,"s":[0,50]},{"t":10,"s":[0,0]}]}}"""

    @Test
    fun `only text layers that move are read`() {
        val motions = readTextMotions(
            listOf(
                layer(5, "moving", sliding),
                layer(5, "still", still),
                layer(4, "a shape", sliding),
                json("""{"ty":5,"ks":$sliding}"""),
                json("""{"ty":5,"nm":"no transform"}"""),
            ),
        )

        assertEquals(setOf("moving"), motions.keys)
    }

    @Test
    fun `a split position that moves makes the layer move`() {
        val split = """{"p":{"s":true,"x":{"a":1,"k":[{"t":0,"s":[0]},{"t":10,"s":[9]}]},"y":{"a":0,"k":0}}}"""

        assertEquals(setOf("split"), readTextMotions(listOf(layer(5, "split", split))).keys)
    }

    @Test
    fun `a layer's motion runs on its own time`() {
        val motion = readTextMotions(listOf(layer(5, "moving", sliding))).getValue("moving")

        assertNear(Offset(0f, 50f), motion.matrixAt(5f).map(Offset.Zero))
        assertNear(Offset(0f, 0f), motion.matrixAt(15f).map(Offset.Zero))
    }

    @Test
    fun `a 3D layer's rotation is read from rz`() {
        val turning = """{"rz":{"a":1,"k":[{"t":0,"s":[0]},{"t":10,"s":[90]}]}}"""
        val motion = readTextMotions(listOf(layer(5, "turning", turning))).getValue("turning")

        assertNear(Offset(0f, 10f), motion.matrixAt(15f).map(Offset(10f, 0f)))
    }

    @Test
    fun `a layer that moves in every way is read whole, from the start of the composition`() {
        val all = json(
            """{"ty":5,"nm":"all","ks":{
                "a":{"a":1,"k":[{"t":0,"s":[10,0]},{"t":10,"s":[10,0]}]},
                "p":{"s":1,"x":{"a":0,"k":50},"y":{"a":0,"k":0}},
                "s":{"a":1,"k":[{"t":0,"s":[100,100]},{"t":10,"s":[200,200]}]},
                "r":{"a":1,"k":[{"t":0,"s":[0]},{"t":10,"s":[0]}]}
            }}""",
        )

        val motion = readTextMotions(listOf(all)).getValue("all")

        assertNear(Offset(50f, 0f), motion.matrixAt(0f).map(Offset(10f, 0f)), "the anchor sits on the position")
        assertNear(Offset(70f, 0f), motion.matrixAt(10f).map(Offset(20f, 0f)), "scaled about the anchor")
    }

    // ── The matrix ──────────────────────────────────────────────────────────────

    @Test
    fun `the matrix scales and turns about the anchor, then moves it to the position`() {
        val motion = LayerMotion(
            anchor = { listOf(10f, 10f) },
            position = { listOf(100f, 100f) },
            scale = { listOf(200f, 50f) },
            rotation = { listOf(0f) },
        )

        assertNear(Offset(100f, 100f), motion.matrixAt(0f).map(Offset(10f, 10f)))
        assertNear(Offset(120f, 105f), motion.matrixAt(0f).map(Offset(20f, 20f)))
    }

    @Test
    fun `a value a track leaves out falls back to rest`() {
        val motion = LayerMotion(
            anchor = { emptyList() },
            position = { emptyList() },
            scale = { emptyList() },
            rotation = { emptyList() },
        )

        assertNear(Offset(3f, 4f), motion.matrixAt(0f).map(Offset(3f, 4f)))
    }
}
