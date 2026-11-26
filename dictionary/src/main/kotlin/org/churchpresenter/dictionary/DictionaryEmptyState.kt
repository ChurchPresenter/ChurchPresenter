package org.churchpresenter.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.icons.generated.resources.ic_storage_bible
import org.churchpresenter.sharedui.composables.EmptyStateContent
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.dictionary_empty_body
import org.churchpresenter.strings.generated.resources.dictionary_empty_title
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/** The examples' row, so a finder for the entry list can tell a chip from a list row. */
const val EXAMPLE_ENTRIES_TAG = "dictionaryExampleEntries"

/** Love, in Hebrew and Greek, and grace: entries worth opening to see what the pane shows. */
private val EXAMPLE_ENTRIES = listOf("H157", "G26", "G5485")

/** The details side with no entry chosen: how to find one, and a few to open straight away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoEntrySelected(getEntry: ((String) -> StrongsEntry?)?, onPick: (String) -> Unit) {
    // Only the examples this install's dictionary has; none before it has loaded.
    val examples = EXAMPLE_ENTRIES.mapNotNull { getEntry?.invoke(it) }
    EmptyStateContent(
        icon = IconRes.drawable.ic_storage_bible,
        neutral = true,
        compact = true,
        title = stringResource(Res.string.dictionary_empty_title),
        body = stringResource(Res.string.dictionary_empty_body),
    ) {
        if (examples.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.testTag(EXAMPLE_ENTRIES_TAG),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                examples.forEach { entry -> ExampleEntryChip(entry) { onPick(entry.number) } }
            }
        }
    }
}

/** One example: its Strong's number, then its transliteration. */
@Composable
private fun ExampleEntryChip(entry: StrongsEntry, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    RaisedButton(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        shape = AppShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceVariant,
            contentColor = colors.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Text(
            entry.number,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Bold,
            color = colors.tertiary,
            maxLines = 1,
        )
        Text(
            entry.transliteration.ifBlank { entry.word },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
        )
    }
}
