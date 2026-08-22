package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.spec.AnimProperty
import org.churchpresenter.lottiegen.spec.AnimTrack
import org.churchpresenter.lottiegen.spec.EasingKind
import org.churchpresenter.lottiegen.spec.ElementSpec
import org.churchpresenter.lottiegen.spec.OffsetUnit
import org.churchpresenter.lottiegen.spec.SpecKeyframe
import org.churchpresenter.lottiegen.ui.Strings

/** A keyframe track runs 0..100% of the animation; the end is where every default lands. */
private const val TRACK_END_PCT = 100.0

/** Starting values a newly added element or track gets, chosen to read as an obvious default. */
private const val NEW_SLIDE_FROM_EM = -2.0
private const val NEW_ROTATE_FROM_DEG = -90.0

/** base64 carries three bytes in every four characters. */

/** The narrower of the two columns in a paired field row. */
private const val NARROW_FIELD_WEIGHT = 0.8f

private val ALIGN_KEYS = listOf("left", "center", "right")

internal fun valueArity(property: AnimProperty): Int = when (property) {
    AnimProperty.POSITION_OFFSET, AnimProperty.RECT_SIZE, AnimProperty.TRIM, AnimProperty.SCALE -> 2
    else -> 1
}

internal fun defaultKeyframes(property: AnimProperty): List<SpecKeyframe> = when (property) {
    AnimProperty.POSITION_OFFSET -> listOf(
        SpecKeyframe(0.0, listOf(NEW_SLIDE_FROM_EM, 0.0)), SpecKeyframe(TRACK_END_PCT, listOf(0.0, 0.0))
    )
    AnimProperty.OPACITY -> listOf(
        SpecKeyframe(0.0, listOf(0.0)), SpecKeyframe(TRACK_END_PCT, listOf(TRACK_END_PCT))
    )
    AnimProperty.SCALE -> listOf(
        SpecKeyframe(0.0, listOf(0.0, 0.0)), SpecKeyframe(TRACK_END_PCT, listOf(TRACK_END_PCT, TRACK_END_PCT))
    )
    AnimProperty.ROTATION -> listOf(
        SpecKeyframe(0.0, listOf(NEW_ROTATE_FROM_DEG)), SpecKeyframe(TRACK_END_PCT, listOf(0.0))
    )
    AnimProperty.RECT_SIZE -> listOf(
        SpecKeyframe(0.0, listOf(0.0, 1.0)), SpecKeyframe(TRACK_END_PCT, listOf(1.0, 1.0))
    )
    AnimProperty.STROKE_WIDTH -> listOf(
        SpecKeyframe(0.0, listOf(0.0)), SpecKeyframe(TRACK_END_PCT, listOf(1.0))
    )
    AnimProperty.TRIM -> listOf(
        SpecKeyframe(0.0, listOf(0.0, 0.0)), SpecKeyframe(TRACK_END_PCT, listOf(0.0, 1.0))
    )
}

@Composable
internal fun TracksEditor(element: ElementSpec, onChange: (List<AnimTrack>) -> Unit) {
    val tracks = element.tracks
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tracks.forEachIndexed { trackIndex, track ->
            TrackEditor(
                track = track,
                onChange = { new -> onChange(tracks.replaceAt(trackIndex) { new }) },
                onRemove = { onChange(tracks.filterIndexed { i, _ -> i != trackIndex }) }
            )
        }
        TextButton(onClick = {
            onChange(tracks + AnimTrack(AnimProperty.OPACITY, defaultKeyframes(AnimProperty.OPACITY)))
        }) {
            Text(Strings.editorAddTrack)
        }
    }
}

@Composable
internal fun TrackEditor(track: AnimTrack, onChange: (AnimTrack) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OptionDropdown(
                label = Strings.editorProperty,
                options = AnimProperty.entries,
                selected = track.property,
                display = { TrackLabels.property(it) },
                onSelect = { new ->
                    if (new != track.property) {
                        onChange(
                            track.copy(property = new, keyframes = defaultKeyframes(new), alignOverrides = emptyMap()),
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRemove) { Text(Strings.editorRemoveTrack) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionDropdown(
                label = Strings.editorEasing,
                options = EasingKind.entries,
                selected = track.easing,
                display = { TrackLabels.easing(it) },
                onSelect = { onChange(track.copy(easing = it)) },
                modifier = Modifier.weight(1f)
            )
            if (track.property == AnimProperty.POSITION_OFFSET) {
                OptionDropdown(
                    label = Strings.editorOffsetUnit,
                    options = OffsetUnit.entries,
                    selected = track.offsetUnit,
                    display = { TrackLabels.offsetUnit(it) },
                    onSelect = { onChange(track.copy(offsetUnit = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        KeyframeTable(
            label = Strings.editorKeyframes,
            keyframes = track.keyframes,
            arity = valueArity(track.property),
            onChange = { onChange(track.copy(keyframes = it)) }
        )

        for (align in ALIGN_KEYS) {
            val override = track.alignOverrides[align]
            if (override == null) {
                TextButton(onClick = {
                    onChange(track.copy(alignOverrides = track.alignOverrides + (align to track.keyframes)))
                }) {
                    Text("${Strings.editorAddOverride}: ${EditorLabels.align(align)}")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        EditorLabels.align(align),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = {
                        onChange(track.copy(alignOverrides = track.alignOverrides - align))
                    }) {
                        Text(Strings.editorRemoveOverride)
                    }
                }
                KeyframeTable(
                    label = null,
                    keyframes = override,
                    arity = valueArity(track.property),
                    onChange = { new ->
                        onChange(track.copy(alignOverrides = track.alignOverrides + (align to new)))
                    }
                )
            }
        }
    }
}

@Composable
internal fun KeyframeTable(
    label: String?,
    keyframes: List<SpecKeyframe>,
    arity: Int,
    onChange: (List<SpecKeyframe>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        keyframes.forEachIndexed { index, keyframe ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(
                    Strings.editorPct, keyframe.pct,
                    onCommit = { newPct ->
                        onChange(
                            keyframes.replaceAt(index) { it.copy(pct = newPct) }
                                .sortedBy { it.pct }
                        )
                    },
                    modifier = Modifier.weight(NARROW_FIELD_WEIGHT)
                )
                repeat(arity) { valueIndex ->
                    NumberField(
                        Strings.editorValue, keyframe.values.getOrElse(valueIndex) { 0.0 },
                        onCommit = { newValue ->
                            onChange(keyframes.replaceAt(index) { kf ->
                                val padded = List(arity) { i -> kf.values.getOrElse(i) { 0.0 } }
                                kf.copy(values = padded.mapIndexed { i, v -> if (i == valueIndex) newValue else v })
                            })
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = { onChange(keyframes.filterIndexed { i, _ -> i != index }) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = Strings.editorDelete,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        TextButton(onClick = {
            val last = keyframes.lastOrNull()
            val newKf = SpecKeyframe(100.0, last?.values ?: List(arity) { 0.0 })
            onChange(keyframes + newKf)
        }) {
            Text(Strings.editorAddKeyframe)
        }
    }
}
