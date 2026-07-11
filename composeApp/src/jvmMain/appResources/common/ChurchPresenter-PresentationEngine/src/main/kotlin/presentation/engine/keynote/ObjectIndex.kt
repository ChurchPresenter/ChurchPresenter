package presentation.engine.keynote

import presentation.engine.keynote.IwaChunkReader.IwaObject
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
                if (file.isDirectory) {
                    File(file, "Index").listFiles()
                        ?.filter { it.isFile && it.extension == "iwa" }
                        ?.sortedBy { it.name }
                        ?.forEach { iwa -> IwaChunkReader.readObjects(iwa.readBytes()).forEach { objects[it.identifier] = it } }
                    File(file, "Metadata").listFiles()
                        ?.filter { it.isFile && it.extension == "iwa" }
                        ?.forEach { iwa -> IwaChunkReader.readObjects(iwa.readBytes()).forEach { objects[it.identifier] = it } }
                } else {
                    ZipFile(file).use { zip ->
                        for (entry in zip.entries()) {
                            if (entry.isDirectory || !entry.name.endsWith(".iwa")) continue
                            val bytes = zip.getInputStream(entry).readBytes()
                            IwaChunkReader.readObjects(bytes).forEach { objects[it.identifier] = it }
                        }
                    }
                }
            } catch (_: Exception) {
                return null
            }
            if (objects.isEmpty()) return null

            val dataFileNames = mutableMapOf<Long, String>()
            objects.values.filter { it.type == KnFields.TYPE_TSP_PACKAGE_METADATA }.forEach { metadata ->
                val message = IwaMessage.parse(metadata.payload) ?: return@forEach
                for (dataInfo in message.messages(KnFields.PACKAGE_METADATA_DATAS)) {
                    val id = dataInfo.varint(KnFields.DATA_INFO_IDENTIFIER) ?: continue
                    val name = dataInfo.string(KnFields.DATA_INFO_FILE_NAME)
                        ?: dataInfo.string(KnFields.DATA_INFO_PREFERRED_FILE_NAME)
                        ?: continue
                    dataFileNames[id] = name
                }
            }
            return ObjectIndex(objects, dataFileNames)
        }
    }
}
