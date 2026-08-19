package lottiegen.persistence

import lottiegen.spec.SpecJson
import lottiegen.spec.StyleSpec
import java.io.File

/**
 * Disk storage for Style Editor projects: one spec JSON per file under
 * `~/.churchpresenter/churchpresenter-lottiegen/style-specs/`.
 */
object StyleSpecStorage {

    private fun specsDir(): File {
        val dir = File(System.getProperty("user.home"), ".churchpresenter/churchpresenter-lottiegen/style-specs")
        dir.mkdirs()
        return dir
    }

    fun list(): List<File> = specsDir()
        .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

    fun load(file: File): StyleSpec? = try {
        SpecJson.decode(file.readText(Charsets.UTF_8))
    } catch (e: Exception) {
        System.err.println("Failed to load style spec ${file.name}: ${e.message}")
        null
    }

    fun save(spec: StyleSpec, file: File): Boolean = try {
        file.writeText(SpecJson.encode(spec), Charsets.UTF_8)
        true
    } catch (e: Exception) {
        System.err.println("Failed to save style spec ${file.name}: ${e.message}")
        false
    }

    fun delete(file: File): Boolean = file.delete()

    /** A writable file in the project dir for [name], uniquified if the slug is taken. */
    fun fileForName(name: String): File {
        val slug = slugify(name)
        var file = File(specsDir(), "$slug.json")
        var i = 1
        while (file.exists()) {
            file = File(specsDir(), "$slug-$i.json")
            i++
        }
        return file
    }

    fun slugify(name: String): String {
        val cleaned = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return cleaned.ifEmpty { "untitled" }
    }
}
