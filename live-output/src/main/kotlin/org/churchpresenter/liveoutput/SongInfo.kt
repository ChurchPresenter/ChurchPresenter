package org.churchpresenter.liveoutput

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.songchords.ChordTransposer

/**
 * The line above the chart: what the song is in, what the player's hands are doing, and how fast.
 *
 * `Key` is the song as written, taken from its first chord. `Capo` and `Play` only appear with a
 * capo set, because `Play` is the whole point of one — a capo at 2 in G means F shapes, and the
 * shapes are what the player actually reads. Tempo appears whenever there is one.
 *
 * Returns null when there is nothing to say, so the caller can leave the row out entirely.
 */
fun songInfoOf(
    section: LyricSection,
    keyLabel: String,
    capoLabel: String,
    playLabel: String,
    bpmLabel: String,
): String? {
    val parts = mutableListOf<String>()
    if (section.chordLines.isNotEmpty()) {
        val key = ChordTransposer.detectKey(section.chordLines.joinToString("\n"))
        parts.add("$keyLabel $key")
        if (section.capo > 0) {
            val shapes = ChordTransposer.pitchOf(key)?.let { pitch ->
                val played = pitch - section.capo
                ChordTransposer.nameOf(played, ChordTransposer.prefersFlats(played))
            }
            parts.add("$capoLabel ${section.capo}")
            if (shapes != null) parts.add("$playLabel $shapes")
        }
    }
    if (section.bpm > 0) parts.add("${section.bpm} $bpmLabel")
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}
