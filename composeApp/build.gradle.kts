import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File
import java.util.Calendar
import java.util.Properties

val versionYear = Calendar.getInstance().get(Calendar.YEAR) % 100

fun gitCommitCount(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output.toInt()
    } catch (_: Exception) { 0 }
}

fun gitCommitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output
    } catch (_: Exception) { "unknown" }
}

// ── macOS DMG volume icon ──────────────────────────────────────────────────────
// jpackage sets the icon for the .app bundle it creates, but has no support for
// setting a custom icon on the .dmg container itself — Finder shows the generic
// Java icon for the closed .dmg file until this runs. Baking the icon into the
// dmg's own volume data (rather than an external resource-fork attribute on the
// finished file) is what survives zip/artifact-upload/HTTP transport intact.
fun runCommand(vararg args: String) {
    val process = ProcessBuilder(*args).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "Command failed (${args.joinToString(" ")}): $output" }
}

fun setDmgVolumeIcon(dmg: File, iconIcns: File) {
    val writableDmg = File(dmg.parentFile, "${dmg.nameWithoutExtension}-writable.dmg")
    writableDmg.delete()
    runCommand("hdiutil", "convert", dmg.absolutePath, "-format", "UDRW", "-o", writableDmg.absolutePath)

    val attachOutput = ProcessBuilder("hdiutil", "attach", writableDmg.absolutePath, "-nobrowse", "-readwrite", "-noautoopen")
        .redirectErrorStream(true)
        .start()
        .let { process ->
            val out = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "hdiutil attach failed: $out" }
            out
        }
    val deviceLine = attachOutput.lineSequence().first { it.startsWith("/dev/") }
    val device = deviceLine.substringBefore('\t').trim()
    val mountPoint = attachOutput.lineSequence().last { it.contains("/Volumes/") }
        .substringAfterLast('\t').trim()

    try {
        iconIcns.copyTo(File(mountPoint, ".VolumeIcon.icns"), overwrite = true)
        runCommand("SetFile", "-c", "icnC", "$mountPoint/.VolumeIcon.icns")
        runCommand("SetFile", "-a", "C", mountPoint)
    } finally {
        runCommand("hdiutil", "detach", device)
    }

    dmg.delete()
    runCommand("hdiutil", "convert", writableDmg.absolutePath, "-format", "UDZO", "-o", dmg.absolutePath)
    writableDmg.delete()
}

// ── Desktop Signing helpers ───────────────────────────────────────────────────
// Signing credentials are stored in the private ChurchPresenter-Signing repo.
// The path to that repo's desktop/ folder is configured in local.properties:
//   desktop.signing.repo.path=/absolute/path/to/ChurchPresenter-Signing/desktop
// If the property is absent the build proceeds unsigned (safe for dev machines).

fun loadPropsFile(path: String): Properties {
    val props = Properties()
    val f = File(path)
    if (f.exists()) props.load(f.inputStream())
    return props
}

val desktopSigningRepoPath: String? = run {
    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
    localProps.getProperty("desktop.signing.repo.path")
}

val macSigningProps: Properties = if (desktopSigningRepoPath != null)
    loadPropsFile("$desktopSigningRepoPath/macos/signing.properties")
else Properties()

val winSigningProps: Properties = if (desktopSigningRepoPath != null)
    loadPropsFile("$desktopSigningRepoPath/windows/signing.properties")
else Properties()

val linuxSigningProps: Properties = if (desktopSigningRepoPath != null)
    loadPropsFile("$desktopSigningRepoPath/linux/signing.properties")
else Properties()

/** Returns true when a signing.properties value looks like it has been filled in. */
fun String?.isConfigured() = this != null && isNotBlank() && !contains("XXXXXXXXXX") && this != "CHANGE_ME" && !startsWith("YOUR_")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sentry)
}

// Sentry source context: uploads a source bundle at build time so stack traces show
// actual source lines. Only enabled when SENTRY_AUTH_TOKEN is present (CI/release),
// so ordinary developer builds without the token are unaffected.
sentry {
    val sentryAuthToken = System.getenv("SENTRY_AUTH_TOKEN")
    includeSourceContext.set(sentryAuthToken != null)
    org.set("church-projector")
    projectName.set("church-presenter-desktop")
    authToken.set(sentryAuthToken)
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
            implementation(libs.compose.material.icons.extended)
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
            // Logging backend + Sentry log forwarding (WARN/ERROR → breadcrumbs/events)
            implementation(libs.logback.classic)
            implementation(libs.sentry.logback)
            implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
            implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.10.1")
            // Apache PDFBox for PDF slide extraction
            implementation("org.apache.pdfbox:pdfbox:2.0.33")
            // Apache POI for PowerPoint slide extraction
            implementation("org.apache.poi:poi:5.3.0")
            implementation("org.apache.poi:poi-ooxml:5.3.0")
            implementation("org.apache.poi:poi-scratchpad:5.3.0")
            // Ktor server for companion API
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.cors)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
            // BouncyCastle for custom CA / PKI cert generation
            implementation(libs.bouncycastle.pkix)
            implementation(libs.bouncycastle.prov)
            // VLCJ for media playback (requires VLC installed on system)
            implementation("uk.co.caprica:vlcj:4.8.3")
            implementation("net.java.dev.jna:jna:5.18.1")
            implementation("net.java.dev.jna:jna-platform:5.18.1")
            implementation("com.google.zxing:core:3.5.3")
            implementation("com.google.zxing:javase:3.5.3")
            // Socket.IO client for STT integration
            implementation(libs.socket.io.client)
            // JCEF — embedded Chromium browser for web presenter
            implementation("me.friwi:jcefmaven:143.0.14")
            // Bundle platform-specific Chromium binaries so no runtime download is needed
            val jcefNativesVersion = "jcef-cffac27+cef-143.0.14+gdd46a37+chromium-143.0.7499.193"
            runtimeOnly("me.friwi:jcef-natives-${currentJcefPlatform()}:$jcefNativesVersion")
            // JavaFX for WebView (website presenter)
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
            // FileKit — native OS file dialogs (Windows Explorer dialog, macOS NSOpenPanel)
            implementation(libs.filekit.dialogs)
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
            "-Xmx3072m",
            // GC
            "-XX:+UseG1GC",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:G1NewSizePercent=20",
            "-XX:G1ReservePercent=20",
            "-XX:MaxGCPauseMillis=50",
            "-XX:+UseStringDeduplication",
            // Rendering hints (renderApi is set per-platform in the blocks below / jvmArgs above)
            "-Dawt.useSystemAAFontSettings=on",
            "-Dswing.aatext=true",
            // Dev-mode has no .app bundle to source CFBundleName from, so macOS falls back to
            // the main class name ("MainKt") in the menu bar without this.
            "-Xdock:name=Church Presenter",
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
        // Explicitly set Skiko GPU backend to prevent software fallback
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("mac") -> jvmArgs("-Dskiko.renderApi=METAL")
            osName.contains("win") -> jvmArgs("-Dskiko.renderApi=OPENGL")
            else -> jvmArgs("-Dskiko.renderApi=OPENGL")
        }

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
            copyright = "© ${Calendar.getInstance().get(Calendar.YEAR)} Church Presenter. All rights reserved."
            vendor = "Church Presenter"

            // Bundle app resources alongside the packaged app.
            // These land in Contents/app/resources/ on macOS.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("src/jvmMain/appResources"))

            // Bundle ALL dependency JARs into the distribution — critical for standalone mode
            includeAllModules = true

            val commonJvmArgs = listOf(
                "-Xms512m",
                "-Xmx3072m",
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
                jvmArgs("-Dskiko.renderApi=METAL")

                // ── macOS Code Signing ────────────────────────────────────────
                val macIdentity = macSigningProps.getProperty("identityName", "")
                if (macIdentity.isConfigured()) {
                    signing {
                        sign.set(true)
                        identity.set(macIdentity)
                        val kc = macSigningProps.getProperty("keychain", "")
                        if (!kc.isNullOrBlank()) keychain.set(kc)
                    }
                    logger.lifecycle("macOS signing ENABLED  identity=\"$macIdentity\"")

                    // Notarization is handled directly in CI via xcrun notarytool + xcrun stapler
                    // (the built-in Gradle notarizeDmg task omits --password from the command line)
                } else {
                    logger.lifecycle("macOS signing SKIPPED  (fill identityName in desktop/macos/signing.properties)")
                }
            }

            windows {
                menuGroup = "ChurchPresenter"
                perUserInstall = false
                dirChooser = true
                upgradeUuid = "A1B2C3D4-E5F6-4789-A012-3456789ABCDE"
                iconFile.set(project.file("src/jvmMain/appResources/windows/icon.ico"))
                jvmArgs(*commonJvmArgs.toTypedArray())
                jvmArgs("-Dskiko.renderApi=OPENGL")
            }

            linux {
                iconFile.set(project.file("src/jvmMain/appResources/linux/icon.png"))
                jvmArgs(*commonJvmArgs.toTypedArray())
            }
        }
    }
}

val fixDmgIcon by tasks.registering {
    group = "compose desktop"
    description = "Bakes the app icon into the DMG's own volume (jpackage doesn't set this) so it survives real distribution."
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    doLast {
        val dmgDir = file("build/compose/binaries/main/dmg")
        val iconIcns = file("src/jvmMain/appResources/macos/icon.icns")
        check(iconIcns.exists()) { "fixDmgIcon: icon file not found at $iconIcns" }
        dmgDir.listFiles { f -> f.extension == "dmg" }?.forEach { dmg ->
            logger.lifecycle("Baking volume icon into ${dmg.name}")
            setDmgVolumeIcon(dmg, iconIcns)
        }
    }
}

afterEvaluate {
    tasks.findByName("packageDmg")?.finalizedBy(fixDmgIcon)
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
    // A packaged installer build (packageDmg/Msi/Exe/Deb, packageDistributionForCurrentOS,
    // signing/notarization — see .github/workflows/build.yml) is a real release. A plain
    // `run` or IDE launch is a developer build. Used to tag live-map pings dev vs. user.
    val isRelease = gradle.startParameter.taskNames.any { task ->
        val t = task.lowercase()
        listOf("package", "distributable", "sign", "notariz").any { t.contains(it) }
    }
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")

    // Always re-run — git state (commit count/hash) can change without any file edits
    outputs.upToDateWhen { false }
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
            |    const val IS_RELEASE = $isRelease
            |}
            """.trimMargin()
        )
    }
}

kotlin {
    sourceSets {
        jvmMain {
            kotlin.srcDir(generateBuildConfig.map { layout.buildDirectory.dir("generated/buildconfig") })
            // Include Converter submodule source (builds together, launches as separate window)
            kotlin.srcDir("src/jvmMain/appResources/common/ChurchPresenter-Converter/src/main/kotlin")
            // Include LottieGen submodule source (builds together, launches as separate window)
            kotlin.srcDir("src/jvmMain/appResources/common/ChurchPresenter-LottieGen/src/main/kotlin")
            // Include Bible Lookup Engine (BLE) submodule source — runs in-process as a WebSocket
            // service started when STT connects.
            kotlin.srcDir("src/jvmMain/appResources/common/ChurchPresenter-BLE/src/main/kotlin")
            // Include Companion Satellite client submodule source — pure-Kotlin client for
            // Bitfocus Companion's Satellite protocol, wrapped by CompanionSatelliteViewModel.
            kotlin.srcDir("src/jvmMain/appResources/common/ChurchPresenter-CompanionSatellite/src/main/kotlin")
            // Include submodule resources (.properties files for localization)
            resources.srcDir("src/jvmMain/appResources/common/ChurchPresenter-Converter/src/main/resources")
            resources.srcDir("src/jvmMain/appResources/common/ChurchPresenter-LottieGen/src/main/resources")
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

// prepareAppResources scans the entire appResourcesRootDir — exclude submodule build
// artefacts (.gradle dirs) that contain lock files Gradle can't hash on Windows.
afterEvaluate {
    (tasks.findByName("prepareAppResources") as? org.gradle.api.tasks.AbstractCopyTask)
        ?.exclude { it.path.contains(".gradle") }
}


// ── Windows Code Signing ──────────────────────────────────────────────────────
// Runs signtool.exe on the packaged .msi / .exe after Compose Desktop packaging.
// Only executes on Windows with a configured certificate.
val winCertFileRel = winSigningProps.getProperty("certFile", "")
val winCertPath = if (desktopSigningRepoPath != null && winCertFileRel.isConfigured())
    "$desktopSigningRepoPath/windows/$winCertFileRel" else null
val winCertPassword = winSigningProps.getProperty("certPassword", "")
val winTimestampUrl = winSigningProps.getProperty("timestampUrl", "http://timestamp.digicert.com")
val winSubjectName = winSigningProps.getProperty("subjectName", "Church Presenter")

fun registerWindowsSignTask(taskName: String, packagingTask: String, extension: String) =
    tasks.register(taskName) {
        description = "Sign Windows $extension installer with code signing certificate"
        group = "signing"
        dependsOn(packagingTask)
        onlyIf {
            val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
            val hasCert = winCertPath != null && File(winCertPath).exists() && winCertPassword.isConfigured()
            if (!isWindows) logger.info("$taskName skipped: not running on Windows")
            if (!hasCert) logger.info("$taskName skipped: certificate not configured")
            isWindows && hasCert
        }
        doLast {
            val outDir = layout.buildDirectory.dir("compose/binaries/main/$extension").get().asFile
            outDir.listFiles { f -> f.extension.equals(extension, ignoreCase = true) }?.forEach { installer ->
                val result = ProcessBuilder(
                    "signtool", "sign",
                    "/fd", "SHA256",
                    "/f", winCertPath!!,
                    "/p", winCertPassword,
                    "/t", winTimestampUrl,
                    "/d", winSubjectName,
                    installer.absolutePath
                ).inheritIO().start().waitFor()
                if (result != 0) error("signtool failed with exit code $result for ${installer.name}")
                logger.lifecycle("Windows signed: ${installer.name}")
            }
        }
    }

registerWindowsSignTask("signWindowsMsi", "packageMsi", "msi")
registerWindowsSignTask("signWindowsExe", "packageExe", "exe")

// ── Linux GPG Signing ─────────────────────────────────────────────────────────
// GPG-signs the .deb package using dpkg-sig (must be installed on the build host).
// Only executes on Linux with a configured GPG key.
val linuxGpgKeyId = linuxSigningProps.getProperty("gpgKeyId", "")
val linuxGpgPassphrase = linuxSigningProps.getProperty("gpgPassphrase", "")

tasks.register("signLinuxDeb") {
    description = "GPG-sign the Linux .deb package (requires dpkg-sig)"
    group = "signing"
    dependsOn("packageDeb")
    onlyIf {
        val isLinux = System.getProperty("os.name").contains("Linux", ignoreCase = true)
        val hasKey = linuxGpgKeyId.isConfigured()
        if (!isLinux) logger.info("signLinuxDeb skipped: not running on Linux")
        if (!hasKey) logger.info("signLinuxDeb skipped: gpgKeyId not configured")
        isLinux && hasKey
    }
    doLast {
        val debDir = layout.buildDirectory.dir("compose/binaries/main/deb").get().asFile
        // Pass the passphrase via a user-only temp file rather than on the command
        // line, where it would be visible in process listings while gpg runs.
        val passphraseFile = if (linuxGpgPassphrase.isConfigured()) {
            File.createTempFile("gpg-pass", null).apply {
                setReadable(false, false); setReadable(true, true)
                setWritable(false, false); setWritable(true, true)
                writeText(linuxGpgPassphrase)
            }
        } else null
        try {
            debDir.listFiles { f -> f.extension.equals("deb", ignoreCase = true) }?.forEach { debFile ->
                val cmd = buildList {
                    add("dpkg-sig")
                    add("--sign"); add("builder")
                    add("-k"); add(linuxGpgKeyId)
                    if (passphraseFile != null) {
                        add("--gpg-options"); add("--batch --pinentry-mode loopback --passphrase-file ${passphraseFile.absolutePath}")
                    }
                    add(debFile.absolutePath)
                }
                val result = ProcessBuilder(cmd).inheritIO().start().waitFor()
                if (result != 0) error("dpkg-sig failed with exit code $result for ${debFile.name}")
                logger.lifecycle("Linux signed: ${debFile.name}")
            }
        } finally {
            passphraseFile?.delete()
        }
    }
}

// ── Crossword puzzle sync ─────────────────────────────────────────────────────
// Copies encrypted .xwp files from the ChurchPresenter-Cross submodule into
// composeResources so they are bundled with the app. Run `git submodule update`
// in ChurchPresenter-Cross to pull the latest puzzles, then rebuild.
val syncCrosswordFiles by tasks.registering(Copy::class) {
    from(rootProject.file("composeApp/src/jvmMain/appResources/common/ChurchPresenter-Cross/encoded"))
    include("*.xwp")
    into(layout.projectDirectory.file("src/jvmMain/composeResources/files/crossword"))
    doFirst {
        destinationDir.mkdirs()
    }
}
tasks.matching {
    it.name.contains("ProcessResources", ignoreCase = true) ||
    it.name.contains("ResourcesForJvmMain", ignoreCase = true)
}.configureEach {
    dependsOn(syncCrosswordFiles)
}

