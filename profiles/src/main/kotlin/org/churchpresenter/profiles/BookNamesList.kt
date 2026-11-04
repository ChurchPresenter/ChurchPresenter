package org.churchpresenter.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_undo
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.book_names_book
import org.churchpresenter.strings.generated.resources.book_names_new_testament
import org.churchpresenter.strings.generated.resources.book_names_no_match
import org.churchpresenter.strings.generated.resources.book_names_old_testament
import org.churchpresenter.strings.generated.resources.book_names_show_as
import org.churchpresenter.strings.generated.resources.book_names_use_default
import org.churchpresenter.strings.generated.resources.book_names_use_file_name
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.keyboardFocusRing
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val BUTTON_SHAPE = AppShape(6.dp)
private val NUMBER_WIDTH = 34.dp
private val RESET_SIZE = 24.dp
private val COLUMN_GAP = 10.dp
private const val SOURCE_WEIGHT = 1f
private const val FIELD_WEIGHT = 1.25f
private const val OVERRIDDEN_ROW_ALPHA = 0.06f
private const val OVERRIDDEN_FIELD_ALPHA = 0.14f
private const val FAINT_ALPHA = 0.7f

@Composable
internal fun SunkenField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fill: Color = Color.Unspecified,
    bold: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = AppShape(6.dp)
    Row(
        modifier = modifier
            .keyboardFocusRing(shape, includeChildren = true)
            .sunken(shape, elevationPalette(), fill = fill)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = FAINT_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
    }
}

@Composable
internal fun ColumnHeaders() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(start = 26.dp, end = 26.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
    ) {
        ColumnCaption("#", Modifier.width(NUMBER_WIDTH))
        ColumnCaption(stringResource(Res.string.book_names_book), Modifier.weight(SOURCE_WEIGHT))
        ColumnCaption(stringResource(Res.string.book_names_show_as), Modifier.weight(FIELD_WEIGHT))
        Spacer(Modifier.width(RESET_SIZE))
    }
}

@Composable
internal fun ColumnCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun BookList(
    ids: List<Int>,
    fileNames: Map<Int, String>,
    titles: BookTitles,
    draft: Map<Int, String>,
    onEdit: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ids.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.book_names_no_match),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val (oldTestament, newTestament) = ids.partition { it <= OLD_TESTAMENT_BOOKS }
    val oldTitle = stringResource(Res.string.book_names_old_testament)
    val newTitle = stringResource(Res.string.book_names_new_testament)
    LazyColumn(modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        listOf(oldTitle to oldTestament, newTitle to newTestament).forEach { (title, books) ->
            if (books.isNotEmpty()) {
                item(key = title) { ColumnCaption(title, Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp)) }
                items(books, key = { it }) { id ->
                    BookRow(
                        id = id,
                        fileName = fileNames[id],
                        englishName = titles.englishOf(id),
                        localizedName = titles.localizedOf(id),
                        override = draft[id],
                        onEdit = { typed -> onEdit(id, typed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookRow(
    id: Int,
    fileName: String?,
    englishName: String,
    localizedName: String,
    override: String?,
    onEdit: (String) -> Unit,
) {
    val overridden = override != null
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (overridden) accent.copy(alpha = OVERRIDDEN_ROW_ALPHA) else Color.Transparent, BUTTON_SHAPE)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
    ) {
        Text(
            id.toString(),
            modifier = Modifier.width(NUMBER_WIDTH),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BookTitle(localizedName, englishName, Modifier.weight(SOURCE_WEIGHT))
        SunkenField(
            value = override.orEmpty(),
            onValueChange = onEdit,
            placeholder = fileName ?: englishName,
            modifier = Modifier.weight(FIELD_WEIGHT).height(28.dp),
            fill = if (overridden) accent.copy(alpha = OVERRIDDEN_FIELD_ALPHA) else Color.Unspecified,
            bold = overridden,
        )
        if (overridden) {
            TooltipIconButton(
                painter = painterResource(IconRes.drawable.ic_undo),
                text = stringResource(
                    if (fileName != null) Res.string.book_names_use_file_name else Res.string.book_names_use_default,
                ),
                onClick = { onEdit("") },
                iconSize = 13.dp,
                buttonSize = RESET_SIZE,
            )
        } else {
            Spacer(Modifier.size(RESET_SIZE))
        }
    }
}

@Composable
private fun BookTitle(localizedName: String, englishName: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            localizedName,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (englishName != localizedName) {
            Text(
                englishName,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
