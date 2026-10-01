plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

compose.resources {
    packageOfResClass = "org.churchpresenter.strings.generated.resources"
    publicResClass = true
}

dependencies {
    implementation(libs.compose.runtime)
    api(libs.compose.components.resources)
}
