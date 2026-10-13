// A standalone build, deliberately outside the app's settings.gradle.kts: no coverage gate, no CI row.
// Run with the root wrapper: ./gradlew --project-dir prototypes/web-osr run
rootProject.name = "web-osr-prototype"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}
