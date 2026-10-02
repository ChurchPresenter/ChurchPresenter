package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.planningcenter.PcoItemType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.app.churchpresenter.viewmodel.PlanningCenterImportViewModel
import org.churchpresenter.planningcenter.PlanningCenterClient

/** Where an import puts what it brings in: the schedule's add functions, and a heading's colours. */
internal data class PcoImportActions(
    val onAddSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit,
    val onAddLabel: (text: String, textColor: String, backgroundColor: String) -> Unit,
    val onAddPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit,
    val onAddPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit,
    val onAddMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit,
    val onAddAnnouncement: (text: String) -> Unit,
    val onAddBibleVerse: (bookName: String, chapter: Int, verseNumber: Int, verseText: String,
        verseRange: String, bookId: Int) -> Unit,
    val headerTextColor: String,
    val headerBackgroundColor: String,
)

/** Whether anything is selected that an import would bring in. */
internal fun canImportSelection(viewModel: PlanningCenterImportViewModel): Boolean =
    viewModel.planItems.any { entry ->
    val pco = entry.pco
    val hasScripture = viewModel.detectedScripturesByItemId[pco.id]?.isNotEmpty() == true
    when (pco.itemType) {
        PcoItemType.HEADER -> entry.selected
        PcoItemType.SONG -> entry.matchedSongId != null
        PcoItemType.ITEM -> if (hasScripture) {
            viewModel.selectedScriptureIndices[pco.id]?.isNotEmpty() == true
        } else {
            val selectedAttachmentIds = viewModel.selectedAttachmentIds[pco.id].orEmpty()
            entry.selected || viewModel.attachmentsByItemId[pco.id].orEmpty()
                .any { it.id in selectedAttachmentIds && isSupportedAttachment(it.filename) }
        }
        else -> false
    }
}

/** Brings every selected item of the plan into the schedule, in plan order. */
internal suspend fun importSelection(
    viewModel: PlanningCenterImportViewModel,
    planId: String,
    actions: PcoImportActions,
) {
    for (entry in viewModel.planItems) {
        val pco = entry.pco
        when (pco.itemType) {
            PcoItemType.SONG -> {
                val songId = entry.matchedSongId.takeIf { entry.selected }
                if (songId != null) {
                    val parts = songId.split("::", limit = 2)
                    val songbook = parts.getOrNull(0) ?: ""
                    val songNumber = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    actions.onAddSong(songNumber, pco.songTitle ?: pco.title, songbook, songId)
                }
            }
            PcoItemType.HEADER -> {
                if (entry.selected) {
                    actions.onAddLabel(pco.title, actions.headerTextColor, actions.headerBackgroundColor)
                }
            }
            PcoItemType.ITEM -> {
                // Scripture and attachment checkboxes are independent of
                // the row's own checkbox (matching the button's enabled
                // check above) — unchecking the row while leaving one of
                // those checked must still import just that one thing.
                val scriptures = viewModel.detectedScripturesByItemId[pco.id].orEmpty()
                val selectedAttachmentIds = viewModel.selectedAttachmentIds[pco.id].orEmpty()
                val hasSelectedAttachments = viewModel.attachmentsByItemId[pco.id].orEmpty()
                    .any {
                        it.id in selectedAttachmentIds && isSupportedAttachment(it.filename)
                    }
                if (scriptures.isNotEmpty()) {
                    val selectedIdx = viewModel.selectedScriptureIndices[pco.id].orEmpty()
                    scriptures.forEachIndexed { index, verse ->
                        if (index !in selectedIdx) return@forEachIndexed
                        actions.onAddBibleVerse(
                            verse.bookName,
                            verse.chapter,
                            verse.verseNumber,
                            verse.verseText,
                            verse.verseRange,
                            verse.bookId
                        )
                    }
                } else if (entry.selected && !hasSelectedAttachments) {
                    // Only fall back to a text announcement when there's
                    // no attached file — a file import already becomes its
                    // own Presentation/Picture/Media schedule entry below,
                    // so adding an announcement too would just duplicate it.
                    actions.onAddAnnouncement(pco.description.ifBlank { pco.title })
                }
            }
            else -> continue
        }
        if (pco.itemType == PcoItemType.ITEM) importAttachments(viewModel, planId, pco, actions)
    }
}

/**
 * Brings one item's selected attachments in. They are their own per-file checkboxes, independent of
 * the row's main checkbox; every selected image of the item becomes one Picture entry, a slideshow.
 */
private suspend fun importAttachments(
    viewModel: PlanningCenterImportViewModel,
    planId: String,
    pco: PlanningCenterClient.PlanItem,
    actions: PcoImportActions,
) {
val attachments = viewModel.attachmentsByItemId[pco.id].orEmpty()
val selectedIds = viewModel.selectedAttachmentIds[pco.id].orEmpty()
// All selected images for this item share one cache folder
// (keyed by item id) — collect them into a single Picture
// schedule entry (one slideshow) instead of one per image.
var pictureFolderPath: String? = null
var pictureFolderName: String? = null
var pictureCount = 0
for (att in attachments) {
    if (att.id !in selectedIds || !isSupportedAttachment(att.filename)) continue
    // The schedule item's title should read as the plan item's
    // own title (e.g. "Guest Speaker Presentation"), not the
    // raw uploaded filename — fall back to the filename only
    // when the plan item has no title.
    when (val imported = viewModel.importAttachment(planId, pco.id, att)) {
        is PlanningCenterImportViewModel.ImportedMedia.Presentation ->
            actions.onAddPresentation(
                imported.filePath,
                pco.title.ifBlank { imported.fileName },
                imported.slideCount,
                imported.fileType
            )
        is PlanningCenterImportViewModel.ImportedMedia.Picture -> {
            pictureFolderPath = imported.folderPath
            pictureFolderName = pco.title.ifBlank { imported.folderName }
            pictureCount++
        }
        is PlanningCenterImportViewModel.ImportedMedia.Media ->
            actions.onAddMedia(
                imported.mediaUrl,
                pco.title.ifBlank { imported.mediaTitle },
                "local"
            )
        null -> {}
    }
}
if (pictureFolderPath != null && pictureFolderName != null) {
    actions.onAddPicture(pictureFolderPath, pictureFolderName, pictureCount)
}
}

/** What the song editor opens with for a plan song the library lacks: its details and its arrangement. */
internal suspend fun newSongPrefill(
    viewModel: PlanningCenterImportViewModel,
    pco: PlanningCenterClient.PlanItem,
): SongItem {
    val detail = viewModel.fetchArrangementForAddSong(pco)
    return SongItem(
        number = "",
        title = pco.songTitle ?: pco.title,
        songbook = viewModel.defaultSongbookForNewSongs(),
        author = pco.songAuthor ?: "",
        lyrics = (detail?.lyrics ?: "").split("\n"),
        ccliNumber = pco.songCcliNumber ?: ""
    )
}
