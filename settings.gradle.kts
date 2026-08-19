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
// The Bitfocus Companion Satellite protocol client: a plain Kotlin library, depended on by
// :composeApp, which wraps it in CompanionSatelliteViewModel.
include(":companion-satellite")
include(":theme")
include(":core-models")
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
