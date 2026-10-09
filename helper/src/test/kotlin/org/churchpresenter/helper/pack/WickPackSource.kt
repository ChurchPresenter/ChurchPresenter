package org.churchpresenter.helper.pack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** `wick-pack/source.json`, the hand-written side of a Wick pack, as [WickPackBuild] reads it. */
@Serializable
internal data class WickPackSource(
    val version: Int,
    val minApp: String,
    val phrases: List<SourcePhrase> = emptyList(),
    val tours: List<PackTourFile> = emptyList(),
    val tips: List<PackTipFile> = emptyList(),
) {
    /** Every target and text the pack embeds, in the order `pack.json` lists them. */
    fun wanted(): List<Pair<String, String>> =
        phrases.flatMap { phrase -> phrase.texts.map { phrase.target to it } } +
            tours.flatMap { tour -> tour.triggers.map { "tour:${tour.id}" to it } }

    companion object {
        private val root = File(System.getProperty("user.dir")).parentFile
        val sourceFile = File(root, "wick-pack/source.json")
        val packFile = File(root, "wick-pack/pack.json")

        fun read(): WickPackSource = Json.decodeFromString(serializer(), sourceFile.readText())
    }
}

/** Some ways of asking for one [target], a `catalog.tsv` target name. */
@Serializable
internal data class SourcePhrase(val target: String, val texts: List<String>)
