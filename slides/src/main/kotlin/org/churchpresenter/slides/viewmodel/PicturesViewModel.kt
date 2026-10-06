package org.churchpresenter.slides.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.sharedui.utils.isSystemArtifact
import org.churchpresenter.slides.SlidesOutput
import org.churchpresenter.slides.data.HiddenItemsStore
import org.churchpresenter.slides.data.firstVisibleIndex
import java.io.File
import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * The Pictures tab's state and what the operator does with it.
 *
 * The work is split across parts that share one [PicturesState]: thumbnails ([PictureThumbnails]),
 * hiding ([HiddenPictures]), a cue's playback ([PicturePlayback]), moving through the pictures
 * ([PictureNavigator]), going live ([PicturesLive]) and the folder watcher ([PictureFolderWatcher]).
 * Their public functions are this class's own, by delegation, so a caller sees one view model.
 */
class PicturesViewModel private constructor(
    appSettings: AppSettings?,
    private val parts: PicturesParts,
) : PictureNavigation by parts.navigator,
    PictureHiding by parts.hiding,
    PicturePlaybackControls by parts.playback,
    PicturesPresenting by parts.live {

    constructor(
        appSettings: AppSettings? = null,
        /**
         * Where every write to this class's snapshot state is made.
         *
         * `Dispatchers.Main` is the Swing event queue, which is the thread that advances the global
         * snapshot — in the app because that is where composition runs, and under
         * `runComposeUiTest` because `SkikoComposeUiTest` routes `setContent`/`render`/`closeScene`
         * through `SwingUtilities.invokeAndWait`. Confining the writes to it is what stops a write
         * from the folder watcher racing the composition; see [PictureFolderWatcher.start] and [PictureThumbnails].
         *
         * A parameter so a test can watch where the writes land. Note that `kotlinx-coroutines-test`
         * is on the test classpath, so this resolves to `TestMainDispatcher` delegating to the Swing
         * one — still the event queue. Nothing calls `Dispatchers.setMain` today; anything that did
         * would quietly take this confinement away.
         */
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
        /** Where hidden pictures are remembered -- a parameter so a test can keep them in a temp dir. */
        hiddenStore: HiddenItemsStore = HiddenItemsStore(),
    ) : this(appSettings, PicturesParts(appSettings, mainDispatcher, hiddenStore))

    private val state = parts.state
    private val scope = parts.scope
    private val defaultDirectory = appSettings?.pictureSettings?.storageDirectory ?: ""

    /** The thumbnail decoder, for a test that drives one decode directly. */
    internal val thumbnailing: PictureThumbnails get() = parts.thumbnails

    /** The folder watcher, for a test that feeds it events directly. */
    internal val watching: PictureFolderWatcher get() = parts.watcher

    val selectedFolder: File? get() = state.selectedFolder.value

    /**
     * What the tab shows for the current folder: the primary's path verbatim when this folder is
     * mirrored over Instance Link, the local absolute path otherwise. Null when nothing is selected.
     *
     * Read this rather than [selectedFolder] for anything the operator looks at. [selectedFolder]
     * stays a [File] because it is also an identity — `stableFileId` derives the folder id this
     * instance publishes to its own remote clients from it.
     */
    val selectedFolderDisplayPath: String?
        get() = state.selectedFolder.value?.let { folder ->
            state.remoteFolderPath.value?.takeIf { it.first == folder }?.second ?: folder.absolutePath
        }

    val images: List<File> get() = state.images
    val thumbnails: Map<File, ImageBitmap> get() = state.thumbnails

    /** Files whose thumbnail could not be decoded, against the reason; see [PicturesState]. */
    val thumbnailFailures: Map<File, String> get() = state.thumbnailFailures

    var selectedImageIndex: Int
        get() = state.selectedImageIndex.value
        set(value) { state.selectedImageIndex.value = value }

    var isPlaying: Boolean
        get() = state.isPlaying.value
        set(value) { state.isPlaying.value = value }

    var autoScrollInterval: Float
        get() = state.autoScrollInterval.value
        set(value) { state.autoScrollInterval.value = value }

    var isLooping: Boolean
        get() = state.isLooping.value
        set(value) { state.isLooping.value = value }

    var transitionDuration: Float
        get() = state.transitionDuration.value
        set(value) { state.transitionDuration.value = value }

    var animationType: AnimationType
        get() = state.animationType.value
        set(value) { state.animationType.value = value }

    val imageOrderVersion: Int get() = state.imageOrderVersion.value

    /**
     * The in-flight [loadPictureFromRemote] download, so [clearImages] can stop it.
     *
     * Without this, re-selecting the same mirrored picture item started a second loop over the same
     * cache directory while the first was still running, and both appended the identical file — a
     * duplicate `absolutePath` in the images, which is a fatal crash in the grid that keys on it.
     */
    private var remoteLoadJob: Job? = null

    init {
        val savedFolder = appSettings?.pictureSettings?.storageDirectory.orEmpty()
        if (savedFolder.isNotEmpty()) {
            val folder = File(savedFolder)
            if (folder.exists() && folder.isDirectory) {
                selectFolder(folder)
            }
        }
    }

    fun selectFolder(folder: File) {
        state.selectedFolder.value = folder
        clearImages() // also cancels the previous folder's watcher
        parts.hiding.loadFor(folder)
        loadImagesFromFolder(folder)
        state.selectedImageIndex.value = firstVisibleIndex(state.images.size, state.hiddenIndexes())
        parts.watcher.start(folder)
        parts.playback.applyPendingPlayback()
    }

    fun loadImagesFromFolder(folder: File) {
        if (!folder.exists() || !folder.isDirectory) {
            return
        }

        // Load images from folder
        val imageFiles = folder.listFiles { file ->
            file.isFile && file.extension.lowercase() in PICTURE_EXTENSIONS && !isSystemArtifact(file.name)
        }?.sortedBy { it.name } ?: emptyList()

        // Add only files not already present so a re-entrant/repeated load stays idempotent — a
        // duplicate path in the images would crash the LazyVerticalGrid keyed by absolutePath.
        synchronized(state.imagesLock) {
            imageFiles.forEach { state.addUniqueLocked(it) }
        }

        // Load thumbnails in background
        scope.launch {
            parts.thumbnails.reportThumbnailFailures(
                imageFiles.mapNotNull { file -> parts.thumbnails.decodeThumbnail(file) }
            )
        }
    }

    /**
     * Loads a picture folder from an Instance Link primary when [folderPath] doesn't resolve on this
     * machine (e.g. a mirrored schedule item whose folder lives on a network drive mounted
     * differently, or not mounted at all, here). Downloads each image's bytes via [fetchBytes] into
     * a cache dir keyed by [folderId] and populates the images with the cached files — same public
     * state contract as [selectFolder], so thumbnails, [syncWithPresenter], and navigation all work
     * unchanged afterward. [folderPath] is the primary's own path: it is kept verbatim in
     * [PicturesState.remoteFolderPath] for [selectedFolderDisplayPath] to show, and the [File] built from it is
     * an identity for [selectedFolder], never a path to read from.
     * [presenterManager] — when non-null, explicitly re-synced after every downloaded image (not
     * just once): images arrive one at a time here (unlike the synchronous local-folder path), and
     * PicturesTab's own reactive sync effect only restarts on selectedImageIndex changes, so
     * without this the presenter could be left showing nothing if pictures were already on air
     * (e.g. a second remote item clicked while one was already live) and the currently-selected
     * index's bytes hadn't arrived yet when that effect last ran.
     */
    fun loadPictureFromRemote(
        folderId: String,
        folderPath: String,
        imageCount: Int,
        presenterManager: SlidesOutput? = null,
        fetchBytes: suspend (index: Int) -> ByteArray?
    ) {
        clearImages()
        val displayFolder = File(folderPath)
        state.selectedFolder.value = displayFolder
        state.remoteFolderPath.value = displayFolder to folderPath
        parts.hiding.loadFor(displayFolder)
        val cacheDir = File(
            System.getProperty("user.home"),
            ".churchpresenter/instance-link/cache/picture-folders/$folderId"
        )
        cacheDir.mkdirs()
        remoteLoadJob = scope.launch {
            for (index in 0 until imageCount) {
                // Cancellation is cooperative and most of this loop is blocking I/O, so a
                // superseded download runs on unless it is asked to stop. Checking here stops it
                // fetching and writing for a folder nobody is looking at any more.
                if (!isActive) return@launch
                val cacheFile = File(cacheDir, "image_%04d.jpg".format(index))
                var cached = cacheFile.exists()
                if (!cached) {
                    val bytes = fetchBytes(index)
                    if (bytes != null) {
                        // Unique per attempt. Two loads of the same folder overlap whenever the
                        // operator re-selects a mirrored item, and a shared "image_0000.jpg.tmp"
                        // made them fight over one path: whichever renamed second found its temp
                        // file already moved, treated the download as failed and dropped the image,
                        // so the folder came up short a picture for no stated reason.
                        val tmp = File(cacheDir, "${cacheFile.name}.${UUID.randomUUID()}.tmp")
                        tmp.writeBytes(bytes)
                        cached = tmp.renameTo(cacheFile) || cacheFile.exists()
                        if (tmp.exists()) tmp.delete()
                    }
                }
                if (cached) {
                    val added = isActive && synchronized(state.imagesLock) { state.addUniqueLocked(cacheFile) }
                    if (added) {
                        parts.thumbnails.reportThumbnailFailures(
                            listOfNotNull(parts.thumbnails.decodeThumbnail(cacheFile))
                        )
                        presenterManager?.let { syncWithPresenter(it) }
                    }
                }
            }
        }
    }

    fun clearImages() {
        parts.watcher.cancel()
        remoteLoadJob?.cancel()
        remoteLoadJob = null
        // The one choke point for a mirrored folder's display path: selectFolder and
        // loadPictureFromRemote both come through here, so neither can inherit the other's.
        state.remoteFolderPath.value = null
        synchronized(state.imagesLock) { state.images.clear() }
        parts.thumbnails.clear()
        state.selectedImageIndex.value = 0
        state.isPlaying.value = false
    }

    /**
     * Opens a native folder chooser dialog and loads images from the selected folder.
     */
    fun openFolderChooser(dialogTitle: String, onFolderSelected: ((String) -> Unit) = {}) {
        scope.launch {
            val dir = FileChooser.platformInstance.chooseSingle(
                path = Path(defaultDirectory),
                title = dialogTitle,
                selectDirectory = true,
                filters = emptyList()
            )
            if (dir != null) {
                selectFolder(dir.toFile())
                onFolderSelected(dir.absolutePathString())
            }
        }
    }

    fun dispose() {
        parts.watcher.cancel()
        scope.cancel()
    }
}

/** The parts of one [PicturesViewModel], built together so they share one state and one scope. */
internal class PicturesParts(
    appSettings: AppSettings?,
    mainDispatcher: CoroutineDispatcher,
    hiddenStore: HiddenItemsStore,
) {
    val state = PicturesState(appSettings)
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val thumbnails = PictureThumbnails(state, mainDispatcher)
    val hiding = HiddenPictures(state, hiddenStore)
    val playback = PicturePlayback(state)
    val navigator = PictureNavigator(state, playback)
    val live = PicturesLive(state)
    val watcher = PictureFolderWatcher(state, thumbnails, mainDispatcher, scope)
}
