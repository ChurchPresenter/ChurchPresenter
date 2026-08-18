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
