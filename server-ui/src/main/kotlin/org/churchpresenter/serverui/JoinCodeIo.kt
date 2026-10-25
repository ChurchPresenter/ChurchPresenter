package org.churchpresenter.serverui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.server.CalendarJoin
import org.churchpresenter.sharedui.filechooser.SwingFileChooser
import org.churchpresenter.sharedui.utils.SystemClipboard
import org.churchpresenter.sharedui.utils.UrlOpener
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter

/** What the join dialogs do outside their window: the clipboard, a file chooser, the mail app. */
interface JoinCodeIo {
    fun copy(text: String): Boolean

    fun paste(): String?

    /** Asks where, writes [join] there; the folder it went to, or null when nothing was written. */
    suspend fun save(join: CalendarJoin, title: String, filter: FileNameExtensionFilter): String?

    /** Asks which file, reads it; its text and the file, or null when none was read. */
    suspend fun open(title: String, filter: FileNameExtensionFilter): Pair<String, File>?

    fun email(url: String)
}

/** The real clipboard, file choosers and mail app. */
object SystemJoinCodeIo : JoinCodeIo {
    override fun copy(text: String): Boolean = SystemClipboard.copy(text)

    override fun paste(): String? = SystemClipboard.paste()

    override suspend fun save(join: CalendarJoin, title: String, filter: FileNameExtensionFilter): String? {
        val path = SwingFileChooser.save(null, CalendarJoin.FILE_NAME, listOf(filter), title) ?: return null
        return writeJoin(path.toFile(), join)
    }

    override suspend fun open(title: String, filter: FileNameExtensionFilter): Pair<String, File>? {
        val path = SwingFileChooser.chooseSingle(null, listOf(filter), title, selectDirectory = false) ?: return null
        return readJoin(path.toFile())
    }

    override fun email(url: String) {
        UrlOpener.open(url)
    }
}

/** Writes [join] to [file]; the folder it went to, or null when it could not be written. */
internal suspend fun writeJoin(file: File, join: CalendarJoin): String? =
    withContext(Dispatchers.IO) { runCatching { file.writeText(join.toJson()) } }
        .map { file.parent.orEmpty() }
        .getOrNull()

/** [file]'s text and the file, or null when it could not be read. */
internal suspend fun readJoin(file: File): Pair<String, File>? =
    withContext(Dispatchers.IO) { runCatching { file.readText() } }.getOrNull()?.let { it to file }
