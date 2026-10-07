package org.churchpresenter.presentationengine.keynote

import org.churchpresenter.presentationengine.keynote.KnFields as F
import java.io.File

/**
 * Traverses the IWA object graph (Document → Show → slide nodes → slides → drawables/builds/
 * transitions/notes) into a renderable [KeynoteScene].
 *
 * Whitelist + per-slide fidelity gate: a slide containing anything the renderer can't reproduce
 * (charts, tables, masked images, unresolvable movie assets, unknown drawable types) gets a
 * [KnSlide.gateReason] and renders from the static fallback instead — never a crash, never a
 * missing slide. Movies with a resolvable asset are NOT gated — they render as a poster-frame
 * layer that the app layer can decode and play back live (see [KnDrawable.Movie]).
 *
 * The drawables themselves are [KeynoteDrawableParser], their styles [KeynoteStyleResolver] and
 * their text [KeynoteTextParser].
 */
internal object KeynoteDeckParser {

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

    private fun parseSlide(index: ObjectIndex, slideId: Long, slideIndex: Int): KnSlide {
        val gate = KnGate()
        val slide = index.message(slideId)
        if (slide == null) {
            return KnSlide(slideIndex, null, emptyList(), "", gateReason = "slide archive unreadable")
        }

        // Master chain, deepest first (theme decorations render below slide content).
        val masters = masterChain(index, slide)

        // Background: the slide's own style fill, else the nearest master's.
        val background = (listOf(slide) + masters.reversed())
            .firstNotNullOfOrNull { archive -> slideStyleFill(index, archive) }

        val drawables = mutableListOf<KnPlacedDrawable>()
        for (master in masters) {
            for (id in drawableIds(master)) {
                if (isPlaceholderType(index.typeOf(id))) continue // master placeholders are prompts
                KeynoteDrawableParser.parseDrawable(index, id, gate)?.let { drawables.add(KnPlacedDrawable(id, it)) }
            }
        }
        for (id in drawableIds(slide)) {
            KeynoteDrawableParser.parseDrawable(index, id, gate)?.let { drawables.add(KnPlacedDrawable(id, it)) }
        }

        val notes = slide.message(F.SLIDE_NOTE)?.varint(F.REFERENCE_IDENTIFIER)
            ?.let { index.message(it) }
            ?.message(F.NOTE_CONTAINED_STORAGE)?.varint(F.REFERENCE_IDENTIFIER)
            ?.let { index.message(it) }
            ?.strings(F.STORAGE_TEXT)?.joinToString("")
            ?.let(KeynoteTextParser::normalizeParagraphBreaks)?.trim()
            ?: ""

        val builds = KeynoteBuildMapper.map(index, slide, drawables)
        val transition = KeynoteBuildMapper.mapTransition(slide)

        return KnSlide(
            index = slideIndex,
            background = background,
            drawables = drawables,
            notes = notes,
            timeline = builds?.timeline,
            builtDrawableIds = builds?.builtDrawableIds ?: emptySet(),
            paragraphBuiltDrawableIds = builds?.paragraphBuiltDrawableIds ?: emptySet(),
            transition = transition,
            gateReason = gate.reason
        )
    }

    /** [slide]'s template masters, deepest first; the walk stops at an unreadable one. */
    private fun masterChain(index: ObjectIndex, slide: IwaMessage): List<IwaMessage> {
        fun templateOf(archive: IwaMessage): IwaMessage? =
            archive.message(F.SLIDE_TEMPLATE_SLIDE)?.varint(F.REFERENCE_IDENTIFIER)?.let { index.message(it) }
        return generateSequence(templateOf(slide)) { templateOf(it) }
            .take(KeynoteStyleResolver.MAX_STYLE_CHAIN)
            .toList()
            .reversed()
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
        return KeynoteStyleResolver.parseFill(index, fill)
    }
}
