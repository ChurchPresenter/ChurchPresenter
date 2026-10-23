package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.diagnostics.Log

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

/** Runs [warmClasses] on a daemon thread of its own at the lowest priority, so it never competes with drawing. */
internal fun startClassWarmUp() {
    Thread({ warmClasses() }, "class-warm-up").apply {
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

private const val TAG = "ClassWarmUp"
