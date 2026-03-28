import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.Calendar

val versionYear = Calendar.getInstance().get(Calendar.YEAR) % 100

fun gitCommitCount(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim().toInt()
    } catch (_: Exception) { 0 }
}

fun gitCommitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim()
    } catch (_: Exception) { "unknown" }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
}

// Detect current OS classifier for JavaFX native binaries
fun currentOsClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") -> if (arch.contains("aarch64")) "mac-aarch64" else "mac"
        os.contains("win") -> "win"
        else -> "linux"
    }
}

// Detect current OS+arch for JCEF native binaries
fun currentJcefPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") -> if (arch.contains("aarch64")) "macosx-arm64" else "macosx-amd64"
        os.contains("win") -> when {
            arch.contains("aarch64") -> "windows-arm64"
            arch.contains("x86") && !arch.contains("64") -> "windows-i386"
            else -> "windows-amd64"
        }
        else -> when {
            arch.contains("aarch64") -> "linux-arm64"
            arch.contains("arm") -> "linux-arm"
            else -> "linux-amd64"
        }
    }
}

configurations.all {
    resolutionStrategy {
        // Force ktx coroutines version to prevent cast exception
        force(libs.kotlinx.coroutines.core)
        force(libs.kotlinx.coroutines.core.jvm)
        force(libs.kotlinx.coroutines.swing)
        force(libs.kotlin.stdlib)
    }
}

kotlin {
    jvm()

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.sqlite.jdbc)
            implementation(libs.kotlinx.serialization.json)
            implementation("io.github.alexzhirkevich:compottie:2.0.0-rc01")
            implementation("io.github.alexzhirkevich:compottie-dot:2.0.0-rc01")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            // Sentry crash reporting
            implementation(libs.sentry)
            implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
            implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.10.1")
            // Apache PDFBox for PDF slide extraction
            implementation("org.apache.pdfbox:pdfbox:2.0.30")
            // Apache POI for PowerPoint slide extraction
            implementation("org.apache.poi:poi:5.2.5")
            implementation("org.apache.poi:poi-ooxml:5.2.5")
            implementation("org.apache.poi:poi-scratchpad:5.2.5")
            // Ktor server for companion API
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.cors)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.status.pages)
            // VLCJ for media playback (requires VLC installed on system)
            implementation("uk.co.caprica:vlcj:4.8.3")
            implementation("net.java.dev.jna:jna:5.16.0")
            implementation("net.java.dev.jna:jna-platform:5.16.0")
            implementation("com.google.zxing:core:3.5.3")
            implementation("com.google.zxing:javase:3.5.3")
            // JCEF — embedded Chromium browser for web presenter
            implementation("me.friwi:jcefmaven:143.0.14")
            // Bundle platform-specific Chromium binaries so no runtime download is needed
            val jcefNativesVersion = "jcef-cffac27+cef-143.0.14+gdd46a37+chromium-143.0.7499.193"
            runtimeOnly("me.friwi:jcef-natives-${currentJcefPlatform()}:$jcefNativesVersion")
            // JavaFX for WebView (website presenter, Lottie settings)
            val jfxClassifier = currentOsClassifier()
            val jfxModules = listOf("javafx-base", "javafx-graphics", "javafx-media", "javafx-swing", "javafx-controls", "javafx-web")
            jfxModules.forEach { module ->
                implementation("org.openjfx:$module:21:$jfxClassifier")
            }
            // Windows-specific: also pull win natives when building on Windows
            if (jfxClassifier == "win") {
                jfxModules.forEach { module ->
                    runtimeOnly("org.openjfx:$module:21:win")
                }
            }
            // DBus for Linux file chooser integration
            implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
            implementation("com.github.hypfvieh:dbus-java-transport-junixsocket:5.2.0")
        }
    }
}

// Resolve Java 21 home for both the run task and packaging tasks.
// Java 24 breaks jlink/jpackage used by Compose Desktop — always use Java 21.
// 1. Try Gradle's toolchain API (works on any machine with JDK 21 registered).
// 2. Fall back to a manual path scan for common macOS install locations.
val resolvedJdk21Home: String? = run {
    try {
        val launcher = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }.get()
        // executablePath is …/bin/java; go up two levels to get JAVA_HOME
        launcher.executablePath.asFile.parentFile?.parentFile?.absolutePath
    } catch (_: Exception) {
        null
    }
} ?: run {
    val jdk21Paths = listOf(
        "${System.getProperty("user.home")}/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home",
        "${System.getProperty("user.home")}/Library/Java/JavaVirtualMachines/jdk-21.0.6+7/Contents/Home",
        "${System.getProperty("user.home")}/Library/Java/JavaVirtualMachines/temurin-21.0.6/Contents/Home",
        "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home",
        "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home",
        "/Library/Java/JavaVirtualMachines/temurin-21.0.6.jdk/Contents/Home"
    )
    jdk21Paths.firstOrNull { path -> File("$path/bin/java").exists() }
}

compose.desktop {
    application {
        mainClass = "org.churchpresenter.app.churchpresenter.MainKt"

        if (resolvedJdk21Home != null) {
            javaHome = resolvedJdk21Home
        }

        jvmArgs(
            // Memory
            "-Xms512m",
            "-Xmx1536m",
            // GC
            "-XX:+UseG1GC",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:G1NewSizePercent=20",
            "-XX:G1ReservePercent=20",
            "-XX:MaxGCPauseMillis=50",
            "-XX:+UseStringDeduplication",
            // Rendering - macOS requires Metal, other platforms use OpenGL
            "-Dawt.useSystemAAFontSettings=on",
            "-Dswing.aatext=true",
            // Reflective access needed by Apache POI, PDFBox, and JavaFX internals
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
            // JCEF on macOS needs access to sun.awt internals.
            // --add-exports is required for direct bytecode access (CefBrowserWindowMac references
            // sun.awt.AWTAccessor directly, not via reflection, so --add-opens alone is insufficient).
            "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
        )

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "ChurchPresenter"
            val commits = gitCommitCount()
            // Windows MSI limits each version segment to 0-255, so split commit count across minor.patch
            packageVersion = "$versionYear.${commits / 256}.${commits % 256}"
            description = "Church Presenter - Presentation software for worship services"
            copyright = "© 2025 Church Presenter. All rights reserved."
            vendor = "Church Presenter"

            // Bundle app resources (Lottie-Gen) alongside the packaged app.
            // These land in Contents/app/resources/ on macOS.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("src/jvmMain/appResources"))

            // Bundle ALL dependency JARs into the distribution — critical for standalone mode
            includeAllModules = true

            val commonJvmArgs = listOf(
                "-Xms512m",
                "-Xmx1536m",
                "-XX:+UseG1GC",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:G1NewSizePercent=20",
                "-XX:G1ReservePercent=20",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+UseStringDeduplication",
                "-Dawt.useSystemAAFontSettings=on",
                "-Dswing.aatext=true",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
            )

            macOS {
                bundleID = "org.churchpresenter.app"
                iconFile.set(project.file("src/jvmMain/appResources/macos/icon.icns"))
                jvmArgs(*commonJvmArgs.toTypedArray())
            }

            windows {
                menuGroup = "ChurchPresenter"
                perUserInstall = false
                dirChooser = true
                upgradeUuid = "A1B2C3D4-E5F6-4789-A012-3456789ABCDE"
                iconFile.set(project.file("src/jvmMain/appResources/windows/icon.ico"))
                jvmArgs(*commonJvmArgs.toTypedArray())
            }

            linux {
                iconFile.set(project.file("src/jvmMain/appResources/linux/icon.png"))
                jvmArgs(*commonJvmArgs.toTypedArray())
            }
        }
    }
}

// Apply JCEF-required JVM args to every JavaExec task in this subproject.
// This covers:
//   • ./gradlew :composeApp:run  (Compose Desktop run task)
//   • IntelliJ's auto-generated run configuration for fun main()
//     (task name: org.churchpresenter.app.churchpresenter.MainKt.main())
//   • Any other direct Java execution task
// Without this, IntelliJ's run button bypasses compose.desktop.application.jvmArgs
// entirely, causing CefBrowserWindowMac to crash with IllegalAccessError on sun.awt.
val jcefJvmArgs = listOf(
    "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.lwawt=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
)

tasks.withType<JavaExec>().configureEach {
    jvmArgs(jcefJvmArgs)
}

// Generate BuildConfig with version info accessible at runtime
val generateBuildConfig by tasks.registering {
    val commitHash = gitCommitHash()
    val commits = gitCommitCount()
    val appVersion = "$versionYear.${commits / 256}.${commits % 256}"
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")

    outputs.dir(outputDir)

    doLast {
        val dir = outputDir.get().asFile.resolve("org/churchpresenter/app/churchpresenter")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """
            |package org.churchpresenter.app.churchpresenter
            |
            |object BuildConfig {
            |    const val APP_VERSION = "$appVersion"
            |    const val COMMIT_HASH = "$commitHash"
            |    const val COMMIT_COUNT = "$commits"
            |    const val VERSION_DISPLAY = "$appVersion ($commitHash)"
            |}
            """.trimMargin()
        )
    }
}

kotlin {
    sourceSets {
        jvmMain {
            kotlin.srcDir(generateBuildConfig.map { layout.buildDirectory.dir("generated/buildconfig") })
        }
    }
}

tasks.named("compileKotlinJvm") {
    dependsOn(generateBuildConfig)
}

// Workaround: avoid Gradle incremental state tracking on Compose resource generation tasks
val problematicTasks = setOf(
    "generateResourceAccessorsForJvmMain",
    "generateComposeResClass",
    "generateExpectResourceCollectorsForCommonMain"
)

tasks.matching { it.name in problematicTasks }.configureEach {
    doNotTrackState("Temporary workaround: OneDrive placeholder snapshot errors")
}

// Copy Lottie-Gen into appResources so it gets bundled with the packaged app.
val copyLottieGen by tasks.registering(Copy::class) {
    from(rootProject.file("Lottie-Gen"))
    into(layout.projectDirectory.dir("src/jvmMain/appResources/common/Lottie-Gen"))
}

afterEvaluate {
    tasks.matching { t ->
        t.name == "prepareAppResources" ||
        t.name.startsWith("createDistributable") ||
        t.name.startsWith("packageDmg") ||
        t.name.startsWith("packageMsi") ||
        t.name.startsWith("packageExe") ||
        t.name.startsWith("packageDeb")
    }.configureEach {
        dependsOn(copyLottieGen)
    }
}

