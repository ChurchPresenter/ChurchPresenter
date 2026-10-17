// `kotlin-dsl` brings the Kotlin Gradle plugin Gradle embeds, older than the build's own and open to
// unsafe deserialization in the Kotlin build cache (fixed in 2.4.20). The conventions compile with
// the build's version instead -- the one every module already uses.
buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-gradle-plugin")) {
                useVersion(libs.versions.kotlin.get())
                because("Unsafe deserialization in the Kotlin build cache before 2.4.20")
            }
        }
    }
}

plugins {
    `kotlin-dsl`
}

// The plugins these conventions apply are compile-only here: the root build puts each on the
// classpath once (`apply false`), and a convention applies it by id. A second copy loaded from this
// build would be a second Kotlin Gradle plugin in another classloader.
dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.compose:compose-gradle-plugin:${libs.versions.composeMultiplatform.get()}")
    compileOnly("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
    compileOnly("io.github.takahirom.roborazzi:roborazzi-gradle-plugin:${libs.versions.roborazzi.get()}")
    compileOnly("info.solidsoft.gradle.pitest:gradle-pitest-plugin:${libs.versions.pitestPlugin.get()}")
}
