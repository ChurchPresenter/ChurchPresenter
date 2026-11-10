package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.signage.hub.HeldScreen
import org.churchpresenter.signage.hub.PairingQueue
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_held_screen
import org.churchpresenter.strings.generated.resources.signage_held_screen_hint
import org.churchpresenter.strings.generated.resources.signage_pair_here
import org.jetbrains.compose.resources.stringResource

/** The removed screens this computer is not taking back for a few minutes, and the way to take one back. */
class HeldScreens internal constructor(private val queue: PairingQueue) {
    val held: StateFlow<List<HeldScreen>> = queue.held

    /** Takes [playerId] off hold and asks it to come and pair here, even from another ChurchPresenter. */
    fun pairHere(playerId: String) = queue.invite(playerId)
}

/** A card for each removed screen still held back; a hold ends by itself, so the list ticks while there is one. */
@Composable
internal fun HeldScreenCards(holds: HeldScreens) {
    val held by holds.held.collectAsState()
    // Ticks only while a hold is still running: one past its time stays in the list until a player connects.
    val now by produceState(System.currentTimeMillis(), held) {
        while (held.any { it.untilMs > value }) {
            delay(TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    held.filter { it.untilMs > now }.forEach { screen ->
        HeldScreenCard(screen, onPairHere = { holds.pairHere(screen.playerId) })
    }
}

/** A removed screen this computer is not taking back yet, so that another can; [onPairHere] lets it back. */
@Composable
internal fun HeldScreenCard(held: HeldScreen, onPairHere: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().bibleListCard().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(Res.string.signage_held_screen, held.name.ifBlank { held.playerId }),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.signage_held_screen_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RingedGhostButton(onClick = onPairHere) { Text(stringResource(Res.string.signage_pair_here)) }
    }
}

private const val TICK_MS = 5_000L
