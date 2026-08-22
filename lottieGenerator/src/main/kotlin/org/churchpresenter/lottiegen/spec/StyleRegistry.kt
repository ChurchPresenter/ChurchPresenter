package org.churchpresenter.lottiegen.spec

import java.io.IOException
import kotlinx.serialization.Serializable

/**
 * The bundled spec-style registry (`/styles/registry.json`): maps style ids to spec
 * resources so a style registered by the editor ships in the next build with no code
 * edits. An entry whose id matches a compiled style (1-12) replaces that style's
 * renderer; a new id adds a style to the picker.
 */
@Serializable
data class StyleRegistry(val entries: List<RegistryEntry> = emptyList()) {
    companion object {
        const val REGISTRY_RESOURCE = "/styles/registry.json"

        /** Loads the bundled registry; corrupt/missing degrades to empty, never throws. */
        fun load(): StyleRegistry {
            val stream = StyleRegistry::class.java.getResourceAsStream(REGISTRY_RESOURCE)
                ?: return StyleRegistry()
            return try {
                decode(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } catch (e: IOException) {
                System.err.println("Failed to load style registry: ${e.message}")
                StyleRegistry()
            } catch (e: IllegalArgumentException) {
                System.err.println("Failed to load style registry: ${e.message}")
                StyleRegistry()
            }
        }

        fun encode(registry: StyleRegistry): String =
            SpecJson.json.encodeToString(serializer(), registry)

        fun decode(text: String): StyleRegistry =
            SpecJson.json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class RegistryEntry(
    /** Numeric style id ("13"). */
    val id: String,
    /** Display name; the picker label falls back to "Style <id> — <name>". */
    val name: String,
    /** Classpath resource of the spec ("/styles/style13_ribbon.json"). */
    val resource: String
)
