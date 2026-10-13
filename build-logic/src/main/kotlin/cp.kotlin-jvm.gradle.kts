import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// A plain Kotlin/JVM module of this build: Java 21, the build's group and version.
plugins {
    id("cp.base")
}

apply(plugin = "org.jetbrains.kotlin.jvm")

group = "org.churchpresenter"

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(21)
}
