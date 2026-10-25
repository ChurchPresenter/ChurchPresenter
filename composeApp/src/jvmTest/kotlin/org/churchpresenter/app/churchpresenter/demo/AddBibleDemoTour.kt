@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.churchpresenter.appsettings.SystemSettingsTab
import org.churchpresenter.bibleformats.catalog.EBibleSource
import org.churchpresenter.bibletab.catalog.BibleCatalogBrowserDialogContent
import org.churchpresenter.bibletab.catalog.BibleCatalogViewModel
import org.churchpresenter.profiles.BibleSettingsTab
import org.churchpresenter.profiles.ProfilesSettingsTab
import org.churchpresenter.settings.withInstalledBible
import java.io.File
import kotlin.test.Test

/** Adding a Bible: where they are kept, downloading one, naming and ordering them, splitting long verses. */
class AddBibleDemoTour {

    @Test
    fun addBible() {
        library()
        File(LIBRARY, "Bibles").listFiles { f -> f.name != "kjv1769.spb" }?.forEach { it.delete() }
        recordTour(
            "add-bible",
            tab = "Bible",
            liveLabel = "Add a Bible",
            steps = 10,
            heading = "Adding a " to "",
            panel = { settings, change ->
                when (screen.value) {
                    "system" -> Box(Modifier.size(900.dp, 640.dp)) { SystemSettingsTab(settings, change) }
                    "catalog" -> Box(Modifier.size(860.dp, 780.dp)) {
                        val models = remember {
                            listOf(BibleCatalogViewModel(EBibleSource, settings.bibleSettings.storageDirectory))
                        }
                        BibleCatalogBrowserDialogContent(
                            viewModels = models,
                            tabLabels = listOf("eBible.org"),
                            onDismiss = {},
                            onBibleInstalled = { name -> change { s -> s.withInstalledBible(name) } },
                        )
                    }
                    "bible" -> Box(Modifier.size(520.dp, 760.dp)) { BibleSettingsTab(settings, change) }
                    else -> Box(Modifier.size(1500.dp, 900.dp)) { ProfilesSettingsTab(settings, change) }
                }
            },
        ) { addBibleTour() }
    }

    private fun DemoTour.addBibleTour() {
        say("intro")
        intro("Download a Bible, install it, and set up how it's shown.")
        storage()
        download()
        translations()
        split()
        say("outro")
        outro()
    }

    /** Settings → System: the folder the Bibles live in. */
    private fun DemoTour.storage() {
        say("open-settings")
        step(
            "Open Settings",
            "Everything about your Bibles starts here",
            find(hasContentDescription("Settings")) { it.top < dp(60f) },
        ) {}
        screen.value = "system"
        openPanel("Settings — System", 900f, 640f)
        say("storage")
        panelNote(
            "Bible storage folder",
            "Where your Bible files are kept",
            panelItem(hasText("Bibles", substring = true)),
            numbered = true,
        )
        say("browse")
        panelNote("Browse", "Choose a different folder", panelItems(hasText("Browse", substring = true)).first())
        say("detected")
        panelNote("Bibles found", "Every Bible file in that folder", panelItem(hasText("kjv1769.spb")))
        say("download-button")
        panelNote(
            "Download Bibles",
            "Hundreds of translations, free to install",
            panelItem(hasText("Download Bibles", substring = true)),
            numbered = true,
        )
        closePanel()
    }

    /** The catalogue: finding a translation, the licensing promise, and the install. */
    private fun DemoTour.download() {
        screen.value = "catalog"
        openPanel("Download Bibles", 860f, 780f)
        waitFor("the list of Bibles has loaded", 60_000L) { downloadButtons().isNotEmpty() }
        say("catalog")
        panelNote("The Bible catalog", "Translations in hundreds of languages", panelItem(hasText("eBible.org")))
        say("languages")
        panelNote("Filter by language", "Or type a name", panelItem(hasText("All languages", substring = true)))
        say("search")
        panelEdit("Search", "Find a translation by name", panelItems(hasSetTextAction()).last(), "World English")
        waitFor("the search narrowed the list", 30_000L) {
            panelItems(hasText("World English", substring = true)).size > 1
        }
        say("pick")
        panelNote(
            "Download",
            "Pick the translation you want",
            downloadButtons().first(),
            click = true,
            numbered = true,
        )
        say("licence")
        panelNote(
            "Copyright and licensing",
            "Show only Bibles your church is licensed to use",
            dialogPopupItem(hasText("Copyright and licensing")),
            numbered = true,
        )
        say("accept")
        panelNote(
            "I understand — Download",
            "You confirm you may use it",
            dialogPopupItem(hasText("I understand", substring = true)),
            click = true,
        )
        waitFor("the Bible is installed", 180_000L) { runCatching { dialogPopupItem(hasText("OK")) }.isSuccess }
        dialogPopupItem(hasText("OK")).node.performClick()
        waitFor("the list is back", 60_000L) { downloadButtons().isNotEmpty() }
        // The list reloads with the install; the search is put back without a word.
        panelItems(hasSetTextAction()).last().node.performTextReplacement("World English")
        waitFor("the installed Bible is listed", 30_000L) { panelItems(hasText("Installed")).isNotEmpty() }
        say("installed")
        panelNote("Installed", "It's in your Bible folder, ready to use", panelItems(hasText("Installed")).first())
        closePanel()
    }

    /** Settings → Bible: which translations, in which order, under which name and abbreviation. */
    private fun DemoTour.translations() {
        say("bible-settings")
        screen.value = "bible"
        menusInPlace = true
        openPanel("Settings — Bible", 520f, 760f)
        panelNote("Translations", "The Bibles you show, in order", panelItem(hasText("Translations")), numbered = true)
        say("add-translation")
        val add = panelReveal(hasText("Add translation"))
        panelNote("Add translation", "Show two or more Bibles at once", add, click = true)
        say("pick-translation")
        panelNote(
            "World English Bible",
            "The one we just downloaded",
            panelItems(hasText("World English", substring = true)).last(),
            click = true,
        )
        say("order")
        panelNote(
            "Order",
            "Move a translation up or down",
            panelItems(hasContentDescription("Move translation down")).first(),
        )
        say("name")
        val name = panelItems(hasText("Name", ignoreCase = true)).first()
        panelNote("Name", "What your church calls it", name, numbered = true)
        say("abbreviation")
        val abbreviation = panelItems(hasText("Abbreviation", ignoreCase = true)).first()
        panelNote("Abbreviation", "The short form shown with each verse", abbreviation)
        closePanel()
    }

    /** Profiles → Bible: long verses split across two slides. */
    private fun DemoTour.split() {
        say("profiles")
        menusInPlace = false
        screen.value = "profiles"
        openPanel("Settings — Profiles", 1500f, 900f)
        panelNote(
            "Profiles → Bible",
            "How verses look on each screen",
            panelItems(hasText("Bible")).first(),
            click = true,
            numbered = true,
        )
        // Splitting is one of the advanced Bible settings.
        panelItem(hasText("Advanced")).node.performClick()
        ui.waitForIdle()
        panelItems(hasText("Open all")).firstOrNull()?.node?.performClick()
        ui.waitForIdle()
        say("split")
        panelNote(
            "Split long verses",
            "Across two slides",
            middleReveal(hasText("Split long verses", substring = true)),
            click = true,
            numbered = true,
        )
        say("split-words")
        panelNote(
            "How long is too long",
            "Split verses longer than this many words",
            middleReveal(hasText("Split verses longer", substring = true)),
        )
        closePanel()
    }
}

/** The catalogue's Download buttons, top to bottom — not the dialog's own "Download Bibles" title. */
private fun DemoTour.downloadButtons() =
    panelItems(hasClickAction() and hasText("Download", substring = true)).filter { it.bounds.width < dp(200f) }

/** Scrolls every scrollable list in the panel (the rail is the first one) until [m] shows. */
private fun DemoTour.middleReveal(m: SemanticsMatcher): Target {
    repeat(40) {
        if (panelItems(m).isNotEmpty()) return panelReveal(m)
        panelItems(hasScrollAction()).forEach {
            it.node.performSemanticsAction(SemanticsActions.ScrollBy) { by -> by(0f, dp(200f)) }
        }
        ui.waitForIdle()
    }
    return panelReveal(m)
}

/** Which settings screen the panel shows. */
private val screen = mutableStateOf("system")
