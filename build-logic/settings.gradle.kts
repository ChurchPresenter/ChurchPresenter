// The build's convention plugins: what every module's build file used to repeat, written once.
// Included from the root `settings.gradle.kts` through `pluginManagement { includeBuild(...) }`, so
// a module asks for them by id (`id("cp.jvm-module")`) like any other plugin. See AGENT.md.
rootProject.name = "build-logic"

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
        gradlePluginPortal()
    }
    // The app's own catalogue, so a plugin version is written in one place for both builds.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
