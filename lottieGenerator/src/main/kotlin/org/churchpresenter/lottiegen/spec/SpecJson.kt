package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Shared serialization entry point for [StyleSpec] documents — editor projects,
 * bundled shipped styles, and exports all go through here so the on-disk format
 * is identical everywhere.
 */
object SpecJson {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
    }

    fun encode(spec: StyleSpec): String = json.encodeToString(StyleSpec.serializer(), spec)

    /**
     * Decodes and validates a spec document. Throws [IllegalArgumentException] with a
     * descriptive message on malformed JSON or an unsupported [StyleSpec.formatVersion]
     * — callers decide whether that is fatal (bundled style) or a status message (editor).
     */
    fun decode(text: String): StyleSpec {
        val spec = try {
            json.decodeFromString(StyleSpec.serializer(), text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Invalid style spec: ${e.message}", e)
        }
        require(spec.formatVersion <= StyleSpec.CURRENT_FORMAT_VERSION) {
            "Style spec format version ${spec.formatVersion} is newer than supported " +
                "version ${StyleSpec.CURRENT_FORMAT_VERSION}"
        }
        return spec
    }
}
