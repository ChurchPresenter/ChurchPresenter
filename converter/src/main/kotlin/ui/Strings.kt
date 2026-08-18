package ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.ResourceBundle

object Strings {
    /**
     * Snapshot state, so switching language while the converter is open redraws it in the new one
     * instead of waiting for the window to be closed and opened again.
     *
     * Standalone, the OS locale is the only thing to go on. Hosted inside ChurchPresenter, the app
     * sets it from the language chosen there — see `ConverterWindow`.
     */
    private var bundle: ResourceBundle by mutableStateOf(bundleFor(Locale.getDefault()))

    fun setLocale(locale: Locale) {
        bundle = bundleFor(locale)
    }

    /**
     * `ResourceBundle.getBundle` tries the *default locale's* bundle before the base one, so asking
     * for a language the converter has no bundle for answers in whatever the machine is set to — a
     * Dutch user on a Russian machine would get Russian. No-fallback lookup gives English instead.
     */
    private fun bundleFor(locale: Locale): ResourceBundle =
        ResourceBundle.getBundle(
            "converter_strings",
            locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    // Tab names
    val tabBibles: String get() = bundle.getString("tab_bibles")
    val tabSongs: String get() = bundle.getString("tab_songs")
    val tabDuplicates: String get() = bundle.getString("tab_duplicates")
    val tabRename: String get() = bundle.getString("tab_rename")

    // Common buttons/actions
    val selectFolder: String get() = bundle.getString("select_folder")
    val preview: String get() = bundle.getString("preview")
    val convert: String get() = bundle.getString("convert")
    val back: String get() = bundle.getString("back")
    val startOver: String get() = bundle.getString("start_over")
    val converting: String get() = bundle.getString("converting")
    val delete: String get() = bundle.getString("delete")
    val cancel: String get() = bundle.getString("cancel")
    val rescan: String get() = bundle.getString("rescan")
    val open: String get() = bundle.getString("open")
    val left: String get() = bundle.getString("left")
    val right: String get() = bundle.getString("right")

    // Source rail
    val convertFrom: String get() = bundle.getString("convert_from")
    val searchFormats: String get() = bundle.getString("search_formats")
    val groupSongFormats: String get() = bundle.getString("group_song_formats")
    val groupDocuments: String get() = bundle.getString("group_documents")
    val requestFormatTitle: String get() = bundle.getString("request_format_title")
    val requestFormatBody: String get() = bundle.getString("request_format_body")

    fun noFormatMatches(query: String): String = bundle.getString("no_format_matches").format(query)
    fun sourceDescription(id: String): String = bundle.getString("source_desc_$id")
    fun sourceAccepts(id: String): String = bundle.getString("source_accepts_$id")

    // Conversion steps
    val stepSourceFiles: String get() = bundle.getString("step_source_files")
    val stepDestination: String get() = bundle.getString("step_destination")
    val selectFiles: String get() = bundle.getString("select_files")
    val browse: String get() = bundle.getString("browse")
    val change: String get() = bundle.getString("change")
    val clear: String get() = bundle.getString("clear")
    val chooseOutputFolder: String get() = bundle.getString("choose_output_folder")
    val outputManyFilesWarning: String get() = bundle.getString("output_many_files_warning")

    fun dropFilesHere(ext: String): String = bundle.getString("drop_files_here").format(ext)
    fun filesSelected(n: Int): String = bundle.getString("files_selected").format(n)
    fun folderSelected(path: String, n: Int): String = bundle.getString("folder_selected").format(path, n)
    fun convertingFiles(n: Int): String = bundle.getString("converting_files").format(n)
    fun nConverted(n: Int): String = bundle.getString("n_converted").format(n)
    val sameAsInput: String get() = bundle.getString("same_as_input")
    val songsLabel: String get() = bundle.getString("songs_label")
    val completedWithErrors: String get() = bundle.getString("completed_with_errors")
    val doneLabel: String get() = bundle.getString("done_label")
    val allSongsConverted: String get() = bundle.getString("all_songs_converted")

    // Bible section
    val bibleTitle: String get() = bundle.getString("bible_title")
    val bibleDesc: String get() = bundle.getString("bible_desc")
    val selectXmlFiles: String get() = bundle.getString("select_xml_files")
    val xmlBibleFiles: String get() = bundle.getString("xml_bible_files")
    val spbBibleFiles: String get() = bundle.getString("spb_bible_files")
    val fixVersesTitle: String get() = bundle.getString("fix_verses_title")
    val fixVersesDesc: String get() = bundle.getString("fix_verses_desc")
    val selectSpbFiles: String get() = bundle.getString("select_spb_files")
    val fixVerses: String get() = bundle.getString("fix_verses")
    val fixingVerses: String get() = bundle.getString("fixing_verses")

    // Duplicate finder section
    val dupesTitle: String get() = bundle.getString("dupes_title")
    val dupesDesc: String get() = bundle.getString("dupes_desc")
    val scanForDuplicates: String get() = bundle.getString("scan_for_duplicates")
    val scanning: String get() = bundle.getString("scanning")
    val scanAgain: String get() = bundle.getString("scan_again")
    val matchByNumber: String get() = bundle.getString("match_by_number")
    val matchByTitle: String get() = bundle.getString("match_by_title")
    val similarityThreshold: String get() = bundle.getString("similarity_threshold")
    val dupesEmptyState: String get() = bundle.getString("dupes_empty_state")
    val keepFolder: String get() = bundle.getString("keep_folder")
    val selectHint: String get() = bundle.getString("select_hint")
    val selectSameFolder: String get() = bundle.getString("select_same_folder")
    val collapseAll: String get() = bundle.getString("collapse_all")
    val expandAll: String get() = bundle.getString("expand_all")
    val markForDeletion: String get() = bundle.getString("mark_for_deletion")
    val labelKeep: String get() = bundle.getString("label_keep")
    val labelDelete: String get() = bundle.getString("label_delete")

    // Homoglyph section
    val findHomoglyphs: String get() = bundle.getString("find_homoglyphs")
    val noHomoglyphs: String get() = bundle.getString("no_homoglyphs")
    val homoglyphFixNote: String get() = bundle.getString("homoglyph_fix_note")
    val homoglyphDialogTitle: String get() = bundle.getString("homoglyph_dialog_title")
    val homoglyphDialogDescSuffix: String get() = bundle.getString("homoglyph_dialog_desc_suffix")
    val homoglyphDialogQuestion: String get() = bundle.getString("homoglyph_dialog_question")
    val homoglyphDialogNote: String get() = bundle.getString("homoglyph_dialog_note")
    val fixAndScan: String get() = bundle.getString("fix_and_scan")
    val skipAndScan: String get() = bundle.getString("skip_and_scan")

    // Control characters section
    val findControlChars: String get() = bundle.getString("find_control_chars")
    val noControlChars: String get() = bundle.getString("no_control_chars")

    // Filters
    val filters: String get() = bundle.getString("filters")
    val catSameNumber: String get() = bundle.getString("cat_same_number")
    val catSameTitle: String get() = bundle.getString("cat_same_title")
    val catSimilarLyrics: String get() = bundle.getString("cat_similar_lyrics")
    val minSim: String get() = bundle.getString("min_sim")
    val filesPerGroup: String get() = bundle.getString("files_per_group")
    val clearFilters: String get() = bundle.getString("clear_filters")

    // Delete confirmation dialog
    val deleteDupesTitle: String get() = bundle.getString("delete_dupes_title")
    val filesToDeleteLabel: String get() = bundle.getString("files_to_delete")
    val deleteFilesTitle: String get() = bundle.getString("delete_files_title")

    // Bulk rename section
    val renameTitle: String get() = bundle.getString("rename_title")
    val renameDesc: String get() = bundle.getString("rename_desc")
    val stripNumbers: String get() = bundle.getString("strip_numbers")
    val renameFirstVerse: String get() = bundle.getString("rename_first_verse")
    val letterCase: String get() = bundle.getString("letter_case")
    val exampleLabel: String get() = bundle.getString("example_label")
    val renameExampleBefore: String get() = bundle.getString("rename_example_before")
    val renameExampleFirstLine: String get() = bundle.getString("rename_example_first_line")
    val caseNone: String get() = bundle.getString("case_none")
    val caseSentence: String get() = bundle.getString("case_sentence")
    val caseTitle: String get() = bundle.getString("case_title")
    val caseLower: String get() = bundle.getString("case_lower")
    val caseUpper: String get() = bundle.getString("case_upper")
    val renaming: String get() = bundle.getString("renaming")
    val conflict: String get() = bundle.getString("conflict")

    // Preview row
    val outputOverwrite: String get() = bundle.getString("output_overwrite")
    val outputFolderOverwrite: String get() = bundle.getString("output_folder_overwrite")

    // Preview label
    val previewLabel: String get() = bundle.getString("preview_label")

    // Parameterized strings
    fun fileCount(n: Int): String = bundle.getString("file_count").format(n)
    fun convertNFiles(n: Int): String = bundle.getString("convert_n_files").format(n)
    fun convertNSongs(n: Int): String = bundle.getString("convert_n_songs").format(n)
    fun doneConverted(ok: Int, failed: Int): String = bundle.getString("done_converted").format(ok, failed)
    fun doneDeleted(deleted: Int, failed: Int): String = bundle.getString("done_deleted").format(deleted, failed)
    fun doneFixed(fixed: Int, failed: Int): String = bundle.getString("done_fixed").format(fixed, failed)
    fun doneRenamed(ok: Int, skipped: Int, failed: Int): String = bundle.getString("done_renamed").format(ok, skipped, failed)
    fun noDupesFound(scanned: Int): String = bundle.getString("no_dupes_found").format(scanned)
    fun groupSummary(groups: Int, songs: Int, scanned: Int): String = bundle.getString("group_summary").format(groups, songs, scanned)
    fun deleteNSelected(n: Int): String = bundle.getString("delete_n_selected").format(n)
    fun permanentlyDelete(n: Int): String = bundle.getString("permanently_delete").format(n)
    fun permanentlyDeleteShort(n: Int): String = bundle.getString("permanently_delete_short").format(n)
    fun deleteNMarked(n: Int): String = bundle.getString("delete_n_marked").format(n)
    fun filesWithHomoglyphs(n: Int): String = bundle.getString("files_with_homoglyphs").format(n)
    fun filesWithControlChars(n: Int): String = bundle.getString("files_with_control_chars").format(n)
    fun andNMore(n: Int): String = bundle.getString("and_n_more").format(n)
    fun fixNFiles(n: Int): String = bundle.getString("fix_n_files").format(n)
    fun fixedNChars(n: Int, name: String): String = bundle.getString("fixed_n_chars").format(n, name)
    fun renameNFiles(n: Int): String = bundle.getString("rename_n_files").format(n)
    fun renameSummary(toRename: Int, unchanged: Int): String = bundle.getString("rename_summary").format(toRename, unchanged)
    fun conflictsSummary(n: Int): String = bundle.getString("conflicts_summary").format(n)
    fun showingGroups(shown: Int, total: Int): String = bundle.getString("showing_groups").format(shown, total)
    fun groupHeader(idx: Int, title: String): String = bundle.getString("group_header").format(idx, title)
    fun groupDetail(files: Int, reason: String, sim: Int): String = bundle.getString("group_detail").format(files, reason, sim)
    fun titlePrefix(title: String): String = bundle.getString("title_prefix").format(title)
    fun similaritySuffix(pct: Int): String = bundle.getString("similarity_suffix").format(pct)
    fun sectionsPrefix(sections: String): String = bundle.getString("sections_prefix").format(sections)
    fun missingPrefix(missing: String): String = bundle.getString("missing_prefix").format(missing)
    fun sectionsLines(sections: Int, lines: Int): String = bundle.getString("sections_lines").format(sections, lines)
    fun songbookPrefix(name: String): String = bundle.getString("songbook_prefix").format(name)
    fun songsConverted(n: Int): String = bundle.getString("songs_converted").format(n)
    fun outputPrefix(path: String): String = bundle.getString("output_prefix").format(path)
    fun songsFound(n: Int): String = bundle.getString("songs_found").format(n)
    fun outputFolderPrefix(path: String): String = bundle.getString("output_folder_prefix").format(path)
    fun keepFolderPrefix(path: String): String = bundle.getString("keep_folder_prefix").format(path)
    fun compareTitle(name: String): String = bundle.getString("compare_title").format(name)
    fun selectDialog(desc: String): String = bundle.getString("select_dialog").format(desc)
    fun errorPrefix(msg: String): String = bundle.getString("error_prefix").format(msg)
    fun sectionCount(n: Int): String = bundle.getString("section_count").format(n)
    fun songCount(n: Int): String = bundle.getString("song_count").format(n)
    fun verseOrderPrefix(order: String): String = bundle.getString("verse_order_prefix").format(order)
    fun parseError(msg: String): String = bundle.getString("parse_error").format(msg)

    // Document conversion section
    val docPreviewMarkdown: String get() = bundle.getString("doc_preview_markdown")
    val docPreviewSong: String get() = bundle.getString("doc_preview_song")
    fun songsExtracted(n: Int): String = bundle.getString("songs_extracted").format(n)
}
