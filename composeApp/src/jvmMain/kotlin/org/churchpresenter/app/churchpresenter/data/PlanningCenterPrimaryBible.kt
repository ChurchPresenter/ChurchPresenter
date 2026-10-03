package org.churchpresenter.app.churchpresenter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.bible.Bible
import org.churchpresenter.planningcenter.ui.PlanningCenterScripture
import org.churchpresenter.settings.SettingsManager
import java.io.File

/**
 * The primary Bible, loaded once per Planning Center import (not shared with BibleViewModel's own
 * instance -- a plain standalone load, as StatisticsManager does for its CCLI lookup), and the
 * scripture references found against it.
 */
internal class PlanningCenterPrimaryBible {
    private var cachedPrimaryBible: Bible? = null
    private var triedLoadingPrimaryBible = false

    private suspend fun primaryBible(): Bible? = withContext(Dispatchers.IO) {
        if (triedLoadingPrimaryBible) return@withContext cachedPrimaryBible
        triedLoadingPrimaryBible = true
        try {
            val bibleSettings = SettingsManager().loadSettings().bibleSettings
            // The navigation bible, read from the stack rather than from the legacy field it
            // mirrors -- the same thing today, but the stack is what is actually maintained.
            val fileName = bibleSettings.translationList().firstOrNull()?.fileName.orEmpty()
            val storageDir = bibleSettings.storageDirectory
            if (fileName.isBlank() || storageDir.isBlank()) return@withContext null
            val path = File(storageDir, fileName).absolutePath
            cachedPrimaryBible = Bible().apply { loadFromSpb(path) }
            cachedPrimaryBible
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Scans [text] for scripture references (one per line, e.g. "Psalm 23:1-6") and resolves
     * them against the primary Bible. Empty if the primary Bible isn't loaded/available or no
     * reference is recognized.
     */
    suspend fun detect(text: String): List<PlanningCenterScripture> {
        val bible = primaryBible() ?: return emptyList()
        val refs = PlanningCenterScriptureDetector.detectReferences(text, bible)
        return refs.mapNotNull { PlanningCenterScriptureDetector.resolveVerses(it, bible) }
    }
}
