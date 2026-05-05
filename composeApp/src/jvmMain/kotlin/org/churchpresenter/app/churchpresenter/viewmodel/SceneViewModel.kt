package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.models.Scene
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.models.SourceTransform
import java.io.File
import java.util.UUID

class SceneViewModel {
    private val appDataDir = File(System.getProperty("user.home"), ".churchpresenter")
    private val scenesFile = File(appDataDir, "scenes.json")

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _scenes = mutableStateListOf<Scene>()
    val scenes: List<Scene> get() = _scenes

    private val _currentSceneId = mutableStateOf<String?>(null)
    val currentSceneId: State<String?> = _currentSceneId

    val currentScene: Scene?
        get() = _scenes.find { it.id == _currentSceneId.value }

    private val _selectedSourceId = mutableStateOf<String?>(null)
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
        val bounds = org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds()
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

    fun updateCanvasSize(width: Int, height: Int) {
        val id = _currentSceneId.value ?: return
        val index = _scenes.indexOfFirst { it.id == id }
        if (index >= 0) {
            _scenes[index] = _scenes[index].copy(canvasWidth = width, canvasHeight = height)
            saveScenes()
        }
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

    fun duplicateScene(sceneId: String): Scene? {
        val original = _scenes.find { it.id == sceneId } ?: return null
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (copy)"
        )
        _scenes.add(duplicate)
        _currentSceneId.value = duplicate.id
        saveScenes()
        return duplicate
    }

    // --- Source operations ---

    fun addSource(source: SceneSource) {
        val scene = currentScene ?: return
        updateScene(scene.id) { it.copy(sources = it.sources + source) }
        _selectedSourceId.value = source.id
    }

    fun removeSource(sourceId: String) {
        val scene = currentScene ?: return
        updateScene(scene.id) { it.copy(sources = it.sources.filter { s -> s.id != sourceId }) }
        if (_selectedSourceId.value == sourceId) {
            _selectedSourceId.value = null
        }
    }

    fun selectSource(sourceId: String?) {
        _selectedSourceId.value = sourceId
    }

    fun updateSource(sourceId: String, updater: (SceneSource) -> SceneSource) {
        val scene = currentScene ?: return
        updateScene(scene.id) {
            it.copy(sources = it.sources.map { s ->
                if (s.id == sourceId) updater(s) else s
            })
        }
    }

    fun moveSourceUp(sourceId: String) {
        val scene = currentScene ?: return
        val sources = scene.sources.toMutableList()
        val index = sources.indexOfFirst { it.id == sourceId }
        if (index > 0) {
            val temp = sources[index]
            sources[index] = sources[index - 1]
            sources[index - 1] = temp
            updateScene(scene.id) { it.copy(sources = sources) }
        }
    }

    fun moveSourceDown(sourceId: String) {
        val scene = currentScene ?: return
        val sources = scene.sources.toMutableList()
        val index = sources.indexOfFirst { it.id == sourceId }
        if (index >= 0 && index < sources.size - 1) {
            val temp = sources[index]
            sources[index] = sources[index + 1]
            sources[index + 1] = temp
            updateScene(scene.id) { it.copy(sources = sources) }
        }
    }

    fun toggleSourceVisibility(sourceId: String) {
        updateSource(sourceId) { source ->
            when (source) {
                is SceneSource.ImageSource -> source.copy(visible = !source.visible)
                is SceneSource.TextSource -> source.copy(visible = !source.visible)
                is SceneSource.ColorSource -> source.copy(visible = !source.visible)
                is SceneSource.VideoSource -> source.copy(visible = !source.visible)
                is SceneSource.BrowserSource -> source.copy(visible = !source.visible)
                is SceneSource.ShapeSource -> source.copy(visible = !source.visible)
                is SceneSource.ClockSource -> source.copy(visible = !source.visible)
                is SceneSource.QRCodeSource -> source.copy(visible = !source.visible)
                is SceneSource.CameraSource -> source.copy(visible = !source.visible)
                is SceneSource.ScreenCaptureSource -> source.copy(visible = !source.visible)
                is SceneSource.BibleSource -> source.copy(visible = !source.visible)
            }
        }
    }

    fun toggleSourceLock(sourceId: String) {
        updateSource(sourceId) { source ->
            when (source) {
                is SceneSource.ImageSource -> source.copy(locked = !source.locked)
                is SceneSource.TextSource -> source.copy(locked = !source.locked)
                is SceneSource.ColorSource -> source.copy(locked = !source.locked)
                is SceneSource.VideoSource -> source.copy(locked = !source.locked)
                is SceneSource.BrowserSource -> source.copy(locked = !source.locked)
                is SceneSource.ShapeSource -> source.copy(locked = !source.locked)
                is SceneSource.ClockSource -> source.copy(locked = !source.locked)
                is SceneSource.QRCodeSource -> source.copy(locked = !source.locked)
                is SceneSource.CameraSource -> source.copy(locked = !source.locked)
                is SceneSource.ScreenCaptureSource -> source.copy(locked = !source.locked)
                is SceneSource.BibleSource -> source.copy(locked = !source.locked)
            }
        }
    }

    fun updateTransform(sourceId: String, transform: SourceTransform) {
        updateSource(sourceId) { source ->
            when (source) {
                is SceneSource.ImageSource -> source.copy(transform = transform)
                is SceneSource.TextSource -> source.copy(transform = transform)
                is SceneSource.ColorSource -> source.copy(transform = transform)
                is SceneSource.VideoSource -> source.copy(transform = transform)
                is SceneSource.BrowserSource -> source.copy(transform = transform)
                is SceneSource.ShapeSource -> source.copy(transform = transform)
                is SceneSource.ClockSource -> source.copy(transform = transform)
                is SceneSource.QRCodeSource -> source.copy(transform = transform)
                is SceneSource.CameraSource -> source.copy(transform = transform)
                is SceneSource.ScreenCaptureSource -> source.copy(transform = transform)
                is SceneSource.BibleSource -> source.copy(transform = transform)
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
            scenesFile.writeText(jsonFormat.encodeToString(_scenes.toList()))
        } catch (_: Exception) {
            // Silently handle write errors
        }
    }

    private fun updateScene(sceneId: String, updater: (Scene) -> Scene) {
        val index = _scenes.indexOfFirst { it.id == sceneId }
        if (index >= 0) {
            _scenes[index] = updater(_scenes[index])
            saveScenes()
        }
    }
}
