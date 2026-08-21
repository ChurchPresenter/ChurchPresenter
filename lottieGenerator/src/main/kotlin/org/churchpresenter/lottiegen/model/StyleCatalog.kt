package org.churchpresenter.lottiegen.model

import org.churchpresenter.lottiegen.spec.RegistryEntry
import org.churchpresenter.lottiegen.spec.StyleRegistry
import org.churchpresenter.lottiegen.ui.Strings
import java.util.MissingResourceException

/** One selectable style: a compiled classic or a registry-registered spec style. */
data class StyleInfo(
    val id: String,
    val label: String,
    /** Spec resource when the style comes from the registry; null for compiled styles. */
    val specResource: String?
)

/**
 * The style picker's source of truth: the 12 compiled styles (localized labels) plus
 * every registry entry with a NEW id. A registry entry that collides with a compiled id
 * does not add a picker entry — it only swaps that style's renderer (see LottieGenerator).
 */
object StyleCatalog {

    val entries: List<StyleInfo> = build(AnimationStyle.entries, StyleRegistry.load().entries)

    fun labelFor(id: String): String = entries.firstOrNull { it.id == id }?.label ?: id

    internal fun build(compiled: List<AnimationStyle>, registry: List<RegistryEntry>): List<StyleInfo> {
        val compiledInfos = compiled.map { StyleInfo(it.id, it.label, null) }
        val compiledIds = compiled.map { it.id }.toSet()
        val registryInfos = registry
            .filter { it.id !in compiledIds }
            .distinctBy { it.id }
            .map { StyleInfo(it.id, registryLabel(it), it.resource) }
        return (compiledInfos + registryInfos).sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
    }

    /**
     * A registry style's label: a translated `style_<id>` bundle key when someone has
     * added one, otherwise the "Style N — Name" format built from the spec's own name.
     */
    private fun registryLabel(entry: RegistryEntry): String = try {
        Strings.styleLabel(entry.id)
    } catch (_: MissingResourceException) {
        Strings.editorStyleLabelFormat(entry.id, entry.name)
    }
}
