package org.churchpresenter.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_arrow_right
import org.churchpresenter.sharedui.composables.SCANNING_ROW_TAG
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.book_names
import org.churchpresenter.strings.generated.resources.book_names_custom
import org.churchpresenter.strings.generated.resources.book_names_edited
import org.churchpresenter.strings.generated.resources.book_names_from_file
import org.churchpresenter.strings.generated.resources.book_names_not_set
import org.churchpresenter.strings.generated.resources.book_names_partly_set
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedChip
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val BUTTON_SHAPE = AppShape(6.dp)
private const val BUTTON_RIM_ALPHA = 0.55f
private val BOOK_NAMES_KEY_HEIGHT = 41.dp
private val BOOK_NAMES_LABEL_WIDTH = 84.dp

/** The Book names key of a translation's row, and the dialog it opens. */
@Composable
internal fun BookNamesButton(
    translationName: String,
    abbreviation: String,
    storageDirectory: String,
    fileName: String,
    overrides: Map<Int, String>,
    onSave: (Map<Int, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val module = rememberModuleBookNames(storageDirectory, fileName)
    var open by remember(fileName) { mutableStateOf(false) }
    BookNamesKey(
        status = module?.let { bookNamesStatus(it.names.isNotEmpty(), overrides.size) },
        onClick = { open = true },
        modifier = modifier,
    )
    if (open && module != null) {
        BookNamesDialog(
            translationName = translationName,
            abbreviation = abbreviation,
            module = module,
            overrides = overrides,
            onSave = { saved ->
                onSave(saved)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
internal fun BookNamesKey(status: BookNamesStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val needsNames = status is BookNamesStatus.NotSet || status is BookNamesStatus.PartlySet
    val rim = if (needsNames) {
        Modifier.border(1.dp, MaterialTheme.semantic.warning.copy(alpha = BUTTON_RIM_ALPHA), BUTTON_SHAPE)
    } else {
        Modifier
    }
    val scanning = if (status == null) Modifier.testTag(SCANNING_ROW_TAG) else Modifier
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        RaisedChip(
            onClick = onClick,
            fill = elevationPalette().key,
            shape = BUTTON_SHAPE,
            enabled = status != null,
            modifier = modifier.height(BOOK_NAMES_KEY_HEIGHT).then(rim).then(scanning),
            label = {
                Column(modifier = Modifier.width(BOOK_NAMES_LABEL_WIDTH)) {
                    ColumnCaption(stringResource(Res.string.book_names))
                    status?.let { StatusLine(it) }
                }
            },
            trailingIcon = {
                Icon(
                    painterResource(IconRes.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            },
        )
    }
}

@Composable
private fun StatusLine(status: BookNamesStatus) {
    val color = when (status) {
        BookNamesStatus.FromFile -> MaterialTheme.semantic.success
        is BookNamesStatus.Edited, BookNamesStatus.Custom -> MaterialTheme.colorScheme.primary
        BookNamesStatus.NotSet, is BookNamesStatus.PartlySet -> MaterialTheme.semantic.warning
    }
    val text = when (status) {
        BookNamesStatus.FromFile -> stringResource(Res.string.book_names_from_file)
        is BookNamesStatus.Edited -> stringResource(Res.string.book_names_edited, status.count)
        BookNamesStatus.NotSet -> stringResource(Res.string.book_names_not_set)
        is BookNamesStatus.PartlySet -> stringResource(Res.string.book_names_partly_set, status.count)
        BookNamesStatus.Custom -> stringResource(Res.string.book_names_custom)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
