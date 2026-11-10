package org.churchpresenter.signage.hub

import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Manifest
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.ScreenOverride
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.core.SignageJson
import org.churchpresenter.signage.core.UpdateState
import org.churchpresenter.signage.core.version
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** A screen's manifest as last written, with the file each of its hashes is served from. */
class PublishedManifest(val manifest: Manifest, val version: String, val files: Map<String, Path>)

/**
 * Everything ChurchPresenter knows about its signage screens, shared by the companion server (which
 * talks to the players) and the Signage tab (which decides what they show). The server only ever
 * reads manifests from here; the tab only ever writes them.
 *
 * @param dir where the paired screens are kept.
 * @param ownerName how the players name this ChurchPresenter: the computer's name.
 */
class SignageHub(
    dir: Path,
    val ownerName: String,
    scope: CoroutineScope,
    clock: () -> Long = System::currentTimeMillis,
    val updates: AgentUpdates = AgentUpdates(dir.resolve(BUILDS_DIR)),
) {
    val registry = ScreenRegistry(dir.resolve(REGISTRY_FILE), scope, clock)
    val pairing = PairingQueue(clock)
    val links = ScreenLinks(clock)
    private val published = ConcurrentHashMap<String, PublishedManifest>()

    /** Pairs a waiting player under [name]; false when it stopped waiting. */
    fun approve(playerId: String, name: String): Boolean {
        val player = pairing.pending.value.firstOrNull { it.playerId == playerId } ?: return false
        val token = registry.pair(player, name)
        if (pairing.decide(playerId, PairingDecision.Approved(token))) return true
        registry.remove(playerId)
        return false
    }

    fun reject(playerId: String) {
        pairing.decide(playerId, PairingDecision.Rejected)
    }

    /**
     * Removes [id]: a player that can reset itself is told to (and told [ServerMessage.Released] if it
     * cannot), and one that is away is refused with 401 when it next connects. It is then held back
     * for a few minutes ([PairingQueue.hold]), so a player that looks again finds another ChurchPresenter.
     */
    fun revoke(id: String) {
        val screen = registry.screen(id)
        val link = links.links.value[id]
        val resets = Features.FACTORY_RESET in link?.features.orEmpty()
        links.send(id, if (resets) ServerMessage.FactoryReset else ServerMessage.Released)
        pairing.hold(id, screen?.name.orEmpty(), link?.address ?: screen?.lastAddress)
        links.forget(id)
        published.remove(id)
        registry.remove(id)
    }

    /**
     * A player connected from [address] with [token], which names no paired screen. When it is a
     * screen removed from here while it was away, that screen is held back from now ([PairingQueue.hold]),
     * as the 401 it is about to get makes it look for a ChurchPresenter again. True when it was one.
     */
    fun refusedToken(token: String, address: String): Boolean {
        val removed = registry.removed.take(token) ?: return false
        pairing.hold(removed.id, removed.name, address)
        Log.info(TAG, "Removed screen ${removed.id} came back at $address; holding it back")
        return true
    }

    fun setOverride(id: String, override: ScreenOverride?) = links.setOverride(id, override)

    /**
     * Makes [manifest] what [id] plays, served from [files], and tells the screen if it is listening.
     * Publishing the same content again changes nothing.
     */
    fun publish(id: String, manifest: Manifest, files: Map<String, Path>) {
        val version = manifest.version()
        if (published[id]?.version == version) return
        published[id] = PublishedManifest(manifest, version, files)
        links.send(id, ServerMessage.ManifestChanged(version))
    }

    fun published(id: String): PublishedManifest? = published[id]

    /**
     * Updates [id] to the latest release: downloads and checks its build here, then offers it to the
     * screen, which checks it again and reports each step. False when there is nothing to offer or
     * the screen is not connected; a failed download shows on the screen's card.
     */
    suspend fun update(id: String): Boolean {
        val screen = registry.screen(id) ?: return false
        val link = links.links.value[id] ?: return false
        val latest = updates.offerFor(screen, link.features) ?: return false
        val version = latest.release.version
        links.reported(id, PlayerMessage.UpdateProgress(version, UpdateState.DOWNLOADING))
        val build = try {
            updates.build(screen.platform)
        } catch (e: IOException) {
            links.reported(id, PlayerMessage.UpdateProgress(version, UpdateState.FAILED, e.message))
            return false
        }
        val offer = ServerMessage.UpdateAvailable(
            agentVersion = version,
            protocolMin = latest.release.protocolMin,
            protocolMax = latest.release.protocolMax,
            sha256 = latest.release.assets.getValue(screen.platform).sha256,
            path = "$AGENT_PATH/${platformName(screen.platform)}",
            release = latest.text,
            releaseSignature = latest.signature,
        )
        Log.info(TAG, "Offering player $version (${build.fileName}) to screen $id")
        return links.send(id, offer)
    }

    /** What a paired player is sent as it connects: a newer manifest, and a switch made while it was away. */
    fun greeting(hello: PlayerMessage.Hello): List<ServerMessage> = listOfNotNull(
        published[hello.playerId]?.version?.takeIf { it != hello.manifestVersion }?.let(ServerMessage::ManifestChanged),
        links.takeHeldOverride(hello.playerId),
    )

    companion object {
        /** Where a screen fetches the build it is offered, followed by the platform's wire name. */
        const val AGENT_PATH = "/api/signage/agent"

        /** [platform] as the wire writes it: `linux_arm64`. */
        fun platformName(platform: Platform): String =
            SignageJson.encodeToString(Platform.serializer(), platform).trim('"')

        /** The hub's folder under ChurchPresenter's app data. */
        const val DIR = "signage"

        /**
         * How many screens the hub in [dir] has paired, by the platform's wire name. Read from disk
         * without starting a hub, for the launch ping, so it blocks: call it off the event thread.
         */
        fun pairedScreensByPlatform(dir: Path): Map<String, Int> =
            countByPlatform(dir.resolve(REGISTRY_FILE)).mapKeys { platformName(it.key) }

        /** The paired screens and the owner id, in the signage folder every sharing computer reads. */
        const val REGISTRY_FILE = "screens.json"
        private const val BUILDS_DIR = "player-builds"
        private const val TAG = "Signage"
    }
}
