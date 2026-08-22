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
import org.churchpresenter.lottiegen.editor.ImageImport
import org.churchpresenter.lottiegen.spec.ColorRole
import org.churchpresenter.lottiegen.spec.FillSpec
import org.churchpresenter.lottiegen.spec.GradientSpec
import org.churchpresenter.lottiegen.spec.ImageElement
import org.churchpresenter.lottiegen.spec.ImageScaleMode
import org.churchpresenter.lottiegen.spec.CurveVertex
import org.churchpresenter.lottiegen.spec.MaskRevealSpec
import org.churchpresenter.lottiegen.spec.PaintSpec
import org.churchpresenter.lottiegen.spec.PathElement
import org.churchpresenter.lottiegen.spec.PolygonElement
import org.churchpresenter.lottiegen.spec.RepeatSpec
import org.churchpresenter.lottiegen.spec.StrokeSpec
import org.churchpresenter.lottiegen.spec.StrokeWidthSpec
import org.churchpresenter.lottiegen.spec.TextAnimatorKind
import org.churchpresenter.lottiegen.spec.TextAnimatorSpec
import org.churchpresenter.lottiegen.spec.TextElement
import org.churchpresenter.lottiegen.spec.TextFieldRef
import org.churchpresenter.lottiegen.spec.WidthBasis
import org.churchpresenter.lottiegen.ui.Strings
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** A keyframe track runs 0..100% of the animation; the end is where every default lands. */

/** Starting values a newly added element or track gets, chosen to read as an obvious default. */
private const val NEW_GRADIENT_SPAN = 5.0
private const val NEW_STROKE_EM = 0.1
private const val NEW_REVEAL_SPAN_PCT = 60.0
private const val NEW_REVEAL_EASE = 0.8

/** base64 carries three bytes in every four characters. */
private const val BASE64_NUM = 3
private const val BASE64_DEN = 4
private const val BYTES_PER_KB = 1024

/** The narrower of the two columns in a paired field row. */

@Composable
internal fun PolygonEditor(element: PolygonElement, onChange: (PolygonElement) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            Strings.editorVertices,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        element.verticesEm.forEachIndexed { index, vertex ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(
                    Strings.editorOffsetX, vertex.getOrElse(0) { 0.0 },
                    onCommit = { new ->
                        onChange(element.copy(verticesEm = element.verticesEm.replaceAt(index) {
                            listOf(new, it.getOrElse(1) { 0.0 })
                        }))
                    },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorOffsetY, vertex.getOrElse(1) { 0.0 },
                    onCommit = { new ->
                        onChange(element.copy(verticesEm = element.verticesEm.replaceAt(index) {
                            listOf(it.getOrElse(0) { 0.0 }, new)
                        }))
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onChange(element.copy(verticesEm = element.verticesEm.filterIndexed { i, _ -> i != index }))
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = Strings.editorDelete,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        TextButton(onClick = { onChange(element.copy(verticesEm = element.verticesEm + listOf(listOf(0.0, 0.0)))) }) {
            Text(Strings.editorAddVertex)
        }
        CheckboxRow(
            label = Strings.editorClosedPath,
            checked = element.closed,
            onCheckedChange = { onChange(element.copy(closed = it)) }
        )
        FitWidthDropdown(element.fitWidthTo) { onChange(element.copy(fitWidthTo = it)) }
    }
}

@Composable
internal fun FitWidthDropdown(
    selected: WidthBasis?,
    label: String = Strings.editorFitWidth,
    onSelect: (WidthBasis?) -> Unit
) {
    OptionDropdown(
        label = label,
        options = listOf<WidthBasis?>(null) + WidthBasis.entries,
        selected = selected,
        display = { basis ->
            when (basis) {
                null -> Strings.editorFitNone
                WidthBasis.NAME -> Strings.editorFitName
                WidthBasis.INFO -> Strings.editorFitInfo
                WidthBasis.TEXT_BLOCK -> Strings.editorFitTextBlock
            }
        },
        onSelect = onSelect
    )
}

internal fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, item -> if (i == index) transform(item) else item }

// ------------------------------------------------------------------ image

@Composable
internal fun ImageOptionsEditor(element: ImageElement, onChange: (ImageElement) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (element.dataUri.isEmpty()) {
                Strings.editorImageNone
            } else {
                // base64 length × 3/4 ≈ decoded bytes; close enough for an info line.
                Strings.editorImageInfo(
                    element.naturalW, element.naturalH,
                    element.dataUri.length * BASE64_NUM / BASE64_DEN / BYTES_PER_KB
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = {
            val chooser = JFileChooser()
            chooser.fileFilter = FileNameExtensionFilter("Images", "png", "jpg", "jpeg")
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                ImageImport.import(chooser.selectedFile)?.let { imported ->
                    onChange(
                        element.copy(
                            dataUri = imported.dataUri,
                            naturalW = imported.width,
                            naturalH = imported.height
                        )
                    )
                }
            }
        }) {
            Text(Strings.editorImageImport)
        }
        OptionDropdown(
            label = Strings.editorImageScaleMode,
            options = ImageScaleMode.entries.toList(),
            selected = element.scaleMode,
            display = { mode ->
                when (mode) {
                    ImageScaleMode.FIT -> Strings.editorImageFit
                    ImageScaleMode.STRETCH -> Strings.editorImageStretch
                    ImageScaleMode.COVER -> Strings.editorImageCover
                }
            },
            onSelect = { onChange(element.copy(scaleMode = it)) }
        )
        NumberField(
            Strings.editorImageAlpha, element.alphaFactor,
            onCommit = { new -> onChange(element.copy(alphaFactor = new.coerceIn(0.0, 1.0))) }
        )
    }
}

@Composable
internal fun PathVerticesEditor(element: PathElement, onChange: (PathElement) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            Strings.editorVertices,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        element.verticesEm.forEachIndexed { index, vertex ->
            fun edit(transform: (CurveVertex) -> CurveVertex) {
                onChange(element.copy(verticesEm = element.verticesEm.replaceAt(index, transform)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(
                    Strings.editorOffsetX, vertex.x,
                    onCommit = { new -> edit { it.copy(x = new) } },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorOffsetY, vertex.y,
                    onCommit = { new -> edit { it.copy(y = new) } },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onChange(element.copy(verticesEm = element.verticesEm.filterIndexed { i, _ -> i != index }))
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = Strings.editorDelete,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    "${Strings.editorVertexIn} X", vertex.inX,
                    onCommit = { new -> edit { it.copy(inX = new) } },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    "${Strings.editorVertexIn} Y", vertex.inY,
                    onCommit = { new -> edit { it.copy(inY = new) } },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    "${Strings.editorVertexOut} X", vertex.outX,
                    onCommit = { new -> edit { it.copy(outX = new) } },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    "${Strings.editorVertexOut} Y", vertex.outY,
                    onCommit = { new -> edit { it.copy(outY = new) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        TextButton(onClick = {
            val last = element.verticesEm.lastOrNull() ?: CurveVertex(0.0, 0.0)
            onChange(element.copy(verticesEm = element.verticesEm + CurveVertex(last.x + 1.0, last.y)))
        }) {
            Text(Strings.editorAddVertex)
        }
        CheckboxRow(
            label = Strings.editorClosedPath,
            checked = element.closed,
            onCheckedChange = { onChange(element.copy(closed = it)) }
        )
    }
}

@Composable
internal fun RepeatEditor(repeat: RepeatSpec?, onChange: (RepeatSpec?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckboxRow(
            label = Strings.editorRepeat,
            checked = repeat != null,
            onCheckedChange = { onChange(if (it) RepeatSpec() else null) }
        )
        if (repeat != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    Strings.editorRepeatCopies, repeat.copies.toDouble(),
                    onCommit = { onChange(repeat.copy(copies = it.toInt().coerceAtLeast(1))) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorRepeatRotation, repeat.rotationDeg,
                    onCommit = { onChange(repeat.copy(rotationDeg = it)) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorRepeatScale, repeat.scalePct,
                    onCommit = { onChange(repeat.copy(scalePct = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    Strings.editorRepeatOffsetX, repeat.offsetXEm,
                    onCommit = { onChange(repeat.copy(offsetXEm = it)) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorRepeatOffsetY, repeat.offsetYEm,
                    onCommit = { onChange(repeat.copy(offsetYEm = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            FitWidthDropdown(repeat.fitWidthTo, label = Strings.editorRepeatFitWidth) {
                onChange(repeat.copy(fitWidthTo = it))
            }
            CheckboxRow(
                label = Strings.editorRepeatFade,
                checked = repeat.fadeOut,
                onCheckedChange = { onChange(repeat.copy(fadeOut = it)) }
            )
        }
    }
}

// ------------------------------------------------------------------ paint

@Composable
internal fun PaintEditor(paint: PaintSpec, onChange: (PaintSpec) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FillEditor(paint, onChange)
        StrokeEditor(paint, onChange)
    }
}

/** The fill: its colour role, and the gradient it optionally carries. */
@Composable
private fun FillEditor(paint: PaintSpec, onChange: (PaintSpec) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fill = paint.fill
        CheckboxRow(
            label = Strings.editorFill,
            checked = fill != null,
            onCheckedChange = { onChange(paint.copy(fill = if (it) FillSpec() else null)) }
        )
        if (fill != null) {
            OptionDropdown(
                label = Strings.editorColorRole,
                options = ColorRole.entries,
                selected = fill.role,
                display = { EditorLabels.role(it) },
                onSelect = { onChange(paint.copy(fill = fill.copy(role = it))) }
            )
            CheckboxRow(
                label = Strings.editorGradient,
                checked = fill.gradient != null,
                onCheckedChange = {
                    onChange(
                        paint.copy(
                            fill = fill.copy(
                                gradient = if (it) GradientSpec(0.0, 0.0, NEW_GRADIENT_SPAN, 0.0) else null,
                            )
                        )
                    )
                }
            )
            fill.gradient?.let { gradient ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        Strings.editorStartX, gradient.startXEm,
                        onCommit = { onChange(paint.copy(fill = fill.copy(gradient = gradient.copy(startXEm = it)))) },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        Strings.editorStartY, gradient.startYEm,
                        onCommit = { onChange(paint.copy(fill = fill.copy(gradient = gradient.copy(startYEm = it)))) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        Strings.editorEndX, gradient.endXEm,
                        onCommit = { onChange(paint.copy(fill = fill.copy(gradient = gradient.copy(endXEm = it)))) },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        Strings.editorEndY, gradient.endYEm,
                        onCommit = { onChange(paint.copy(fill = fill.copy(gradient = gradient.copy(endYEm = it)))) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** The stroke: its colour role, width and dash. */
@Composable
private fun StrokeEditor(paint: PaintSpec, onChange: (PaintSpec) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val stroke = paint.stroke
        CheckboxRow(
            label = Strings.editorStroke,
            checked = stroke != null,
            onCheckedChange = { onChange(paint.copy(stroke = if (it) StrokeSpec() else null)) }
        )
        if (stroke != null) {
            OptionDropdown(
                label = Strings.editorColorRole,
                options = ColorRole.entries,
                selected = stroke.role,
                display = { EditorLabels.role(it) },
                onSelect = { onChange(paint.copy(stroke = stroke.copy(role = it))) }
            )
            OptionDropdown(
                label = Strings.editorStrokeWidthType,
                options = listOf(Strings.editorStrokeFromConfig, Strings.editorStrokeEm),
                selected = when (stroke.width) {
                    is StrokeWidthSpec.FromConfig -> Strings.editorStrokeFromConfig
                    is StrokeWidthSpec.Em -> Strings.editorStrokeEm
                },
                display = { it },
                onSelect = { label ->
                    onChange(
                        paint.copy(
                            stroke = stroke.copy(
                                width = if (label == Strings.editorStrokeEm) StrokeWidthSpec.Em(NEW_STROKE_EM)
                                else StrokeWidthSpec.FromConfig
                            )
                        )
                    )
                }
            )
            (stroke.width as? StrokeWidthSpec.Em)?.let { width ->
                NumberField(
                    Strings.editorWidthEm, width.em,
                    onCommit = { onChange(paint.copy(stroke = stroke.copy(width = StrokeWidthSpec.Em(it)))) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            NumberField(
                Strings.editorDash, stroke.dashEm,
                onCommit = { onChange(paint.copy(stroke = stroke.copy(dashEm = it))) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


// ------------------------------------------------------------------ text

@Composable
internal fun TextOptionsEditor(element: TextElement, onChange: (TextElement) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionDropdown(
            label = Strings.editorTextFieldRef,
            options = TextFieldRef.entries,
            selected = element.field,
            display = { EditorLabels.textField(it) },
            onSelect = { onChange(element.copy(field = it)) }
        )

        val mask = element.maskReveal
        CheckboxRow(
            label = Strings.editorMaskReveal,
            checked = mask != null,
            onCheckedChange = { onChange(element.copy(maskReveal = if (it) MaskRevealSpec() else null)) }
        )
        if (mask != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    Strings.editorMaskPad, mask.padPx,
                    onCommit = { onChange(element.copy(maskReveal = mask.copy(padPx = it))) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorMaskHeight, mask.heightFactor,
                    onCommit = { onChange(element.copy(maskReveal = mask.copy(heightFactor = it))) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorMaskYOffset, mask.yOffsetFactor,
                    onCommit = { onChange(element.copy(maskReveal = mask.copy(yOffsetFactor = it))) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val animator = element.animator
        CheckboxRow(
            label = Strings.editorAnimator,
            checked = animator != null,
            onCheckedChange = {
                onChange(
                    element.copy(
                        animator = if (it) TextAnimatorSpec(
                            TextAnimatorKind.SEQUENTIAL_REVEAL, 0.0, NEW_REVEAL_SPAN_PCT, NEW_REVEAL_EASE,
                        )
                        else null
                    )
                )
            }
        )
        if (animator != null) {
            OptionDropdown(
                label = Strings.editorAnimatorKind,
                options = TextAnimatorKind.entries,
                selected = animator.kind,
                display = { TrackLabels.animatorKind(it) },
                onSelect = { onChange(element.copy(animator = animator.copy(kind = it))) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    Strings.editorStartPct, animator.startPct,
                    onCommit = { onChange(element.copy(animator = animator.copy(startPct = it))) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorEndPct, animator.endPct,
                    onCommit = { onChange(element.copy(animator = animator.copy(endPct = it))) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorPosOffsetEm, animator.posOffsetEm,
                    onCommit = { onChange(element.copy(animator = animator.copy(posOffsetEm = it))) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ tracks
