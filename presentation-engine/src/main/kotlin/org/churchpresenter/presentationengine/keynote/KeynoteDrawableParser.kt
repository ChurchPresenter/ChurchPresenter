package org.churchpresenter.presentationengine.keynote

import org.churchpresenter.presentationengine.keynote.KnFields as F
import java.awt.geom.Path2D
import kotlin.math.PI
import kotlin.math.abs

/** The first reason a slide cannot be drawn natively; later ones are not recorded. */
internal class KnGate {
    var reason: String? = null
        private set

    fun raise(reason: String) {
        if (this.reason == null) this.reason = reason
    }
}

/**
 * One drawable archive -- image, shape, text box, placeholder, group or movie -- into a
 * [KnDrawable], raising on the [KnGate] whatever the renderer cannot reproduce.
 */
internal object KeynoteDrawableParser {

    /** Keynote path element types, and how many points each needs. */
    private const val PATH_MOVE_TO = 1
    private const val PATH_LINE_TO = 2
    private const val PATH_QUAD_TO = 3
    private const val PATH_CURVE_TO = 4
    private const val PATH_CLOSE = 5
    private const val QUAD_POINTS = 2
    private const val CURVE_POINTS = 3

    fun parseDrawable(index: ObjectIndex, id: Long, gate: KnGate): KnDrawable? {
        val type = index.typeOf(id)
        val message = index.message(id) ?: run {
            gate.raise("drawable $id unreadable")
            return null
        }
        return when (type) {
            F.TYPE_TSD_IMAGE -> parseImage(index, message, gate)
            F.TYPE_TSD_SHAPE -> parseShapeCore(index, message)
            F.TYPE_TSWP_SHAPE_INFO -> parseTextShape(index, message, gate)
            F.TYPE_KN_PLACEHOLDER, F.TYPE_KN_PLACEHOLDER_ALT ->
                message.message(F.PLACEHOLDER_SUPER)?.let { parseTextShape(index, it, gate) }
            F.TYPE_TSD_GROUP -> parseGroup(index, message, gate)
            F.TYPE_TSD_MOVIE -> parseMovie(index, message, gate)
            else -> {
                gate.raise("drawable type $type")
                null
            }
        }
    }

    private fun parseImage(index: ObjectIndex, message: IwaMessage, gate: KnGate): KnDrawable? {
        val geometry = geometryOf(message.message(F.IMAGE_SUPER))
        val fileName = renderableImageFile(index, message, gate) ?: return null
        return KnDrawable.Image(geometry, fileName)
    }

    /** The image's data file when the renderer can draw it; otherwise null, with the reason raised. */
    private fun renderableImageFile(index: ObjectIndex, message: IwaMessage, gate: KnGate): String? {
        val dataId = message.message(F.IMAGE_DATA)?.varint(F.DATA_REFERENCE_IDENTIFIER)
        val fileName = dataId?.let { index.dataFileNames[it] }
        val problem = when {
            message.message(F.IMAGE_MASK)?.varint(F.REFERENCE_IDENTIFIER) != null -> "masked image"
            dataId == null -> "image without data"
            fileName == null -> "image data $dataId not in package metadata"
            !KeynoteStyleResolver.isRenderableImage(fileName) -> "image format .${fileName.substringAfterLast('.', "")}"
            else -> null
        }
        problem?.let { gate.raise(it) }
        return fileName.takeIf { problem == null }
    }

    /** [message] is a TSD.ShapeArchive (top-level or the embedded super of a ShapeInfo). */
    private fun parseShapeCore(index: ObjectIndex, message: IwaMessage): KnDrawable.Shape {
        val geometry = geometryOf(message.message(F.SHAPE_SUPER))
        val style = KeynoteStyleResolver.resolveShapeStyle(
            index, message.message(F.SHAPE_STYLE)?.varint(F.REFERENCE_IDENTIFIER),
        )
        val path = message.message(F.SHAPE_PATHSOURCE)?.let { parsePathSource(it) }
        return KnDrawable.Shape(
            geometry = geometry,
            path = path,
            fill = style?.fill,
            strokeColor = style?.strokeColor,
            strokeWidthPt = style?.strokeWidthPt ?: 0.0,
            opacity = style?.opacity ?: 1.0
        )
    }

    private fun parseTextShape(index: ObjectIndex, shapeInfo: IwaMessage, gate: KnGate): KnDrawable? {
        val shapeArchive = shapeInfo.message(F.SHAPE_INFO_SUPER) ?: run {
            gate.raise("text shape without shape archive")
            return null
        }
        val shape = parseShapeCore(index, shapeArchive)
        val storageId = shapeInfo.message(F.SHAPE_INFO_OWNED_STORAGE)?.varint(F.REFERENCE_IDENTIFIER)
        val storage = storageId?.let { index.message(it) }
        val paragraphs = storage?.let { KeynoteTextParser.parseParagraphs(index, it) } ?: emptyList()
        return if (paragraphs.any { it.text.isNotBlank() }) {
            KnDrawable.Text(shape.geometry, shape, paragraphs)
        } else {
            shape
        }
    }

    /** Gates only when the movie's own asset can't be resolved — a missing poster still plays. */
    private fun parseMovie(index: ObjectIndex, message: IwaMessage, gate: KnGate): KnDrawable? {
        val geometry = geometryOf(message.message(F.MOVIE_SUPER))
        val dataId = message.message(F.MOVIE_DATA)?.varint(F.DATA_REFERENCE_IDENTIFIER) ?: run {
            gate.raise("movie without data")
            return null
        }
        val videoFile = index.dataFileNames[dataId] ?: run {
            gate.raise("movie data $dataId not in package metadata")
            return null
        }
        val posterDataId = message.message(F.MOVIE_POSTER)?.varint(F.DATA_REFERENCE_IDENTIFIER)
        val posterFile = posterDataId?.let { index.dataFileNames[it] }
        return KnDrawable.Movie(geometry, videoFile, posterFile)
    }

    private fun parseGroup(index: ObjectIndex, message: IwaMessage, gate: KnGate): KnDrawable {
        val geometry = geometryOf(message.message(F.GROUP_SUPER))
        val children = message.messages(F.GROUP_CHILDREN)
            .mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }
            .mapNotNull { childId -> parseDrawable(index, childId, gate)?.let { KnPlacedDrawable(childId, it) } }
        return KnDrawable.Group(geometry, children)
    }

    private fun geometryOf(drawable: IwaMessage?): KnGeometry {
        val geometry = drawable?.message(F.DRAWABLE_GEOMETRY) ?: return KnGeometry.ZERO
        val position = geometry.message(F.GEOMETRY_POSITION)
        val size = geometry.message(F.GEOMETRY_SIZE)
        val rawAngle = geometry.float(F.GEOMETRY_ANGLE)?.toDouble() ?: 0.0
        // Angle units are undocumented; magnitudes beyond 2π are clearly degrees.
        val angle = if (abs(rawAngle) > 2 * PI + 0.1) Math.toRadians(rawAngle) else rawAngle
        // GeometryArchive.flags is NOT a flip bitmask: real documents carry flags=3 on plain
        // unflipped drawables (validated against a real deck — interpreting them as flips
        // rendered every slide rotated 180°). Flip handling needs the true bit meaning first.
        return KnGeometry(
            x = position?.float(F.POINT_X)?.toDouble() ?: 0.0,
            y = position?.float(F.POINT_Y)?.toDouble() ?: 0.0,
            w = size?.float(F.SIZE_WIDTH)?.toDouble() ?: 0.0,
            h = size?.float(F.SIZE_HEIGHT)?.toDouble() ?: 0.0,
            angle = angle,
            hFlip = false,
            vFlip = false
        )
    }

    /** Normalizes the path source into the unit square; null = plain rectangle. */
    private fun parsePathSource(pathSource: IwaMessage): Path2D.Double? {
        pathSource.message(F.PATHSOURCE_BEZIER)?.let { bezier ->
            val naturalSize = bezier.message(F.BEZIER_PATH_NATURAL_SIZE)
            val w = naturalSize?.float(F.SIZE_WIDTH)?.toDouble()?.takeIf { it > 0 } ?: 1.0
            val h = naturalSize?.float(F.SIZE_HEIGHT)?.toDouble()?.takeIf { it > 0 } ?: 1.0
            return bezier.message(F.BEZIER_PATH_PATH)?.let { parseTspPath(it, w, h) }
        }
        // Scalar (rounded rect etc.) and point paths approximate to a rectangle; the fill and
        // geometry still match, only corner styling is lost.
        return null
    }

    private fun parseTspPath(path: IwaMessage, naturalW: Double, naturalH: Double): Path2D.Double? {
        val result = Path2D.Double()
        var hasContent = false
        for (element in path.messages(F.PATH_ELEMENTS)) {
            val type = element.varint(F.PATH_ELEMENT_TYPE)?.toInt() ?: return null
            val points = element.messages(F.PATH_ELEMENT_POINTS).map { p ->
                ((p.float(F.POINT_X)?.toDouble() ?: 0.0) / naturalW) to
                    ((p.float(F.POINT_Y)?.toDouble() ?: 0.0) / naturalH)
            }
            when (type) {
                PATH_MOVE_TO -> points.getOrNull(0)?.let { result.moveTo(it.first, it.second); hasContent = true }
                PATH_LINE_TO -> points.getOrNull(0)?.let { result.lineTo(it.first, it.second); hasContent = true }
                PATH_QUAD_TO -> if (points.size >= QUAD_POINTS) {
                    result.quadTo(points[0].first, points[0].second, points[1].first, points[1].second)
                    hasContent = true
                }
                PATH_CURVE_TO -> if (points.size >= CURVE_POINTS) {
                    result.curveTo(
                        points[0].first, points[0].second,
                        points[1].first, points[1].second,
                        points[2].first, points[2].second
                    )
                    hasContent = true
                }
                PATH_CLOSE -> result.closePath()
                else -> return null
            }
        }
        return result.takeIf { hasContent }
    }
}
