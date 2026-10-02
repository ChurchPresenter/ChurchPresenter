package org.churchpresenter.sharedui.testing

import java.io.File
import java.nio.file.Files

/**
 * Points a recent-files singleton at a throwaway directory for one test, and puts it back.
 *
 * The three recent-files objects — `RecentPictureFolders`, `RecentMediaFiles` and
 * `RecentPresentationFiles` — are JVM-wide singletons that resolve their JSON paths once, at class
 * init. A test that exercises `add`/`togglePin`/`clear` for real has to repoint them, and **has to
 * put them back**: whatever the last test left behind is what every later test in the JVM sees.
 *
 * That restore used to be hand-written per test class, in three near-identical twenty-line blocks.
 * This is the single implementation, so the restore is structural rather than something each author
 * has to remember. It is also why the seam is permitted at all — see `AGENT.md`'s note on mutable
 * singleton state.
 *
 * **This is about isolation, not safety.** The Gradle test config already points `user.home` at
 * `build/test-home` for the whole JVM (`composeApp/build.gradle.kts`), so no test can reach the
 * developer's real `~/.churchpresenter` either way. What this buys is a *fresh* file per test
 * instead of one shared across a class, so tests cannot see each other's writes.
 *
 * Usage — two lines of lifecycle per test class:
 * ```
 * private val swap = RecentFilesSwap(
 *     readPaths = { RecentPictureFolders.file to RecentPictureFolders.pinnedFile },
 *     writePaths = { f, p -> RecentPictureFolders.file = f; RecentPictureFolders.pinnedFile = p },
 *     entries = RecentPictureFolders.folders,
 *     pinned = RecentPictureFolders.pinned,
 *     prefix = "cp-recent-picture-folders",
 * )
 *
 * @BeforeTest fun setUp() = swap.install()
 * @AfterTest fun tearDown() = swap.restore()
 * ```
 *
 * [entries] and [pinned] are the singleton's own live lists, mutated in place — they are
 * `mutableStateListOf`, so replacing them is not an option and clearing/refilling is the only way
 * to hand them back as they were.
 */
class RecentFilesSwap(
    private val readPaths: () -> Pair<File, File>,
    private val writePaths: (File, File) -> Unit,
    private val entries: MutableList<String>,
    private val pinned: MutableList<String>,
    private val prefix: String,
) {
    private var tempDir: File? = null
    private var savedRecentPath: File? = null
    private var savedPinnedPath: File? = null
    private var savedEntries: List<String> = emptyList()
    private var savedPinned: List<String> = emptyList()

    /** The temp file the singleton's recent list is currently written to. */
    val recentFile: File get() = File(requireNotNull(tempDir) { "install() first" }, "recent.json")

    /** The temp file the singleton's pinned list is currently written to. */
    val pinnedFile: File get() = File(requireNotNull(tempDir) { "install() first" }, "pinned.json")

    fun install() {
        val dir = Files.createTempDirectory(prefix).toFile()
        tempDir = dir

        val (recent, pinnedPath) = readPaths()
        savedRecentPath = recent
        savedPinnedPath = pinnedPath
        savedEntries = entries.toList()
        savedPinned = pinned.toList()

        writePaths(recentFile, pinnedFile)
        entries.clear()
        pinned.clear()
    }

    fun restore() {
        savedRecentPath?.let { recent ->
            writePaths(recent, requireNotNull(savedPinnedPath))
        }
        entries.clear()
        entries.addAll(savedEntries)
        pinned.clear()
        pinned.addAll(savedPinned)

        tempDir?.deleteRecursively()
        tempDir = null
        savedRecentPath = null
        savedPinnedPath = null
    }
}
