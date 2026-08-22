package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.font_category_display
import churchpresenter.composeapp.generated.resources.font_category_mono
import churchpresenter.composeapp.generated.resources.font_category_sans
import churchpresenter.composeapp.generated.resources.font_category_serif
import churchpresenter.composeapp.generated.resources.font_picker_clear_search
import churchpresenter.composeapp.generated.resources.font_picker_keys
import churchpresenter.composeapp.generated.resources.font_picker_search
import churchpresenter.composeapp.generated.resources.font_preview
import churchpresenter.composeapp.generated.resources.font_warning_no_cyrillic
import churchpresenter.composeapp.generated.resources.font_warning_no_hebrew
import churchpresenter.composeapp.generated.resources.ic_check
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_search
import churchpresenter.composeapp.generated.resources.ic_warning
import org.churchpresenter.app.churchpresenter.utils.FontCategory
import org.churchpresenter.app.churchpresenter.utils.FontFace
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The panel is a fixed slab: the names it lists are long and must not resize as they are filtered. */
internal val FONT_PANEL_WIDTH = 378.dp

/** One row of the menu, whether a heading or a family. */
internal sealed interface FontRow {
    data class Header(val kind: FontGroupKind, val count: Int) : FontRow
    data class Family(val face: FontFace) : FontRow
}

/** The search box, which is also where the arrow keys are read from. */
@Composable
internal fun FontSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
) {
    val searchLabel = stringResource(Res.string.font_picker_search)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = searchLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                // The box has a placeholder and no label, so without this a screen reader reaches a
                // text field that says nothing about itself.
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = searchLabel },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClear,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.font_picker_clear_search),
                    modifier = Modifier.size(9.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A group heading: what the rows under it have in common, and how many there are. */
@Composable
internal fun FontGroupHeader(header: FontRow.Header) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = groupLabel(header.kind).uppercase(),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = groupColor(header.kind),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = header.count.toString(),
            fontSize = 9.5.sp,
            lineHeight = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** A family: drawn in itself, with what it covers and what it is shaped like beside it. */
@Composable
internal fun FontFamilyRow(
    face: FontFace,
    selected: Boolean,
    highlighted: Boolean,
    onPick: () -> Unit,
    onHover: () -> Unit,
) {
    // The highlight follows the pointer as well as the arrow keys, so hovering a row previews it.
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .hoverable(interaction)
            .background(rowBackground(selected, highlighted), RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPick,
            )
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = face.name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontFamily = remember(face.name) { systemFontFamilyOrDefault(face.name) },
            ),
            color = if (selected || highlighted) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = categoryLabel(face.category),
            fontSize = 9.5.sp,
            lineHeight = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            // Wide enough for the translated terms: "Bezszeryfowa", "Моноширинный", "Schreefloos"
            // all run past what the English "Sans" needs.
            modifier = Modifier.width(66.dp),
        )
    }
}

@Composable
private fun rowBackground(selected: Boolean, highlighted: Boolean): Color = when {
    highlighted -> MaterialTheme.colorScheme.surfaceVariant
    selected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    else -> Color.Transparent
}

@Composable
internal fun categoryLabel(category: FontCategory): String = when (category) {
    FontCategory.SANS -> stringResource(Res.string.font_category_sans)
    FontCategory.SERIF -> stringResource(Res.string.font_category_serif)
    FontCategory.MONO -> stringResource(Res.string.font_category_mono)
    FontCategory.DISPLAY -> stringResource(Res.string.font_category_display)
}

/**
 * What the highlighted family looks like on the screen the room sees.
 *
 * Black with white text on purpose: the projector shows that regardless of the operator's theme, so
 * a preview in the app's own colours would be a preview of the wrong thing. The [lines] are Genesis
 * 1:1 out of the translations actually loaded — the point of the preview is the operator's own text
 * in their own scripts, which is also what catches a family that cannot draw them.
 */
@Composable
internal fun FontPreviewPane(face: FontFace, measured: Boolean, lines: List<String>) {
    val family = remember(face.name) { systemFontFamilyOrDefault(face.name) }
    val missing = remember(face, lines, measured) {
        if (!measured) emptyList() else lines.flatMap { missingScripts(it, face) }.distinct()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.font_preview).uppercase(),
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = face.name,
                fontSize = 10.5.sp,
                lineHeight = 11.sp,
                fontFamily = family,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lines.forEach { line ->
                // A line the family cannot draw is shown in the fallback it would actually be drawn
                // in, dimmed, rather than hidden — seeing the substitution is the whole point.
                val drawable = measured && missingScripts(line, face).isEmpty()
                Text(
                    text = line,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontFamily = if (drawable || !measured) family else null,
                    color = if (drawable || !measured) Color.White else Color.White.copy(alpha = 0.45f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        missing.forEach { script ->
            Spacer(Modifier.height(7.dp))
            FontScriptWarning(face.name, script)
        }
    }
}

@Composable
private fun FontScriptWarning(name: String, script: PreviewScript) {
    val semantic = MaterialTheme.semantic
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(semantic.warningContainer, RoundedCornerShape(6.dp))
            .border(1.dp, semantic.warning.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_warning),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = semantic.warning,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = when (script) {
                PreviewScript.CYRILLIC -> stringResource(Res.string.font_warning_no_cyrillic, name)
                PreviewScript.HEBREW -> stringResource(Res.string.font_warning_no_hebrew, name)
            },
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            color = semantic.onWarningContainer,
        )
    }
}

/** How much of the machine's set the list is showing, and what it left out. */
@Composable
internal fun FontCatalogNote(note: String) {
    Text(
        text = note,
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 3.dp),
    )
}

/** The keys the panel answers to, spelled out where a first-time user will look for them. */
@Composable
internal fun FontPickerFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = stringResource(Res.string.font_picker_keys),
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            maxLines = 1,
        )
    }
}
