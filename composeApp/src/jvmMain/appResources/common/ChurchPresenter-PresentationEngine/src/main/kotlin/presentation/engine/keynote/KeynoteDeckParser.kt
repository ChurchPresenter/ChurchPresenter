package presentation.engine.keynote

import presentation.engine.keynote.KnFields as F
import java.awt.Color
import java.awt.geom.Path2D
import java.io.File
import kotlin.math.PI
import kotlin.math.abs

/**
 * Traverses the IWA object graph (Document → Show → slide nodes → slides → drawables/builds/
 * transitions/notes) into a renderable [KeynoteScene].
 *
 * Whitelist + per-slide fidelity gate: a slide containing anything the renderer can't reproduce
 * (movies, charts, tables, masked images, unknown drawable types) gets a [KnSlide.gateReason]
 * and renders from the static fallback instead — never a crash, never a missing slide.
 */
internal object KeynoteDeckParser {

    private val RENDERABLE_IMAGE_EXTENSIONS =
        setOf("jpg", "jpeg", "png", "gif", "tiff", "tif", "bmp")

    fun parse(file: File): KeynoteScene? {
        val index = ObjectIndex.load(file) ?: return null
        val document = index.firstOfType(F.TYPE_KN_DOCUMENT)?.second ?: return null
        val showId = document.message(F.DOCUMENT_SHOW)?.varint(F.REFERENCE_IDENTIFIER) ?: return null
        val show = index.message(showId) ?: return null
        val size = show.message(F.SHOW_SIZE) ?: return null
        val widthPt = size.float(F.SIZE_WIDTH)?.toDouble() ?: return null
        val heightPt = size.float(F.SIZE_HEIGHT)?.toDouble() ?: return null
        if (widthPt <= 0 || heightPt <= 0) return null

        val slideIds = collectSlideIds(index, show)
        if (slideIds.isEmpty()) return null

        val slides = slideIds.mapIndexed { slideIndex, slideId ->
            parseSlide(index, slideId, slideIndex)
        }
        return KeynoteScene(file, widthPt, heightPt, slides)
    }

    /** Depth-first over the slide-node tree, skipping slides marked skipped. */
    private fun collectSlideIds(index: ObjectIndex, show: IwaMessage): List<Long> {
        val result = mutableListOf<Long>()
        fun walkNode(nodeId: Long) {
            val node = index.message(nodeId) ?: return
            if (node.bool(F.SLIDE_NODE_IS_SKIPPED) != true) {
                node.message(F.SLIDE_NODE_SLIDE)?.varint(F.REFERENCE_IDENTIFIER)?.let { result.add(it) }
            }
            node.messages(F.SLIDE_NODE_CHILDREN)
                .mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }
                .forEach { walkNode(it) }
        }
        show.message(F.SHOW_SLIDE_TREE)
            ?.messages(F.SLIDE_TREE_SLIDES)
            ?.mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }
            ?.forEach { walkNode(it) }
        return result
    }

    // ── Slide ─────────────────────────────────────────────────────────────────

    private class Gate {
        var reason: String? = null
        fun raise(reason: String) {
            if (this.reason == null) this.reason = reason
        }
    }

    private fun parseSlide(index: ObjectIndex, slideId: Long, slideIndex: Int): KnSlide {
        val gate = Gate()
        val slide = index.message(slideId)
        if (slide == null) {
            return KnSlide(slideIndex, null, emptyList(), "", null, emptySet(), null, "slide archive unreadable")
        }

        // Master chain, deepest first (theme decorations render below slide content).
        val masters = mutableListOf<IwaMessage>()
        var templateRef = slide.message(F.SLIDE_TEMPLATE_SLIDE)?.varint(F.REFERENCE_IDENTIFIER)
        var guard = 0
        while (templateRef != null && guard++ < 8) {
            val master = index.message(templateRef) ?: break
            masters.add(0, master)
            templateRef = master.message(F.SLIDE_TEMPLATE_SLIDE)?.varint(F.REFERENCE_IDENTIFIER)
        }

        // Background: the slide's own style fill, else the nearest master's.
        val background = (listOf(slide) + masters.reversed())
            .firstNotNullOfOrNull { archive -> slideStyleFill(index, archive) }

        val drawables = mutableListOf<KnPlacedDrawable>()
        for (master in masters) {
            for (id in drawableIds(master)) {
                if (isPlaceholderType(index.typeOf(id))) continue // master placeholders are prompts
                parseDrawable(index, id, gate)?.let { drawables.add(KnPlacedDrawable(id, it)) }
            }
        }
        for (id in drawableIds(slide)) {
            parseDrawable(index, id, gate)?.let { drawables.add(KnPlacedDrawable(id, it)) }
        }

        val notes = slide.message(F.SLIDE_NOTE)?.varint(F.REFERENCE_IDENTIFIER)
            ?.let { index.message(it) }
            ?.message(F.NOTE_CONTAINED_STORAGE)?.varint(F.REFERENCE_IDENTIFIER)
            ?.let { index.message(it) }
            ?.strings(F.STORAGE_TEXT)?.joinToString("")
            ?.replace('\u2029', '\n')?.trim()
            ?: ""

        val builds = KeynoteBuildMapper.map(index, slide)
        val transition = KeynoteBuildMapper.mapTransition(slide)

        return KnSlide(
            index = slideIndex,
            background = background,
            drawables = drawables,
            notes = notes,
            timeline = builds?.timeline,
            builtDrawableIds = builds?.builtDrawableIds ?: emptySet(),
            transition = transition,
            gateReason = gate.reason
        )
    }

    private fun drawableIds(slide: IwaMessage): List<Long> {
        val placeholders = listOf(
            F.SLIDE_TITLE_PLACEHOLDER, F.SLIDE_BODY_PLACEHOLDER,
            F.SLIDE_OBJECT_PLACEHOLDER, F.SLIDE_SLIDE_NUMBER_PLACEHOLDER
        ).mapNotNull { slide.message(it)?.varint(F.REFERENCE_IDENTIFIER) }
        val zOrder = slide.messages(F.SLIDE_DRAWABLES_Z_ORDER).mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }
        if (zOrder.isNotEmpty()) {
            // drawables_z_order omits placeholders (validated on a real deck: the title
            // placeholder was missing from it) — draw them below the ordered content.
            return placeholders.filter { it !in zOrder } + zOrder
        }
        val owned = slide.messages(F.SLIDE_OWNED_DRAWABLES).mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }
        return placeholders + owned
    }

    private fun isPlaceholderType(type: Int?): Boolean =
        type == F.TYPE_KN_PLACEHOLDER || type == F.TYPE_KN_PLACEHOLDER_ALT

    private fun slideStyleFill(index: ObjectIndex, slide: IwaMessage): KnFill? {
        val styleId = slide.message(F.SLIDE_STYLE)?.varint(F.REFERENCE_IDENTIFIER) ?: return null
        val style = index.message(styleId) ?: return null
        val fill = style.message(F.SLIDE_STYLE_PROPERTIES)?.message(F.SLIDE_STYLE_PROPS_FILL) ?: return null
        return parseFill(index, fill)
    }

    // ── Drawables ─────────────────────────────────────────────────────────────

    private fun parseDrawable(index: ObjectIndex, id: Long, gate: Gate): KnDrawable? {
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
            F.TYPE_TSD_MOVIE -> {
                gate.raise("movie drawable")
                null
            }
            else -> {
                gate.raise("drawable type $type")
                null
            }
        }
    }

    private fun parseImage(index: ObjectIndex, message: IwaMessage, gate: Gate): KnDrawable? {
        val geometry = geometryOf(message.message(F.IMAGE_SUPER))
        if (message.message(F.IMAGE_MASK)?.varint(F.REFERENCE_IDENTIFIER) != null) {
            gate.raise("masked image")
            return null
        }
        val dataId = message.message(F.IMAGE_DATA)?.varint(F.DATA_REFERENCE_IDENTIFIER) ?: run {
            gate.raise("image without data")
            return null
        }
        val fileName = index.dataFileNames[dataId] ?: run {
            gate.raise("image data $dataId not in package metadata")
            return null
        }
        if (fileName.substringAfterLast('.', "").lowercase() !in RENDERABLE_IMAGE_EXTENSIONS) {
            gate.raise("image format .${fileName.substringAfterLast('.', "")}")
            return null
        }
        return KnDrawable.Image(geometry, fileName)
    }

    /** [message] is a TSD.ShapeArchive (top-level or the embedded super of a ShapeInfo). */
    private fun parseShapeCore(index: ObjectIndex, message: IwaMessage): KnDrawable.Shape {
        val geometry = geometryOf(message.message(F.SHAPE_SUPER))
        val style = resolveShapeStyle(index, message.message(F.SHAPE_STYLE)?.varint(F.REFERENCE_IDENTIFIER))
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

    private fun parseTextShape(index: ObjectIndex, shapeInfo: IwaMessage, gate: Gate): KnDrawable? {
        val shapeArchive = shapeInfo.message(F.SHAPE_INFO_SUPER) ?: run {
            gate.raise("text shape without shape archive")
            return null
        }
        val shape = parseShapeCore(index, shapeArchive)
        val storageId = shapeInfo.message(F.SHAPE_INFO_OWNED_STORAGE)?.varint(F.REFERENCE_IDENTIFIER)
        val storage = storageId?.let { index.message(it) }
        val paragraphs = storage?.let { parseParagraphs(index, it) } ?: emptyList()
        return if (paragraphs.any { it.text.isNotBlank() }) {
            KnDrawable.Text(shape.geometry, shape, paragraphs)
        } else {
            shape
        }
    }

    private fun parseGroup(index: ObjectIndex, message: IwaMessage, gate: Gate): KnDrawable {
        val geometry = geometryOf(message.message(F.GROUP_SUPER))
        val children = message.messages(F.GROUP_CHILDREN)
            .mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }
            .mapNotNull { childId -> parseDrawable(index, childId, gate)?.let { KnPlacedDrawable(childId, it) } }
        return KnDrawable.Group(geometry, children)
    }

    // ── Geometry / style / path ───────────────────────────────────────────────

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

    private class ResolvedShapeStyle(
        val fill: KnFill?,
        val strokeColor: Color?,
        val strokeWidthPt: Double,
        val opacity: Double
    )

    /** Resolves fill/stroke/opacity, walking the TSS parent chain for inherited values. */
    private fun resolveShapeStyle(index: ObjectIndex, styleId: Long?): ResolvedShapeStyle? {
        var fill: KnFill? = null
        var strokeColor: Color? = null
        var strokeWidth: Double? = null
        var opacity: Double? = null
        var currentId = styleId
        var guard = 0
        while (currentId != null && guard++ < 8) {
            val style = index.message(currentId) ?: break
            val props = style.message(F.SHAPE_STYLE_PROPERTIES)
            if (props != null) {
                if (fill == null) props.message(F.SHAPE_PROPS_FILL)?.let { fill = parseFill(index, it) }
                if (strokeColor == null) {
                    props.message(F.SHAPE_PROPS_STROKE)?.let { stroke ->
                        strokeColor = stroke.message(F.STROKE_COLOR)?.let { parseColor(it) }
                        strokeWidth = stroke.float(F.STROKE_WIDTH)?.toDouble()
                    }
                }
                if (opacity == null) opacity = props.float(F.SHAPE_PROPS_OPACITY)?.toDouble()
            }
            currentId = style.message(F.STYLE_SUPER)
                ?.message(F.TSS_STYLE_PARENT)?.varint(F.REFERENCE_IDENTIFIER)
        }
        if (fill == null && strokeColor == null && opacity == null) return null
        return ResolvedShapeStyle(fill, strokeColor, strokeWidth ?: 1.0, opacity ?: 1.0)
    }

    private fun parseFill(index: ObjectIndex, fill: IwaMessage): KnFill? {
        fill.message(F.FILL_COLOR)?.let { return KnFill(color = parseColor(it)) }
        fill.message(F.FILL_GRADIENT)?.let { gradient ->
            // Approximated as the first stop's solid color (documented degrade).
            val stop = gradient.messages(F.GRADIENT_STOPS).firstOrNull()
            val color = stop?.message(F.GRADIENT_STOP_COLOR)?.let { parseColor(it) }
            if (color != null) return KnFill(color = color)
        }
        fill.message(F.FILL_IMAGE)?.let { image ->
            val dataId = image.message(F.IMAGE_FILL_DATA)?.varint(F.DATA_REFERENCE_IDENTIFIER)
            val fileName = dataId?.let { index.dataFileNames[it] }
            if (fileName != null &&
                fileName.substringAfterLast('.', "").lowercase() in RENDERABLE_IMAGE_EXTENSIONS
            ) {
                return KnFill(imageFile = fileName)
            }
        }
        return null
    }

    private fun parseColor(color: IwaMessage): Color {
        val r = color.float(F.COLOR_R) ?: 0f
        val g = color.float(F.COLOR_G) ?: 0f
        val b = color.float(F.COLOR_B) ?: 0f
        val a = color.float(F.COLOR_A) ?: 1f
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), a.coerceIn(0f, 1f))
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
                1 -> points.getOrNull(0)?.let { result.moveTo(it.first, it.second); hasContent = true }
                2 -> points.getOrNull(0)?.let { result.lineTo(it.first, it.second); hasContent = true }
                3 -> if (points.size >= 2) {
                    result.quadTo(points[0].first, points[0].second, points[1].first, points[1].second)
                    hasContent = true
                }
                4 -> if (points.size >= 3) {
                    result.curveTo(
                        points[0].first, points[0].second,
                        points[1].first, points[1].second,
                        points[2].first, points[2].second
                    )
                    hasContent = true
                }
                5 -> result.closePath()
                else -> return null
            }
        }
        return result.takeIf { hasContent }
    }

    // ── Text ──────────────────────────────────────────────────────────────────

    private fun parseParagraphs(index: ObjectIndex, storage: IwaMessage): List<KnParagraph> {
        val raw = storage.strings(F.STORAGE_TEXT).joinToString("")
        if (raw.isEmpty()) return emptyList()
        val text = raw.replace('\u2029', '\n')

        val charStyleTable = attributeRuns(storage.message(F.STORAGE_TABLE_CHAR_STYLE))
        val paraStyleTable = attributeRuns(storage.message(F.STORAGE_TABLE_PARA_STYLE))

        val paragraphs = mutableListOf<KnParagraph>()
        var start = 0
        for (line in text.split('\n')) {
            val cleanText = line.filterNot { it == '\uFFFC' || it == '\uFFFB' }
            val charStyleId = styleAt(charStyleTable, start)
            val paraStyleId = styleAt(paraStyleTable, start)
            val paraStyle = paraStyleId?.let { index.message(it) }
            val charProps = resolveCharProps(index, charStyleId)
                ?: paraStyle?.let { resolveCharPropsFromParagraphStyle(index, paraStyleId) }
            val alignment = paraStyle?.message(F.PARAGRAPH_STYLE_PARA_PROPERTIES)
                ?.varint(F.PARA_PROPS_ALIGNMENT)?.toInt()
                ?: 0
            paragraphs.add(
                KnParagraph(
                    text = cleanText,
                    fontFamily = charProps?.fontName,
                    fontSizePt = charProps?.fontSize ?: 20.0,
                    bold = charProps?.bold ?: false,
                    italic = charProps?.italic ?: false,
                    color = charProps?.color ?: Color.BLACK,
                    alignment = alignment
                )
            )
            start += line.length + 1
        }
        return paragraphs
    }

    private fun attributeRuns(table: IwaMessage?): List<Pair<Int, Long>> =
        table?.messages(F.ATTR_TABLE_ENTRIES)?.mapNotNull { entry ->
            val charIndex = entry.varint(F.ATTR_ENTRY_CHAR_INDEX)?.toInt() ?: return@mapNotNull null
            val obj = entry.message(F.ATTR_ENTRY_OBJECT)?.varint(F.REFERENCE_IDENTIFIER) ?: return@mapNotNull null
            charIndex to obj
        } ?: emptyList()

    private fun styleAt(runs: List<Pair<Int, Long>>, charIndex: Int): Long? =
        runs.lastOrNull { it.first <= charIndex }?.second

    private class CharProps(
        val fontName: String?,
        val fontSize: Double?,
        val bold: Boolean?,
        val italic: Boolean?,
        val color: Color?
    ) {
        val isComplete get() = fontName != null && fontSize != null && bold != null && italic != null && color != null
    }

    private fun resolveCharProps(index: ObjectIndex, styleId: Long?): CharProps? {
        var fontName: String? = null
        var fontSize: Double? = null
        var bold: Boolean? = null
        var italic: Boolean? = null
        var color: Color? = null
        var currentId = styleId
        var guard = 0
        var sawAny = false
        while (currentId != null && guard++ < 8) {
            val style = index.message(currentId) ?: break
            val props = style.message(F.CHARACTER_STYLE_PROPERTIES)
            if (props != null) {
                sawAny = true
                if (fontName == null) fontName = props.string(F.CHAR_PROPS_FONT_NAME)
                if (fontSize == null) fontSize = props.float(F.CHAR_PROPS_FONT_SIZE)?.toDouble()
                if (bold == null) bold = props.bool(F.CHAR_PROPS_BOLD)
                if (italic == null) italic = props.bool(F.CHAR_PROPS_ITALIC)
                if (color == null) color = props.message(F.CHAR_PROPS_FONT_COLOR)?.let { parseColor(it) }
            }
            if (fontName != null && fontSize != null && color != null) break
            currentId = style.message(F.STYLE_SUPER)
                ?.message(F.TSS_STYLE_PARENT)?.varint(F.REFERENCE_IDENTIFIER)
        }
        return if (sawAny) CharProps(fontName, fontSize, bold, italic, color) else null
    }

    /** Paragraph styles carry char defaults too (same properties message at field 11). */
    private fun resolveCharPropsFromParagraphStyle(index: ObjectIndex, styleId: Long?): CharProps? =
        resolveCharProps(index, styleId)
}
