package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.PlanningCenterClient
import org.churchpresenter.app.churchpresenter.data.PlanningCenterScriptureDetector
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.data.SongFileParser
import org.churchpresenter.app.churchpresenter.data.SongItem
import presentation.engine.LoadResult
import presentation.engine.PresentationLoader
import java.io.File

/**
 * Owns Planning Center Services API calls and per-item import state for
 * [org.churchpresenter.app.churchpresenter.dialogs.PlanningCenterImportDialog]. Created and used
 * only inside that dialog (AGENT.md ViewModel-ownership rule) — it never receives or exposes
 * another tab's ViewModel. Song catalog reads/writes go straight through [SongFileParser] (the
 * same technique already used for the CCLI report lookup in `StatisticsManager`), mirroring
 * exactly what `SongsViewModel.createSong` does on disk — the live Songs tab picks up the new
 * file via its existing folder watcher, no direct coupling needed.
 */
class PlanningCenterImportViewModel(
    initialAccessToken: String,
    initialRefreshToken: String,
    initialExpiresAtEpochMs: Long,
    initialServiceTypeId: String,
    private val importSongbookName: String,
    private val onTokensRefreshed: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long) -> Unit
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var accessToken = initialAccessToken
    private var refreshToken = initialRefreshToken
    private var expiresAtEpochMs = initialExpiresAtEpochMs

    data class ImportPlanItem(
        val pco: PlanningCenterClient.PlanItem,
        val matchedSongId: String? = null,
        val selected: Boolean = true
    )

    var serviceTypes by mutableStateOf<List<PlanningCenterClient.ServiceType>>(emptyList())
        private set
    var selectedServiceTypeId by mutableStateOf(initialServiceTypeId)
        private set
    var plans by mutableStateOf<List<PlanningCenterClient.Plan>>(emptyList())
        private set
    var selectedPlanId by mutableStateOf<String?>(null)
        private set
    var planItems by mutableStateOf<List<ImportPlanItem>>(emptyList())
        private set
    var isLoadingServiceTypes by mutableStateOf(false)
        private set
    var isLoadingPlans by mutableStateOf(false)
        private set
    var isLoadingItems by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private fun currentSongCatalog(): List<SongItem> {
        val storageDir = SettingsManager().loadSettings().songSettings.storageDirectory
        if (storageDir.isBlank()) return emptyList()
        val cached = SongFileParser.loadCachedSongMap(storageDir)
        return SongFileParser().loadSongsFromDirectory(storageDir, cached).map { it.song }
    }

    // Loaded once per dialog session (not shared with BibleViewModel's own instance — plain
    // standalone Bible load, same pattern StatisticsManager uses for its CCLI lookup) and reused
    // across every scripture-reference detection call in this import batch.
    private var cachedPrimaryBible: Bible? = null
    private var triedLoadingPrimaryBible = false

    private suspend fun primaryBible(): Bible? = withContext(Dispatchers.IO) {
        if (triedLoadingPrimaryBible) return@withContext cachedPrimaryBible
        triedLoadingPrimaryBible = true
        try {
            val bibleSettings = SettingsManager().loadSettings().bibleSettings
            val fileName = bibleSettings.primaryBible
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
    suspend fun detectScriptureReferences(text: String): List<PlanningCenterScriptureDetector.ResolvedVerses> {
        val bible = primaryBible() ?: return emptyList()
        val refs = PlanningCenterScriptureDetector.detectReferences(text, bible)
        return refs.mapNotNull { PlanningCenterScriptureDetector.resolveVerses(it, bible) }
    }

    private suspend fun ensureValidToken(): Boolean {
        if (accessToken.isBlank()) return false
        if (System.currentTimeMillis() < expiresAtEpochMs - 60_000) return true
        if (refreshToken.isBlank()) return false
        return when (
            val outcome = PlanningCenterClient.refreshAccessToken(
                BuildConfig.PLANNING_CENTER_CLIENT_ID,
                BuildConfig.PLANNING_CENTER_CLIENT_SECRET,
                refreshToken
            )
        ) {
            is PlanningCenterClient.TokenOutcome.Success -> {
                accessToken = outcome.tokens.accessToken
                refreshToken = outcome.tokens.refreshToken
                expiresAtEpochMs = outcome.tokens.expiresAtEpochMs
                onTokensRefreshed(accessToken, refreshToken, expiresAtEpochMs)
                true
            }
            else -> false
        }
    }

    fun loadServiceTypes() {
        viewModelScope.launch {
            isLoadingServiceTypes = true
            errorMessage = null
            if (!ensureValidToken()) {
                errorMessage = "Not connected to Planning Center"
                isLoadingServiceTypes = false
                return@launch
            }
            when (val outcome = PlanningCenterClient.listServiceTypes(accessToken)) {
                is PlanningCenterClient.ServiceTypesOutcome.Success -> {
                    serviceTypes = outcome.serviceTypes
                    if (selectedServiceTypeId.isBlank()) {
                        selectedServiceTypeId = outcome.serviceTypes.firstOrNull()?.id ?: ""
                    }
                    if (selectedServiceTypeId.isNotBlank()) loadPlans(selectedServiceTypeId)
                }
                PlanningCenterClient.ServiceTypesOutcome.Unauthorized ->
                    errorMessage = "Planning Center session expired — reconnect in Settings"
                else -> errorMessage = "Couldn't load service types"
            }
            isLoadingServiceTypes = false
        }
    }

    fun selectServiceType(id: String) {
        selectedServiceTypeId = id
        selectedPlanId = null
        planItems = emptyList()
        loadPlans(id)
    }

    private fun loadPlans(serviceTypeId: String) {
        viewModelScope.launch {
            isLoadingPlans = true
            errorMessage = null
            if (!ensureValidToken()) {
                isLoadingPlans = false
                return@launch
            }
            when (val outcome = PlanningCenterClient.listUpcomingPlans(accessToken, serviceTypeId)) {
                is PlanningCenterClient.PlansOutcome.Success -> plans = outcome.plans
                PlanningCenterClient.PlansOutcome.Unauthorized ->
                    errorMessage = "Planning Center session expired — reconnect in Settings"
                else -> errorMessage = "Couldn't load plans"
            }
            isLoadingPlans = false
        }
    }

    fun selectPlan(planId: String) {
        selectedPlanId = planId
        viewModelScope.launch {
            isLoadingItems = true
            errorMessage = null
            if (!ensureValidToken()) {
                isLoadingItems = false
                return@launch
            }
            when (val outcome = PlanningCenterClient.getPlanItems(accessToken, selectedServiceTypeId, planId)) {
                is PlanningCenterClient.PlanItemsOutcome.Success -> {
                    val catalog = currentSongCatalog()
                    planItems = outcome.items.map { pco ->
                        ImportPlanItem(pco = pco, matchedSongId = matchLocalSong(pco, catalog)?.songId)
                    }
                    // Scripture detection is local (regex + already-loaded Bible, no network call),
                    // so it's cheap to run eagerly for every generic item as soon as items load —
                    // unlike attachments, which stay lazy since they need a request per item.
                    val scriptureMap = mutableMapOf<String, List<PlanningCenterScriptureDetector.ResolvedVerses>>()
                    for (pco in outcome.items) {
                        if (pco.itemType != "item") continue
                        val combinedText = listOf(pco.title, pco.description).filter { it.isNotBlank() }.joinToString("\n")
                        val detected = detectScriptureReferences(combinedText)
                        if (detected.isNotEmpty()) scriptureMap[pco.id] = detected
                    }
                    detectedScripturesByItemId = scriptureMap
                    selectedScriptureIndices = scriptureMap.mapValues { (_, verses) -> verses.indices.toSet() }

                    // Eagerly check for attachments too, so the "Show Files" affordance can be
                    // hidden entirely for items that don't have any (one request per item — plans
                    // are small enough that this is fine, and it avoids showing dead-end buttons).
                    // "media"-type plan items are deliberately excluded: their PCO attachments are
                    // incidental files (lyric sheets, cue notes), not the actual media the item
                    // represents, so surfacing them as importable is misleading — there's nothing
                    // usable to fetch for these rows.
                    for (pco in outcome.items) {
                        if (pco.itemType == "item") loadAttachments(pco.id)
                    }
                }
                PlanningCenterClient.PlanItemsOutcome.Unauthorized ->
                    errorMessage = "Planning Center session expired — reconnect in Settings"
                else -> errorMessage = "Couldn't load plan items"
            }
            isLoadingItems = false
        }
    }

    var detectedScripturesByItemId by mutableStateOf<Map<String, List<PlanningCenterScriptureDetector.ResolvedVerses>>>(emptyMap())
        private set
    var selectedScriptureIndices by mutableStateOf<Map<String, Set<Int>>>(emptyMap())
        private set

    fun toggleScriptureSelected(itemId: String, index: Int) {
        val current = selectedScriptureIndices[itemId] ?: emptySet()
        val updated = if (index in current) current - index else current + index
        selectedScriptureIndices = selectedScriptureIndices + (itemId to updated)
    }

    /** Matches only a leading 4-digit song number (e.g. "1234 Amazing Grace") — 3 or 5+ digits don't count. */
    private val leadingSongNumberRegex = Regex("""^(\d{4})(?!\d)""")

    private fun matchLocalSong(pco: PlanningCenterClient.PlanItem, catalog: List<SongItem>): SongItem? {
        if (pco.itemType != "song") return null
        val ccli = pco.songCcliNumber
        if (!ccli.isNullOrBlank()) {
            catalog.firstOrNull { it.ccliNumber.isNotBlank() && it.ccliNumber == ccli }?.let { return it }
        }
        val title = pco.songTitle ?: pco.title
        val leadingNumber = leadingSongNumberRegex.find(title.trim())?.groupValues?.get(1)?.toIntOrNull()
        if (leadingNumber != null) {
            catalog.firstOrNull { it.number.toIntOrNull() == leadingNumber }?.let { return it }
        }
        return catalog.firstOrNull { it.title.equals(title, ignoreCase = true) }
    }

    fun toggleItemSelected(pcoItemId: String) {
        var newSelected = false
        planItems = planItems.map {
            if (it.pco.id == pcoItemId) {
                newSelected = !it.selected
                it.copy(selected = newSelected)
            } else it
        }
        // The row's own checkbox is the master for its attached files too — checking/unchecking
        // it should select/deselect all of them, not leave their checkboxes independently stale.
        attachmentsByItemId[pcoItemId]?.let { files ->
            selectedAttachmentIds = selectedAttachmentIds +
                (pcoItemId to if (newSelected) files.map { file -> file.id }.toSet() else emptySet())
        }
    }

    /** True only when every row, detected scripture and attachment checkbox is currently checked. */
    val allSelected: Boolean
        get() = planItems.isNotEmpty() &&
            planItems.all { it.selected } &&
            detectedScripturesByItemId.all { (id, verses) -> selectedScriptureIndices[id]?.size == verses.size } &&
            attachmentsByItemId.all { (id, files) -> selectedAttachmentIds[id]?.size == files.size }

    /** Master checkbox handler — selects or clears every row/scripture/attachment checkbox at once. */
    fun setAllSelected(selectAll: Boolean) {
        planItems = planItems.map { it.copy(selected = selectAll) }
        selectedScriptureIndices = detectedScripturesByItemId.mapValues { (_, verses) ->
            if (selectAll) verses.indices.toSet() else emptySet()
        }
        selectedAttachmentIds = attachmentsByItemId.mapValues { (_, files) ->
            if (selectAll) files.map { it.id }.toSet() else emptySet()
        }
    }

    /** Fetches lyrics/chord-chart text for the add-song dialog's prefill. Null on any failure. */
    suspend fun fetchArrangementForAddSong(pco: PlanningCenterClient.PlanItem): PlanningCenterClient.ArrangementDetail? {
        val songId = pco.songId ?: return null
        val arrangementId = pco.arrangementId ?: return null
        if (!ensureValidToken()) return null
        return when (val outcome = PlanningCenterClient.getArrangementDetail(accessToken, songId, arrangementId)) {
            is PlanningCenterClient.ArrangementOutcome.Success -> outcome.detail
            else -> null
        }
    }

    fun defaultSongbookForNewSongs(): String = importSongbookName.ifBlank { "Planning Center" }

    /**
     * Writes a newly-confirmed song straight to the song storage directory, mirroring
     * `SongsViewModel.createSong`'s file-naming/write logic exactly (mkdirs songbook folder,
     * `"NNNN - Title.song"` filename, [SongFileParser.writeSongFile]) — deliberately not routed
     * through `SongsViewModel` (owned exclusively by the Songs tab per the ViewModel-ownership
     * rule). Returns the saved [SongItem] (with `sourceFile`/`songId` populated) so the caller can
     * immediately add it to the schedule, or null on failure.
     */
    fun createLocalSong(song: SongItem): SongItem? {
        return try {
            val storageDir = SettingsManager().loadSettings().songSettings.storageDirectory
            if (storageDir.isBlank() || song.songbook.isBlank()) return null

            val targetDir = File(storageDir, song.songbook)
            if (!targetDir.exists()) targetDir.mkdirs()

            val fileName = if (song.number.isNotBlank()) {
                "${song.number.padStart(4, '0')} - ${song.title}.song"
            } else {
                "${song.title}.song"
            }
            val filePath = File(targetDir, fileName).absolutePath

            val saved = song.copy(sourceFile = filePath)
            SongFileParser().writeSongFile(saved, filePath)
            saved
        } catch (_: Exception) {
            null
        }
    }

    /** Marks an item as resolved to a just-created (or matched) local song, for the dialog's list state. */
    fun markItemResolved(pcoItemId: String, songId: String) {
        planItems = planItems.map { if (it.pco.id == pcoItemId) it.copy(matchedSongId = songId) else it }
    }

    // ── Attachments (media backgrounds, slide decks) ────────────────────────────

    var attachmentsByItemId by mutableStateOf<Map<String, List<PlanningCenterClient.PlanAttachment>>>(emptyMap())
        private set
    var loadingAttachmentsForItemId by mutableStateOf<String?>(null)
        private set
    var selectedAttachmentIds by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set

    fun loadAttachments(itemId: String) {
        if (attachmentsByItemId.containsKey(itemId)) return
        val planId = selectedPlanId ?: return
        viewModelScope.launch {
            loadingAttachmentsForItemId = itemId
            if (!ensureValidToken()) {
                loadingAttachmentsForItemId = null
                return@launch
            }
            val outcome = PlanningCenterClient.getItemAttachments(accessToken, selectedServiceTypeId, planId, itemId)
            val attachments = (outcome as? PlanningCenterClient.AttachmentsOutcome.Success)?.attachments ?: emptyList()
            attachmentsByItemId = attachmentsByItemId + (itemId to attachments)
            selectedAttachmentIds = selectedAttachmentIds + (itemId to attachments.map { it.id }.toSet())
            loadingAttachmentsForItemId = null
        }
    }

    fun toggleAttachmentSelected(itemId: String, attachmentId: String) {
        val current = selectedAttachmentIds[itemId] ?: emptySet()
        val updated = if (attachmentId in current) current - attachmentId else current + attachmentId
        selectedAttachmentIds = selectedAttachmentIds + (itemId to updated)
    }

    /** Thumbnail URLs are public S3 links — no token needed, just a thin passthrough. */
    suspend fun fetchThumbnailBytes(url: String): ByteArray? = PlanningCenterClient.fetchThumbnailBytes(url)

    sealed interface ImportedMedia {
        data class Presentation(val filePath: String, val fileName: String, val slideCount: Int, val fileType: String) : ImportedMedia
        data class Picture(val folderPath: String, val folderName: String, val imageCount: Int) : ImportedMedia
        data class Media(val mediaUrl: String, val mediaTitle: String) : ImportedMedia
    }

    /**
     * Downloads one attachment to `~/.churchpresenter/planning_center_cache/<planId>/<itemId>/`
     * and classifies it by extension into the schedule item type it should become. `pptx`/`ppt`/
     * `key`/`pdf` slide counts come from the Presentation Engine's [PresentationLoader] (metadata
     * parse only — no rasterization), the same call `PresentationViewModel` makes. Null on any
     * download failure or unrecognized extension.
     */
    suspend fun importAttachment(
        planId: String,
        itemId: String,
        attachment: PlanningCenterClient.PlanAttachment
    ): ImportedMedia? = withContext(Dispatchers.IO) {
        if (!ensureValidToken()) return@withContext null
        val urlOutcome = PlanningCenterClient.resolveAttachmentDownloadUrl(accessToken, attachment.id)
        val resolvedUrl = (urlOutcome as? PlanningCenterClient.AttachmentUrlOutcome.Success)?.url ?: return@withContext null

        val cacheDir = File(
            System.getProperty("user.home"),
            ".churchpresenter/planning_center_cache/$planId/$itemId"
        )
        val destination = File(cacheDir, attachment.filename)
        val outcome = PlanningCenterClient.downloadFile(resolvedUrl, destination)
        val file = (outcome as? PlanningCenterClient.FileDownloadOutcome.Success)?.file ?: return@withContext null

        val ext = file.extension.lowercase()
        when (ext) {
            "ppt", "pptx", "key", "pdf" -> {
                val slideCount = (PresentationLoader.load(file) as? LoadResult.Success)?.deck?.slideCount ?: 0
                ImportedMedia.Presentation(
                    filePath = file.absolutePath,
                    fileName = file.nameWithoutExtension,
                    slideCount = slideCount,
                    fileType = ext
                )
            }
            "jpg", "jpeg", "png", "gif", "bmp", "webp" ->
                ImportedMedia.Picture(folderPath = cacheDir.absolutePath, folderName = cacheDir.name, imageCount = 1)
            "mp4", "avi", "mov", "mkv", "webm", "mp3", "wav", "flac" ->
                ImportedMedia.Media(mediaUrl = file.absolutePath, mediaTitle = file.nameWithoutExtension)
            else -> null
        }
    }
}
