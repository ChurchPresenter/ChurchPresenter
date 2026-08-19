package lottiegen.editor

import lottiegen.persistence.StyleSpecStorage
import lottiegen.spec.RegistryEntry
import lottiegen.spec.SpecJson
import lottiegen.spec.StyleRegistry
import lottiegen.spec.StyleSpec
import java.io.File

/** Result of a Register-into-build write: the two files the dev must commit. */
data class RegisterResult(val specFile: File, val registryFile: File)

/**
 * Writes an exported spec + registry entry straight into the dev's source checkout, so
 * the style ships in the next build with zero code edits (see registry handling in
 * lottieGenerator/StyleCatalog). Only possible when the editor runs from source — in a
 * packaged build [locateStylesDir] returns null and the manual export flow remains.
 */
object BuildRegistrar {

    private const val MODULE_STYLES = "src/main/resources/styles"
    private const val REPO_ROOT_STYLES = "lottieGenerator/$MODULE_STYLES"

    /**
     * The source checkout's styles resource dir, or null when not running from source.
     * Probes the working directory and a few ancestors against the known layouts —
     * standalone module run (cwd = lottieGenerator/) and a repo-root cwd. An embedded dev
     * run sets cwd = composeApp/, which the ancestor walk resolves to the repo root.
     * Requires registry.json to already exist there so an unrelated directory can never be
     * mistaken for the checkout.
     */
    fun locateStylesDir(): File? {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(4) {
            val base = dir ?: return null
            for (relative in listOf(MODULE_STYLES, REPO_ROOT_STYLES)) {
                val candidate = File(base, relative)
                if (candidate.isDirectory && File(candidate, "registry.json").isFile) return candidate
            }
            dir = base.parentFile
        }
        return null
    }

    /**
     * Writes the spec as `style<id>_<slug>.json` (replacing this id's previous spec file
     * name if the registry already maps it) and upserts the registry entry.
     */
    fun register(spec: StyleSpec, stylesDir: File): RegisterResult {
        val registryFile = File(stylesDir, "registry.json")
        val registry = StyleRegistry.decode(registryFile.readText(Charsets.UTF_8))

        val fileName = "style${spec.id}_${StyleSpecStorage.slugify(spec.name)}.json"
        val specFile = File(stylesDir, fileName)
        specFile.writeText(SpecJson.encode(spec), Charsets.UTF_8)

        // If this id previously pointed at a differently-named spec file, remove the
        // stale file so the resources don't accumulate orphans.
        registry.entries.firstOrNull { it.id == spec.id }?.let { previous ->
            val previousFile = File(stylesDir, previous.resource.removePrefix("/styles/"))
            if (previousFile != specFile && previousFile.isFile) previousFile.delete()
        }

        val entry = RegistryEntry(id = spec.id, name = spec.name, resource = "/styles/$fileName")
        val updated = StyleRegistry(registry.entries.filterNot { it.id == spec.id } + entry)
        registryFile.writeText(StyleRegistry.encode(updated), Charsets.UTF_8)

        return RegisterResult(specFile, registryFile)
    }
}
