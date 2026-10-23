package org.churchpresenter.signage.hub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.SignageJson
import org.churchpresenter.signage.core.sha256Hex
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** A screen this ChurchPresenter owns. Its token is kept only as a hash. */
@Serializable
data class PairedScreen(
    /** The player's own id, made once at install. */
    val id: String,
    val name: String,
    val tokenSha256: String,
    val platform: Platform,
    val agentVersion: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val pairedAtMs: Long,
)

@Serializable
private data class RegistryFile(val ownerId: String, val screens: List<PairedScreen> = emptyList())

/**
 * The screens this ChurchPresenter owns, and its own owner id, kept in [file]. Writes happen on
 * [Dispatchers.IO] in [scope], one at a time; the in-memory list is the truth in between.
 */
class ScreenRegistry(
    private val file: Path,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private val _screens = MutableStateFlow<List<PairedScreen>>(emptyList())
    val screens: StateFlow<List<PairedScreen>> = _screens.asStateFlow()

    /** Which ChurchPresenter this is, to its players: made on first use and kept. */
    @Volatile var ownerId: String = ""
        private set

    private val saving = Mutex()

    /** Reads [file], or starts a new owner id when there is none. */
    suspend fun load() = withContext(Dispatchers.IO) {
        val stored = try {
            if (Files.exists(file)) {
                SignageJson.decodeFromString(RegistryFile.serializer(), Files.readString(file))
            } else {
                null
            }
        } catch (e: IOException) {
            Log.warn(TAG, "Could not read the paired screens: ${e.message}")
            null
        } catch (e: SerializationException) {
            Log.warn(TAG, "Paired screens file is damaged: ${e.message}")
            null
        }
        ownerId = stored?.ownerId ?: UUID.randomUUID().toString()
        _screens.value = stored?.screens.orEmpty()
        if (stored == null) save()
    }

    fun screen(id: String): PairedScreen? = _screens.value.firstOrNull { it.id == id }

    /** The screen whose token is [token], compared in constant time. */
    fun byToken(token: String): PairedScreen? {
        val hash = sha256Hex(token.toByteArray()).toByteArray()
        return _screens.value.firstOrNull { MessageDigest.isEqual(it.tokenSha256.toByteArray(), hash) }
    }

    /** Pairs [player] (again, if it was paired before) under [name] and returns its new token. */
    fun pair(player: PendingScreen, name: String): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val screen = PairedScreen(
            id = player.playerId,
            name = screen(player.playerId)?.name ?: name,
            tokenSha256 = sha256Hex(token.toByteArray()),
            platform = player.platform,
            agentVersion = player.agentVersion,
            screenWidth = player.screenWidth,
            screenHeight = player.screenHeight,
            pairedAtMs = clock(),
        )
        _screens.update { list -> list.filterNot { it.id == screen.id } + screen }
        save()
        return token
    }

    /** Keeps what a paired player says about itself current. */
    fun refreshFrom(hello: PlayerMessage.Hello) {
        val old = screen(hello.playerId) ?: return
        val new = old.copy(
            platform = hello.platform,
            agentVersion = hello.agentVersion,
            screenWidth = hello.screenWidth,
            screenHeight = hello.screenHeight,
        )
        if (new != old) replace(new)
    }

    fun rename(id: String, name: String) {
        val old = screen(id) ?: return
        if (name.isNotBlank() && name != old.name) replace(old.copy(name = name.trim()))
    }

    fun remove(id: String) {
        _screens.update { list -> list.filterNot { it.id == id } }
        save()
    }

    private fun replace(screen: PairedScreen) {
        _screens.update { list -> list.map { if (it.id == screen.id) screen else it } }
        save()
    }

    /**
     * Writes the list as it is when the write runs, not when it was asked for: saves run one at a
     * time but not necessarily in order, and a list taken earlier could overwrite a newer one.
     */
    private fun save() {
        scope.launch(Dispatchers.IO) {
            saving.withLock {
                val snapshot = RegistryFile(ownerId, _screens.value)
                try {
                    Files.createDirectories(file.parent)
                    val next = file.resolveSibling("${file.fileName}.next")
                    Files.writeString(next, SignageJson.encodeToString(RegistryFile.serializer(), snapshot))
                    Files.move(next, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: IOException) {
                    Log.error(TAG, "Could not save the paired screens: ${e.message}")
                }
            }
        }
    }

    private companion object {
        const val TAG = "Signage"
        const val TOKEN_BYTES = 32
    }
}
