@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.screenshot.LIBRARY
import org.churchpresenter.app.churchpresenter.screenshot.library
import org.churchpresenter.converter.song.SongFormatConverter
import org.churchpresenter.converter.ui.App
import org.churchpresenter.converter.ui.ConverterPickers
import org.churchpresenter.converter.ui.ConverterTab
import org.churchpresenter.converter.ui.LocalConverterPickers
import org.churchpresenter.converter.ui.SongSource
import java.io.File
import kotlin.test.Test

/** The Converter: songs and Bibles in from other programs, the Duplicate Finder and Bulk Rename. */
class ConverterDemoTour {
    @Test
    fun converter() {
        // Beside the demo library, not under the test's home: the tabs show the folders they read.
        val root = File(LIBRARY.parentFile, "Converter Demo").apply {
            deleteRecursively()
            mkdirs()
        }
        val songs = File(root, "SongBeamer").apply { mkdirs() }
        SONGBEAMER.forEach { (name, text) -> File(songs, name).writeText(text) }
        val bible = File(root, "Bibles/sample-bible.xml").apply {
            parentFile.mkdirs()
            writeText(ZEFANIA)
        }
        // The demo library is written here too: which test makes it first depends on how the suite is split.
        library()
        // A library that collected copies over the years: the demo songs, and an old folder holding three of them.
        val dupes = File(root, "Library").apply { File(LIBRARY, "Songs").copyRecursively(this) }
        val old = File(dupes, "Old Hymnal").apply { mkdirs() }
        listOf("12 - Amazing Grace.song", "455 - It Is Well With My Soul.song", "103 - How Great Thou Art.song")
            .forEach { File(dupes, "Hymnal/$it").copyTo(File(old, it)) }
        val rename = File(root, "Rename").apply { File(LIBRARY, "Songs/Hymnal").copyRecursively(this) }

        val pickers = DemoPickers()
        recordTour(
            "converter",
            tab = "Converter",
            liveLabel = "Bring your library",
            steps = 12,
            heading = "The " to "",
            panel = { _, _ ->
                Box(Modifier.size(CONVERTER_W.dp, CONVERTER_H.dp)) {
                    CompositionLocalProvider(LocalConverterPickers provides pickers) {
                        App(initialTab = ConverterTab.SONGS)
                    }
                }
            },
        ) { converterTour(pickers, songs, bible, dupes, rename) }
    }

    private fun DemoTour.converterTour(pickers: DemoPickers, songs: File, bible: File, dupes: File, rename: File) {
        say("intro")
        intro("Bring your songs and Bibles with you, find duplicates, rename in bulk.")
        say("open")
        val help = runCatching { find(hasText("Help")) { it.top < dp(60f) } }.getOrNull()
        val keys = "Or Ctrl+Shift+K, Cmd+Shift+K on a Mac"
        if (help != null) note("Help menu", keys, help) else caption("Help menu", keys)
        openPanel("Converter", CONVERTER_W, CONVERTER_H)
        say("tabs")
        // The whole row of tabs, from the first label to the last.
        val labels = listOf("Bibles", "Songs", "Duplicates", "Rename")
            .mapNotNull { panelItems(hasText(it)).firstOrNull() }
        if (labels.size == TAB_COUNT) {
            val b = labels.map { it.bounds }
            val row = Rect(b.minOf { it.left }, b.minOf { it.top }, b.maxOf { it.right }, b.maxOf { it.bottom })
            panelNote("Four tabs", "Bibles, Songs, Duplicates, Rename", Target(labels.first().node, row))
        } else {
            caption("Four tabs", "Bibles, Songs, Duplicates, Rename")
        }
        segment("songs")
        songs(pickers, songs)
        segment("bibles")
        bibles(pickers, bible)
        segment("duplicates")
        duplicates(pickers, dupes)
        segment("rename")
        bulkRename(pickers, rename)
        segment("end")
        closePanel()
        say("outro")
        outro()
    }

    private fun DemoTour.songs(pickers: DemoPickers, folder: File) {
        say("songs-why")
        caption("Your songs come with you", "Which software you use is your choice")
        say("songs-formats")
        // The whole list of programs, from its heading to its last entry.
        // From the CONVERT FROM heading down to the Documents entry, and across to the extensions on the
        // right; the headings are drawn in capitals, so case is ignored.
        val list = panelItems(hasText("Convert from", ignoreCase = true)).take(1) +
            panelItems(hasText("Documents", ignoreCase = true)) +
            panelItems(hasText(".json"))
        if (list.size >= RAIL_PARTS) {
            val b = list.map { it.bounds }
            val rail = Rect(b.minOf { it.left }, b.minOf { it.top }, b.maxOf { it.right }, b.maxOf { it.bottom })
            panelNote("Pick your program", "Song formats and documents", Target(list.first().node, rail))
        } else {
            caption("Pick your program", "Song formats and documents")
        }
        say("songs-pick")
        panelNote("SongBeamer", "For this tour", seek(hasText("SongBeamer")), click = true, numbered = true)
        say("songs-select")
        pickers.files = folder.listFiles().orEmpty().sortedBy { it.name }
        panelNote("Select Files", "Or a whole folder", seek(hasText("Select Files")), click = true)
        say("songs-destination")
        point("Destination", "Next to the originals by default", hasText("Same as input", substring = true))
        say("songs-preview")
        panelNote("Preview", "Check each song first", seek(hasText("Preview")), click = true, numbered = true)
        say("songs-convert")
        val convert = seek(hasText("Convert 3", substring = true))
        panelNote("Convert", "Ready for your library", convert, click = true)
        waitFor("the songs to convert") { panelItems(hasText("Done:", substring = true)).isNotEmpty() }
        pause(0.5)
        say("songs-request")
        point("Don't see your program?", "Send a few sample songs", hasText("Don't see your program?"))
    }

    private fun DemoTour.bibles(pickers: DemoPickers, bible: File) {
        say("bible-intro")
        panelNote("Bibles", "Zefania XML to ChurchPresenter", seek(hasText("Bibles")), click = true, numbered = true)
        say("bible-licence")
        caption("Only Bibles you're licensed to use", "The copyright notice stays with it")
        say("bible-convert")
        pickers.files = listOf(bible)
        quietPanelClick(hasText("Select .xml Files"))
        quietPanelClick(hasText("Preview"))
        point("Preview, then convert", "One Bible, ready to add", hasText("Convert 1", substring = true))
        quietPanelClick(hasText("Convert 1", substring = true))
        say("bible-fix")
        point("Fix Known Bible Verses", "For Bibles already converted", hasText("Fix Known Bible Verses"))
    }

    private fun DemoTour.duplicates(pickers: DemoPickers, library: File) {
        say("dupes-intro")
        panelNote(
            "Duplicates",
            "The same song, more than once",
            seek(hasText("Duplicates")),
            click = true,
            numbered = true,
        )
        say("dupes-folder")
        pickers.directory = library
        panelNote("Select Folder", "Your songs folder", seek(hasText("Select Folder")), click = true)
        say("dupes-passes")
        point("Three passes", "Number, title, lyrics", hasText("Match by song number", substring = true))
        say("dupes-threshold")
        point("Similarity threshold", "How close counts as the same", hasText("Similarity threshold"))
        say("dupes-scan")
        panelNote(
            "Scan for Duplicates",
            "Three passes",
            seek(hasText("Scan for Duplicates")),
            click = true,
            numbered = true,
        )
        waitFor("the scan") { panelItems(hasText("scanned", substring = true)).isNotEmpty() }
        pause(0.5)
        say("dupes-clean")
        point("Clean-up checks", "Look-alike letters, control characters", hasText("homoglyphs", substring = true))
        say("dupes-results")
        point("Grouped matches", "Why each matched, and how closely", hasText("Group 1", substring = true))
        say("dupes-keep")
        // Keep Folder is a menu of the folders the scan found: Hymnal keeps the originals.
        panelNote("Keep Folder", "Copies outside it are marked", seek(hasText("Keep Folder")), click = true)
        menusInPlace = true
        val hymnal = panelItems(hasText("Hymnal") and hasAnyAncestor(isPopup())).firstOrNull()
        if (hymnal != null) {
            hymnal.node.performClick()
            ui.waitForIdle()
        }
        menusInPlace = false
        pause(0.4)
        say("dupes-delete")
        val delete = hasText("Delete", substring = true) and hasText("selected", substring = true)
        point("Delete the marked copies", "Confirmed first", delete)
        say("dupes-again")
        point("Run it again", "To catch more", hasText("Rescan") or hasText("Scan Again"))
    }

    private fun DemoTour.bulkRename(pickers: DemoPickers, folder: File) {
        say("rename-intro")
        panelNote("Rename", "Tidy song file names", seek(hasText("Rename")), click = true, numbered = true)
        pickers.directory = folder
        quietPanelClick(hasText("Select Folder"))
        say("rename-numbers")
        point("Strip leading numbers", "0111 - Title becomes Title", hasText("Strip leading numbers", substring = true))
        say("rename-first-line")
        point("First line of verse 1", "As the name", hasText("first line of verse 1", substring = true))
        say("rename-case")
        point("Letter case", "Title, sentence, lower, upper", hasText("Letter case"))
        say("rename-preview")
        panelNote("Preview", "Every new name first", seek(hasText("Preview")), click = true, numbered = true)
        say("rename-go")
        point(
            "Rename them all",
            "At once",
            hasText("Rename", substring = true) and hasText("file(s)", substring = true),
        )
    }
}

/** Hands the converter the sample files and folders a person would have picked. */
private class DemoPickers : ConverterPickers {
    var files: List<File> = emptyList()
    var directory: File? = null

    override fun files(description: String, vararg extensions: String, multiSelection: Boolean): List<File> = files

    override fun directory(): File? = directory

    override fun sourceFiles(source: SongSource, format: SongFormatConverter): List<File> = files
}

private const val CONVERTER_W = 1200f
private const val TAB_COUNT = 4
private const val RAIL_PARTS = 3
private const val CONVERTER_H = 800f

private fun sng(title: String, author: String, vararg sections: Pair<String, String>): String =
    "#Title=$title\n#Author=$author\n" + sections.joinToString("") { (label, lines) -> "---\n$label\n$lines\n" }

private val SONGBEAMER = listOf(
    "Be Thou My Vision.sng" to sng(
        "Be Thou My Vision", "Dallan Forgaill",
        "Verse 1" to "Be Thou my vision, O Lord of my heart;\nNaught be all else to me, save that Thou art.",
        "Verse 2" to "Be Thou my wisdom, and Thou my true word;\nI ever with Thee and Thou with me, Lord.",
    ),
    "Rock Of Ages.sng" to sng(
        "Rock Of Ages", "Augustus Toplady",
        "Verse 1" to "Rock of Ages, cleft for me,\nLet me hide myself in Thee;",
        "Verse 2" to "Not the labors of my hands\nCan fulfill Thy law's demands;",
    ),
    "Abide With Me.sng" to sng(
        "Abide With Me", "Henry F. Lyte",
        "Verse 1" to "Abide with me; fast falls the eventide;\nThe darkness deepens; Lord, with me abide.",
        "Verse 2" to "Swift to its close ebbs out life's little day;\nEarth's joys grow dim; its glories pass away.",
    ),
)

private val ZEFANIA = """
<?xml version="1.0" encoding="UTF-8"?>
<XMLBIBLE biblename="World English Bible (sample)">
<INFORMATION><title>World English Bible</title><language>ENG</language><rights>Public Domain</rights></INFORMATION>
<BIBLEBOOK bnumber="19" bname="Psalms"><CHAPTER cnumber="23">
<VERS vnumber="1">Yahweh is my shepherd: I shall lack nothing.</VERS>
<VERS vnumber="2">He makes me lie down in green pastures. He leads me beside still waters.</VERS>
</CHAPTER></BIBLEBOOK>
<BIBLEBOOK bnumber="43" bname="John"><CHAPTER cnumber="3">
<VERS vnumber="16">For God so loved the world, that he gave his one and only Son.</VERS>
</CHAPTER></BIBLEBOOK>
</XMLBIBLE>
""".trimStart()
