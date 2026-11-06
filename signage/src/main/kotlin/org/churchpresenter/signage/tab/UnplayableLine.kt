package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.churchpresenter.signage.core.BlockAction
import org.churchpresenter.signage.core.Features
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_cannot_play
import org.jetbrains.compose.resources.stringResource

/**
 * The links [plan] asks a screen to play that its player cannot, by name. The manifest leaves them
 * out, so without this a screen would just not show them and nobody would be told why.
 */
internal fun unplayableLinks(
    plan: ScreenPlan,
    added: List<AddedPlaylist>,
    features: Collection<String>,
): List<String> {
    val byId = added.associateBy { it.id }
    val planned = listOfNotNull(plan.playlistId) +
        plan.schedule.mapNotNull { (it.action as? BlockAction.Play)?.playlistId }
    return planned.distinct().mapNotNull(byId::get).flatMap { entry ->
        when (entry) {
            is LinkPlaylist -> listOf(entry).filter { linkItem(it, features) == null }
            is RotationPlaylist -> entry.steps.mapNotNull { byId[it.sourceId] as? LinkPlaylist }
                .filter { linkItem(it, features) == null || Features.ROTATION !in features }
            else -> emptyList()
        }
    }.map { it.name }.distinct()
}

/** Says which of a screen's links it will skip, while its player is connected to say what it can do. */
@Composable
internal fun UnplayableLine(row: ScreenRowState) {
    val link = row.link ?: return
    val names = unplayableLinks(row.folder.settings.plan(row.screen.id), row.folder.settings.added, link.features)
    if (names.isEmpty()) return
    Text(
        stringResource(Res.string.signage_cannot_play, names.joinToString()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
