package presentation.engine.tools

import presentation.engine.keynote.KnFields
import presentation.engine.keynote.ObjectIndex
import java.io.File

/**
 * Keynote structure probe: dumps the IWA object-type histogram, the Document→Show→Slide graph,
 * per-slide drawable types, builds and transitions — the validation tool for the reverse-
 * engineered parser. Usage: `./gradlew dumpKeynote -Pfile=/path/deck.key`
 */
object DumpKeynote {

    @JvmStatic
    fun main(args: Array<String>) {
        val path = args.firstOrNull() ?: run {
            System.err.println("usage: DumpKeynote <file.key>")
            return
        }
        val index = ObjectIndex.load(File(path)) ?: run {
            println("FAILED: no IWA objects parsed")
            return
        }
        println("=== ${File(path).name} ===")
        println("Objects by type (top 25):")
        index.typeHistogram().entries.sortedByDescending { it.value }.take(25).forEach { (type, count) ->
            println("  type $type: $count")
        }
        println("Data files: ${index.dataFileNames.size}")

        val document = index.firstOfType(KnFields.TYPE_KN_DOCUMENT)
        if (document == null) {
            println("No KN.DocumentArchive (type 1) found")
            return
        }
        val showRef = document.second.message(KnFields.DOCUMENT_SHOW)?.varint(KnFields.REFERENCE_IDENTIFIER)
        println("Document id=${document.first} → show=$showRef (type ${showRef?.let { index.typeOf(it) }})")
        val show = showRef?.let { index.message(it) } ?: run { println("Show unreadable"); return }
        val size = show.message(KnFields.SHOW_SIZE)
        println("Show size: ${size?.float(KnFields.SIZE_WIDTH)} x ${size?.float(KnFields.SIZE_HEIGHT)}")
        println("Show fields: ${show.fieldNumbers().sorted()}")
        val slideTree = show.message(KnFields.SHOW_SLIDE_TREE)
        println("SlideTree fields: ${slideTree?.fieldNumbers()?.sorted()}")
        val nodeRefs = slideTree?.messages(KnFields.SLIDE_TREE_SLIDES)
            ?.mapNotNull { it.varint(KnFields.REFERENCE_IDENTIFIER) } ?: emptyList()
        println("Slide tree: ${nodeRefs.size} top-level nodes")

        var slideNumber = 0
        fun dumpNode(nodeId: Long, depth: Int) {
            val node = index.message(nodeId) ?: return
            val slideRef = node.message(KnFields.SLIDE_NODE_SLIDE)?.varint(KnFields.REFERENCE_IDENTIFIER)
            val skipped = node.bool(KnFields.SLIDE_NODE_IS_SKIPPED) == true
            slideNumber++
            println("${"  ".repeat(depth)}node $nodeId fields=${node.fieldNumbers().sorted()} slide=$slideRef (type ${slideRef?.let { index.typeOf(it) }}) skipped=$skipped")
            val slide = slideRef?.let { index.message(it) }
            if (slide != null) {
                println("${"  ".repeat(depth)}  slide fields=${slide.fieldNumbers().sorted()}")
                for ((label, field) in listOf("title" to 5, "body" to 6, "slideNum" to 20, "object" to 30)) {
                    val ref = slide.message(field)?.varint(KnFields.REFERENCE_IDENTIFIER) ?: continue
                    val ph = index.message(ref)
                    val shapeInfo = ph?.message(KnFields.PLACEHOLDER_SUPER)
                    val storageRef = shapeInfo?.message(KnFields.SHAPE_INFO_OWNED_STORAGE)
                        ?.varint(KnFields.REFERENCE_IDENTIFIER)
                    val text = storageRef?.let { index.message(it) }?.strings(KnFields.STORAGE_TEXT)
                    println("${"  ".repeat(depth)}  placeholder $label=$ref type=${index.typeOf(ref)} " +
                        "phFields=${ph?.fieldNumbers()?.sorted()} storage=$storageRef text=$text")
                }
                val z = slide.messages(KnFields.SLIDE_DRAWABLES_Z_ORDER)
                    .mapNotNull { it.varint(KnFields.REFERENCE_IDENTIFIER) }
                val owned = slide.messages(KnFields.SLIDE_OWNED_DRAWABLES)
                    .mapNotNull { it.varint(KnFields.REFERENCE_IDENTIFIER) }
                val drawables = z.ifEmpty { owned }
                println("${"  ".repeat(depth)}  drawables: " +
                    drawables.joinToString { "$it:${index.typeOf(it)}" })
                println("${"  ".repeat(depth)}  owned(f7): " + owned.joinToString { "$it:${index.typeOf(it)}" })
                // Group contents, one level deep, with geometry + text previews.
                fun geometryLine(id: Long): String {
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
                    val text = message.message(KnFields.SHAPE_INFO_OWNED_STORAGE)
                        ?.varint(KnFields.REFERENCE_IDENTIFIER)
                        ?.let { index.message(it) }?.strings(KnFields.STORAGE_TEXT)
                        ?.joinToString("")?.take(30)
                    return "pos=(${pos?.float(KnFields.POINT_X)},${pos?.float(KnFields.POINT_Y)}) " +
                        "size=(${sz?.float(KnFields.SIZE_WIDTH)},${sz?.float(KnFields.SIZE_HEIGHT)}) text=${text ?: ""}"
                }
                for (id in drawables) {
                    if (index.typeOf(id) == KnFields.TYPE_TSD_GROUP) {
                        val children = index.message(id)?.messages(KnFields.GROUP_CHILDREN)
                            ?.mapNotNull { it.varint(KnFields.REFERENCE_IDENTIFIER) } ?: emptyList()
                        println("${"  ".repeat(depth)}  group $id ${geometryLine(id)}")
                        for (child in children) {
                            println("${"  ".repeat(depth)}    child $child:${index.typeOf(child)} ${geometryLine(child)}")
                        }
                    } else {
                        println("${"  ".repeat(depth)}  drawable $id:${index.typeOf(id)} ${geometryLine(id)}")
                    }
                }
                val builds = slide.messages(KnFields.SLIDE_BUILDS)
                    .mapNotNull { it.varint(KnFields.REFERENCE_IDENTIFIER) }
                if (builds.isNotEmpty()) {
                    println("${"  ".repeat(depth)}  builds:")
                    for (buildId in builds) {
                        val build = index.message(buildId) ?: continue
                        val anim = build.message(KnFields.BUILD_ATTRIBUTES)?.message(KnFields.BUILD_ATTRS_ANIMATION)
                        println("${"  ".repeat(depth)}    build $buildId drawable=" +
                            "${build.message(KnFields.BUILD_DRAWABLE)?.varint(KnFields.REFERENCE_IDENTIFIER)} " +
                            "delivery=${build.string(KnFields.BUILD_DELIVERY)} " +
                            "type=${anim?.string(KnFields.ANIM_ATTRS_TYPE)} effect=${anim?.string(KnFields.ANIM_ATTRS_EFFECT)} " +
                            "dur=${anim?.double(KnFields.ANIM_ATTRS_DURATION)} dir=${anim?.varint(KnFields.ANIM_ATTRS_DIRECTION)}")
                    }
                }
                val chunks = slide.messages(KnFields.SLIDE_BUILD_CHUNKS)
                    .mapNotNull { it.varint(KnFields.REFERENCE_IDENTIFIER) }
                if (chunks.isNotEmpty()) {
                    println("${"  ".repeat(depth)}  buildChunks:")
                    for (chunkId in chunks) {
                        val chunk = index.message(chunkId) ?: continue
                        println("${"  ".repeat(depth)}    chunk $chunkId build=" +
                            "${chunk.message(KnFields.BUILD_CHUNK_BUILD)?.varint(KnFields.REFERENCE_IDENTIFIER)} " +
                            "auto=${chunk.bool(KnFields.BUILD_CHUNK_AUTOMATIC)} delay=${chunk.double(KnFields.BUILD_CHUNK_DELAY)} " +
                            "dur=${chunk.double(KnFields.BUILD_CHUNK_DURATION)}")
                    }
                }
                val transitionAnim = slide.message(KnFields.SLIDE_TRANSITION)
                    ?.message(KnFields.TRANSITION_ATTRIBUTES)
                    ?.message(KnFields.TRANSITION_ATTRS_ANIMATION)
                if (transitionAnim != null) {
                    println("${"  ".repeat(depth)}  transition: type=${transitionAnim.string(KnFields.ANIM_ATTRS_TYPE)} " +
                        "effect=${transitionAnim.string(KnFields.ANIM_ATTRS_EFFECT)} " +
                        "dur=${transitionAnim.double(KnFields.ANIM_ATTRS_DURATION)} dir=${transitionAnim.varint(KnFields.ANIM_ATTRS_DIRECTION)}")
                }
            }
            node.messages(KnFields.SLIDE_NODE_CHILDREN)
                .mapNotNull { it.varint(KnFields.REFERENCE_IDENTIFIER) }
                .forEach { dumpNode(it, depth + 1) }
        }
        nodeRefs.forEach { dumpNode(it, 1) }
    }
}
