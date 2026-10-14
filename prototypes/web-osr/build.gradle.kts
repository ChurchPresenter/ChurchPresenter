plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation("me.friwi:jcefmaven:143.0.14")
}

compose.desktop {
    application {
        mainClass = "org.churchpresenter.osrproto.MainKt"
        // Deliberately none of the app's sun.awt / sun.lwawt exports: the point is to find out
        // whether the windowless path still needs them.
    }
}

// `-Purl=…` picks the page, `-Psize=3840x2160` the render size, `-Pfps=30` the frame rate,
// `-Pscale=2` the device scale Chromium is told (the page is then size÷scale DIPs),
// `-PlogKeys=1` prints every key and wheel event forwarded to the page, `-PnoIme=1` turns input
// methods off on both windows (a diagnostic for the Windows language-switch freeze).
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    listOf("url", "size", "fps", "scale", "logKeys", "noIme").forEach { key ->
        project.findProperty(key)?.let { systemProperty("osr.$key", it) }
    }
}

// Prints the run classpath, to launch the JVM by hand when Gradle's output mixing gets in the way.
tasks.register("printRunClasspath") {
    val cp = sourceSets.main.get().runtimeClasspath
    doLast { println("CP=" + cp.asPath) }
}
