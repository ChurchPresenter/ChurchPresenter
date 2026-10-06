plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
    // detekt runs these rules inside its own embedded Kotlin compiler, so the stdlib they ship with
    // must be the one detekt was built against, not this build's.
    coreLibrariesVersion = "2.0.21"
}

dependencies {
    compileOnly(libs.detekt.api)
}
