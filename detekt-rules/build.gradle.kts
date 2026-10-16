import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("cp.jvm-module")
}

// detekt runs this jar beside its own Kotlin standard library, which is older than the build's, so
// the rules are compiled against the oldest API the build's compiler still accepts.
kotlin {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
}
