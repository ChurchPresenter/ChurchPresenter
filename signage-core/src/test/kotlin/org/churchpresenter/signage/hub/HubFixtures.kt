package org.churchpresenter.signage.hub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage

/** A player's hello, as one waiting to be paired (with [code]) or one that is paired (without). */
fun hello(
    id: String = "player-1",
    code: String? = "4821",
    version: String = "26.0.8",
    manifestVersion: String? = null,
    features: List<String> = listOf(Features.WIPE, Features.PREVIEW, Features.UPDATE),
) = PlayerMessage.Hello(
    playerId = id,
    agentVersion = version,
    protocolMin = 1,
    protocolMax = 1,
    manifestVersion = manifestVersion,
    screenWidth = 1920,
    screenHeight = 1080,
    platform = Platform.LINUX_X64,
    pairingCode = code,
    features = features,
)

fun pending(id: String = "player-1", version: String = "26.0.8") = PendingScreen(
    playerId = id,
    code = "4821",
    platform = Platform.LINUX_X64,
    agentVersion = version,
    screenWidth = 1920,
    screenHeight = 1080,
    address = "192.168.1.30",
)

/** A scope whose saves a test can wait for: [settle] joins everything launched in it so far. */
class SaveScope : CoroutineScope by CoroutineScope(Job()) {
    suspend fun settle() {
        coroutineContext.job.children.toList().forEach { it.join() }
    }
}
