package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import org.churchpresenter.lottiegen.editor.EditorState
import org.churchpresenter.lottiegen.editor.ImageImport
import org.churchpresenter.lottiegen.editor.replaceElement
import org.churchpresenter.lottiegen.editor.withCommon
import org.churchpresenter.lottiegen.spec.AnchorIn
import org.churchpresenter.lottiegen.spec.AnimProperty
import org.churchpresenter.lottiegen.spec.AnimTrack
import org.churchpresenter.lottiegen.spec.BackgroundElement
import org.churchpresenter.lottiegen.spec.ColorRole
import org.churchpresenter.lottiegen.spec.CornerSpec
import org.churchpresenter.lottiegen.spec.EasingKind
import org.churchpresenter.lottiegen.spec.ElementSpec
import org.churchpresenter.lottiegen.spec.EllipseElement
import org.churchpresenter.lottiegen.spec.FillSpec
import org.churchpresenter.lottiegen.spec.GradientSpec
import org.churchpresenter.lottiegen.spec.GrowOrigin
import org.churchpresenter.lottiegen.spec.ImageElement
import org.churchpresenter.lottiegen.spec.ImageScaleMode
import org.churchpresenter.lottiegen.spec.LineAnchor
import org.churchpresenter.lottiegen.spec.LogoElement
import org.churchpresenter.lottiegen.spec.CurveVertex
import org.churchpresenter.lottiegen.spec.MaskRevealSpec
import org.churchpresenter.lottiegen.spec.MirrorMode
import org.churchpresenter.lottiegen.spec.OffsetUnit
import org.churchpresenter.lottiegen.spec.PaintSpec
import org.churchpresenter.lottiegen.spec.PathElement
import org.churchpresenter.lottiegen.spec.Placement
import org.churchpresenter.lottiegen.spec.PlacementOverride
import org.churchpresenter.lottiegen.spec.PolygonElement
import org.churchpresenter.lottiegen.spec.RepeatSpec
import org.churchpresenter.lottiegen.spec.RectElement
import org.churchpresenter.lottiegen.spec.SizeSpec
import org.churchpresenter.lottiegen.spec.SpecKeyframe
import org.churchpresenter.lottiegen.spec.SpecLayoutContext
import org.churchpresenter.lottiegen.spec.StrokeSpec
import org.churchpresenter.lottiegen.spec.StrokeWidthSpec
import org.churchpresenter.lottiegen.spec.TextAnimatorKind
import org.churchpresenter.lottiegen.spec.TextAnimatorSpec
import org.churchpresenter.lottiegen.spec.TextElement
import org.churchpresenter.lottiegen.spec.TextFieldRef
import org.churchpresenter.lottiegen.spec.VisibilityRule
import org.churchpresenter.lottiegen.spec.WidthBasis
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.components.CollapsibleSection
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.churchpresenter.lottiegen.ui.components.LottieTextField

private val ALIGN_KEYS = listOf("left", "center", "right")

/** Property inspector for the selected element (or a hint + nothing when none). */
@Composable
fun ElementInspector(state: EditorState) {
    val element = state.spec.elements.firstOrNull { it.id == state.selectedElementId }
    if (element == null) {
        Text(
            Strings.editorNoSelection,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LayoutInspector(state)
        return
    }

    fun update(transform: (ElementSpec) -> ElementSpec) {
        state.updateSpec { it.replaceElement(element.id, transform) }
    }

    CollapsibleSection(Strings.editorSectionGeneral, initiallyExpanded = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LottieTextField(
                value = element.name,
                onValueChange = { new -> update { it.withCommon(name = new) } },
                label = Strings.editorName,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                Strings.editorVisibleWhen,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (rule in VisibilityRule.entries.filter { it != VisibilityRule.ALWAYS }) {
                CheckboxRow(
                    label = EditorLabels.rule(rule),
                    checked = rule in element.visibleWhen,
                    onCheckedChange = { checked ->
                        update {
                            val rules = if (checked) it.visibleWhen + rule else it.visibleWhen - rule
                            it.withCommon(visibleWhen = rules)
                        }
                    }
                )
            }
        }
    }

    CollapsibleSection(Strings.editorSectionPlacement, initiallyExpanded = true) {
        PlacementEditor(state, element) { newPlacement ->
            update { it.withCommon(placement = newPlacement) }
        }
    }

    when (element) {
        is RectElement -> {
            CollapsibleSection(Strings.editorSectionSize) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeEditor(element.size) { new -> update { (it as RectElement).copy(size = new) } }
                    CornerEditor(element.corner) { new -> update { (it as RectElement).copy(corner = new) } }
                    GrowFromEditor(element.growFrom) { new -> update { (it as RectElement).copy(growFrom = new) } }
                    RepeatEditor(element.repeat) { new -> update { (it as RectElement).copy(repeat = new) } }
                }
            }
            CollapsibleSection(Strings.editorSectionPaint) {
                PaintEditor(element.paint) { new -> update { (it as RectElement).copy(paint = new) } }
            }
        }
        is EllipseElement -> {
            CollapsibleSection(Strings.editorSectionSize) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeEditor(element.size) { new -> update { (it as EllipseElement).copy(size = new) } }
                    RepeatEditor(element.repeat) { new -> update { (it as EllipseElement).copy(repeat = new) } }
                }
            }
            CollapsibleSection(Strings.editorSectionPaint) {
                PaintEditor(element.paint) { new -> update { (it as EllipseElement).copy(paint = new) } }
            }
        }
        is PolygonElement -> {
            CollapsibleSection(Strings.editorSectionSize) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PolygonEditor(element) { new -> update { new } }
                    RepeatEditor(element.repeat) { new -> update { (it as PolygonElement).copy(repeat = new) } }
                }
            }
            CollapsibleSection(Strings.editorSectionPaint) {
                PaintEditor(element.paint) { new -> update { (it as PolygonElement).copy(paint = new) } }
            }
        }
        is PathElement -> {
            CollapsibleSection(Strings.editorSectionSize, initiallyExpanded = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PathVerticesEditor(element) { new -> update { new } }
                    FitWidthDropdown(element.fitWidthTo) { new -> update { (it as PathElement).copy(fitWidthTo = new) } }
                    RepeatEditor(element.repeat) { new -> update { (it as PathElement).copy(repeat = new) } }
                }
            }
            CollapsibleSection(Strings.editorSectionPaint) {
                PaintEditor(element.paint) { new -> update { (it as PathElement).copy(paint = new) } }
            }
        }
        is TextElement -> {
            CollapsibleSection(Strings.editorSectionText, initiallyExpanded = true) {
                TextOptionsEditor(element) { new -> update { new } }
            }
        }
        is BackgroundElement -> {
            CollapsibleSection(Strings.editorSectionSize) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeEditor(element.size) { new -> update { (it as BackgroundElement).copy(size = new) } }
                    CornerEditor(element.corner) { new -> update { (it as BackgroundElement).copy(corner = new) } }
                    GrowFromEditor(element.growFrom) { new -> update { (it as BackgroundElement).copy(growFrom = new) } }
                    CheckboxRow(
                        label = Strings.editorBorderFromConfig,
                        checked = element.borderFromConfig,
                        onCheckedChange = { new -> update { (it as BackgroundElement).copy(borderFromConfig = new) } }
                    )
                }
            }
            CollapsibleSection(Strings.editorSectionPaint) {
                PaintEditor(element.paint) { new -> update { (it as BackgroundElement).copy(paint = new) } }
            }
        }
        is ImageElement -> {
            CollapsibleSection(Strings.editorSectionImage, initiallyExpanded = true) {
                ImageOptionsEditor(element) { new -> update { new } }
            }
            CollapsibleSection(Strings.editorSectionSize) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeEditor(element.size) { new -> update { (it as ImageElement).copy(size = new) } }
                    CornerEditor(element.corner) { new -> update { (it as ImageElement).copy(corner = new) } }
                }
            }
        }
        is LogoElement -> Unit
    }

    CollapsibleSection(Strings.editorSectionTracks, initiallyExpanded = true) {
        TracksEditor(element) { newTracks -> update { it.withCommon(tracks = newTracks) } }
    }
}

// ------------------------------------------------------------------ placement

@Composable
private fun PlacementEditor(state: EditorState, element: ElementSpec, onChange: (Placement) -> Unit) {
    val placement = element.placement
    val slotOptions = state.spec.layout.slots.map { it.id } + SpecLayoutContext.BLOCK_SLOT

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionDropdown(
            label = Strings.editorSlot,
            options = slotOptions,
            selected = if (placement.slot.isEmpty()) SpecLayoutContext.BLOCK_SLOT else placement.slot,
            display = { if (it == SpecLayoutContext.BLOCK_SLOT) Strings.editorSlotBlock else it },
            onSelect = { onChange(placement.copy(slot = it)) }
        )
        OptionDropdown(
            label = Strings.editorAnchor,
            options = AnchorIn.entries,
            selected = placement.anchorIn,
            display = { EditorLabels.anchor(it) },
            onSelect = { onChange(placement.copy(anchorIn = it)) }
        )
        OptionDropdown(
            label = Strings.editorLine,
            options = LineAnchor.entries,
            selected = placement.line,
            display = { EditorLabels.line(it) },
            onSelect = { onChange(placement.copy(line = it)) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                Strings.editorOffsetX, placement.offsetXEm,
                onCommit = { onChange(placement.copy(offsetXEm = it)) },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                Strings.editorOffsetY, placement.offsetYEm,
                onCommit = { onChange(placement.copy(offsetYEm = it)) },
                modifier = Modifier.weight(1f)
            )
        }
        CheckboxRow(
            label = Strings.editorMirror,
            checked = placement.mirror == MirrorMode.FLIP_ON_RIGHT,
            onCheckedChange = {
                onChange(placement.copy(mirror = if (it) MirrorMode.FLIP_ON_RIGHT else MirrorMode.NONE))
            }
        )
        if (element is RectElement || element is EllipseElement ||
            element is PolygonElement || element is PathElement
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    Strings.editorPivotX, placement.pivotXEm,
                    onCommit = { onChange(placement.copy(pivotXEm = it)) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorPivotY, placement.pivotYEm,
                    onCommit = { onChange(placement.copy(pivotYEm = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        for (align in ALIGN_KEYS) {
            val override = placement.alignOverrides[align]
            if (override == null) {
                TextButton(onClick = {
                    onChange(
                        placement.copy(
                            alignOverrides = placement.alignOverrides +
                                (align to PlacementOverride(
                                    anchorIn = placement.anchorIn,
                                    line = placement.line,
                                    offsetXEm = placement.offsetXEm,
                                    offsetYEm = placement.offsetYEm
                                ))
                        )
                    )
                }) {
                    Text("${Strings.editorAddOverride}: ${EditorLabels.align(align)}")
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            onChange(placement.copy(alignOverrides = placement.alignOverrides - align))
                        }) {
                            Text(Strings.editorRemoveOverride)
                        }
                    }
                    OptionDropdown(
                        label = Strings.editorAnchor,
                        options = AnchorIn.entries,
                        selected = override.anchorIn ?: placement.anchorIn,
                        display = { EditorLabels.anchor(it) },
                        onSelect = { new ->
                            onChange(placement.withOverride(align) { it.copy(anchorIn = new) })
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            Strings.editorOffsetX, override.offsetXEm ?: placement.offsetXEm,
                            onCommit = { new ->
                                onChange(placement.withOverride(align) { it.copy(offsetXEm = new) })
                            },
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            Strings.editorOffsetY, override.offsetYEm ?: placement.offsetYEm,
                            onCommit = { new ->
                                onChange(placement.withOverride(align) { it.copy(offsetYEm = new) })
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun Placement.withOverride(align: String, transform: (PlacementOverride) -> PlacementOverride): Placement {
    val current = alignOverrides[align] ?: PlacementOverride()
    return copy(alignOverrides = alignOverrides + (align to transform(current)))
}

// ------------------------------------------------------------------ size/shape

@Composable
private fun SizeEditor(size: SizeSpec, onChange: (SizeSpec) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionDropdown(
            label = Strings.editorSizeType,
            options = listOf(
                Strings.editorSizeEm, Strings.editorSizeContent,
                Strings.editorSizeTextWrap, Strings.editorSizeCanvasWidth
            ),
            selected = when (size) {
                is SizeSpec.Em -> Strings.editorSizeEm
                is SizeSpec.ContentDerived -> Strings.editorSizeContent
                is SizeSpec.TextWrap -> Strings.editorSizeTextWrap
                is SizeSpec.CanvasWidth -> Strings.editorSizeCanvasWidth
            },
            display = { it },
            onSelect = { label ->
                onChange(
                    when (label) {
                        Strings.editorSizeContent -> SizeSpec.ContentDerived()
                        Strings.editorSizeTextWrap -> SizeSpec.TextWrap(TextFieldRef.NAME)
                        Strings.editorSizeCanvasWidth -> SizeSpec.CanvasWidth(3.5)
                        else -> SizeSpec.Em(1.0, 1.0)
                    }
                )
            }
        )
        when (size) {
            is SizeSpec.Em -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    Strings.editorWidthEm, size.wEm,
                    onCommit = { onChange(size.copy(wEm = it)) }, modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorHeightEm, size.hEm,
                    onCommit = { onChange(size.copy(hEm = it)) }, modifier = Modifier.weight(1f)
                )
            }
            is SizeSpec.ContentDerived -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    Strings.editorPadX, size.padXEm,
                    onCommit = { onChange(size.copy(padXEm = it)) }, modifier = Modifier.weight(1f)
                )
                NumberField(
                    Strings.editorPadY, size.padYEm,
                    onCommit = { onChange(size.copy(padYEm = it)) }, modifier = Modifier.weight(1f)
                )
            }
            is SizeSpec.TextWrap -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionDropdown(
                    label = Strings.editorTextFieldRef,
                    options = TextFieldRef.entries,
                    selected = size.field,
                    display = { EditorLabels.textField(it) },
                    onSelect = { onChange(size.copy(field = it)) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        Strings.editorPadX, size.padXEm,
                        onCommit = { onChange(size.copy(padXEm = it)) }, modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        Strings.editorPadY, size.padYEm,
                        onCommit = { onChange(size.copy(padYEm = it)) }, modifier = Modifier.weight(1f)
                    )
                }
            }
            is SizeSpec.CanvasWidth -> NumberField(
                Strings.editorHeightEm, size.hEm,
                onCommit = { onChange(size.copy(hEm = it)) }, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CornerEditor(corner: CornerSpec, onChange: (CornerSpec) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionDropdown(
            label = Strings.editorCorner,
            options = listOf(Strings.editorCornerNone, Strings.editorCornerFromConfig, Strings.editorCornerEm),
            selected = when (corner) {
                is CornerSpec.None -> Strings.editorCornerNone
                is CornerSpec.FromConfig -> Strings.editorCornerFromConfig
                is CornerSpec.Em -> Strings.editorCornerEm
            },
            display = { it },
            onSelect = { label ->
                onChange(
                    when (label) {
                        Strings.editorCornerFromConfig -> CornerSpec.FromConfig()
                        Strings.editorCornerEm -> CornerSpec.Em(0.3)
                        else -> CornerSpec.None
                    }
                )
            }
        )
        when (corner) {
            is CornerSpec.FromConfig -> NumberField(
                Strings.editorCornerFactor, corner.factor,
                onCommit = { onChange(corner.copy(factor = it)) }, modifier = Modifier.fillMaxWidth()
            )
            is CornerSpec.Em -> NumberField(
                Strings.editorCornerRadius, corner.em,
                onCommit = { onChange(corner.copy(em = it)) }, modifier = Modifier.fillMaxWidth()
            )
            is CornerSpec.None -> Unit
        }
    }
}

@Composable
private fun GrowFromEditor(growFrom: GrowOrigin, onChange: (GrowOrigin) -> Unit) {
    OptionDropdown(
        label = Strings.editorGrowFrom,
        options = GrowOrigin.entries,
        selected = growFrom,
        display = {
            when (it) {
                GrowOrigin.CENTER -> Strings.editorGrowCenter
                GrowOrigin.ALIGN_EDGE -> Strings.editorGrowEdge
            }
        },
        onSelect = onChange
    )
}

@Composable
private fun PolygonEditor(element: PolygonElement, onChange: (PolygonElement) -> Unit) {
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
private fun FitWidthDropdown(
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

private fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, item -> if (i == index) transform(item) else item }

// ------------------------------------------------------------------ image

@Composable
private fun ImageOptionsEditor(element: ImageElement, onChange: (ImageElement) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (element.dataUri.isEmpty()) {
                Strings.editorImageNone
            } else {
                // base64 length × 3/4 ≈ decoded bytes; close enough for an info line.
                Strings.editorImageInfo(
                    element.naturalW, element.naturalH,
                    element.dataUri.length * 3 / 4 / 1024
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
private fun PathVerticesEditor(element: PathElement, onChange: (PathElement) -> Unit) {
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
private fun RepeatEditor(repeat: RepeatSpec?, onChange: (RepeatSpec?) -> Unit) {
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
private fun PaintEditor(paint: PaintSpec, onChange: (PaintSpec) -> Unit) {
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
                            fill = fill.copy(gradient = if (it) GradientSpec(0.0, 0.0, 5.0, 0.0) else null)
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
                                width = if (label == Strings.editorStrokeEm) StrokeWidthSpec.Em(0.1)
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
private fun TextOptionsEditor(element: TextElement, onChange: (TextElement) -> Unit) {
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
                        animator = if (it) TextAnimatorSpec(TextAnimatorKind.SEQUENTIAL_REVEAL, 0.0, 60.0, 0.8)
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
                display = { EditorLabels.animatorKind(it) },
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

private fun valueArity(property: AnimProperty): Int = when (property) {
    AnimProperty.POSITION_OFFSET, AnimProperty.RECT_SIZE, AnimProperty.TRIM, AnimProperty.SCALE -> 2
    else -> 1
}

private fun defaultKeyframes(property: AnimProperty): List<SpecKeyframe> = when (property) {
    AnimProperty.POSITION_OFFSET -> listOf(
        SpecKeyframe(0.0, listOf(-2.0, 0.0)), SpecKeyframe(100.0, listOf(0.0, 0.0))
    )
    AnimProperty.OPACITY -> listOf(
        SpecKeyframe(0.0, listOf(0.0)), SpecKeyframe(100.0, listOf(100.0))
    )
    AnimProperty.SCALE -> listOf(
        SpecKeyframe(0.0, listOf(0.0, 0.0)), SpecKeyframe(100.0, listOf(100.0, 100.0))
    )
    AnimProperty.ROTATION -> listOf(
        SpecKeyframe(0.0, listOf(-90.0)), SpecKeyframe(100.0, listOf(0.0))
    )
    AnimProperty.RECT_SIZE -> listOf(
        SpecKeyframe(0.0, listOf(0.0, 1.0)), SpecKeyframe(100.0, listOf(1.0, 1.0))
    )
    AnimProperty.STROKE_WIDTH -> listOf(
        SpecKeyframe(0.0, listOf(0.0)), SpecKeyframe(100.0, listOf(1.0))
    )
    AnimProperty.TRIM -> listOf(
        SpecKeyframe(0.0, listOf(0.0, 0.0)), SpecKeyframe(100.0, listOf(0.0, 1.0))
    )
}

@Composable
private fun TracksEditor(element: ElementSpec, onChange: (List<AnimTrack>) -> Unit) {
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
private fun TrackEditor(track: AnimTrack, onChange: (AnimTrack) -> Unit, onRemove: () -> Unit) {
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
                display = { EditorLabels.property(it) },
                onSelect = { new ->
                    if (new != track.property) {
                        onChange(track.copy(property = new, keyframes = defaultKeyframes(new), alignOverrides = emptyMap()))
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
                display = { EditorLabels.easing(it) },
                onSelect = { onChange(track.copy(easing = it)) },
                modifier = Modifier.weight(1f)
            )
            if (track.property == AnimProperty.POSITION_OFFSET) {
                OptionDropdown(
                    label = Strings.editorOffsetUnit,
                    options = OffsetUnit.entries,
                    selected = track.offsetUnit,
                    display = { EditorLabels.offsetUnit(it) },
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
private fun KeyframeTable(
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
                    modifier = Modifier.weight(0.8f)
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
