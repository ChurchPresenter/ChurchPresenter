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

// Every asset the app draws or reads, in one place: the icons, the interface strings in all 35
// languages, the fonts and the bundled files. Assets and the one `Res` accessor generated from
// them, no Kotlin of its own — so nothing has to duplicate a resource to use it.
include(":resources")

// Everything the app has ever put on the screen, counted: the song and verse tallies, the
// timestamped play log behind them, the periods a report covers and the CSV/Excel export CCLI
// licence reporting needs. The window that draws it stays in :composeApp.
include(":statistics")
// The app's own widget library: the custom composables every tab and dialog is built from —
// dropdowns, settings fields, segmented buttons, the colour picker, tooltips and the rest. Generic
// by construction: it knows the theme and the resources, and nothing else about the app.
include(":ui-components")
// The Dictionary tab, whole: the Strong's browser and its "In Scripture" panel, the view model
// behind them, the settings tab that styles the output, and the presenter that draws it. The data
// it reads is :dictionary; this is everything the operator and the audience see of it.
include(":dictionary-tab")
// The Dictionary tab's settings tab: the one page of the options dialog that styles what the
// audience sees of a Strong's entry — the word, its definition, its reference and its KJV usage.
// Depends on the settings-field widgets rather than on the dictionary, so it stands alone.
include(":dictionary-settings-tab")
// The Lower Third tab: the animated Lottie band the audience sees over the picture, its presets,
// the offscreen renderer behind the Browser Source overlay, and the ATEM media-pool upload beside
// it. Already callback-driven before it moved, so it needs no port — `PresenterManager` never
// reached it in the first place.
include(":lower-third-tab")
// The page of the options dialog that configures that tab: the Lottie library on the left, a live
// preview of the selected animation on the right, and the window insets the band is placed with
// beneath it. Split from :lower-third-tab the way :dictionary-settings-tab is split from
// :dictionary-tab — but unlike that pair it does depend on the tab module, because the preview is
// the same compottie render with the same bundled fonts.
include(":lower-third-settings-tab")
// The Announcements tab: on-screen text with its slide and scroll animations, and the countdown
// timers beside it. It speaks to the outputs through the AnnouncementsOutput port rather than
// touching PresenterManager, so it knows nothing about the rest of the app.
include(":announcements-tab")
// The Audience Q&A tab: the moderation queue people submit to from their phones, the QR code they
// scan to find it, the remote/display settings dialog beside it and the presenter that puts a
// question on the screen. It reaches the outputs through its own QaOutput port, so `Presenting` and
// `PresenterManager` stay in the app.
include(":qa-tab")
