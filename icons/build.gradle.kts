plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

// Every consumer reaches these through `org.churchpresenter.icons.generated.resources.Res`,
// conventionally imported `as IconRes` because a file that also uses a string resource would
// otherwise have two classes called `Res`. `publicResClass` is what makes the accessor visible
// outside this module at all.
compose.resources {
    packageOfResClass = "org.churchpresenter.icons.generated.resources"
    publicResClass = true
}

// No detekt, no jacoco, no test source set, and that is deliberate: this module holds assets and
// the accessor generated from them, and no Kotlin of its own. There is nothing here for a linter
// to read or a test to exercise -- the root build only wires those in for a module that applies
// the `detekt` and `jacoco` plugins, so leaving them off is what keeps the gates honest rather
// than green-by-vacuum.
dependencies {
    implementation(compose.runtime)
    implementation(compose.components.resources)
}
