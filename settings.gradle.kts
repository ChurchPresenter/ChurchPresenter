rootProject.name = "churchpresenter"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // The build's convention plugins (`cp.*`): what every module's build file shares.
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")
// The converter: its own module, compiled and packaged separately, and depended on by
// :composeApp, which opens it in a window from the Help menu.
include(":converter")
// The song library: the grid view of every song in the library, opened from the Help menu beside
// the converter.
include(":songlibrary")
// The models the app and its screens share: what a song is, the `.song` file format, and the
// library folder it lives in. Depended on by :composeApp and :songlibrary, and by whatever screen
// is pulled out into a module of its own next.
include(":core-models")
// The Bitfocus Companion Satellite protocol client: a plain Kotlin library, depended on by
// :composeApp, which wraps it in CompanionSatelliteViewModel.
include(":companion-satellite")
include(":theme")
// The animated lower-third generator: its own module, compiled and packaged separately, and
// depended on by :composeApp, which opens it in a window from the Lower Third settings.
include(":lottieGenerator")
include(":bible-engine")
// The crossword puzzle authoring tool: its own module, not compiled into the app — a build-time
// task copies its encoded puzzles into :composeApp's resources.
include(":crossword")
// The presentation engine: PPTX/PPT/Keynote/PDF parsing, timing and animation. Its own module,
// depended on by :composeApp, which drives it from PresentationViewModel and CompanionServer.
include(":presentation-engine")
// Everything the app persists: the settings data classes, the SettingsManager that loads, migrates
// and saves settings.json, and the constants those defaults are spelled with.
include(":settings")
// Crash reporting and diagnostics: the crash log on disk and the Sentry forwarding behind it.
// Depended on by :composeApp and by every module that needs to report a fault.
include(":diagnostics")
// The Blackmagic ATEM protocol client: a plain Kotlin library speaking the switcher's UDP protocol
// — connect, state dump, upstream/downstream key, media-pool upload. Depended on by :composeApp,
// which wires it to settings and the lower third in AtemBridge.
include(":atem")
// The Planning Center Online client: the OAuth conversation, the Services REST calls and the
// loopback listener that catches the consent redirect. Depended on by :composeApp, which wraps it
// in PlanningCenterImportViewModel.
// NDI (Network Device Interface): the send half of Vizrt's IP video protocol, over the NDI Runtime
// the user installs separately — this module ships no NDI binaries. Plain Kotlin over JNA, with
// every native call behind one interface. Depended on by :composeApp, which wires it to settings
// and the presenters in NdiVideoRenderer.
include(":ndi")
// OMT (Open Media Transport): the open-source IP video protocol, both directions, over the libomt
// and libvmx the app bundles — both are MIT, so unlike NDI they ship inside the installer. Plain
// Kotlin over JNA, every native call behind one interface. Depended on by :composeApp, which wires
// it to settings and the presenters in OmtVideoRenderer and to the Canvas in OmtFrameCache.
include(":omt")
include(":planning-center")
// Bible formats: the .spb converters (USFX, Zefania XML, Beblia) and the catalogues the app
// downloads modules from. Depended on by :composeApp for the in-app browser and by :converter,
// which offers the same conversions from its own window.
include(":bible-formats")

// Song chords: the grammar a song's `[G]lyric` markup is written in — what counts as a chord, what
// counts as a section heading, transposition, and the chord-sheet import that produces the markup.
// Depended on by :composeApp and by :converter, which needs the same rule to write songs out.
include(":song-chords")

// The Bible itself: the loaded translation, its books, verses and search.
include(":bible")

// The Calendar Manager: the planner window that holds every planned service — the month grid, each
// day's services and their run of show — and writes them to `calendar.json` of its own, separate
// from settings and from any `.schedule` file. Depended on by :composeApp, which opens it from the
// Help menu beside the Song Library Manager and loads a planned service into the Schedule tab.
include(":calendar")
// The Calendar Manager's model without its window: a run of show, its clocks and cues, the
// reference parser and the PDF export. The Schedule tab, the helper and the song editor read it;
// :calendar draws it.
include(":calendar-core")

include(":strings")

include(":icons")

include(":shared-ui")

include(":slides")
// What the picture and presentation outputs draw, without the tabs: PicturePresenter,
// PresentationPresenter, PictureDecoder. :slides, :presenter, :canvas and :live-output use it.
include(":slides-engine")

include(":media")
// The playback under the Media tab, without the tab: the VLC and software players, VLC discovery,
// subtitles, MediaPresenter and MediaViewModel. The outputs, the presenter and canvas play with it.
include(":media-engine")

// The Web tab and the embedded Chromium it and the output window browse with (JCEF). Depended on by
// :composeApp, which hands it the live output through WebOutput.
include(":web")
// The embedded Chromium (JCEF) without the Web tab: CefManager, the embedded web view and
// WebsitePresenter, which the output window draws a page with.
include(":web-engine")

// The hidden Crossword tab and the decoder for the puzzles it plays. Not :crossword, which is the
// authoring tool and must stay out of the app; the puzzles reach this module by a build-time copy.
include(":crossword-tab")

// The Q&A tab and QAManager, the questions a congregation sends from their phones. Depended on by
// :composeApp, which hands it the live output through QAOutput and draws the remote dialog.
include(":qa")
include(":dictionary")
include(":stt")
// STTManager without the STT tab: the caption server's socket.io client, the capture and the
// transcript. The Bible tab's detection and the outputs read it.
include(":stt-engine")
include(":announcements")
include(":lower-third")
// What the Lower Third tab drives, without the tab: the lottie render cache and its off-screen renderer,
// the output's presenter, the ATEM sequencer and the bundled lottie fonts. The outputs and the
// server use it.
include(":lower-third-engine")

// The helper lamp: tips, display setup, typed tasks and the spotlight that points at controls.
// Depended on by :composeApp, which carries the actions out through HelperActionExecutor.
include(":helper")

// The Songs tab, SongsViewModel and the song library on disk. Depended on by :composeApp, which
// hands it the song editor, the title slide, statistics and the Instance Link catalog.
include(":songs")

// The Bible tab, BibleViewModel, the cross references and the verse-sequence log. Depended on by
// :composeApp, which hands it the live output, the detection engine's status and statistics.
include(":bible-tab")

// The companion server and Instance Link: the Ktor REST/WebSocket API phones and other instances
// use, the tunnel, SSL and calendar sync. Depended on by :composeApp, which applies what remote
// clients ask for to the live output.
include(":server")
// The signage screens' wire types (a copy of the ChurchPresenter-Signage repo's :core, kept to its
// fixtures) and the hub of paired screens the server and the Signage tab share.
include(":signage-core")
// The Signage tab (dev mode only): the lobby screens, the players asking to join, and the signage
// folder their playlists come from.
include(":signage")

// The Schedule tab and ScheduleViewModel: the running order, its rows, and the .schedule files it
// opens and saves. Depended on by :composeApp, which hands it the Planning Center import.
include(":schedule")

// The Canvas tab: SceneViewModel, the scene editor and the source property editors. Depended on by
// :composeApp.
include(":canvas")
// What a scene is drawn with, without the Canvas tab: the scene renderer and its capture sources --
// cameras, screen and window capture, the browser source, NDI and OMT in, DeckLink. The outputs and
// the presenter draw with it.
include(":canvas-engine")

// What the song and Bible outputs draw: the slides, their looks and layouts, the backgrounds and the
// Lottie bands. Depended on by :composeApp, which keeps the windows and the off-screen outputs.
include(":presenter")

// The Profiles settings pages -- each output profile's looks, layout, backgrounds and previews -- and the
// settings row kit every settings page is built from. Depended on by :composeApp.
include(":profiles")

// The Companion Surface tab and CompanionSatelliteViewModel: the Compose face of
// :companion-satellite, which stays free of any UI toolkit. Depended on by :composeApp.
include(":companion-surface")

// The OBS Studio integration: the obs-websocket client, the scene each content type switches to,
// and the settings page. Depended on by :composeApp.
include(":obs")

// The layer model: the layers, the cues that go on them, and what is on air and cued. Depended on
// by :composeApp.
include(":live-show")

// The action vocabulary -- one list of things that change the show -- and the runner that plays
// action lists through the app's host. Depended on by :composeApp and :calendar.
include(":show-control")
include(":control-in")
// The live output: PresenterManager and what is on air, the output windows and the stage monitor, and
// the off-screen outputs (NDI, OMT, Browser Source, DeckLink). Depended on by :composeApp.
// What each tab needs of the live output, as interfaces -- BibleOutput, SlidesOutput, QAOutput and
// the rest -- and the frame a deck plays. The tabs declare against it, :live-output implements it,
// and neither depends on the other.
// The build's own detekt rules: the live-safe check on the output modules (AGENT.md, "Live-safe
// output paths"). On no app classpath; build-logic's cp.detekt hands it to detekt as a plugin.
include(":detekt-rules")
include(":output-api")
// The output guard: the second process that keeps a still of each output under its window and shows
// it if the app dies (docs/SHOW_CONTROL.md, Output isolation). AWT only.
include(":output-guard")
include(":live-output")

// Statistics: what was presented and when, the play log, and the CCLI report window over them.
// Depended on by :composeApp.
include(":statistics")

// The updater: the GitHub release check, the installer download and the update window. Depended on
// by :composeApp.
include(":updater")

// What the app reports about itself: the live-map ping, the usage counts it carries,
// the contact form, the device report and the GPU it runs on.
include(":telemetry")

// The System settings page and the first-run setup wizard. Depended on by :composeApp.
include(":app-settings")

// The Server settings page, calendar sync and Instance Link's windows: the Compose face of :server.
// Depended on by :composeApp.
include(":server-ui")

// The app's dialogs and small windows -- About, Contact Us, keyboard shortcuts, the remote and
// show-control dialogs. Depended on by :composeApp.
include(":dialogs")
