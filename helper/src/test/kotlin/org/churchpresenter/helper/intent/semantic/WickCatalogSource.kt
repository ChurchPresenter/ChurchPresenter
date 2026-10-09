package org.churchpresenter.helper.intent.semantic

import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.models.labelRes
import java.io.File

/**
 * Reads the codebase into what Wick's catalog says: every piece of text that describes something Wick can
 * do, and where it leads. Nothing here is written by hand except which files make up each Settings page:
 *
 * - every [SuggestedRequest] chip, by its request and its keywords;
 * - every multi-word phrase in the `*Topics.kt` tables that the rules turn into an action;
 * - every control tagged with `guideTarget(GuideTargets.X)`, by the labels written beside it;
 * - every Settings page, by the labels its files show;
 * - every row of the Profiles settings page, by its label, once for each profile page that shows it;
 * - every tab and keyboard shortcut, by its own label.
 *
 * English text comes from `strings/…/values/strings.xml`: the model reads English, and every other
 * language reaches it through the glossaries.
 */
internal class WickCatalogSource(private val root: File) {

    data class Item(val target: CatalogTarget, val text: String)

    private val english: Map<String, String> = readEnglishStrings(
        File(root, "strings/src/main/composeResources/values/strings.xml"),
    )
    private val rules = RuleIntentResolver()

    /** Every item, sorted so the file diffs line by line when the code changes. */
    fun items(): List<Item> =
        (suggested() + rulePhrases() + controls() + settingsPages() + profileRows() + pageRows() + tabs() + shortcuts())
            .distinctBy { it.target.format() to it.text }
            .sortedWith(compareBy({ it.target.format() }, { it.text }))

    /** Each `GuideTargets` constant the scan found no label beside — reported, so a gap is visible. */
    fun unlabelledControls(): List<String> {
        val labelled = controls().map { (it.target as CatalogTarget.Control).id }.toSet()
        return guideTargetIds().values.filter { it !in labelled && placeOf(it) != Place.Unreachable }.sorted()
    }

    private fun suggested(): List<Item> = SuggestedRequest.entries.flatMap { request ->
        listOf(request.request, request.keywords).map { Item(CatalogTarget.Suggested(request), it) }
    }

    private fun rulePhrases(): List<Item> {
        val chips = SuggestedRequest.entries.map { it.request }.toSet()
        return File(root, "helper/src/main/kotlin/org/churchpresenter/helper/intent")
            .listFiles { file -> file.name.endsWith("Topics.kt") }.orEmpty().sortedBy { it.name }
            .flatMap { file -> STRING_LITERAL.findAll(file.readText()).map { it.groupValues[1] }.toList() }
            .filter { PHRASE.matches(it) && ' ' in it && it !in chips }
            .distinct()
            .filter { rules.resolveNow(it, CONTEXT) != Resolution.Unknown }
            .map { Item(CatalogTarget.Request(it), it) }
    }

    private fun controls(): List<Item> {
        val ids = guideTargetIds()
        return sourceFiles().flatMap { file ->
            val code = file.readText()
            TAGGED.findAll(code).toList().flatMap { match ->
                val id = ids[match.groupValues[1]] ?: return@flatMap emptyList()
                val before = when (val place = placeOf(id)) {
                    Place.Unreachable -> return@flatMap emptyList()
                    Place.Visible -> null
                    is Place.Behind -> place.before
                }
                val keys = labelsOfCall(code, match.range)
                val labelKey = keys.firstOrNull() ?: return@flatMap emptyList()
                keys.mapNotNull { key -> label(key) }.map { Item(CatalogTarget.Control(id, labelKey, before), it) }
            }
        }
    }

    private fun settingsPages(): List<Item> {
        val perPage = SETTINGS_FILES.mapValues { (_, files) ->
            files.flatMap { path -> STRING_KEY.findAll(File(root, path).readText()).map { it.groupValues[1] }.toList() }
                .mapNotNull(::label).toSet()
        }
        val shared = perPage.values.flatten().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return perPage.flatMap { (page, texts) ->
            (texts - shared).map { Item(CatalogTarget.Settings(page), it) }
        }
    }

    /**
     * Every `SettingsRow(Res.string.x, …)` of the Profiles editor: its label and the other labels in its
     * call (the line under it), each said bare for the first page that shows the row and after that
     * page's words ("song lyrics margins") for every page, so the request's own words pick the page.
     */
    private fun profileRows(): List<Item> = PROFILE_ROW_FILES.flatMap { (file, filePages) ->
        val code = File(root, "$PROFILES_DIR/$file").readText()
        PROFILE_ROW.findAll(code).toList().flatMap { match ->
            val key = match.groupValues[1]
            val pages = PROFILE_ROW_FUNCTIONS["$file#${enclosingFunction(code, match.range.first)}"] ?: filePages
            val texts = (listOf(key) + labelsOfCall(code, match.groups[1]!!.range)).distinct().mapNotNull(::label)
            pages.flatMapIndexed { i, page ->
                val said = texts.map { "${PAGE_WORDS.getValue(page)} $it" } + if (i == 0) texts else emptyList()
                said.map { Item(CatalogTarget.ProfileRow(key, page), it) }
            }
        }
    }

    /** Every keyed row of the other Settings pages — `SettingRow(Res.string.x, …)` and its kin — by its label. */
    private fun pageRows(): List<Item> =
        SETTINGS_FILES.filterKeys { it != SettingsPage.PROFILES }.flatMap { (page, files) -> pageRowsIn(page, files) }

    private fun pageRowsIn(page: SettingsPage, files: List<String>): List<Item> = run {
        files.flatMap { path ->
            val code = File(root, path).readText()
            PAGE_ROW.findAll(code).toList().flatMap { match ->
                val key = match.groupValues[1]
                (listOf(key) + labelsOfCall(code, match.groups[1]!!.range)).distinct().mapNotNull(::label)
                    .map { Item(CatalogTarget.PageRow(key, page), it) }
            }
        }
    }

    /** Each file with a `SettingsRow(Res.string…` the scan reads no page for — reported, so a gap is visible. */
    fun unmappedProfileRowFiles(): List<String> = File(root, PROFILES_DIR).listFiles().orEmpty()
        .filter { it.extension == "kt" && PROFILE_ROW.containsMatchIn(it.readText()) }
        .map { it.name }.filter { it !in PROFILE_ROW_FILES }.sorted()

    private fun enclosingFunction(code: String, at: Int): String =
        FUNCTION.findAll(code.substring(0, at)).lastOrNull()?.groupValues?.get(1).orEmpty()

    private fun tabs(): List<Item> = Tabs.entries.filter { it != Tabs.CROSSWORD }.mapNotNull { tab ->
        english[tab.labelRes.key]?.let { Item(CatalogTarget.Tab(tab), "${clean(it)} tab") }
    }

    private fun shortcuts(): List<Item> = ShortcutAction.entries.mapNotNull { action ->
        label(action.descriptionRes.key)?.let { Item(CatalogTarget.Shortcut(action), it) }
    }

    /**
     * The string keys in the argument list of the call the tag at [tag] is an argument of —
     * `ToolbarKey(text = stringResource(Res.string.background), …, modifier = Modifier.guideTarget(…))` —
     * in the order they are written. Only that call: a label beside it belongs to another control.
     */
    private fun labelsOfCall(code: String, tag: IntRange): List<String> {
        var depth = 0
        var start = tag.first - 1
        while (start >= 0) {
            when (code[start]) {
                ')' -> depth++
                '(' -> if (depth == 0) break else depth--
            }
            start--
        }
        depth = 0
        var end = tag.last + 1
        while (end < code.length) {
            when (code[end]) {
                '(' -> depth++
                ')' -> if (depth == 0) break else depth--
            }
            end++
        }
        if (start < 0 || end >= code.length) return emptyList()
        return STRING_KEY.findAll(code.substring(start, end)).map { it.groupValues[1] }
            .filter { label(it) != null }.distinct().toList()
    }

    /** The English text of [key], or null for a key that says nothing about a feature. */
    private fun label(key: String): String? {
        if (key.startsWith("helper_") || key in GENERIC_KEYS) return null
        val text = english[key]?.let(::clean) ?: return null
        // A label starts with a letter and has a real word in it: not a fragment left by a placeholder.
        return text.takeIf {
            it.length in MIN_TEXT..MAX_TEXT && it.first().isLetter() && WORD.containsMatchIn(it)
        }
    }

    private fun guideTargetIds(): Map<String, String> =
        GUIDE_TARGET.findAll(File(root, GUIDE_TARGETS_FILE).readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun sourceFiles(): List<File> = root.listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".") && it.name != "build" }
        .flatMap { module ->
            listOf("src/main/kotlin", "src/jvmMain/kotlin").map { File(module, it) }.filter { it.isDirectory }
        }
        .flatMap { dir -> dir.walk().filter { it.isFile && it.extension == "kt" }.toList() }
        .sortedBy { it.path }

    companion object {
        private val CONTEXT = ResolveContext(language = "en")
        private val STRING_LITERAL = Regex("\"([^\"\\\\$]+)\"")
        private val PHRASE = Regex("[a-z0-9' ]+")
        private val TAGGED = Regex("""guideTarget\(GuideTargets\.([A-Z_]+)\)""")
        private val STRING_KEY = Regex("""Res\.string\.([a-z0-9_]+)""")
        private val GUIDE_TARGET = Regex("""val ([A-Z_]+) = GuideTarget\("([^"]+)"\)""")
        private val WORD = Regex("\\p{L}{3,}")
        private const val GUIDE_TARGETS_FILE =
            "shared-ui/src/main/kotlin/org/churchpresenter/sharedui/guide/GuideTarget.kt"
        private const val MIN_TEXT = 4
        private const val MAX_TEXT = 140

        /** Labels every page and dialog shows, which say nothing about where a feature is. */
        private val GENERIC_KEYS = setOf(
            "ok", "cancel", "close", "save", "delete", "yes", "no", "apply", "done", "back", "next", "add", "remove",
            "edit", "browse", "reset", "clear", "none", "default", "enabled", "disabled", "on", "off", "name",
        )

        private const val PROFILES_DIR = "profiles/src/main/kotlin/org/churchpresenter/profiles"
        private val PROFILE_ROW = Regex("""SettingsRow\(\s*Res\.string\.([a-z0-9_]+)""")
        private val PAGE_ROW =
            Regex(
                """(?:SettingRow|SettingSwitchRow|GeneralToggleRow|CompanionTextRow)""" +
                    """\(\s*(?:label\s*=\s*)?Res\.string\.([a-z0-9_]+)""",
            )
        private val FUNCTION = Regex("""fun (?:[A-Za-z]+\.)?([A-Za-z]+)\(""")

        /**
         * The profile pages each file's rows show on, the likeliest first — a file of shared rows (the
         * text look, a background) lists every page that draws it. Page names are `ProfileFocus.page`'s.
         */
        val PROFILE_ROW_FILES: Map<String, List<String>> = mapOf(
            "ProfileGeneralPage.kt" to listOf("GENERAL"),
            "ProfileLinkingGroup.kt" to listOf("GENERAL"),
            "ProfileContentPage.kt" to listOf("CONTENT"),
            "ProfileScaleRow.kt" to listOf("CONTENT"),
            "ProfileSongsPage.kt" to listOf("SONGS"),
            "SongBoxRows.kt" to listOf("SONGS"),
            "SongElementMove.kt" to listOf("SONGS"),
            "ProfileBiblePage.kt" to listOf("BIBLE"),
            "BibleBoxTarget.kt" to listOf("BIBLE"),
            "TextLookRows.kt" to listOf("SONGS", "BIBLE"),
            "TextBoxRows.kt" to listOf("SONGS", "BIBLE", "CAPTIONS", "DICTIONARY", "QA", "STAGE_MONITOR"),
            "ContentBackgroundGroup.kt" to listOf("SONGS", "BIBLE", "BACKGROUND"),
            "BackgroundEditorRows.kt" to listOf("BACKGROUND", "SONGS", "BIBLE"),
            "ProfileBackgroundPage.kt" to listOf("BACKGROUND"),
            "ProfilePageGroups.kt" to listOf("SONGS", "BIBLE", "CAPTIONS", "DICTIONARY", "STAGE_MONITOR"),
            "ProfileCaptionsPage.kt" to listOf("CAPTIONS"),
            "CaptionReadingGroup.kt" to listOf("CAPTIONS"),
            "DisplayTextRows.kt" to listOf("CAPTIONS", "SUBTITLES", "QA"),
            "ItemBoxGroup.kt" to listOf("DICTIONARY", "CAPTIONS", "SUBTITLES", "QA", "STAGE_MONITOR"),
            "ProfileOverlayPages.kt" to listOf("SUBTITLES", "QA"),
            "ProfileDictionaryPage.kt" to listOf("DICTIONARY"),
            "ProfileStagePage.kt" to listOf("STAGE_MONITOR"),
            "ProfileStageText.kt" to listOf("STAGE_MONITOR"),
        )

        /** Where one function of a file draws on a page of its own: `file#function` to its pages. */
        private val PROFILE_ROW_FUNCTIONS: Map<String, List<String>> = mapOf(
            "ProfileOverlayPages.kt#ProfileSubtitlesPage" to listOf("SUBTITLES"),
            "ProfileOverlayPages.kt#ProfileQaPage" to listOf("QA"),
        )

        /** The words a request uses for each profile page, put before a row's label. */
        private val PAGE_WORDS = mapOf(
            "GENERAL" to "profile", "OUTPUTS" to "profile outputs", "CONTENT" to "profile content",
            "SONGS" to "song lyrics", "BIBLE" to "bible verse", "BACKGROUND" to "background",
            "CAPTIONS" to "live captions", "SUBTITLES" to "subtitles", "QA" to "q&a questions",
            "DICTIONARY" to "dictionary", "STAGE_MONITOR" to "stage monitor",
        )

        /** Which files make up each Settings page. A test fails when one of them is gone. */
        val SETTINGS_FILES: Map<SettingsPage, List<String>> = run {
            val tabs = "composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/dialogs/tabs"
            val profiles = "profiles/src/main/kotlin/org/churchpresenter/profiles"
            mapOf(
                SettingsPage.SYSTEM to listOf(
                    "$tabs/SystemSettingsTab.kt", "$tabs/SystemSettingsActions.kt", "$tabs/SystemStorageCard.kt",
                    "$tabs/SystemStorageDetails.kt", "$tabs/TabLabelsRow.kt",
                ),
                SettingsPage.BIBLE to listOf("$profiles/BibleSettingsTab.kt"),
                SettingsPage.BACKGROUND to listOf("$profiles/BackgroundSettingsTab.kt"),
                SettingsPage.PROFILES to listOf("$profiles/ProfilesSettingsTab.kt"),
                SettingsPage.PROJECTION to listOf(
                    "$tabs/ProjectionSettingsTab.kt", "$tabs/ProjectionScreenAssignmentCard.kt",
                    "$tabs/ProjectionNdiCard.kt", "$tabs/ProjectionOmtCard.kt", "$tabs/ProjectionFfmpegCard.kt",
                    "$tabs/ProjectionBrowserSourceCard.kt", "$tabs/OutputProfilePicker.kt",
                ),
                SettingsPage.SERVER to listOf(
                    "$tabs/ServerSettingsTab.kt", "$tabs/ServerClientsCard.kt", "$tabs/CalendarSyncCard.kt",
                ),
                SettingsPage.ATEM to
                    listOf("lower-third/src/main/kotlin/org/churchpresenter/lowerthird/AtemSettingsTab.kt"),
                SettingsPage.INTEGRATIONS to listOf(
                    "obs/src/main/kotlin/org/churchpresenter/obs/OBSSettingsTab.kt",
                    "$tabs/CompanionSatelliteSettingsTab.kt", "$tabs/CompanionTriggersCard.kt",
                ),
            )
        }

        /** Where a tagged control lives, as far as the helper can get there. */
        sealed interface Place {
            /** Always on screen: the toolbar, the preview, the schedule. */
            data object Visible : Place

            /** Behind [before]: a tab to select or a Settings page to open. */
            data class Behind(val before: CatalogTarget.Before) : Place

            /** Inside a dialog the helper cannot open on its own (the song editor, the Bible catalog). */
            data object Unreachable : Place
        }

        /** Where the control [id] lives, from the area its id starts with. */
        fun placeOf(id: String): Place {
            val area = id.substringBefore('.')
            val behind = when (area) {
                "schedule", "preview", "toolbar" -> return Place.Visible
                "settings" -> SettingsPage.entries.find { it.name.equals(id.split('.')[1], ignoreCase = true) }
                    ?.let(CatalogTarget.Before::OnSettings)
                else -> AREA_TABS[area]?.let(CatalogTarget.Before::OnTab)
            }
            return behind?.let(Place::Behind) ?: Place.Unreachable
        }

        private val AREA_TABS = mapOf(
            "songs" to Tabs.SONGS, "bible" to Tabs.BIBLE, "pictures" to Tabs.PICTURES,
            "presentation" to Tabs.PRESENTATION, "media" to Tabs.MEDIA, "lowerThird" to Tabs.LOWER_THIRD,
            "announcements" to Tabs.ANNOUNCEMENTS, "web" to Tabs.WEB, "qa" to Tabs.QA, "canvas" to Tabs.CANVAS,
        )

        /** Placeholders, markup and escapes taken out, so the model reads the words. */
        fun clean(text: String): String = text
            .replace(Regex("%\\d\\$[sd]"), "")
            .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ").trim()

        fun readEnglishStrings(file: File): Map<String, String> =
            Regex("""<string name="([a-z0-9_]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }
}
