rootProject.name = "churchpresenter"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
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

// The bundled study data behind the Dictionary tab: the Strong's dictionary itself and the
// interlinear index that says where each number appears. Plain files on the classpath and the
// lookups over them — depended on by :composeApp, which draws the tab and serves the REST routes.
include(":dictionary")

// The HTTP/WebSocket surface the desktop exposes: the wire format, the routes, the pages it serves,
// TLS and the tunnel, plus the client a follower instance consumes it with. What a remote request
// then *does* to the app stays in :composeApp, under `remote/`.
include(":companion-server")

// Every icon the app draws, in one place: the vector drawables the tabs, dialogs and widgets all
// reach for. Assets and their generated accessor, no Kotlin of its own — so nothing has to
// duplicate an icon to use it.
include(":resources")
