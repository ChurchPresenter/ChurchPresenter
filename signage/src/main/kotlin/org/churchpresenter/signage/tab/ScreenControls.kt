package org.churchpresenter.signage.tab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.hub.LatestRelease
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.signage.hub.SignageHub

/** What the Signage tab does to screens that needs nothing from the signage folder. */
class ScreenControls internal constructor(private val hub: SignageHub, private val scope: CoroutineScope) {
    /** The newest signed player release found on GitHub, offered to screens that run an older one. */
    val latestRelease: StateFlow<LatestRelease?> = hub.updates.latest

    fun approve(playerId: String, name: String) {
        if (!hub.approve(playerId, name)) Log.info(TAG, "Screen $playerId stopped waiting before it was approved")
    }

    fun reject(playerId: String) = hub.reject(playerId)

    /** Renames [screenId]; its manifest follows, since the controller republishes on any change of screens. */
    fun rename(screenId: String, name: String) = hub.registry.rename(screenId, name)

    /** Shows the screen's name full screen for a few seconds; false when it is not connected. */
    fun identify(screenId: String): Boolean = hub.links.send(screenId, ServerMessage.Identify())

    /**
     * Has [screenId] delete every picture and video it holds; it stays paired. Send it once its plan
     * is [ScreenPlan.NOTHING], or the manifest it fetches straight after fills it again.
     */
    fun wipe(screenId: String): Boolean = hub.links.send(screenId, ServerMessage.Wipe)

    /** Looks for a newer player release. */
    fun checkForUpdates() {
        scope.launch { hub.updates.check() }
    }

    /** The release [screen] can be updated to now, or null. */
    fun offerFor(screen: PairedScreen, link: ScreenLink?): LatestRelease? =
        link?.let { hub.updates.offerFor(screen, it.features) }

    /** Downloads the latest player build and sends it to [screenId]; its card follows the steps. */
    fun update(screenId: String) {
        scope.launch { hub.update(screenId) }
    }

    /**
     * Asks every connected screen that can for a picture of what it shows: [large] (a screen whose
     * picture is open big) for a sharper one, the rest for a thumbnail.
     */
    fun requestPreviews(large: String? = null) {
        hub.links.links.value.forEach { (id, link) ->
            if (Features.PREVIEW in link.features) {
                val width = if (id == large) LARGE_PREVIEW_WIDTH else ServerMessage.PreviewRequest.DEFAULT_PREVIEW_WIDTH
                hub.links.send(id, ServerMessage.PreviewRequest(width))
            }
        }
    }

    private companion object {
        const val TAG = "Signage"
        const val LARGE_PREVIEW_WIDTH = 1280
    }
}
