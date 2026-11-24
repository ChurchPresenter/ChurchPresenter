package org.churchpresenter.bibletab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.churchpresenter.hosanna.composables.feedback.Banner
import org.churchpresenter.hosanna.composables.model.MessageAction
import org.churchpresenter.hosanna.composables.model.MessageIntent
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_row_warning_detail
import org.churchpresenter.strings.generated.resources.bible_row_warning_hide
import org.churchpresenter.strings.generated.resources.bible_row_warning_hide_verses
import org.churchpresenter.strings.generated.resources.bible_row_warning_more
import org.churchpresenter.strings.generated.resources.bible_row_warning_never
import org.churchpresenter.strings.generated.resources.bible_row_warning_show
import org.churchpresenter.strings.generated.resources.bible_row_warning_title
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
    val details = warnings.map {
        stringResource(Res.string.bible_row_warning_detail, it.fileName, it.report.total)
    }
    val toggle = stringResource(
        if (showVerses) Res.string.bible_row_warning_hide_verses else Res.string.bible_row_warning_show,
    )
    Banner(
        body = details.joinToString("\n"),
        modifier = modifier,
        intent = MessageIntent.Warning,
        title = stringResource(Res.string.bible_row_warning_title),
        otherActions = listOf(
            MessageAction(toggle) { showVerses = !showVerses },
            MessageAction(stringResource(Res.string.bible_row_warning_hide), onHide),
            MessageAction(stringResource(Res.string.bible_row_warning_never), onNeverRemind),
        ),
        extra = if (showVerses) {
            { warnings.forEach { WarningVerses(it) } }
        } else {
            null
        },
    )
}

@Composable
private fun WarningVerses(warning: BibleRowWarning) {
    val more = warning.moreCount
    val tail = if (more > 0) ", " + stringResource(Res.string.bible_row_warning_more, more) else ""
    Text(
        text = warning.verses.joinToString(", ") + tail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}
