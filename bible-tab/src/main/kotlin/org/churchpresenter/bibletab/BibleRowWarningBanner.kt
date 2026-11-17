package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_warning
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_row_warning_detail
import org.churchpresenter.strings.generated.resources.bible_row_warning_hide
import org.churchpresenter.strings.generated.resources.bible_row_warning_hide_verses
import org.churchpresenter.strings.generated.resources.bible_row_warning_more
import org.churchpresenter.strings.generated.resources.bible_row_warning_never
import org.churchpresenter.strings.generated.resources.bible_row_warning_show
import org.churchpresenter.strings.generated.resources.bible_row_warning_title
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Tells the operator a translation is missing verses because of damaged ids in its file.
 *
 * A warning, not an error: the Bible opened and works, so the loss would otherwise go unseen until
 * someone looks for the verse. It is information only — **Show verses** lists which verses are
 * affected, [onHide] hides it until the app restarts and [onNeverRemind] for good.
 */
@Composable
internal fun BibleRowWarningBanner(
    warnings: List<BibleRowWarning>,
    onHide: () -> Unit,
    onNeverRemind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showVerses by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = AppShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(IconRes.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(top = 1.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.bible_row_warning_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                warnings.forEach { warning -> WarningDetail(warning, showVerses) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showVerses = !showVerses }) {
                        Text(
                            stringResource(
                                if (showVerses) Res.string.bible_row_warning_hide_verses
                                else Res.string.bible_row_warning_show,
                            ),
                        )
                    }
                    TextButton(onClick = onHide) { Text(stringResource(Res.string.bible_row_warning_hide)) }
                    TextButton(onClick = onNeverRemind) {
                        Text(stringResource(Res.string.bible_row_warning_never))
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningDetail(warning: BibleRowWarning, showVerses: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(Res.string.bible_row_warning_detail, warning.fileName, warning.report.total),
            style = MaterialTheme.typography.bodySmall,
        )
        if (showVerses) {
            val more = warning.moreCount
            val tail = if (more > 0) ", " + stringResource(Res.string.bible_row_warning_more, more) else ""
            Text(
                text = warning.verses.joinToString(", ") + tail,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
