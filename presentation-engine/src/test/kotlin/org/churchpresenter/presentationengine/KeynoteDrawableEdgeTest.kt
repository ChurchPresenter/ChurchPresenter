package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.Fixtures.ProtoWriter
import org.churchpresenter.presentationengine.keynote.KeynoteDrawableParser
import org.churchpresenter.presentationengine.keynote.KnDrawable
import org.churchpresenter.presentationengine.keynote.KnFields as F
import org.churchpresenter.presentationengine.keynote.KnGate
import org.churchpresenter.presentationengine.keynote.ObjectIndex
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KeynoteDrawableEdgeTest {

    private val temp: File = Files.createTempDirectory("keynote-drawable-edge-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun proto(block: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(block).toByteArray()

    private fun parse(id: Long, vararg objects: Triple<Long, Int, ByteArray>): Pair<KnDrawable?, KnGate> {
        val dir = Files.createTempDirectory(temp.toPath(), "deck").toFile()
        val index = assertNotNull(ObjectIndex.load(Fixtures.writeKeynoteDir(dir, objects.toList())))
        val gate = KnGate()
        return KeynoteDrawableParser.parseDrawable(index, id, gate) to gate
    }

    private fun point(x: Float?, y: Float?) = proto {
        x?.let { floatField(F.POINT_X, it) }
        y?.let { floatField(F.POINT_Y, it) }
    }

    private fun element(type: Int?, vararg points: ByteArray) = proto {
        type?.let { varintField(F.PATH_ELEMENT_TYPE, it.toLong()) }
        points.forEach { bytesField(F.PATH_ELEMENT_POINTS, it) }
    }

    private fun bezierShape(vararg elements: ByteArray, naturalSize: ByteArray? = null) = proto {
        val bezier = proto {
            naturalSize?.let { bytesField(F.BEZIER_PATH_NATURAL_SIZE, it) }
            bytesField(F.BEZIER_PATH_PATH, proto { elements.forEach { bytesField(F.PATH_ELEMENTS, it) } })
        }
        bytesField(F.SHAPE_PATHSOURCE, proto { bytesField(F.PATHSOURCE_BEZIER, bezier) })
    }

    private fun shapePath(vararg elements: ByteArray, naturalSize: ByteArray? = null) =
        assertIs<KnDrawable.Shape>(
            parse(1L, Triple(1L, F.TYPE_TSD_SHAPE, bezierShape(*elements, naturalSize = naturalSize))).first,
        ).path

    private fun segments(path: Path2D.Double): List<Int> {
        val result = mutableListOf<Int>()
        val it = path.getPathIterator(null)
        val coords = DoubleArray(6)
        while (!it.isDone) {
            result.add(it.currentSegment(coords))
            it.next()
        }
        return result
    }

    @Test
    fun `a path with points missing coordinates or segments missing points keeps what it can`() {
        val size = proto { floatField(F.SIZE_WIDTH, 0f); floatField(F.SIZE_HEIGHT, -1f) }
        val path = assertNotNull(
            shapePath(
                element(1, point(null, null)),
                element(2),
                element(3, point(1f, 1f)),
                element(4, point(1f, 1f), point(2f, 2f)),
                element(2, point(1f, null)),
                element(5),
                naturalSize = size,
            )
        )
        assertEquals(
            listOf(PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO, PathIterator.SEG_CLOSE),
            segments(path),
        )
        val bounds = path.bounds2D
        assertEquals(1.0, bounds.maxX, 1e-9, "a non-positive natural size counts as one")
    }

    @Test
    fun `a path with nothing drawable is a plain rectangle`() {
        assertNull(shapePath(element(1)))
    }

    @Test
    fun `a path element of unknown or missing type abandons the outline`() {
        assertNull(shapePath(element(1, point(0f, 0f)), element(9, point(1f, 1f))))
        assertNull(shapePath(element(1, point(0f, 0f)), element(null, point(1f, 1f))))
    }

    @Test
    fun `a bezier source with no path and a scalar source are both rectangles`() {
        val noPath = proto {
            bytesField(F.SHAPE_PATHSOURCE, proto { bytesField(F.PATHSOURCE_BEZIER, proto { varintField(9, 1) }) })
        }
        val scalar = proto {
            bytesField(F.SHAPE_PATHSOURCE, proto { bytesField(F.PATHSOURCE_SCALAR, proto { varintField(1, 1) }) })
        }
        assertNull(assertIs<KnDrawable.Shape>(parse(1L, Triple(1L, F.TYPE_TSD_SHAPE, noPath)).first).path)
        assertNull(assertIs<KnDrawable.Shape>(parse(1L, Triple(1L, F.TYPE_TSD_SHAPE, scalar)).first).path)
    }

    @Test
    fun `geometry without position or size sits at the origin with no extent`() {
        val geometryNoSize = proto { floatField(F.GEOMETRY_ANGLE, 90f) }
        val shape = proto {
            bytesField(F.SHAPE_SUPER, proto { bytesField(F.DRAWABLE_GEOMETRY, geometryNoSize) })
        }
        val geometry = assertNotNull(parse(1L, Triple(1L, F.TYPE_TSD_SHAPE, shape)).first).geometry
        assertEquals(0.0, geometry.x)
        assertEquals(0.0, geometry.w)
        assertEquals(Math.PI / 2, geometry.angle, 1e-9, "a magnitude beyond a full turn is degrees")

        val partial = proto {
            bytesField(F.GEOMETRY_POSITION, proto { varintField(9, 1) })
            bytesField(F.GEOMETRY_SIZE, proto { varintField(9, 1) })
        }
        val partialShape = proto {
            bytesField(F.SHAPE_SUPER, proto { bytesField(F.DRAWABLE_GEOMETRY, partial) })
        }
        val zero = assertNotNull(parse(1L, Triple(1L, F.TYPE_TSD_SHAPE, partialShape)).first).geometry
        assertEquals(0.0, zero.y)
        assertEquals(0.0, zero.h)
    }

    @Test
    fun `a placeholder without its text shape is skipped without gating`() {
        val (drawable, gate) = parse(1L, Triple(1L, F.TYPE_KN_PLACEHOLDER_ALT, proto { varintField(9, 1) }))
        assertNull(drawable)
        assertNull(gate.reason)
    }

    @Test
    fun `a text shape without its shape archive gates`() {
        val (drawable, gate) = parse(1L, Triple(1L, F.TYPE_TSWP_SHAPE_INFO, proto { varintField(9, 1) }))
        assertNull(drawable)
        assertEquals("text shape without shape archive", gate.reason)
    }

    @Test
    fun `a movie whose data reference has no identifier gates`() {
        val movie = proto { bytesField(F.MOVIE_DATA, proto { varintField(9, 1) }) }
        val (drawable, gate) = parse(1L, Triple(1L, F.TYPE_TSD_MOVIE, movie))
        assertNull(drawable)
        assertEquals("movie without data", gate.reason)
    }

    @Test
    fun `a text shape whose storage holds only blank text is a plain shape`() {
        val shapeArchive = proto { varintField(9, 1) }
        val info = proto {
            bytesField(F.SHAPE_INFO_SUPER, shapeArchive)
            bytesField(F.SHAPE_INFO_OWNED_STORAGE, proto { varintField(F.REFERENCE_IDENTIFIER, 2L) })
        }
        val storage = proto { stringField(3, "   ") }
        val (drawable, _) =
            parse(1L, Triple(1L, F.TYPE_TSWP_SHAPE_INFO, info), Triple(2L, F.TYPE_TSWP_STORAGE, storage))
        assertIs<KnDrawable.Shape>(drawable)
    }
}
