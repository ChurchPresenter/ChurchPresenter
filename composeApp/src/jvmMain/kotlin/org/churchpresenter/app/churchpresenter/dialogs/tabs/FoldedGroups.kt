package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_fold_all
import org.churchpresenter.strings.generated.resources.profile_open_all
import org.jetbrains.compose.resources.stringResource

/**
 * The groups folded away on the page being drawn, and the way to fold or open them -- one set per page,
 * written by [onFoldedChange]. [present] is filled in by the groups themselves as they are drawn, so
 * Fold all folds exactly the groups the page has.
 */
internal class FoldedGroups(
    val folded: Set<String>,
    val present: MutableSet<String>,
    private val onFoldedChange: (Set<String>) -> Unit,
) {
    fun toggle(key: String) = onFoldedChange(if (key in folded) folded - key else folded + key)

    fun foldAll() = onFoldedChange(folded + present)

    fun openAll() = onFoldedChange(emptySet())
}

/** The page's folded groups; null where groups do not fold. */
internal val LocalFoldedGroups = compositionLocalOf<FoldedGroups?> { null }

/** Test handle for the caption that folds the group [key]. */
internal fun groupFoldTag(key: String): String = "settings_group_fold_$key"

/**
 * The groups folded on [page], kept in the document by the page's key, so a page folds the same way
 * whichever profile is open and after a restart. Fold all reaches the groups the page has drawn.
 */
@Composable
internal fun rememberFoldedGroups(
    settings: AppSettings,
    page: ProfilePage,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
): FoldedGroups {
    val pageKey = page.navTag()
    val present = remember(pageKey) { mutableSetOf<String>() }
    return FoldedGroups(settings.profilesFoldedGroups[pageKey].orEmpty(), present) { keys ->
        onSettingsChange { s ->
            val pages = s.profilesFoldedGroups - pageKey
            s.copy(profilesFoldedGroups = if (keys.isEmpty()) pages else pages + (pageKey to keys))
        }
    }
}

/** Fold all / Open all, when the page offers them. */
@Composable
internal fun FoldLinks(onFoldAll: (() -> Unit)?, onOpenAll: (() -> Unit)?) {
    if (onFoldAll == null || onOpenAll == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinkText(stringResource(Res.string.profile_fold_all), onFoldAll)
        LinkText(stringResource(Res.string.profile_open_all), onOpenAll)
    }
}
