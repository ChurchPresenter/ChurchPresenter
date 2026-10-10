package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.Fixtures.ProtoWriter
import org.churchpresenter.presentationengine.keynote.IwaMessage
import org.churchpresenter.presentationengine.keynote.KeynoteStyleResolver
import org.churchpresenter.presentationengine.keynote.KnFields as F
import org.churchpresenter.presentationengine.keynote.ObjectIndex
import java.awt.Color
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KeynoteStyleResolverTest {

    private val temp: File = Files.createTempDirectory("keynote-style-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun reference(id: Long) = ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, id) }.toByteArray()

    private fun color(r: Float? = null, g: Float? = null, b: Float? = null, a: Float? = null) = ProtoWriter().apply {
        varintField(F.COLOR_MODEL, 1)
        r?.let { floatField(F.COLOR_R, it) }
        g?.let { floatField(F.COLOR_G, it) }
        b?.let { floatField(F.COLOR_B, it) }
        a?.let { floatField(F.COLOR_A, it) }
    }.toByteArray()

    private fun message(bytes: ByteArray) = assertNotNull(IwaMessage.parse(bytes))

    private fun index(vararg objects: Triple<Long, Int, ByteArray>): ObjectIndex {
        val dir = Files.createTempDirectory(temp.toPath(), "deck").toFile()
        return assertNotNull(ObjectIndex.load(Fixtures.writeKeynoteDir(dir, objects.toList())))
    }

    private fun style(parent: Long?, props: ByteArray?) = ProtoWriter().apply {
        if (parent != null) {
            bytesField(F.STYLE_SUPER, ProtoWriter().apply { bytesField(F.TSS_STYLE_PARENT, reference(parent)) }.toByteArray())
        }
        if (props != null) bytesField(F.SHAPE_STYLE_PROPERTIES, props)
    }.toByteArray()

    private fun fillWithColor(c: ByteArray) = ProtoWriter().apply { bytesField(F.FILL_COLOR, c) }.toByteArray()

    private val empty = index(Triple(1L, 1, ProtoWriter().apply { varintField(1, 1) }.toByteArray()))

    @Test
    fun `a color with no channels is opaque black and out-of-range channels are clamped`() {
        assertEquals(Color(0f, 0f, 0f, 1f), KeynoteStyleResolver.parseColor(message(color())))
        assertEquals(Color(1f, 0f, 0.5f, 1f), KeynoteStyleResolver.parseColor(message(color(2f, -1f, 0.5f, 3f))))
    }

    @Test
    fun `a gradient fill degrades to its first stop`() {
        val stop = ProtoWriter().apply { bytesField(F.GRADIENT_STOP_COLOR, color(0f, 0f, 1f, 1f)) }.toByteArray()
        val gradient = ProtoWriter().apply { bytesField(F.GRADIENT_STOPS, stop) }.toByteArray()
        val fill = ProtoWriter().apply { bytesField(F.FILL_GRADIENT, gradient) }.toByteArray()
        assertEquals(Color.BLUE, KeynoteStyleResolver.parseFill(empty, message(fill))?.color)

        val colorless = ProtoWriter().apply {
            bytesField(F.FILL_GRADIENT, ProtoWriter().apply { bytesField(F.GRADIENT_STOPS, ProtoWriter().apply { varintField(9, 1) }.toByteArray()) }.toByteArray())
        }.toByteArray()
        assertNull(KeynoteStyleResolver.parseFill(empty, message(colorless)))
    }

    @Test
    fun `an image fill names its data file only when it can be drawn`() {
        val metadata = ProtoWriter().apply {
            bytesField(F.PACKAGE_METADATA_DATAS, ProtoWriter().apply { varintField(F.DATA_INFO_IDENTIFIER, 7); stringField(F.DATA_INFO_FILE_NAME, "bg.png") }.toByteArray())
            bytesField(F.PACKAGE_METADATA_DATAS, ProtoWriter().apply { varintField(F.DATA_INFO_IDENTIFIER, 8); stringField(F.DATA_INFO_PREFERRED_FILE_NAME, "clip.mov") }.toByteArray())
        }.toByteArray()
        val index = index(Triple(1L, F.TYPE_TSP_PACKAGE_METADATA, metadata))

        fun imageFill(dataId: Long) = message(ProtoWriter().apply {
            bytesField(F.FILL_IMAGE, ProtoWriter().apply {
                bytesField(F.IMAGE_FILL_DATA, ProtoWriter().apply { varintField(F.DATA_REFERENCE_IDENTIFIER, dataId) }.toByteArray())
            }.toByteArray())
        }.toByteArray())

        assertEquals("bg.png", KeynoteStyleResolver.parseFill(index, imageFill(7))?.imageFile)
        assertNull(KeynoteStyleResolver.parseFill(index, imageFill(8)), "a movie is not an image fill")
        assertNull(KeynoteStyleResolver.parseFill(index, imageFill(9)), "an unknown data id")
    }

    @Test
    fun `shape style values are inherited from the nearest style that sets them`() {
        val strokeNoColor = ProtoWriter().apply { floatField(F.STROKE_WIDTH, 4f) }.toByteArray()
        val strokeColored = ProtoWriter().apply { bytesField(F.STROKE_COLOR, color(1f, 0f, 0f)); floatField(F.STROKE_WIDTH, 2f) }.toByteArray()
        val childProps = ProtoWriter().apply { floatField(F.SHAPE_PROPS_OPACITY, 0.5f); bytesField(F.SHAPE_PROPS_STROKE, strokeNoColor) }.toByteArray()
        val parentProps = ProtoWriter().apply {
            bytesField(F.SHAPE_PROPS_FILL, fillWithColor(color(0f, 1f, 0f)))
            bytesField(F.SHAPE_PROPS_STROKE, strokeColored)
        }.toByteArray()
        val index = index(Triple(10L, F.TYPE_TSD_SHAPE_STYLE, style(11L, childProps)), Triple(11L, F.TYPE_TSD_SHAPE_STYLE, style(null, parentProps)))

        val resolved = assertNotNull(KeynoteStyleResolver.resolveShapeStyle(index, 10L))
        assertEquals(Color.GREEN, resolved.fill?.color)
        assertEquals(Color.RED, resolved.strokeColor)
        assertEquals(2.0, resolved.strokeWidthPt, 1e-6, "the width beside the colour taken")
        assertEquals(0.5, resolved.opacity, 1e-6)
    }

    @Test
    fun `with no stroke colour anywhere the farthest stroke width is used`() {
        val near = ProtoWriter().apply { bytesField(F.SHAPE_PROPS_STROKE, ProtoWriter().apply { floatField(F.STROKE_WIDTH, 1f) }.toByteArray()); floatField(F.SHAPE_PROPS_OPACITY, 1f) }.toByteArray()
        val far = ProtoWriter().apply { bytesField(F.SHAPE_PROPS_STROKE, ProtoWriter().apply { floatField(F.STROKE_WIDTH, 6f) }.toByteArray()) }.toByteArray()
        val index = index(Triple(10L, F.TYPE_TSD_SHAPE_STYLE, style(11L, near)), Triple(11L, F.TYPE_TSD_SHAPE_STYLE, style(null, far)))

        val resolved = assertNotNull(KeynoteStyleResolver.resolveShapeStyle(index, 10L))
        assertNull(resolved.strokeColor)
        assertNull(resolved.fill)
        assertEquals(6.0, resolved.strokeWidthPt, 1e-6)
    }

    @Test
    fun `a style chain that sets nothing resolves to nothing`() {
        val index = index(
            Triple(10L, F.TYPE_TSD_SHAPE_STYLE, style(null, ProtoWriter().apply { varintField(9, 1) }.toByteArray())),
            Triple(12L, F.TYPE_TSWP_CHARACTER_STYLE, style(null, null)),
        )
        assertNull(KeynoteStyleResolver.resolveShapeStyle(index, 10L))
        assertNull(KeynoteStyleResolver.resolveShapeStyle(index, null))
        assertNull(KeynoteStyleResolver.resolveCharProps(index, 12L))
    }

    @Test
    fun `character properties merge up the chain and stop once complete`() {
        fun charStyle(parent: Long?, props: ProtoWriter.() -> Unit) = ProtoWriter().apply {
            if (parent != null) {
                bytesField(F.STYLE_SUPER, ProtoWriter().apply { bytesField(F.TSS_STYLE_PARENT, reference(parent)) }.toByteArray())
            }
            bytesField(F.CHARACTER_STYLE_PROPERTIES, ProtoWriter().apply(props).toByteArray())
        }.toByteArray()
        val index = index(
            Triple(20L, F.TYPE_TSWP_CHARACTER_STYLE, charStyle(21L) { stringField(F.CHAR_PROPS_FONT_NAME, "Avenir"); varintField(F.CHAR_PROPS_ITALIC, 1) }),
            Triple(21L, F.TYPE_TSWP_CHARACTER_STYLE, charStyle(22L) {
                stringField(F.CHAR_PROPS_FONT_NAME, "Ignored")
                floatField(F.CHAR_PROPS_FONT_SIZE, 36f)
                varintField(F.CHAR_PROPS_BOLD, 1)
                bytesField(F.CHAR_PROPS_FONT_COLOR, color(1f, 1f, 1f))
            }),
            Triple(22L, F.TYPE_TSWP_CHARACTER_STYLE, charStyle(null) { varintField(F.CHAR_PROPS_BOLD, 0) }),
        )

        val props = assertNotNull(KeynoteStyleResolver.resolveCharProps(index, 20L))
        assertEquals("Avenir", props.fontName)
        assertEquals(36.0, props.fontSize)
        assertEquals(true, props.bold)
        assertEquals(true, props.italic)
        assertEquals(Color.WHITE, props.color)
    }
}
