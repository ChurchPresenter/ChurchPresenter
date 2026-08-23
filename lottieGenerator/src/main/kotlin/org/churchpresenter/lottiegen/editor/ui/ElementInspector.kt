package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.editor.EditorState
import org.churchpresenter.lottiegen.editor.replaceElement
import org.churchpresenter.lottiegen.editor.withCommon
import org.churchpresenter.lottiegen.spec.AnchorIn
import org.churchpresenter.lottiegen.spec.BackgroundElement
import org.churchpresenter.lottiegen.spec.CornerSpec
import org.churchpresenter.lottiegen.spec.ElementSpec
import org.churchpresenter.lottiegen.spec.EllipseElement
import org.churchpresenter.lottiegen.spec.GrowOrigin
import org.churchpresenter.lottiegen.spec.ImageElement
import org.churchpresenter.lottiegen.spec.LineAnchor
import org.churchpresenter.lottiegen.spec.LogoElement
import org.churchpresenter.lottiegen.spec.MirrorMode
import org.churchpresenter.lottiegen.spec.PathElement
import org.churchpresenter.lottiegen.spec.Placement
import org.churchpresenter.lottiegen.spec.PlacementOverride
import org.churchpresenter.lottiegen.spec.PolygonElement
import org.churchpresenter.lottiegen.spec.RectElement
import org.churchpresenter.lottiegen.spec.SizeSpec
import org.churchpresenter.lottiegen.spec.SpecLayoutContext
import org.churchpresenter.lottiegen.spec.TextElement
import org.churchpresenter.lottiegen.spec.TextFieldRef
import org.churchpresenter.lottiegen.spec.VisibilityRule
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.components.CollapsibleSection
import org.churchpresenter.lottiegen.ui.components.LottieTextField

/** A keyframe track runs 0..100% of the animation; the end is where every default lands. */

/** Starting values a newly added element or track gets, chosen to read as an obvious default. */
private const val NEW_CANVAS_WIDTH_PCT = 3.5
private const val NEW_CORNER_EM = 0.3

/** base64 carries three bytes in every four characters. */

/** The narrower of the two columns in a paired field row. */

private val ALIGN_KEYS = listOf("left", "center", "right")

/**
 * Whether this element has a pivot to edit. The shape elements do -- they are drawn around one --
 * and text and image elements do not.
 */
private val ElementSpec.hasPivot: Boolean
    get() = this is RectElement || this is EllipseElement ||
        this is PolygonElement || this is PathElement

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

    val update: ((ElementSpec) -> ElementSpec) -> Unit = { transform ->
        state.updateSpec { it.replaceElement(element.id, transform) }
    }

    GeneralSection(element, update)

    CollapsibleSection(Strings.editorSectionPlacement, initiallyExpanded = true) {
        PlacementEditor(state, element) { newPlacement ->
            update { it.withCommon(placement = newPlacement) }
        }
    }

    ElementKindSections(element, update)

    CollapsibleSection(Strings.editorSectionTracks, initiallyExpanded = true) {
        TracksEditor(element) { newTracks -> update { it.withCommon(tracks = newTracks) } }
    }
}

/** Name and visibility rules -- the two things every element kind has. */
@Composable
private fun GeneralSection(element: ElementSpec, update: ((ElementSpec) -> ElementSpec) -> Unit) {
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
}

/** The editors that belong to one element kind and no other. */
@Composable
private fun ElementKindSections(
    element: ElementSpec,
    update: ((ElementSpec) -> ElementSpec) -> Unit,
) {
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
                    FitWidthDropdown(element.fitWidthTo) { new -> update { (it as PathElement)
                        .copy(fitWidthTo = new) } }
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
                    GrowFromEditor(element.growFrom) { new -> update { (it as BackgroundElement)
                        .copy(growFrom = new) } }
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
}


// ------------------------------------------------------------------ placement

@Composable
internal fun PlacementEditor(state: EditorState, element: ElementSpec, onChange: (Placement) -> Unit) {
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
        if (element.hasPivot) {
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

        AlignOverrides(placement, onChange)
    }
}


/** Per-alignment overrides: one card per alignment that has one, plus a button to add it. */
@Composable
private fun AlignOverrides(placement: Placement, onChange: (Placement) -> Unit) {
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

internal fun Placement.withOverride(align: String, transform: (PlacementOverride) -> PlacementOverride): Placement {
    val current = alignOverrides[align] ?: PlacementOverride()
    return copy(alignOverrides = alignOverrides + (align to transform(current)))
}

// ------------------------------------------------------------------ size/shape

@Composable
internal fun SizeEditor(size: SizeSpec, onChange: (SizeSpec) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionDropdown(
            label = Strings.editorSizeType,
            options = listOf(
                Strings.editorSizeEm, Strings.editorSizeContent,
                Strings.editorSizeTextWrap, Strings.editorSizeCanvasWidth,
                Strings.editorSizeLogoPlate
            ),
            selected = when (size) {
                is SizeSpec.Em -> Strings.editorSizeEm
                is SizeSpec.ContentDerived -> Strings.editorSizeContent
                is SizeSpec.TextWrap -> Strings.editorSizeTextWrap
                is SizeSpec.CanvasWidth -> Strings.editorSizeCanvasWidth
                is SizeSpec.LogoPlate -> Strings.editorSizeLogoPlate
            },
            display = { it },
            onSelect = { label ->
                onChange(
                    when (label) {
                        Strings.editorSizeContent -> SizeSpec.ContentDerived()
                        Strings.editorSizeTextWrap -> SizeSpec.TextWrap(TextFieldRef.NAME)
                        Strings.editorSizeCanvasWidth -> SizeSpec.CanvasWidth(NEW_CANVAS_WIDTH_PCT)
                        Strings.editorSizeLogoPlate -> SizeSpec.LogoPlate()
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
            is SizeSpec.LogoPlate -> NumberField(
                Strings.editorPadX, size.padEm,
                onCommit = { onChange(size.copy(padEm = it)) }, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun CornerEditor(corner: CornerSpec, onChange: (CornerSpec) -> Unit) {
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
                        Strings.editorCornerEm -> CornerSpec.Em(NEW_CORNER_EM)
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
internal fun GrowFromEditor(growFrom: GrowOrigin, onChange: (GrowOrigin) -> Unit) {
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
