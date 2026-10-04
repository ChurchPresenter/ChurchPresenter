package org.churchpresenter.canvas

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.io.writeTextAtomically
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneAlternateLayout
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.sharedui.utils.presenterScreenBounds
import java.io.File
import java.util.UUID

/**
 * The smallest and largest side a scene's canvas may have -- no zero, no 1-pixel sliver, nothing
 * past 8K. The same bounds a profile's custom preview shape uses.
 */
internal val CANVAS_SIDE_RANGE = 16..7680

class SceneViewModel {
    private val appDataDir = File(System.getProperty("user.home"), ".churchpresenter")
    private val scenesFile = File(appDataDir, "scenes.json")

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    internal val _scenes = mutableStateListOf<Scene>()
    val scenes: List<Scene> get() = _scenes

    internal val _currentSceneId = mutableStateOf<String?>(null)
    val currentSceneId: State<String?> = _currentSceneId

    val currentScene: Scene?
        get() = _scenes.find { it.id == _currentSceneId.value }

    internal val _selectedSourceId = mutableStateOf<String?>(null)
    val selectedSourceId: State<String?> = _selectedSourceId

    val selectedSource: SceneSource?
        get() = currentScene?.sources?.find { it.id == _selectedSourceId.value }

    init {
        loadScenes()
        if (_scenes.isNotEmpty() && _currentSceneId.value == null) {
            _currentSceneId.value = _scenes.first().id
        }
    }

    // --- Scene operations ---

    fun addScene(name: String = "Scene"): Scene {
        val bounds = presenterScreenBounds()
        val scene = Scene(
            name = name,
            canvasWidth = if (bounds.width > 0) bounds.width else 1920,
            canvasHeight = if (bounds.height > 0) bounds.height else 1080
        )
        _scenes.add(scene)
        _currentSceneId.value = scene.id
        _selectedSourceId.value = null
        saveScenes()
        return scene
    }

    /**
     * Sets [sceneId]'s canvas to [width]×[height] -- the current scene unless another is named, so
     * every row of the scene list can be resized, not only the one being edited.
     *
     * Layers are fractions of the canvas, so they keep their relative places and stretch with it.
     * Each side is kept in [CANVAS_SIDE_RANGE].
     */
    fun updateCanvasSize(width: Int, height: Int, sceneId: String? = _currentSceneId.value) {
        val id = sceneId ?: return
        val index = _scenes.indexOfFirst { it.id == id }
        if (index < 0) return
        val w = width.coerceIn(CANVAS_SIDE_RANGE)
        val h = height.coerceIn(CANVAS_SIDE_RANGE)
        val scene = _scenes[index]
        if (scene.canvasWidth == w && scene.canvasHeight == h) return
        _scenes[index] = scene.copy(canvasWidth = w, canvasHeight = h)
        saveScenes()
    }

    fun removeScene(sceneId: String) {
        val index = _scenes.indexOfFirst { it.id == sceneId }
        if (index >= 0) {
            _scenes.removeAt(index)
            if (_currentSceneId.value == sceneId) {
                _currentSceneId.value = _scenes.firstOrNull()?.id
                _selectedSourceId.value = null
            }
            saveScenes()
        }
    }

    fun selectScene(sceneId: String) {
        _currentSceneId.value = sceneId
        _selectedSourceId.value = null
    }

    fun renameScene(sceneId: String, newName: String) {
        updateScene(sceneId) { it.copy(name = newName) }
    }

    /**
     * Adds a copy of [sceneId] named [name] and makes it current. Every source gets a new id: caches
     * such as the shared browser are keyed by source id, so a copy that kept them would share its
     * layers with the original. A second layout's positions move over to the new ids.
     */
    fun duplicateScene(sceneId: String, name: String): Scene? {
        val original = _scenes.find { it.id == sceneId } ?: return null
        val newIds = original.sources.associate { it.id to UUID.randomUUID().toString() }
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            sources = original.sources.map { it.withId(newIds.getValue(it.id)) },
            alternate = original.alternate?.let { layout ->
                val remapped = layout.transforms.mapNotNull { (id, t) -> newIds[id]?.let { it to t } }
                layout.copy(transforms = remapped.toMap())
            }
        )
        _scenes.add(duplicate)
        _currentSceneId.value = duplicate.id
        _selectedSourceId.value = null
        saveScenes()
        return duplicate
    }

    // --- Source operations ---

    /**
     * Gives [sceneId] a second layout for screens of the other orientation, or takes it away. A new
     * one starts empty, so every layer begins at the same fractions of the turned canvas.
     */
    fun setDualLayout(sceneId: String, enabled: Boolean) {
        updateScene(sceneId) { scene ->
            when {
                enabled && scene.alternate == null -> scene.copy(alternate = SceneAlternateLayout())
                !enabled -> scene.copy(alternate = null)
                else -> scene
            }
        }
    }

    // --- Persistence ---

    private fun loadScenes() {
        try {
            if (scenesFile.exists()) {
                val json = scenesFile.readText()
                val loaded = jsonFormat.decodeFromString<List<Scene>>(json)
                _scenes.clear()
                _scenes.addAll(loaded)
            }
        } catch (_: Exception) {
            // Silently handle parse errors
        }
    }

    fun saveScenes() {
        try {
            appDataDir.mkdirs()
            scenesFile.writeTextAtomically(jsonFormat.encodeToString(_scenes.toList()))
        } catch (_: Exception) {
            // Silently handle write errors
        }
    }

    internal fun updateScene(sceneId: String, updater: (Scene) -> Scene) {
        val index = _scenes.indexOfFirst { it.id == sceneId }
        if (index >= 0) {
            _scenes[index] = updater(_scenes[index])
            saveScenes()
        }
    }
}
