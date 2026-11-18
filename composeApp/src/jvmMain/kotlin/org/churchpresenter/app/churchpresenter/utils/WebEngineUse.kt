package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.utils.AppDataDir
import org.churchpresenter.web.presenter.CefManager
import java.io.File

/**
 * Whether this person has used the web engine before, kept as an empty file in the app's data folder.
 *
 * Starting the web engine holds the window for a fraction of a second to a few seconds, and JCEF runs
 * it on the event thread whichever thread asks. Started at launch it falls where nobody is waiting;
 * started by a web cue going live it falls in the middle of a service (CHURCH-PRESENTER-DESKTOP-1EQ).
 * But a launch should only pay for it, and for the browser's memory, if the engine will be used: so
 * the first client the engine ever makes leaves this mark, and a later launch starts the engine early
 * only where it finds it.
 */
internal object WebEngineUse {
    private const val MARKER = "web-engine-used"

    /**
     * Marks the app's data folder when the engine makes its first client of a run, on a thread of its
     * own: a client is made on the event thread, which must not touch the disk.
     */
    fun markOnFirstClient() {
        CefManager.onClientCreated = {
            Thread { mark(AppDataDir.resolve()) }.apply { isDaemon = true }.start()
        }
    }

    /**
     * Records that the web engine has been used. Never throws: a folder that cannot be written
     * costs only the early start.
     */
    fun mark(dataDir: File) {
        runCatching {
            dataDir.mkdirs()
            File(dataDir, MARKER).createNewFile()
        }
    }

    fun wasUsed(dataDir: File): Boolean = File(dataDir, MARKER).exists()
}
