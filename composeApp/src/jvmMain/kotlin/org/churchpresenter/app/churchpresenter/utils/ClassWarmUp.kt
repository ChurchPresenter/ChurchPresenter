package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.diagnostics.Log
import java.io.File
import java.io.IOException
import java.util.jar.JarFile

/**
 * Loads the classes behind the screens an operator opens first, so the JVM does not read them from
 * the jar on the event thread the first time one of them opens.
 *
 * A class is read, verified and defined the first time it is used, on whichever thread uses it; for
 * the settings pages, the profiles editor, the Bible output and the server that is the event
 * thread, and a first opening on a slow disk freezes the window for most of a second. Loading a
 * class here leaves it defined for the whole run, so the screen finds it already there. Nothing is
 * initialised: `Class.forName(name, false, …)` runs no static initialiser, so this cannot change
 * what any class does or when.
 *
 * Call it off the event thread, after the main window's first frame and [warmStrings]
 * ([startClassWarmUp] does). A name that does not load is counted and the rest go on; the count is
 * logged once, never thrown -- the screen will simply load that class itself, as it would have
 * without this.
 *
 * @return how many classes were loaded.
 */
internal fun warmClasses(
    names: List<String> = WARM_CLASSES,
    load: (String) -> Unit = { Class.forName(it, false, ClassWarmUpLoader::class.java.classLoader) },
): Int {
    var failed = 0
    var firstFailure: Throwable? = null
    for (name in names) {
        try {
            load(name)
        } catch (e: ClassNotFoundException) {
            failed++
            firstFailure = firstFailure ?: e
        } catch (e: LinkageError) {
            failed++
            firstFailure = firstFailure ?: e
        }
    }
    if (firstFailure != null) {
        Log.info(TAG, "$failed of ${names.size} classes could not be loaded ahead: ${firstFailure::class.simpleName}")
    }
    return names.size - failed
}

/**
 * Every class under each of [prefixes] (`/`-separated package paths, subpackages included) in the
 * jars and class folders of [classPath] -- so the lambdas, `ComposableSingletons` and small helper
 * classes a screen loads one at a time on its first opening are found without naming each one.
 * A location that cannot be read is skipped.
 */
internal fun classesUnder(
    prefixes: List<String>,
    classPath: String = System.getProperty("java.class.path").orEmpty(),
): List<String> = classPath.split(File.pathSeparator)
    .filter { it.isNotBlank() }
    .map(::File)
    .filter { it.isDirectory || it.name.endsWith(".jar") }
    .flatMap { location -> prefixes.flatMap { classNamesUnder(location, it) } }
    .distinct()

/** The class names under [prefix] (a `/`-separated package path) in [location], a jar or a class folder. */
internal fun classNamesUnder(location: File, prefix: String): List<String> = try {
    val paths = if (location.isDirectory) {
        val root = File(location, prefix)
        root.walkTopDown().filter { it.isFile }.map { prefix + it.relativeTo(root).invariantSeparatorsPath }.toList()
    } else if (location.isFile) {
        JarFile(location).use { jar ->
            jar.entries().asSequence().map { it.name }.filter { it.startsWith(prefix) }.toList()
        }
    } else {
        emptyList()
    }
    paths.filter { it.endsWith(CLASS_SUFFIX) && it.substringAfterLast('/') !in NOT_CLASSES }
        .map { it.removeSuffix(CLASS_SUFFIX).replace('/', '.') }
} catch (e: IOException) {
    Log.info(TAG, "could not list ${location.name}: ${e::class.simpleName}")
    emptyList()
}

/** Runs [warmClasses] on a daemon thread of its own at the lowest priority, so it never competes with drawing. */
internal fun startClassWarmUp() {
    Thread({
        warmClasses()
        warmClasses(classesUnder(WARM_PACKAGES))
    }, "class-warm-up").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
        start()
    }
}

/** The class whose loader [warmClasses] loads through: the app's own, which sees every module's jar. */
private object ClassWarmUpLoader

/**
 * The classes [warmClasses] loads: the file classes of the screens whose first opening was seen
 * stalling while the JVM loaded them, and the main ones beside them. Every name must resolve --
 * `ClassWarmUpTest` fails on one that a rename left behind.
 */
internal val WARM_CLASSES = listOf(
    // The Options dialog and its pages.
    "org.churchpresenter.app.churchpresenter.dialogs.OptionsDialogKt",
    "org.churchpresenter.appsettings.SystemSettingsTabKt",
    "org.churchpresenter.appsettings.SystemSettingsActionsKt",
    "org.churchpresenter.appsettings.SystemStorageCardKt",
    "org.churchpresenter.appsettings.SystemStorageDetailsKt",
    "org.churchpresenter.theme.components.RaisedSelectionControlsKt",
    // Profiles: the settings row kit every page uses, the list, the editor and its pages.
    "org.churchpresenter.profiles.SettingsRowControlsKt",
    "org.churchpresenter.profiles.ProfilesSettingsTabKt",
    "org.churchpresenter.profiles.ProfileListKt",
    "org.churchpresenter.profiles.ProfileEditorKt",
    "org.churchpresenter.profiles.ProfileGeneralPageKt",
    "org.churchpresenter.profiles.ProfileContentPageKt",
    "org.churchpresenter.profiles.ProfileBiblePageKt",
    "org.churchpresenter.profiles.ProfileBackgroundPageKt",
    "org.churchpresenter.profiles.ProfileOutputsPageKt",
    "org.churchpresenter.profiles.BibleSettingsTabKt",
    "org.churchpresenter.profiles.BackgroundSettingsTabKt",
    // What an output draws: the Bible frame and its verse boxes, and the output's content.
    "org.churchpresenter.presenter.BibleFrame",
    "org.churchpresenter.presenter.BibleFrameKt",
    "org.churchpresenter.liveoutput.PresenterOutputContentKt",
    // The Server settings page and its cards.
    "org.churchpresenter.serverui.ServerSettingsTabKt",
    "org.churchpresenter.serverui.ServerClientsCardKt",
    "org.churchpresenter.serverui.CompanionTriggersCardKt",
    "org.churchpresenter.serverui.CalendarSyncCardKt",
    // The song editor's Output pane: its preview, the song's own look and the font picker in it.
    "org.churchpresenter.songs.EditSongOutputPreviewKt",
    "org.churchpresenter.songs.EditSongLookKt",
    "org.churchpresenter.sharedui.composables.FontSettingsDropdownKt",
    // The plugins the companion server installs as it starts.
    "io.ktor.server.plugins.cors.routing.CORSKt",
    "io.ktor.server.plugins.cors.CORSConfig",
    "io.ktor.server.plugins.contentnegotiation.ContentNegotiationKt",
    "io.ktor.server.plugins.statuspages.StatusPagesKt",
    "io.ktor.server.plugins.partialcontent.PartialContentKt",
    "io.ktor.server.websocket.WebSockets",
)

/**
 * The packages whose every class [startClassWarmUp] loads: the operator's tabs, settings pages and
 * dialogs, the shared components they are built from, and the material3 and foundation controls
 * they draw. A first opening of any of them was seen stalling on class loading after
 * [WARM_CLASSES] had run -- the song editor, the System and Profiles settings, the Announcements
 * tab's font picker. About 5,600 classes: half a second of a low-priority thread, and at most
 * 36 MB of metaspace on a cold JVM (less in the app, which has loaded many of them by then).
 * Engines, converters and the off-screen outputs are left out: they load on their own threads.
 */
internal val WARM_PACKAGES = listOf(
    "org/churchpresenter/songs/",
    "org/churchpresenter/bibletab/",
    "org/churchpresenter/announcements/",
    "org/churchpresenter/schedule/",
    "org/churchpresenter/slides/tabs/",
    "org/churchpresenter/appsettings/",
    "org/churchpresenter/profiles/",
    "org/churchpresenter/dialogs/",
    "org/churchpresenter/serverui/",
    "org/churchpresenter/sharedui/",
    "org/churchpresenter/theme/",
    "org/churchpresenter/app/churchpresenter/dialogs/",
    "org/churchpresenter/app/churchpresenter/tabs/",
    "org/churchpresenter/converter/ui/",
    "androidx/compose/material3/",
    "androidx/compose/foundation/",
    // The native file dialogs every Open and Save goes through.
    "io/github/vinceglb/filekit/",
)

private const val CLASS_SUFFIX = ".class"

/** Entries that end in `.class` and are not classes to load. */
private val NOT_CLASSES = setOf("module-info.class", "package-info.class")
private const val TAG = "ClassWarmUp"
