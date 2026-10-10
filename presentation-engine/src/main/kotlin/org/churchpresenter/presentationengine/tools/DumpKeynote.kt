package org.churchpresenter.presentationengine.tools

import org.churchpresenter.presentationengine.keynote.IwaMessage
import org.churchpresenter.presentationengine.keynote.KnFields
import org.churchpresenter.presentationengine.keynote.ObjectIndex
import org.churchpresenter.presentationengine.keynote.bool
import org.churchpresenter.presentationengine.keynote.message
import java.io.File
import java.io.PrintStream

/**
 * Keynote structure probe: dumps the IWA object-type histogram, the Document→Show→Slide graph,
 * per-slide drawable types, builds and transitions — the validation tool for the reverse-
 * engineered parser. Usage: `./gradlew dumpKeynote -Pfile=/path/deck.key`
 */

/** How many rows of the object-type histogram the dump prints. */
private const val HISTOGRAM_ROWS = 25

/** Slide placeholder reference fields, by the label the dump prints for each. */
private val PLACEHOLDER_FIELDS = listOf("title" to 5, "body" to 6, "slideNum" to 20, "object" to 30)

/** The object a reference message points at. */
private fun ref(message: IwaMessage?): Long? = message?.varint(KnFields.REFERENCE_IDENTIFIER)

/** The objects a repeated reference field points at. */
private fun refs(message: IwaMessage, field: Int): List<Long> =
    message.messages(field).mapNotNull { it.varint(KnFields.REFERENCE_IDENTIFIER) }

object DumpKeynote {

    @JvmStatic
    fun main(args: Array<String>) = dump(args, System.out, System.err)

    internal fun dump(args: Array<String>, out: PrintStream, err: PrintStream) {
        val path = args.firstOrNull() ?: run {
            err.println("usage: DumpKeynote <file.key>")
            return
        }
        val index = ObjectIndex.load(File(path)) ?: run {
            out.println("FAILED: no IWA objects parsed")
            return
        }
        out.println("=== ${File(path).name} ===")
        out.println("Objects by type (top $HISTOGRAM_ROWS):")
        index.typeHistogram().entries.sortedByDescending { it.value }.take(HISTOGRAM_ROWS)
            .forEach { (type, count) ->
            out.println("  type $type: $count")
        }
        out.println("Data files: ${index.dataFileNames.size}")

        val document = index.firstOfType(KnFields.TYPE_KN_DOCUMENT)
        if (document == null) {
            out.println("No KN.DocumentArchive (type 1) found")
            return
        }
        KeynoteDump(index, out).dumpShow(document)
    }
}

/** The dump below the document: the show, then every slide node with what its slide holds. */
private class KeynoteDump(private val index: ObjectIndex, private val out: PrintStream) {

    fun dumpShow(document: Pair<Long, IwaMessage>) {
        val showRef = ref(document.second.message(KnFields.DOCUMENT_SHOW))
        out.println("Document id=${document.first} → show=$showRef (type ${showRef?.let { index.typeOf(it) }})")
        val show = showRef?.let { index.message(it) } ?: run { out.println("Show unreadable"); return }
        val size = show.message(KnFields.SHOW_SIZE)
        out.println("Show size: ${size?.float(KnFields.SIZE_WIDTH)} x ${size?.float(KnFields.SIZE_HEIGHT)}")
        out.println("Show fields: ${show.fieldNumbers().sorted()}")
        val slideTree = show.message(KnFields.SHOW_SLIDE_TREE)
        out.println("SlideTree fields: ${slideTree?.fieldNumbers()?.sorted()}")
        val nodeRefs = slideTree?.let { refs(it, KnFields.SLIDE_TREE_SLIDES) } ?: emptyList()
        out.println("Slide tree: ${nodeRefs.size} top-level nodes")
        nodeRefs.forEach { dumpNode(it, 1) }
    }

    private fun dumpNode(nodeId: Long, depth: Int) {
        val node = index.message(nodeId) ?: return
        val slideRef = ref(node.message(KnFields.SLIDE_NODE_SLIDE))
        val skipped = node.bool(KnFields.SLIDE_NODE_IS_SKIPPED) == true
        out.println(
            "${"  ".repeat(depth)}node $nodeId fields=${node.fieldNumbers().sorted()} " +
                "slide=$slideRef (type ${slideRef?.let { index.typeOf(it) }}) skipped=$skipped"
        )
        slideRef?.let { index.message(it) }?.let { dumpSlide(it, "  ".repeat(depth)) }
        refs(node, KnFields.SLIDE_NODE_CHILDREN).forEach { dumpNode(it, depth + 1) }
    }

    private fun dumpSlide(slide: IwaMessage, indent: String) {
        out.println("$indent  slide fields=${slide.fieldNumbers().sorted()}")
        dumpPlaceholders(slide, indent)
        val z = refs(slide, KnFields.SLIDE_DRAWABLES_Z_ORDER)
        val owned = refs(slide, KnFields.SLIDE_OWNED_DRAWABLES)
        val drawables = z.ifEmpty { owned }
        out.println("$indent  drawables: " + drawables.joinToString { "$it:${index.typeOf(it)}" })
        out.println("$indent  owned(f7): " + owned.joinToString { "$it:${index.typeOf(it)}" })
        drawables.forEach { dumpDrawable(it, indent) }
        dumpBuilds(slide, indent)
        dumpTransition(slide, indent)
    }

    private fun dumpPlaceholders(slide: IwaMessage, indent: String) {
        for ((label, field) in PLACEHOLDER_FIELDS) {
            val ref = ref(slide.message(field)) ?: continue
            val ph = index.message(ref)
            val shapeInfo = ph?.message(KnFields.PLACEHOLDER_SUPER)
            val storageRef = ref(shapeInfo?.message(KnFields.SHAPE_INFO_OWNED_STORAGE))
            val text = storageRef?.let { index.message(it) }?.strings(KnFields.STORAGE_TEXT)
            out.println("$indent  placeholder $label=$ref type=${index.typeOf(ref)} " +
                "phFields=${ph?.fieldNumbers()?.sorted()} storage=$storageRef text=$text")
        }
    }

    /** One drawable, a group with its children one level deep, with geometry and text previews. */
    private fun dumpDrawable(id: Long, indent: String) {
        if (index.typeOf(id) == KnFields.TYPE_TSD_GROUP) {
            val children = index.message(id)?.let { refs(it, KnFields.GROUP_CHILDREN) } ?: emptyList()
            out.println("$indent  group $id ${geometryLine(id)}")
            for (child in children) {
                out.println("$indent    child $child:${index.typeOf(child)} " + geometryLine(child))
            }
            return
        }
        out.println("$indent  drawable $id:${index.typeOf(id)} ${geometryLine(id)}")
        if (index.typeOf(id) == KnFields.TYPE_TSD_MOVIE) dumpMovie(index.message(id), indent)
    }

    private fun dumpMovie(movie: IwaMessage?, indent: String) {
        val fields = movie?.fieldNumbers()?.sorted().orEmpty()
        out.println("$indent    movie fields=$fields")
        for (f in fields) {
            val sub = movie?.message(f)
            if (sub != null) {
                out.println("$indent    movie.$f fields=${sub.fieldNumbers().sorted()} " +
                    "ref=${sub.varint(KnFields.REFERENCE_IDENTIFIER)} " +
                    "dataRef=${sub.varint(KnFields.DATA_REFERENCE_IDENTIFIER)} " +
                    "str=${sub.string(1)}")
            } else {
                out.println("$indent    movie.$f scalar varint=${movie?.varint(f)} " +
                    "bool=${movie?.bool(f)} float=${movie?.float(f)} double=${movie?.double(f)}")
            }
        }
    }

    private fun dumpBuilds(slide: IwaMessage, indent: String) {
        val builds = refs(slide, KnFields.SLIDE_BUILDS)
        if (builds.isNotEmpty()) {
            out.println("$indent  builds:")
            builds.mapNotNull { id -> index.message(id)?.let { id to it } }.forEach { (buildId, build) ->
                val anim = build.message(KnFields.BUILD_ATTRIBUTES)?.message(KnFields.BUILD_ATTRS_ANIMATION)
                out.println("$indent    build $buildId drawable=" +
                    "${ref(build.message(KnFields.BUILD_DRAWABLE))} " +
                    "delivery=${build.string(KnFields.BUILD_DELIVERY)} " +
                    "type=${anim?.string(KnFields.ANIM_ATTRS_TYPE)} " +
                    "effect=${anim?.string(KnFields.ANIM_ATTRS_EFFECT)} " +
                    "dur=${anim?.double(KnFields.ANIM_ATTRS_DURATION)} " +
                    "dir=${anim?.varint(KnFields.ANIM_ATTRS_DIRECTION)}")
            }
        }
        val chunks = refs(slide, KnFields.SLIDE_BUILD_CHUNKS)
        if (chunks.isNotEmpty()) {
            out.println("$indent  buildChunks:")
            chunks.mapNotNull { id -> index.message(id)?.let { id to it } }.forEach { (chunkId, chunk) ->
                out.println("$indent    chunk $chunkId build=" +
                    "${ref(chunk.message(KnFields.BUILD_CHUNK_BUILD))} " +
                    "auto=${chunk.bool(KnFields.BUILD_CHUNK_AUTOMATIC)} " +
                    "delay=${chunk.double(KnFields.BUILD_CHUNK_DELAY)} " +
                    "dur=${chunk.double(KnFields.BUILD_CHUNK_DURATION)}")
            }
        }
    }

    private fun dumpTransition(slide: IwaMessage, indent: String) {
        val transitionAnim = slide.message(KnFields.SLIDE_TRANSITION)
            ?.message(KnFields.TRANSITION_ATTRIBUTES)
            ?.message(KnFields.TRANSITION_ATTRS_ANIMATION)
            ?: return
        out.println(
            "$indent  transition: " +
                "type=${transitionAnim.string(KnFields.ANIM_ATTRS_TYPE)} " +
                "effect=${transitionAnim.string(KnFields.ANIM_ATTRS_EFFECT)} " +
                "dur=${transitionAnim.double(KnFields.ANIM_ATTRS_DURATION)} " +
                "dir=${transitionAnim.varint(KnFields.ANIM_ATTRS_DIRECTION)}"
        )
    }

    private fun geometryLine(id: Long): String {
        val message = index.message(id) ?: return "?"
        val superField = when (index.typeOf(id)) {
            KnFields.TYPE_TSD_GROUP -> KnFields.GROUP_SUPER
            KnFields.TYPE_TSD_IMAGE -> KnFields.IMAGE_SUPER
            else -> KnFields.SHAPE_INFO_SUPER // 2011: ShapeArchive at f1
        }
        val drawableMsg = if (index.typeOf(id) == KnFields.TYPE_TSWP_SHAPE_INFO) {
            message.message(KnFields.SHAPE_INFO_SUPER)?.message(KnFields.SHAPE_SUPER)
        } else {
            message.message(superField)
        }
        val geo = drawableMsg?.message(KnFields.DRAWABLE_GEOMETRY)
        val pos = geo?.message(KnFields.GEOMETRY_POSITION)
        val sz = geo?.message(KnFields.GEOMETRY_SIZE)
        val chunks = ref(message.message(KnFields.SHAPE_INFO_OWNED_STORAGE))
            ?.let { index.message(it) }?.strings(KnFields.STORAGE_TEXT)
        val text = chunks?.joinToString("")?.replace("\n", "\\n")?.replace(' ', '¶')
        val codePoints = chunks?.joinToString("")
            ?.filter { it.code !in 32..126 }
            ?.map { "U+%04X".format(it.code) }
            ?.distinct()
        return "pos=(${pos?.float(KnFields.POINT_X)},${pos?.float(KnFields.POINT_Y)}) " +
            "size=(${sz?.float(KnFields.SIZE_WIDTH)},${sz?.float(KnFields.SIZE_HEIGHT)}) " +
            "chunks=${chunks?.size ?: 0} nonAscii=$codePoints text=${text ?: ""}"
    }
}
