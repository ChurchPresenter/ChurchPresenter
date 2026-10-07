package org.churchpresenter.presentationengine.keynote

import org.churchpresenter.presentationengine.keynote.IwaChunkReader.IwaObject
import java.io.File
import java.util.zip.ZipFile

/**
 * The document's full object graph: every archive from every iwa component under Index/, keyed by
 * identifier (identifiers are global across components), plus the data-id → `Data/` file-name
 * map from Metadata.iwa's TSP.PackageMetadata.
 */
internal class ObjectIndex private constructor(
    private val objects: Map<Long, IwaObject>,
    /** TSP.DataInfo identifier → file name under Data/. */
    val dataFileNames: Map<Long, String>
) {

    fun typeOf(identifier: Long): Int? = objects[identifier]?.type

    fun message(identifier: Long): IwaMessage? =
        objects[identifier]?.let { IwaMessage.parse(it.payload) }

    /** First object of [type] in the whole document (e.g. the DocumentArchive root). */
    fun firstOfType(type: Int): Pair<Long, IwaMessage>? {
        val obj = objects.values.firstOrNull { it.type == type } ?: return null
        return IwaMessage.parse(obj.payload)?.let { obj.identifier to it }
    }

    fun typeHistogram(): Map<Int, Int> = objects.values.groupingBy { it.type }.eachCount()

    companion object {

        /** Loads a `.key` file (zip or package directory). Returns null when nothing parses. */
        fun load(file: File): ObjectIndex? {
            val objects = mutableMapOf<Long, IwaObject>()
            try {
                val archives = if (file.isDirectory) directoryArchives(file) else zipArchives(file)
                archives.forEach { bytes -> IwaChunkReader.readObjects(bytes).forEach { objects[it.identifier] = it } }
            } catch (_: Exception) {
                return null
            }
            if (objects.isEmpty()) return null
            return ObjectIndex(objects, dataFileNames(objects.values))
        }

        /** A package directory's archives: Index/ in name order, then Metadata/. */
        private fun directoryArchives(dir: File): List<ByteArray> {
            fun iwaFiles(sub: String) = File(dir, sub).listFiles()
                ?.filter { it.isFile && it.extension == "iwa" }
                .orEmpty()
            return (iwaFiles("Index").sortedBy { it.name } + iwaFiles("Metadata")).map { it.readBytes() }
        }

        /** Every `.iwa` entry of a zipped deck, in the zip's own order. */
        private fun zipArchives(file: File): List<ByteArray> = ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".iwa") }
                .map { zip.getInputStream(it).readBytes() }
                .toList()
        }

        /** Data-id → file-name pairs from every TSP.PackageMetadata, skipping entries without both. */
        private fun dataFileNames(objects: Collection<IwaObject>): Map<Long, String> =
            objects.asSequence()
                .filter { it.type == KnFields.TYPE_TSP_PACKAGE_METADATA }
                .mapNotNull { IwaMessage.parse(it.payload) }
                .flatMap { it.messages(KnFields.PACKAGE_METADATA_DATAS) }
                .mapNotNull { dataInfo ->
                    val id = dataInfo.varint(KnFields.DATA_INFO_IDENTIFIER)
                    val name = dataInfo.string(KnFields.DATA_INFO_FILE_NAME)
                        ?: dataInfo.string(KnFields.DATA_INFO_PREFERRED_FILE_NAME)
                    if (id != null && name != null) id to name else null
                }
                .toMap()
    }
}
