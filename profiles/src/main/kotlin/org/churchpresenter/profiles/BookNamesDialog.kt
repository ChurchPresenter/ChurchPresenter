package org.churchpresenter.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.churchpresenter.bibletab.BibleBookNames
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_search
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.book_names
import org.churchpresenter.strings.generated.resources.book_names_clear
import org.churchpresenter.strings.generated.resources.book_names_failed_text
import org.churchpresenter.strings.generated.resources.book_names_failed_title
import org.churchpresenter.strings.generated.resources.book_names_some_text
import org.churchpresenter.strings.generated.resources.book_names_some_title
import org.churchpresenter.strings.generated.resources.book_names_file_text
import org.churchpresenter.strings.generated.resources.book_names_file_title
import org.churchpresenter.strings.generated.resources.book_names_filter_all
import org.churchpresenter.strings.generated.resources.book_names_find
import org.churchpresenter.strings.generated.resources.book_names_new_testament
import org.churchpresenter.strings.generated.resources.book_names_no_file_text
import org.churchpresenter.strings.generated.resources.book_names_no_file_title
import org.churchpresenter.strings.generated.resources.book_names_no_names_set
import org.churchpresenter.strings.generated.resources.book_names_no_overrides
import org.churchpresenter.strings.generated.resources.book_names_old_testament
import org.churchpresenter.strings.generated.resources.book_names_one_override
import org.churchpresenter.strings.generated.resources.book_names_overridden
import org.churchpresenter.strings.generated.resources.book_names_overrides
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.RaisedFilterChip
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val DIALOG_SHAPE = AppShape(12.dp)
private val BUTTON_SHAPE = AppShape(6.dp)
private const val BANNER_FILL_ALPHA = 0.08f
private const val BANNER_RIM_ALPHA = 0.28f

@Composable
internal fun BookNamesDialog(
    translationName: String,
    abbreviation: String,
    module: ModuleBookNames,
    overrides: Map<Int, String>,
    onSave: (Map<Int, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val titles by produceState(BookTitles(emptyList(), emptyList())) {
        val english = BibleBookNames.getEnglishBookNames()
        val localized = BibleBookNames.getBookNameMapping()
        value = BookTitles(english, english.map { localized[it.lowercase()] ?: it })
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BookNamesContent(
            title = "$translationName · $abbreviation",
            module = module,
            titles = titles,
            overrides = overrides,
            onSave = onSave,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun BookNamesContent(
    title: String,
    module: ModuleBookNames,
    titles: BookTitles,
    overrides: Map<Int, String>,
    onSave: (Map<Int, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(overrides) }
    var filter by remember { mutableStateOf(BookNameFilter.ALL) }
    var query by remember { mutableStateOf("") }
    val fileNames = module.names
    val hasFileNames = fileNames.isNotEmpty()
    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .heightIn(max = 780.dp)
            .fillMaxHeight()
            .shadow(24.dp, DIALOG_SHAPE)
            .clip(DIALOG_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, DIALOG_SHAPE)
            .onPreviewKeyEvent { event ->
                val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                if (escape) onDismiss()
                escape
            },
    ) {
        DialogHeader(title, onDismiss)
        SourceBanner(module)
        FilterBar(filter, draft.size, query, onFilter = { filter = it }, onQuery = { query = it })
        ColumnHeaders()
        BookList(
            ids = bookIdsShown(filter, query, draft, fileNames, titles),
            fileNames = fileNames,
            titles = titles,
            draft = draft,
            onEdit = { id, typed -> draft = if (typed.isEmpty()) draft - id else draft + (id to typed) },
            modifier = Modifier.weight(1f),
        )
        DialogFooter(
            overrideCount = draft.size,
            hasFileNames = hasFileNames,
            onClear = { draft = emptyMap() },
            onCancel = onDismiss,
            onSave = { onSave(savedBookOverrides(draft, fileNames)) },
        )
    }
}

@Composable
private fun DialogHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.book_names),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_close),
            text = stringResource(Res.string.close),
            onClick = onClose,
            iconSize = 14.dp,
            buttonSize = 28.dp,
        )
    }
}

@Composable
private fun SourceBanner(module: ModuleBookNames) {
    val tone = if (module.read == BookNamesRead.ALL) MaterialTheme.semantic.success else MaterialTheme.semantic.warning
    val title = when (module.read) {
        BookNamesRead.ALL -> Res.string.book_names_file_title
        BookNamesRead.SOME -> Res.string.book_names_some_title
        BookNamesRead.NONE -> Res.string.book_names_no_file_title
        BookNamesRead.FAILED -> Res.string.book_names_failed_title
    }
    val text = when (module.read) {
        BookNamesRead.ALL -> stringResource(Res.string.book_names_file_text)
        BookNamesRead.SOME -> stringResource(Res.string.book_names_some_text, module.names.size)
        BookNamesRead.NONE -> stringResource(Res.string.book_names_no_file_text)
        BookNamesRead.FAILED -> stringResource(Res.string.book_names_failed_text)
    }
    val shape = AppShape(8.dp)
    Row(
        modifier = Modifier
            .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
            .fillMaxWidth()
            .background(tone.copy(alpha = BANNER_FILL_ALPHA), shape)
            .border(1.dp, tone.copy(alpha = BANNER_RIM_ALPHA), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).background(tone, CircleShape))
        Column {
            Text(
                stringResource(title),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterBar(
    filter: BookNameFilter,
    overrideCount: Int,
    query: String,
    onFilter: (BookNameFilter) -> Unit,
    onQuery: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterKey(stringResource(Res.string.book_names_filter_all), BOOK_COUNT, filter == BookNameFilter.ALL) {
                onFilter(BookNameFilter.ALL)
            }
            FilterKey(
                stringResource(Res.string.book_names_old_testament),
                OLD_TESTAMENT_BOOKS,
                filter == BookNameFilter.OLD_TESTAMENT,
            ) { onFilter(BookNameFilter.OLD_TESTAMENT) }
            FilterKey(
                stringResource(Res.string.book_names_new_testament),
                BOOK_COUNT - OLD_TESTAMENT_BOOKS,
                filter == BookNameFilter.NEW_TESTAMENT,
            ) { onFilter(BookNameFilter.NEW_TESTAMENT) }
            FilterKey(
                stringResource(Res.string.book_names_overridden),
                overrideCount,
                filter == BookNameFilter.OVERRIDDEN,
            ) { onFilter(BookNameFilter.OVERRIDDEN) }
        }
        SunkenField(
            value = query,
            onValueChange = onQuery,
            placeholder = stringResource(Res.string.book_names_find),
            modifier = Modifier.fillMaxWidth().height(32.dp),
            leading = {
                Icon(
                    painterResource(IconRes.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            },
        )
    }
}

@Composable
private fun FilterKey(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    RaisedFilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label  $count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
    )
}

@Composable
private fun DialogFooter(
    overrideCount: Int,
    hasFileNames: Boolean,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val summary = when {
        overrideCount == 1 -> stringResource(Res.string.book_names_one_override)
        overrideCount > 1 -> stringResource(Res.string.book_names_overrides, overrideCount)
        hasFileNames -> stringResource(Res.string.book_names_no_overrides)
        else -> stringResource(Res.string.book_names_no_names_set)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (overrideCount > 0) {
            GhostButton(shape = BUTTON_SHAPE, onClick = onClear) { Text(stringResource(Res.string.book_names_clear)) }
        }
        Spacer(Modifier.weight(1f))
        GhostButton(
            shape = BUTTON_SHAPE,
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(stringResource(Res.string.cancel))
        }
        RaisedButton(
            shape = BUTTON_SHAPE,
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(stringResource(Res.string.save))
        }
    }
}
