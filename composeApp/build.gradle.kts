import java.time.Duration
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File
import java.util.Calendar
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.gradle.process.ExecOperations

val versionYear = Calendar.getInstance().get(Calendar.YEAR) % 100

fun gitCommitCount(): Int = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 0

fun gitCommitHash(): String = gitOutput("rev-parse", "--short", "HEAD") ?: "unknown"

// ── Build provenance ──────────────────────────────────────────────────────────
// The app is GPLv3 and hardcodes the live-map ping URL, so a fork built from
// source pings churchpresenter.org with a payload identical to an official
// install. Stamping the build's git origin and build kind lets the server tell
// them apart (see the build_channel column and api/ping.ts in the website
// repo). This is attribution for honest forks, not authentication — anyone can
// patch these constants out. Every helper falls back to "unknown"/"nogit"
// rather than failing the build.

/**
 * Runs a git command, returning null when git is missing or exits non-zero. Through
 * `providers.exec`, because starting a process with ProcessBuilder while the build is being
 * configured is deprecated and fails in Gradle 11. Its stderr is captured apart from its output,
 * so git's "fatal: not a git repository" is never handed back as if it were a value.
 */
fun gitOutput(vararg args: String): String? = try {
    val git = providers.exec {
        commandLine("git", *args)
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
    }
    if (git.result.get().exitValue != 0) null else git.standardOutput.asText.get().trim()
} catch (_: Exception) {
    null
}

// Matches the server's REPO_RE — anything else is stored as 'unknown' there, so
// there's no point sending it.
val repoSlugRegex = Regex("^[a-z0-9][a-z0-9._-]{0,38}/[a-z0-9][a-z0-9._-]{0,99}$")

/**
 * Parses a git remote URL down to a lowercase "owner/name" slug.
 *
 * Only the slug is ever sent — never the raw URL, which can embed credentials
 * (https://user:token@github.com/...). Handles https://, ssh:// and scp-style
 * (git@host:owner/name) remotes; anything else yields "unknown".
 */
fun parseRepoSlug(remoteUrl: String): String {
    val trimmed = remoteUrl.trim().removeSuffix(".git").trimEnd('/')
    if (trimmed.isEmpty()) return "unknown"
    val path = when {
        trimmed.contains("://") -> trimmed.substringAfter("://").substringAfter('/', "")
        trimmed.contains(':') -> trimmed.substringAfter(':')
        // A bare filesystem path is a local clone, not an identifiable remote.
        else -> return "unknown"
    }
    val parts = path.split('/').filter { it.isNotEmpty() }
    if (parts.size < 2) return "unknown"
    val slug = "${parts[parts.size - 2]}/${parts[parts.size - 1]}".lowercase()
    return if (repoSlugRegex.matches(slug)) slug else "unknown"
}

fun gitRepoSlug(): String = parseRepoSlug(gitOutput("remote", "get-url", "origin") ?: "")

/**
 * Build kind, mirroring the server's KNOWN_BUILD_TYPE set.
 *
 * Precedence puts `release` ahead of `dirty` on purpose: IS_RELEASE means a
 * packaged-installer task ran, which is already this project's user-vs-developer
 * signal (see the ?src=dev flag). Letting a dirty tree outrank it would
 * misclassify official CI builds as developer builds if the packaging steps ever
 * touch a tracked file — and counting real users as developers is the costlier
 * mistake. `dirty` therefore only distinguishes among non-packaged builds.
 */
fun gitBuildType(isRelease: Boolean): String = when {
    gitOutput("rev-parse", "--git-dir") == null -> "nogit" // source tarball / distro packaging
    isRelease -> "release"
    !gitOutput("status", "--porcelain").isNullOrEmpty() -> "dirty"
    else -> "snapshot"
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
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.detekt)
    jacoco
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/jvmMain/kotlin", "src/jvmTest/kotlin")
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compottie)
            implementation(libs.compottie.dot)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.roborazzi.composeDesktop)
            implementation(libs.mockk)
            implementation(libs.ktor.client.mock)
            implementation(libs.compose.uiTest)

            // The suite runs through the JUnit Platform launcher (useJUnitPlatform() below) but the
            // tests themselves are still JUnit 4 -- the launcher is there for the per-fork hook in
            // PerForkTestHome, not for JUnit 5 syntax.
            //
            // `kotlin-test` is resolved variant-aware and, the moment the Kotlin plugin sees
            // useJUnitPlatform(), it swaps in the JUnit 5 flavour on its own. That would remap
            // @BeforeTest to @BeforeEach and silently stop running the 22 classes that use
            // org.junit.BeforeClass and the two that use @get:Rule TemporaryFolder -- silently,
            // because the annotations still compile and every other test still passes. The
            // capabilitiesResolution block below pins it back to the JUnit 4 flavour; declaring
            // kotlin-test-junit here instead does NOT work, it just fails resolution with a
            // capability conflict against the junit5 variant kotlin-test drags in.
            implementation(libs.kotlin.testJunit)
            runtimeOnly(libs.junit.vintage.engine)
            implementation(libs.junit.platform.launcher)
        }
        jvmMain.dependencies {
            // The converter: a real module rather than a mounted source directory, opened in its
            // own window from the Help menu (AboutDialog) and used for Bible/song file conversion.
            implementation(projects.converter)
            // The shared models: the song, the `.song` format and the library it lives in.
            implementation(projects.coreModels)
            // Everything the app persists: the settings data classes, SettingsManager and the
            // Constants their defaults are spelled with.
            implementation(projects.settings)
            // Crash reporting: the crash log on disk and the Sentry forwarding behind it. It
            // exposes SentryLevel through CrashReporter.breadcrumb, hence `api` on its side.
            implementation(projects.diagnostics)
            // The Planning Center client: OAuth, the Services REST calls and the loopback
            // listener for the consent redirect, wrapped by PlanningCenterImportViewModel.
            implementation(projects.planningCenter)
            // Bible module formats and the download catalogues behind the in-app browser.
            implementation(projects.bibleFormats)
            // The Bible itself: the loaded translation, its books, verses and search.
            implementation(projects.bible)
            // The song library: the grid of every song in the library, opened from the Help menu.
            implementation(projects.songlibrary)
            // The Calendar Manager: the planner window, opened from the Help menu beside it. Holds
            // its own calendar.json; a planned run of show is a List<ScheduleItem> the Schedule tab
            // loads as-is.
            implementation(projects.calendar)
            implementation(projects.strings)
            implementation(projects.icons)
            implementation(projects.sharedUi)
            implementation(projects.slides)
            implementation(projects.media)
            implementation(projects.web)
            implementation(projects.crosswordTab)
            implementation(projects.qa)
            implementation(projects.dictionary)
            implementation(projects.stt)
            implementation(projects.announcements)
            implementation(projects.songs)
            implementation(projects.bibleTab)
            implementation(projects.server)
            implementation(projects.schedule)
            implementation(projects.canvas)
            implementation(projects.presenter)
            implementation(projects.profiles)
            implementation(projects.lowerThird)
            implementation(projects.songChords)
            // The Companion Satellite protocol client: a real module rather than a mounted source
            // directory, wrapped by CompanionSatelliteViewModel.
            implementation(projects.companionSatellite)
            implementation(projects.companionSurface)
            // The OBS Studio integration: client, scene mapping and settings page.
            implementation(projects.obs)
            // The layer model: layers, cues, program and preview.
            implementation(projects.liveShow)
            implementation(projects.showControl)
            // The live output: PresenterManager, the output windows and the off-screen outputs.
            implementation(projects.liveOutput)
            // Statistics: the counters, the play log and the CCLI report window over them.
            implementation(projects.statistics)
            // The updater: the release check, the installer download and the update window.
            implementation(projects.updater)
            // The live-map ping, usage counts, contact form and device report.
            implementation(projects.telemetry)
            // The System settings page and the setup wizard.
            implementation(projects.appSettings)
            // The Server settings page, calendar sync and Instance Link's windows.
            implementation(projects.serverUi)
            // The ATEM protocol client: the UDP conversation with the switcher — connect, state
            // dump, key control and media-pool upload. AtemBridge is the app-side wiring.
            implementation(projects.atem)
            // The NDI send client: the runtime discovery and the six native calls behind one
            // interface. NdiVideoRenderer is the app-side wiring. Ships no NDI binaries — the
            // runtime is installed separately, exactly as VLC is.
            implementation(projects.ndi)
            // The OMT client, both directions, over the libomt/libvmx the app bundles (both MIT —
            // unlike NDI they ship inside the installer; see fetchBundledOmt). OmtVideoRenderer and
            // OmtFrameCache are the app-side wiring.
            implementation(projects.omt)
            implementation(projects.theme)
            implementation(projects.coreModels)
            implementation(projects.lottieGenerator)
            implementation(projects.bibleEngine)
            // The presentation engine: a real module rather than a mounted source directory —
            // PresentationViewModel, PresentationPlayer and CompanionServer drive it.
            implementation(projects.presentationEngine)
            implementation(libs.kotlinx.coroutines.swing)
            // Sentry crash reporting
            implementation(libs.sentry)
            // Logging backend + Sentry log forwarding (WARN/ERROR → breadcrumbs/events)
            implementation(libs.logback.classic)
            implementation(libs.sentry.logback)
            // ImageIO readers for what Skia refuses — see PictureDecoder. jpeg covers CMYK/YCCK
            // JPEGs out of print workflows; psd covers Photoshop files, which reach picture
            // folders under a .jpg name often enough to have been reported from the field.
            implementation(libs.twelvemonkeys.imageio.core)
            implementation(libs.twelvemonkeys.imageio.jpeg)
            implementation(libs.twelvemonkeys.imageio.psd)
            // Apache PDFBox for PDF slide extraction
            implementation(libs.pdfbox)
            // Apache POI for PowerPoint slide extraction.
            // poi-ooxml-lite is swapped for poi-ooxml-full: the presentation engine's timing
            // parser needs the <p:timing> schema classes (CTTLTimeNode*, …) that lite omits.
            // Keep exactly one schema jar on the classpath. Versions come from the catalogue,
            // which is what :presentation-engine and :converter resolve from too.
            implementation(libs.apache.poi)
            implementation(libs.apache.poi.ooxml)
            implementation(libs.apache.poi.ooxmlFull)
            implementation(libs.apache.poi.scratchpad)
            // Ktor server for companion API
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.cors)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.ktor.server.partial.content)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
            // BouncyCastle for custom CA / PKI cert generation
            implementation(libs.bouncycastle.pkix)
            implementation(libs.bouncycastle.prov)
            // VLCJ for media playback (requires VLC installed on system)
            implementation(libs.vlcj)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.zxing.core)
            implementation(libs.zxing.javase)
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
            // DBus for Linux file chooser integration.
            implementation(libs.dbus.java.core)
            implementation(libs.dbus.java.transport.junixsocket)
            // FileKit — native OS file dialogs (Windows Explorer dialog, macOS NSOpenPanel). Its
            // native UNIX transport is excluded graph-wide below.
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

// The KMP source-set DSL has no testFixtures() helper, so the fixture dependency is added to the
// jvmTest configuration directly. Gives the app's keyboard tests the shared keyDown() builder that
// now lives in :core-models.
dependencies {
    add("jvmTestImplementation", testFixtures(projects.coreModels))
    add("jvmTestImplementation", testFixtures(projects.sharedUi))
    add("jvmTestImplementation", testFixtures(projects.qa))
    add("jvmTestImplementation", testFixtures(projects.dictionary))
    add("jvmTestImplementation", testFixtures(projects.stt))
    add("jvmTestImplementation", testFixtures(projects.announcements))
    add("jvmTestImplementation", testFixtures(projects.songs))
    add("jvmTestImplementation", testFixtures(projects.bibleTab))
    add("jvmTestImplementation", testFixtures(projects.schedule))
    add("jvmTestImplementation", testFixtures(projects.lowerThird))
    add("jvmTestImplementation", testFixtures(projects.presenter))
    add("jvmTestImplementation", testFixtures(projects.profiles))
    add("jvmTestImplementation", testFixtures(projects.statistics))
    // CrashReportSweep: the Bible tab and view-model failure tests exercise paths that really
    // write a crash report. It lives with :diagnostics because it exists for CrashReporter's own
    // design -- the report directory is resolved once per JVM and cannot be redirected after.
    add("jvmTestImplementation", testFixtures(projects.diagnostics))
    // SpbFixture: the .spb writer the Bible tab, view-model and server suites build fixtures with.
    // It stays with the module that owns the format.
    add("jvmTestImplementation", testFixtures(projects.bible))
    // pdfDeck(): Deck's constructor is internal to :presentation-engine, so tests that need a
    // synthetic deck build it through the module's own fixtures.
    add("jvmTestImplementation", testFixtures(projects.presentationEngine))
    // FakeAtemSwitcher: the loopback switcher the ATEM suites drive. It stays with the client it
    // was captured against, and the app's own ATEM tests (bridge, upload routes, lower third) borrow
    // it from there rather than keeping a second copy.
    add("jvmTestImplementation", testFixtures(projects.atem))
    add("jvmTestImplementation", testFixtures(projects.ndi))
    add("jvmTestImplementation", testFixtures(projects.omt))
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
        // ── No -Dskiko.renderApi here, on any platform ────────────────────────────────────
        // The GPU backend is chosen by MainLogic.preferredRenderApi, which main() applies before
        // the first SkiaLayer. Two reasons it cannot be done from this file:
        //   • This runs on the machine doing the BUILD, and the value is baked into the artifact.
        //   • `jvmArgs` is declared only on JvmApplication, so a call inside macOS { }/windows { }/
        //     linux { } resolves against the enclosing application { } and applies EVERYWHERE. A
        //     METAL pin written in the macOS block was reaching Windows and Linux this way, and
        //     only stayed invisible because the branch below pinned OPENGL first and Gradle keeps
        //     the first value for a repeated -D key.
        // Deciding it at runtime instead is right whoever built the app and wherever it runs, and
        // gives CHURCHPRESENTER_RENDER_API the last word on the operator's own machine.
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("mac")) {
            // Dev-mode has no .app bundle to source CFBundleName from, so macOS falls back to the
            // main class name ("MainKt") in the menu bar without this. macOS-only: an unrecognized
            // -Xdock option on other platforms aborts JVM startup entirely — so this one genuinely
            // does belong to the build, and is guarded on the build machine being a Mac.
            jvmArgs("-Xdock:name=Church Presenter")
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

            // No jvmArgs here or in the platform blocks below. The application-level
            // jvmArgs(...) above already carries this list, and it reaches every platform:
            // `jvmArgs` is declared only on JvmApplication, so a call inside macOS { } /
            // windows { } / linux { } resolves against the enclosing application { } and
            // applies everywhere. A second copy per platform block therefore did not scope
            // anything to that platform -- it just wrote the same 24 options into the
            // artifact four times over (measured in ChurchPresenter.cfg before this was
            // removed). Anything genuinely platform-specific has to be decided at runtime,
            // the way MainLogic.preferredRenderApi decides the render API.

            macOS {
                bundleID = "org.churchpresenter.app"
                iconFile.set(project.file("src/jvmMain/appResources/macos/icon.icns"))

                // ── No renderApi here ─────────────────────────────────────────
                // A platform block has NO jvmArgs of its own: `jvmArgs` is declared only on
                // JvmApplication, so a call written in here silently resolves against the
                // enclosing application { } receiver and applies to EVERY platform, including
                // the `run` task on Windows and Linux. This block used to pin METAL, and it was
                // pinning it everywhere; Windows only escaped because the block above pinned
                // OPENGL first and Gradle keeps the first value for a repeated -D key. Take that
                // pin away and METAL is the only value left, so skiko throws
                // "Windows does not support Metal rendering API" before the first window opens.
                // macOS is pinned by MainLogic.preferredRenderApi, on the machine that runs it.

                // ── macOS Privacy ─────────────────────────────────────────────
                // macOS attributes a spawned child process's privacy requests to the app bundle
                // that spawned it, so the ffmpeg the camera source runs asks on ChurchPresenter's
                // behalf — and without a usage description here the request is denied outright,
                // with no prompt. Capture-device *names* stay readable either way, which is why a
                // missing key looks like "the card is detected but shows nothing" (issue #431)
                // rather than like a permissions problem.
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSCameraUsageDescription</key>
                        <string>ChurchPresenter needs camera access to show cameras and capture cards on the canvas and the presenter output.</string>
                    """.trimIndent()
                }

                // These REPLACE the Compose plugin's default-entitlements.plist rather than adding
                // to it — the files restate its three keys for that reason. Read the comments in
                // them before editing either.
                entitlementsFile.set(rootProject.file("desktop/macos/churchpresenter.entitlements"))
                runtimeEntitlementsFile.set(rootProject.file("desktop/macos/churchpresenter-runtime.entitlements"))

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

                // ── No renderApi here, deliberately ───────────────────────────
                // This used to pin OPENGL, overriding skiko's own Direct3D default, and that is
                // the only reason a GPU driver access violation surfaced inside
                // WindowsOpenGLRedrawer.swapBuffers rather than anywhere else. skiko's
                // fallbackRenderApiQueue covers *context creation* only, so a fault during
                // swapBuffers never triggers it — the default has to be right, not recoverable.
                // An operator whose machine fares worse on Direct3D sets CHURCHPRESENTER_RENDER_API
                // (see DevFlags.renderApiOverride); main() applies it before the first SkiaLayer.
                //
                // Nor could it go here even if it were wanted: a platform block has no jvmArgs of
                // its own, so the call above adds to the application-wide list and reaches macOS
                // and Linux too. MainLogic.preferredRenderApi is where the choice is made.
            }

            linux {
                iconFile.set(project.file("src/jvmMain/appResources/linux/icon.png"))
            }
        }
    }
}

val fixDmgIcon = tasks.register("fixDmgIcon") {
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
val generateBuildConfig = tasks.register("generateBuildConfig") {
    val commitHash = gitCommitHash()
    val commits = gitCommitCount()
    val appVersion = "$versionYear.${commits / 256}.${commits % 256}"
    // A packaged installer build (packageDmg/Msi/Exe/Deb, packageDistributionForCurrentOS,
    // signing/notarization — see .github/workflows/build.yml) is a real release. A plain
    // `run` or IDE launch is a developer build. Used to tag live-map pings dev vs. user.
    //
    // Task names only. `taskNames` also carries task options and their values, and a test filter
    // is matched like any other string: `--tests '*KeySignalModifierTest*'` contains "sign", so
    // naming that class compiled the whole run as a release -- and every test that branches on
    // IS_RELEASE saw production behaviour, depending on which classes the command line happened
    // to name.
    val requested = gradle.startParameter.taskNames
    val isRelease = requested.filterIndexed { i, arg ->
        !arg.startsWith("-") && requested.getOrNull(i - 1) != "--tests"
    }.any { task ->
        val t = task.lowercase()
        listOf("package", "distributable", "sign", "notariz").any { t.contains(it) }
    }
    // Planning Center integration: one shared OAuth app for the whole project (per PCO's own
    // multi-tenant guidance — each church authorizes this one app, rather than registering their
    // own). Neither value is committed — both are read from env vars at build time, same pattern
    // as SENTRY_AUTH_TOKEN above. Local/dev builds without these set simply can't complete OAuth.
    // Build provenance for the live-map ping — see the helpers at the top of this
    // file. Resolved here at build time, never at runtime: a runtime git call
    // would read whatever directory the user happened to launch the app from.
    val repoSlug = gitRepoSlug()
    val buildType = gitBuildType(isRelease)
    // Whether this build came off CI or somebody's machine. `IS_RELEASE` cannot answer that: it
    // means "a packaging task ran", so `./gradlew packageDmg` on a laptop produces a build that
    // calls itself a release and reports `environment=production` to Sentry. Over 30 days that
    // left 224 of 234 camera reports labelled production, including a tester's, and no way to tell
    // an operator's build from one built to try a fix out.
    //
    // `providers.environmentVariable` and NOT `System.getenv`, for the reason recorded on
    // `printCoverageLink` below: `System.getenv` reads the Gradle DAEMON's environment, frozen when
    // the daemon started, which need not be the environment of the build asking.
    val buildChannel = providers.environmentVariable("GITHUB_ACTIONS")
        .map { if (it == "true") "ci" else "local" }
        .getOrElse("local")
    val planningCenterClientId = System.getenv("PLANNING_CENTER_CLIENT_ID") ?: ""
    val planningCenterClientSecret = System.getenv("PLANNING_CENTER_CLIENT_SECRET") ?: ""
    // Calendar sync: the relay and the website endpoint its client key is fetched from. Kept out of
    // the repository like the Planning Center pair -- from GitHub Secrets in CI, and locally from the
    // environment or ~/.gradle/gradle.properties. A build without them has calendar sync but no
    // relay to reach. Only characters a URL needs survive, so nothing can break the generated source.
    fun endpoint(name: String): String =
        (providers.environmentVariable(name).orNull ?: providers.gradleProperty(name).orNull ?: "")
            .filter { it.isLetterOrDigit() || it in ":/.-_~?=&%" }
    val calendarRelayUrl = endpoint("CALENDAR_RELAY_URL")
    val calendarRelayConfigUrl = endpoint("CALENDAR_RELAY_CONFIG_URL")
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
            |    const val REPO_SLUG = "$repoSlug"
            |    const val BUILD_TYPE = "$buildType"
            |    const val BUILD_CHANNEL = "$buildChannel"
            |    const val PLANNING_CENTER_CLIENT_ID = "$planningCenterClientId"
            |    const val PLANNING_CENTER_CLIENT_SECRET = "$planningCenterClientSecret"
            |    const val CALENDAR_RELAY_URL = "$calendarRelayUrl"
            |    const val CALENDAR_RELAY_CONFIG_URL = "$calendarRelayConfigUrl"
            |}
            """.trimMargin()
        )
    }
}

// Exactly ONE POI schema jar may be on the classpath, and it must be poi-ooxml-full: the
// presentation engine's <p:timing> parser needs schema classes (CTTLTimeNode*, …) that
// poi-ooxml-lite omits. Excluded graph-wide rather than per-dependency, so no transitive path
// through :presentation-engine or :converter can bring the lite jar back.
configurations.configureEach {
    exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
    // filekit-dialogs-jvm transitively pulls in dbus-java-transport-native-unixsocket, which
    // conflicts with the junixsocket transport the app uses -- TransportBuilder throws
    // TransportRegistrationException if both UNIX transport providers are on the classpath at once.
    // Excluded graph-wide so neither FileKit nor :shared-ui's file chooser can bring it back.
    exclude(group = "com.github.hypfvieh", module = "dbus-java-transport-native-unixsocket")
}

kotlin {
    sourceSets {
        jvmMain {
            kotlin.srcDir(generateBuildConfig.map { layout.buildDirectory.dir("generated/buildconfig") })
        }
    }
}

// Keeps `kotlin-test` on its JUnit 4 flavour. Both flavours offer the same
// `kotlin-test-framework-impl` capability, so exactly one may be on the classpath, and with
// useJUnitPlatform() present the Kotlin plugin picks junit5 unless told otherwise. The tests are
// JUnit 4 and run on junit-vintage -- see the note in jvmTest.dependencies for what silently breaks
// if this is removed.
configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability(
        "org.jetbrains.kotlin:kotlin-test-framework-impl"
    ) {
        val junit4 = candidates.firstOrNull {
            (it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)
                ?.module == "kotlin-test-junit"
        }
        if (junit4 != null) select(junit4)
    }
}

// Unit tests must never need a display: they run on headless CI runners and must not pop up an
// AWT window or fail on a missing X server. Compose text measurement (used by the auto-fit tests)
// works fine headless, but only if AWT is told so before it initialises. Mirrors the same setting
// the PresentationEngine sub-build already uses for its suite.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("java.awt.headless", "true")

    // Pixel density for the app-preview screenshots, for exporting retina-sized captures:
    //   ./gradlew :composeApp:recordRoborazziJvm --tests '*AppPreview*' -PpreviewDensity=2
    // Left at 1 by default and NOT raised for CI: this harness is the visual-regression baseline on
    // every pull request, and doubling density quadruples the pixel work. See PREVIEW_DENSITY in
    // AppPreviewSupport.kt.
    systemProperty(
        "churchpresenter.previewDensity",
        providers.gradleProperty("previewDensity").getOrElse("1"),
    )

    // Point the whole test JVM at a throwaway home directory. Several singletons resolve
    // ~/.churchpresenter paths in a `by lazy` or a constructor -- captured ONCE per JVM -- so
    // without this a test could permanently latch onto (and write into, or delete from) the
    // developer's real settings, autosave, QA state or STT training logs. Individual tests may
    // still override user.home per-test; they restore to this fake one, not the real one.
    //
    // This is the FLOOR, not the whole story: with maxParallelForks below, PerForkTestHome (jvmTest)
    // repoints user.home at <this>/worker-N before discovery, so no two forks share the directory.
    // The task-level value still has to be set, because it is what a fork sees if that listener does
    // not run -- and the one thing that must never happen is a test reaching the developer's real
    // ~/.churchpresenter.
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("churchpresenter.test.homeBase", testHome.absolutePath)

    // Keep tests OUT of the production Sentry project. `sentry.properties` is on the runtime
    // classpath with a real DSN, and the Sentry SDK auto-initialises from it as soon as any
    // Sentry API is touched — which happens indirectly via CrashReporter.breadcrumb() from
    // ordinary app code. Without this, a test run reports events (and release-health sessions)
    // as if it were a real install. An empty DSN leaves the SDK permanently disabled.
    systemProperty("sentry.dsn", "")
    systemProperty("sentry.enable-external-configuration", "false")
    // Neither of the two above actually stops CrashReporter: they disable the SDK's own external
    // configuration, while initSentry reads sentry.properties off the classpath and hands the DSN
    // to Sentry.init directly. This is the switch initSentry itself honours, and it is what keeps
    // a suite that calls CrashReporter.initialize out of the production project.
    systemProperty("churchpresenter.telemetry.disabled", "true")

    // Opt-in gate for DeckLinkHardwareTest, which drives a real Blackmagic card: it opens the
    // output (pushing a frame to whatever that card is wired to), installs a JVM shutdown hook and
    // pays driver-init time, so it must never run as part of an ordinary `check`. Off by default;
    // enable for a deliberate hardware pass with:
    //   ./gradlew :composeApp:jvmTest -PdecklinkHardware=true --tests '*DeckLinkHardwareTest*'
    systemProperty(
        "churchpresenter.decklinkHardware",
        if (project.hasProperty("decklinkHardware")) project.property("decklinkHardware").toString() else "false"
    )

    // A watchdog on the harness, NOT a wait inside a test: no assertion depends on it, and nothing
    // about which tests pass changes. It exists because a hung suite otherwise runs until the CI
    // *step* gives up at 40 minutes, and a killed step uploads no XML -- so the run costs three
    // quarters of an hour and produces nothing to diagnose. Failing here leaves the log and the
    // START line above naming the class.
    //
    // 30, against a measured green `App unit tests` step of 18m45s and 19m24s (2026-08-21). The
    // step's own comment still says "11-14 minutes"; that band is stale and the suite has grown
    // into it, which is exactly the trap to avoid here -- a 20-minute cap was tried first and would
    // have failed both of those healthy runs. Re-measure before tightening this, and raise it when
    // the suite grows rather than leaving it to bite a green run.
    //
    // Not the soak test: it runs for hours by design, and sets its own limit from the run asked for.
    if (name != "soakTest") timeout.set(Duration.ofMinutes(30))

    // Lets a session chasing a hang tighten HungTestReporter's threshold:
    //   ./gradlew :composeApp:jvmTest -PhangThresholdMs=30000
    // Absent, the reporter uses its own five-minute default. A Gradle `-D` would set the property on
    // the daemon, not on the forked test JVM, which is the whole reason this line exists.
    providers.gradleProperty("hangThresholdMs").orNull?.let {
        systemProperty("churchpresenter.test.hangThresholdMs", it)
    }

    // Where HungTestReporter writes its thread dump. Inside test-results so the workflow's existing
    // upload carries it off a CI runner that would otherwise take the diagnosis to the grave.
    systemProperty(
        "churchpresenter.test.hangDumpDir",
        layout.buildDirectory.dir("test-results/" + name).get().asFile.absolutePath,
    )

    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    // Name every test class as it starts, and again as it finishes with its duration. Gradle prints
    // nothing while a suite runs, so when the screenshots job hit its 20-minute step timeout twice
    // there was no way to say
    // WHICH class was on screen when the clock ran out -- the log simply stopped after
    // "> Task :composeApp:jvmTest" and a killed step uploads no XML to read afterwards. One line per
    // class (772 in the screenshot run, ~2500 for the whole suite) is a cheap price for the next
    // occurrence naming itself. `-Pquiet` turns it off.
    if (!project.hasProperty("quietTests")) {
        addTestListener(object : TestListener {
            // Named on the way IN as well as out, because a class that never finishes never
            // reaches afterSuite: two 40-minute step timeouts (PR #344's screenshots job, PR #359's
            // test job) both stopped after the last completed class, so which class was actually
            // running had to be inferred from the execution order rather than read.
            override fun beforeSuite(suite: TestDescriptor) {
                if (suite.parent != null && suite.className != null) {
                    logger.lifecycle("         START %s".format(suite.displayName))
                }
            }
            override fun beforeTest(testDescriptor: TestDescriptor) = Unit
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                // Only class-level suites have a parent; the root and the JVM-level ones do not.
                if (suite.parent != null && suite.className != null) {
                    val seconds = (result.endTime - result.startTime) / 1000.0
                    logger.lifecycle(
                        "  %6.1fs  %-5s %s".format(seconds, result.resultType, suite.displayName)
                    )
                }
            }
        })
    }

    // The tests are JUnit 4 and stay JUnit 4 -- junit-vintage runs them verbatim, rules and
    // @BeforeClass included. The platform launcher is here for one reason: it is the only hook that
    // runs BEFORE test discovery, which is what PerForkTestHome needs to give each fork its own
    // user.home before any `by lazy` in the app can latch the wrong one. The bare JUnit 4 runner has
    // no equivalent, so parallel forks are not reachable without this.
    useJUnitPlatform()

    // 623s of test time in ONE JVM was the whole cost of this suite. Forks are the fix; the two
    // things that had to be solved first were the shared fake home (PerForkTestHome) and the fixed
    // ports the server suites bind (TestPorts).
    //
    // Half the cores, capped at 4: each fork is largely single-threaded (Skia rasterises per test),
    // and at ~2.1 GB RSS apiece four of them plus the 4 GB daemon already sit at ~12 GB. Override
    // for a one-off with -PtestForks=N.
    maxParallelForks = (
        providers.gradleProperty("testForks").orNull?.toIntOrNull()
            ?: (Runtime.getRuntime().availableProcessors() / 2)
        ).coerceIn(1, 4)

    // NOT forkEvery. Every fork pays skiko's native-library unpack and a Compose warm-up, so
    // restarting JVMs re-buys that cost per batch; it would also renumber org.gradle.test.worker
    // mid-run, and PerForkTestHome's port banding depends on those ids being stable.

    // Gradle's default is 512 MB, which is not enough for a Compose/Skia suite and shows up as GC
    // thrash rather than as an OOM.
    maxHeapSize = "1g"
    jvmArgs("-XX:MaxMetaspaceSize=512m")

    // mockk installs its ByteBuddy agent on first use. A JVM that may not attach to itself makes
    // ByteBuddy spawn a helper process to do it, and under four loaded forks that helper can fail —
    // taking every mock in the fork down with "Could not self-attach to current VM". Allowing the
    // self-attach keeps it in-process; the second flag is JDK 21's opt-in for that dynamic load.
    jvmArgs("-Djdk.attach.allowAttachSelf=true", "-XX:+EnableDynamicAgentLoading")

    // JaCoCo instrumentation costs ~15-25% and is only wanted on `check`. `-PfastTest` drops it for
    // the inner loop; ./test-changed.sh passes it.
    if (project.hasProperty("fastTest")) {
        extensions.getByType<JacocoTaskExtension>().isEnabled = false
    }
}

// ── The suites that cannot share a machine ────────────────────────────────────
// Everything driving FakeAtemSwitcher talks real UDP over loopback and asserts that a command
// arrives within a deadline. That is a correct test of a protocol client -- the wait ends on the
// packet, not on the timeout -- but it measures the machine as well as the code, and with four forks
// saturating the CPU the switcher's receive thread is not scheduled in time. Observed: five
// CompanionServerAtemUploadTest tests failing with "got 0" commands, and AtemClientSocketTest with
// "No response from ATEM", on a run where everything else was green.
//
// So they run on their own, one JVM, after the parallel pass. Widening their deadlines would have
// been the wrong fix twice over: it hides a race rather than removing it, and the number being
// widened would be a claim about a busy machine rather than about the protocol.
//
// ~72s when this list still held `*AtemClientSocketTest`, which has since moved to `:atem` with the
// client it drives. Nothing was lost by that: a module's `test` task forks once by default, so that
// suite is already alone in its JVM there, and CI runs `:atem:test` as its own step after
// `:composeApp:jvmTest` rather than beside it. The suites below still reach FakeAtemSwitcher --
// through `testFixtures(projects.atem)` -- so they still belong here.
//
// Named individually rather than by a `*Atem*` glob so that adding a suite to this list is a
// deliberate act with a reason, not something a class name does by accident.
// The render benchmark and the soak test: each run by its own task (`renderBenchmark`, `soakTest`)
// and kept out of `jvmTest`.
val renderBenchmarkClasses = "*.benchmark.RenderBenchmark"
val soakTestClasses = "*.benchmark.ServiceSoak"

val serialTestClasses = listOf(
    // The ATEM upload suites that used to lead this list moved to `:server`, whose `test` task forks
    // once, so they are alone in their JVM there.
    // The suite that opens the ATEM upload dialog. Doing so renders a Lottie frame and encodes it for
    // the switcher behind 5s deadlines -- the upload button enabling, the dialog's rows composing, and
    // `waitForAtemPrepared` (`:lower-third`'s LowerThirdTabTestSupport.kt). That is real work against a
    // wall clock, so on a runner with four forks competing for the CPU it is the machine being
    // measured, not the code. The Lower Third tab's own suites moved to `:lower-third`, whose `test`
    // task forks once, so they are already alone in their JVM there.
    "*LowerThirdTabScreenshotTest",
    // And these for a third: all thirteen seed one fixed directory on disk -- AppPreviewSupport's
    // `library()` writes songs, bibles, a Gallery of PNGs and a deck into LIBRARY
    // (/Users/Shared/ChurchPresenter, else /tmp/ChurchPresenter) with copyTo(overwrite = true).
    // That was safe while nothing ran at the same time; across forks it is one process reading a
    // file another is rewriting, which surfaced on CI as `AppPreviewPicturesScreenshotTest` failing
    // to find the "04 Church" thumbnail it had just been shown. LIBRARY cannot move per fork the way
    // user.home does -- these previews draw those paths into the images (the schedule rows read
    // "/Users/Shared/ChurchPresenter/..."), so a per-fork root would rewrite every screenshot.
    "*AppPreview*ScreenshotTest",
)

val jvmTestSerial = tasks.register<org.gradle.api.tasks.testing.Test>("jvmTestSerial") {
    group = "verification"
    description = "The loopback-UDP suites, run in one JVM because they cannot share a busy machine."
    val parallel = tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest").get()
    testClassesDirs = parallel.testClassesDirs
    classpath = parallel.classpath
    // The point of the task. Everything else comes from the configureEach block above.
    maxParallelForks = 1
    filter { serialTestClasses.forEach { includeTestsMatching(it) } }
}

tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest") {
    // Excluded from the parallel pass -- but only when the whole suite is being run. An explicit
    // `--tests '*AtemUploadTracedTest*'` has to keep working, and against a standing exclude it would
    // match nothing and fail with "No tests found for given includes". A task's own `--tests`
    // arguments are visible on the start parameter, so the exclusion can simply stand down whenever
    // the developer has named what they want.
    val filteredFromCommandLine = gradle.startParameter.taskRequests.any { request ->
        request.args.any { it == "--tests" }
    }
    // The render benchmark and the soak test have their own tasks and never run in the parallel pass.
    filter {
        excludeTestsMatching(renderBenchmarkClasses)
        excludeTestsMatching(soakTestClasses)
    }
    if (!filteredFromCommandLine) {
        filter { serialTestClasses.forEach { excludeTestsMatching(it) } }
        // Only worth running when this task excluded them. A command-line `--tests` applies to EVERY
        // Test task in the invocation, so finalizing unconditionally meant
        // `recordRoborazziJvm --tests '*ScreenshotTest*'` -- what screenshots.yml runs -- started the
        // serial task with a filter matching none of its six classes, and Gradle failed the build
        // with "No tests found for given includes". Nothing was excluded from that run in the first
        // place, so there is nothing for the serial pass to pick up.
        finalizedBy(jvmTestSerial)
    } else {
        // The other half of standing the exclusion down: those classes now run HERE, in this task,
        // and several of them are in that list precisely because they cannot run beside anything.
        // `recordRoborazziJvm --tests '*ScreenshotTest*'` -- what screenshots.yml runs on every push
        // and pull request -- is exactly that case, and it raced the shared AppPreview library.
        // A named subset is small enough that one JVM costs little, and correctness is not optional.
        maxParallelForks = 1
    }
}

// ── Render benchmark ──────────────────────────────────────────────────────────
// How long each content type takes on an off-screen output, at 1080p and 4K -- see RenderBenchmark.
//   ./gradlew :composeApp:renderBenchmark                         # report to build/reports/render-benchmark/
//   ./gradlew :composeApp:renderBenchmark -PrecordRenderBaseline  # also overwrite composeApp/benchmarks/
//   ./gradlew :composeApp:renderBenchmark -PenforceRenderBudget   # fail past one frame at p99
//   ./gradlew :composeApp:renderBenchmark -PrecordCiRenderBaseline  # write composeApp/benchmarks/ci/ instead
//   ./gradlew :composeApp:renderBenchmark -PcheckRenderRegression   # fail on a row slower than the CI baseline
// The two baselines are different machines and are never compared with each other: the one in
// benchmarks/ is the reference Mac, against the absolute budget; benchmarks/ci/ is recorded BY the
// hosted CI runner (~10x slower), and is what render-benchmark.yml holds main against every week.
// `-PcheckRenderRegression=<file>` names another baseline (relative to the repository root). A row
// regresses when its typical frame (render p50 + readback p50) passes baseline x (1 + margin) +
// slack: `-PrenderRegressionMargin` (default 0.5) and `-PrenderRegressionSlackMs` (default 1.0).
// A missing baseline fails the check -- it never passes for want of something to compare with.
// Alone in one JVM and never part of `jvmTest` or `check`: timings taken beside other forks measure
// the machine, not the code. `renderBenchmarkClasses` is declared with `serialTestClasses` above.

tasks.register<org.gradle.api.tasks.testing.Test>("renderBenchmark") {
    group = "verification"
    description = "Times every content type on an off-screen output, at 1080p and 4K."
    val parallel = tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest").get()
    testClassesDirs = parallel.testClassesDirs
    classpath = parallel.classpath
    maxParallelForks = 1
    maxHeapSize = "2g"
    filter { includeTestsMatching(renderBenchmarkClasses) }
    extensions.getByType<JacocoTaskExtension>().isEnabled = false
    // A measurement, so never up to date and never cached.
    outputs.upToDateWhen { false }
    val reportDir = layout.buildDirectory.dir("reports/render-benchmark").get().asFile
    systemProperty("renderBenchmark.reportDir", reportDir.absolutePath)
    val recordReference = project.hasProperty("recordRenderBaseline")
    val recordCi = project.hasProperty("recordCiRenderBaseline")
    val baselineDir = layout.projectDirectory.dir(if (recordCi) "benchmarks/ci" else "benchmarks").asFile
    systemProperty("renderBenchmark.baselineDir", baselineDir.absolutePath)
    systemProperty("renderBenchmark.record", (recordReference || recordCi).toString())
    systemProperty("renderBenchmark.enforce", project.hasProperty("enforceRenderBudget").toString())
    providers.gradleProperty("renderBenchmarkFrames").orNull?.let { systemProperty("renderBenchmark.frames", it) }
    // `-PcheckRenderRegression` alone has the value "" (or "true" from a properties file): the default.
    val regressionBaselineName = providers.gradleProperty("checkRenderRegression").orNull
        ?.let { if (it.isBlank() || it == "true") "composeApp/benchmarks/ci/results.json" else it }
    val regressionBaseline = regressionBaselineName?.let { rootProject.layout.projectDirectory.file(it).asFile }
    regressionBaseline?.let { systemProperty("renderBenchmark.regressionBaseline", it.absolutePath) }
    // As given, so the report and the failure name the repository's file, not the runner's checkout path.
    regressionBaselineName?.let { systemProperty("renderBenchmark.regressionBaselineName", it) }
    providers.gradleProperty("renderRegressionMargin").orNull
        ?.let { systemProperty("renderBenchmark.regressionMargin", it) }
    providers.gradleProperty("renderRegressionSlackMs").orNull
        ?.let { systemProperty("renderBenchmark.regressionSlackMs", it) }
    doFirst {
        if (recordReference && recordCi) {
            throw GradleException(
                "-PrecordRenderBaseline and -PrecordCiRenderBaseline name different machines' baselines; pass one.",
            )
        }
        // Before minutes of measuring, not after them. RenderBenchmark checks again for a direct run.
        if (regressionBaseline != null && !regressionBaseline.isFile) {
            throw GradleException(
                "No render baseline at $regressionBaseline to check against. Record one on the machine " +
                    "this run is compared on with -PrecordCiRenderBaseline (on CI: dispatch render-benchmark.yml " +
                    "with record_baseline and commit its ci-render-baseline artifact to composeApp/benchmarks/ci/).",
            )
        }
    }
    doLast {
        val report = reportDir.resolve("results.md")
        if (report.exists()) logger.lifecycle(report.readText())
    }
}

// ── GPU output benchmark ──────────────────────────────────────────────────────
// How each content type runs on an on-screen output window, drawn on the GPU -- see GpuOutputBenchmark.
//   ./gradlew :composeApp:gpuBenchmark                      # report to build/reports/gpu-benchmark/
//   ./gradlew :composeApp:gpuBenchmark -PrecordGpuBaseline  # also overwrite composeApp/benchmarks/gpu/
// Needs a display, so it runs on the reference Mac, never on CI. It opens one window per content type
// and size over the main screen. Its `main` lives with the render benchmark in jvmTest, so this runs
// jvmTest's classpath -- not headless, and with its own home so nothing reads the operator's settings.
tasks.register<JavaExec>("gpuBenchmark") {
    group = "verification"
    description = "Times every content type on an on-screen output window, drawn on the GPU."
    val parallel = tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest").get()
    classpath = parallel.classpath
    mainClass.set("org.churchpresenter.app.churchpresenter.benchmark.GpuOutputBenchmarkKt")
    maxHeapSize = "2g"
    outputs.upToDateWhen { false }
    val home = layout.buildDirectory.dir("gpu-benchmark-home").get().asFile
    val reportDir = layout.buildDirectory.dir("reports/gpu-benchmark").get().asFile
    systemProperty("user.home", home.absolutePath)
    systemProperty("gpuBenchmark.reportDir", reportDir.absolutePath)
    systemProperty("gpuBenchmark.baselineDir", layout.projectDirectory.dir("benchmarks/gpu").asFile.absolutePath)
    systemProperty("gpuBenchmark.record", project.hasProperty("recordGpuBaseline").toString())
    providers.gradleProperty("gpuBenchmarkFrames").orNull?.let { systemProperty("gpuBenchmark.frames", it) }
    doFirst { home.mkdirs() }
    doLast {
        val report = reportDir.resolve("results.md")
        if (report.exists()) logger.lifecycle(report.readText())
    }
}

// ── Startup benchmark ─────────────────────────────────────────────────────────
// How long the app takes to start and what it sits at idle -- see StartupProbe and benchmarks/budgets.md.
//   ./gradlew :composeApp:startupBenchmark                  # 5 launches, report to build/reports/startup/
//   ./gradlew :composeApp:startupBenchmark -PcheckBudgets   # fail past a budget in benchmarks/budgets.md
//   -PstartupLaunches=N, -PstartupIdleSeconds=S (default 30) change the run.
// Needs a display, so it runs on the reference Mac. Each launch is the real app (the `run` task's
// classpath and JVM flags) under its own home, seeded by StartupSeed with the licence accepted and
// setup done, on its own single-instance port, so it neither reads the operator's settings nor
// meets a copy already running.
interface StartupExec {
    @get:Inject val exec: ExecOperations
}

tasks.register("startupBenchmark") {
    group = "verification"
    description = "Launches the app several times and reports time to the first frame and idle memory."
    // What the launches run on -- the app's jar and its runtime classpath -- never the `run` task
    // itself, which would launch the app as the operator's own.
    dependsOn("jvmTestClasses", "jvmJar", configurations.named("jvmRuntimeClasspath"))
    outputs.upToDateWhen { false }
    val exec = objects.newInstance(StartupExec::class.java).exec
    val launches = providers.gradleProperty("startupLaunches").orNull?.toIntOrNull() ?: 5
    val idleSeconds = providers.gradleProperty("startupIdleSeconds").orNull ?: "30"
    val check = project.hasProperty("checkBudgets")
    val home = layout.buildDirectory.dir("startup-home").get().asFile
    val reportDir = layout.buildDirectory.dir("reports/startup").get().asFile
    val budgets = layout.projectDirectory.file("benchmarks/budgets.md").asFile
    doLast {
        val run = tasks.named<JavaExec>("run").get()
        val testClasspath = tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest").get().classpath
        home.deleteRecursively()
        reportDir.deleteRecursively()
        home.mkdirs()
        reportDir.mkdirs()
        exec.javaexec {
            classpath = testClasspath
            mainClass.set("org.churchpresenter.app.churchpresenter.benchmark.StartupSeedKt")
            systemProperty("user.home", home.absolutePath)
            systemProperty("java.awt.headless", "true")
        }
        val results = (1..launches).map { i ->
            val out = reportDir.resolve("launch-$i.json")
            exec.javaexec {
                classpath = run.classpath
                mainClass.set(run.mainClass)
                jvmArgs(run.jvmArgs ?: emptyList<String>())
                run.javaLauncher.orNull?.let { executable = it.executablePath.asFile.absolutePath }
                systemProperty("user.home", home.absolutePath)
                systemProperty("churchpresenter.singleInstancePort", "47634")
                systemProperty("churchpresenter.startupProbe", out.absolutePath)
                systemProperty("churchpresenter.startupProbe.idleSeconds", idleSeconds)
            }
            fun field(name: String) = Regex("\"$name\": ([0-9.]+)").find(out.readText())?.groupValues?.get(1)?.toDouble()
            mapOf(
                "toMainMs" to field("toMainMs"),
                "toFirstFrameMs" to field("toFirstFrameMs"),
                "idleHeapMb" to field("idleHeapMb"),
                "idleRssMb" to field("idleRssMb"),
            )
        }
        // A launch that hit StartupProbe's deadline wrote no times: it never drew its window.
        val failed = results.count { it["toFirstFrameMs"] == null }
        // The first launch warms the OS file cache and is reported, not counted.
        val counted = (if (results.size > 1) results.drop(1) else results).filter { it["toFirstFrameMs"] != null }
        fun median(key: String) = counted.mapNotNull { it[key] }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        fun worst(key: String) = counted.mapNotNull { it[key] }.maxOrNull()
        val keys = listOf("toMainMs" to "To main()", "toFirstFrameMs" to "To the main window's first frame",
            "idleHeapMb" to "Idle heap, MB", "idleRssMb" to "Idle resident memory, MB")
        val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}, ${System.getProperty("os.arch")}"
        val report = buildString {
            appendLine("# Startup benchmark")
            appendLine()
            appendLine("$launches launches on $os; the first warms the file cache and is not counted. Idle is ${idleSeconds}s after the first frame.")
            appendLine()
            appendLine("| | Median | Worst |")
            appendLine("|---|---:|---:|")
            keys.forEach { (key, label) -> appendLine("| $label | ${median(key) ?: "n/a"} | ${worst(key) ?: "n/a"} |") }
            if (failed > 0) appendLine("\n$failed launch(es) never drew the main window before the deadline.")
        }
        reportDir.resolve("startup.md").writeText(report)
        logger.lifecycle(report)
        if (check) {
            // budgets.md rows read `| <key> | <limit> | ... |`; a median past its limit fails.
            val limits = budgets.readLines().mapNotNull { line ->
                Regex("^\\|\\s*`(\\w+)`\\s*\\|\\s*([0-9.]+)").find(line)?.let { it.groupValues[1] to it.groupValues[2].toDouble() }
            }.toMap()
            val over = keys.mapNotNull { (key, label) ->
                val limit = limits[key] ?: return@mapNotNull null
                val value = median(key) ?: return@mapNotNull null
                if (value > limit) "$label: $value, past its budget of $limit" else null
            }
            if (failed > 0) throw GradleException("$failed startup launch(es) never drew the main window.")
            if (over.isNotEmpty()) throw GradleException("Startup over budget: " + over.joinToString("; "))
        }
    }
}

// ── Soak test ─────────────────────────────────────────────────────────────────
// A service run for hours on one off-screen output, failing on a leak or a stall -- see ServiceSoak.
//   ./gradlew :composeApp:soakTest                    # four hours, report to build/reports/soak/
//   ./gradlew :composeApp:soakTest -PsoakMinutes=10   # a short local run
// Run weekly on main (and by hand) by .github/workflows/soak.yml; never part of `jvmTest` or `check`.
tasks.register<org.gradle.api.tasks.testing.Test>("soakTest") {
    group = "verification"
    description = "Runs a scripted service for hours on one off-screen output and fails on a leak or a stall."
    val parallel = tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest").get()
    testClassesDirs = parallel.testClassesDirs
    classpath = parallel.classpath
    maxParallelForks = 1
    maxHeapSize = "2g"
    filter { includeTestsMatching(soakTestClasses) }
    extensions.getByType<JacocoTaskExtension>().isEnabled = false
    outputs.upToDateWhen { false }
    // The hung-test reporter's five minutes are for unit tests. This one runs for hours by design, so
    // it is only called hung a quarter of an hour past the length of the run it was asked for --
    // before the task timeout below, so a hung run ends with its thread dump rather than without one.
    val soakMinutes = providers.gradleProperty("soakMinutes").orNull?.toDoubleOrNull() ?: 240.0
    systemProperty("churchpresenter.test.hangThresholdMs", ((soakMinutes + 15) * 60_000).toLong().toString())
    // The task itself gets half an hour past the run: the 30-minute cap every other test task
    // carries would end a four-hour run a quarter of the way in.
    timeout.set(Duration.ofMillis(((soakMinutes + 30) * 60_000).toLong()))
    val reportDir = layout.buildDirectory.dir("reports/soak").get().asFile
    systemProperty("soak.reportDir", reportDir.absolutePath)
    listOf("soakMinutes" to "soak.minutes", "soakFps" to "soak.fps", "soakCueSeconds" to "soak.cueSeconds",
        "soakSampleSeconds" to "soak.sampleSeconds", "soakStallSeconds" to "soak.stallSeconds").forEach { (gradle, system) ->
        providers.gradleProperty(gradle).orNull?.let { systemProperty(system, it) }
    }
    doLast {
        val report = reportDir.resolve("soak.md")
        if (report.exists()) logger.lifecycle(report.readText())
    }
}

// ── Screenshots (Roborazzi) ───────────────────────────────────────────────────
// Record: ./gradlew :composeApp:recordRoborazziJvm --tests '*ScreenshotTest*'
//
// Committed, so a change on screen can be reviewed and approved in the pull request that makes it.
// `.github/workflows/screenshots.yml` also posts the before/after as a PR comment, but the images in
// git are what a human signs off. See `ScreenshotSupport.SCREENSHOT_ROOT`.
//
// Record on ONE platform per branch: Skia rasterises text per platform, so re-recording the same
// unchanged states on another OS rewrites nearly every file and git keeps every version for ever.
//
// The tests pass paths relative to the module directory (`screenshots/...`), so this only has to
// agree with them for the compare/verify tasks; the workflow reads the directory itself.
roborazzi {
    outputDir.set(layout.projectDirectory.dir("screenshots"))
}

// ── Test coverage (JaCoCo) ────────────────────────────────────────────────────
// Run: ./gradlew :composeApp:jacocoTestReport
// HTML: composeApp/build/reports/jacoco/jacocoTestReport/html/index.html
//
// The jacoco plugin instruments every Test task automatically, so `jvmTest` needs no extra
// wiring — but the report itself does, because this is a Kotlin Multiplatform build: there is no
// conventional `test`/`main` pair for the plugin's default report to attach to. Hence an explicit
// task pointing at the jvm compilation's own output.
jacoco {
    toolVersion = "0.8.15" // 0.8.12+ is required for JDK 21 class files
}

// The classes the app's coverage is measured over -- by the report (and so the CI summary and the
// pull-request table, which read its XML) and by the floor alike, so the number printed is the number
// enforced. Restricted to this app's package root: every module is a real Gradle module now and is
// measured by its own build.
//
// The app-entry wiring at the end used to be excluded from the gate only, with the report kept
// all-inclusive. That made the report and the table show the floor breached while the gate passed --
// 4,918 lines of window/menu construction that only runs under a real display are 8.7% of the total.
// One list, one number.
fun coverableAppClasses() = fileTree(layout.buildDirectory.dir("classes/kotlin/jvm/main")) {
            include("org/churchpresenter/**")
            // Generated code -- no value in measuring, and it would inflate the denominator.
            exclude("**/BuildConfig*")
            // Compose emits synthetic ComposableSingletons holder classes per file.
            exclude("**/ComposableSingletons*")
            // Compose + AWT startup side-effect (delayed window activation) with no headless-testable
            // body; only its os gate is unit-tested (see MacWindowActivationTest).
            exclude("**/MacWindowActivationKt*")
            // Whole file is a DialogWindow-wrapped fun easter egg with an infinite
            // `while (true) { withFrameNanos { ... } }` confetti animation loop — can't be composed
            // headless and has no terminating state to assert on.
            exclude("**/KonamiEasterEggDialogKt*")
            // A hidden easter egg (unlocked via the same Konami-style arrow sequence, MainDesktop.kt)
            // rather than a documented feature in FEATURES.md — out of scope for the app's coverage
            // metrics regardless of how well tested it happens to be.
            exclude("**/CrosswordTabKt*")

            // Three further "generated code" exclusions have been proposed and are deliberately NOT
            // here, each measured against the report of 2026-08-08 (76.2% branch, 21,443/28,137):
            //
            //  - `**/*$$serializer*` and `**/*WhenMappings*` — JaCoCo already filters both. All 132
            //    serializer classes and 49 WhenMappings appear in the XML as EMPTY self-closing
            //    <class/> elements carrying zero instructions and zero branches. Adding the globs
            //    moves the number by 0.0 points; they would be config that looks load-bearing and
            //    is not.
            //  - DI-generated classes — there is no DI framework in this build. Zero matches for
            //    *_Factory*, *Hilt*, *Dagger*, *_Impl*.
            //  - the UI packages (`tabs/`, `dialogs/`, `composables/`, `ui/`, `presenter/`), on the
            //    theory that composables dilute branch coverage. They do not: those packages are at
            //    76.1% branch against 76.2% for the whole, so excluding all of them moves branch
            //    coverage from 76.2% to 76.4%. They are also hand-written, screenshot-tested UI, not
            //    generated code. The single largest drag is main.kt at 0.3% branch (711 of 28,137
            //    missed branches, 10.6% of the total), and the gate below already excludes it.

            // The app entry point: `main` itself, which opens real windows and binds real ports and
            // so only runs under a display. MainKt's ~200 synthetic lambda classes come along via
            // the `$` globs.
            //
            // Two files have left this list rather than sitting on it out of habit.
            // NavigationTopBar.kt is covered in full by NavigationTopBarTest. MainDesktop.kt is at
            // ~70% via MainDesktopComposeTest and the extracted MainDesktopLogic — excluding it
            // claimed a demonstrably testable file could not be tested, and kept ~960 covered lines
            // out of the enforced number. Counting it costs the gate about half a point against a
            // ~14 point margin. This list is only worth reading if every entry on it is still true.
            exclude("org/churchpresenter/app/churchpresenter/MainKt*")
            // The rest of main.kt, split out of it into its own files: the same window, menu and
            // dialog construction, which only runs under a display. Its decisions are pulled out
            // into CalendarLogic.kt and MainWindowLogic.kt, which are counted and tested. Temporary:
            // it comes off this list once display-driven tests cover these files.
            exclude("org/churchpresenter/app/churchpresenter/AppRootState.class")
            exclude("org/churchpresenter/app/churchpresenter/AppRootState$*")
            exclude("org/churchpresenter/app/churchpresenter/AppRootStateKt*")
            exclude("org/churchpresenter/app/churchpresenter/AppWiringKt*")
            exclude("org/churchpresenter/app/churchpresenter/CalendarAutomationKt*")
            exclude("org/churchpresenter/app/churchpresenter/CalendarManagerWindowKt*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowKt*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowChromeKt*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowDialogsKt*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowScope*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowState*")
            exclude("org/churchpresenter/app/churchpresenter/MainWindowWiringKt*")
            exclude("org/churchpresenter/app/churchpresenter/RemoteRequestWiringKt*")
            exclude("org/churchpresenter/app/churchpresenter/VirtualOutputsKt*")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates a coverage report for the app's own code from the jvmTest suite."
    dependsOn("jvmTest", "jvmTestSerial")
    // Point at the agent's .exec output explicitly. The `executionData(task)` overload resolves
    // to the task's own binary-results directory here, not the JacocoTaskExtension destination,
    // and fails with "Unable to read execution data file .../test-results/jvmTest/binary".
    // BOTH exec files: the loopback-UDP suites run in the separate serial task, and leaving their
    // data out would drop real coverage from the number and from the floor below.
    executionData.setFrom(
        layout.buildDirectory.file("jacoco/jvmTest.exec"),
        layout.buildDirectory.file("jacoco/jvmTestSerial.exec"),
    )

    classDirectories.setFrom(coverableAppClasses())
    sourceDirectories.setFrom(files("src/jvmMain/kotlin", "src/commonMain/kotlin"))

    onlyIf {
        gradle.startParameter.taskRequests.none { request -> request.args.any { it == "--tests" } }
    }

    reports {
        html.required.set(true)
        xml.required.set(true)  // for CI/coverage services
        csv.required.set(false)
    }

    // Wipe the HTML tree before writing it. JaCoCo only ever writes the files it is reporting on and
    // never removes the ones it isn't, so a page for a class that has since been excluded above --
    // or renamed, or deleted -- stays on disk indefinitely, unreachable from index.html but sitting
    // there at 0% for anyone who opens it directly or lands on it from a stale link or a search.
    // CrosswordTabKt was doing exactly that: excluded from the data, still browsable as a file.
    // The XML is a single file and is overwritten in place, so only the HTML tree needs this.
    val htmlReportDir = layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html")
    doFirst { delete(htmlReportDir) }

    finalizedBy("printCoverageLink")
}

// Coverage floors for this MVVM app, on all six JaCoCo counters (see violationRules below).
//
// This started as a single 75% LINE floor, on the reasoning that the View layer
// (tabs/dialogs/composables) is a large share of the lines but low-logic, so 75% was the honest
// ceiling. That reasoning is now out of date in one specific way: it was measured against the
// REPORT scope, which includes main.kt and the rest of the permanently-uncoverable app entry. The
// gated scope below excludes those and stands at 90.2% line, so an 85% line floor is a floor, not a
// stretch. What the original note got right still holds for BRANCH, which is the counter the UI
// layer genuinely does cap -- see the table in violationRules.
//
// Wired into `check` (see the bottom of this file) as of 2026-07-30, when the gated scope first
// cleared the floor. Run it on its own with:
//   ./gradlew :composeApp:jacocoTestCoverageVerification
// Same execution, class and source wiring as jacocoTestReport -- see coverableAppClasses() for why
// the app-entry wiring is outside both.
tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Fails the build if coverage of the coverable code is below the line/branch targets."
    dependsOn("jvmTest", "jvmTestSerial")
    // BOTH exec files: the loopback-UDP suites run in the separate serial task, and leaving their
    // data out would drop real coverage from the number and from the floor below.
    executionData.setFrom(
        layout.buildDirectory.file("jacoco/jvmTest.exec"),
        layout.buildDirectory.file("jacoco/jvmTestSerial.exec"),
    )
    classDirectories.setFrom(coverableAppClasses())
    sourceDirectories.setFrom(files("src/jvmMain/kotlin", "src/commonMain/kotlin"))
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.79".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "COMPLEXITY"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
            limit {
                counter = "METHOD"
                value = "COVEREDRATIO"
                minimum = "0.84".toBigDecimal()
            }
            limit {
                counter = "CLASS"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

// Prints the headline number and a clickable file:// link so the report doesn't have to be hunted
// for under build/. Deliberately a SEPARATE task rather than a doLast on jacocoTestReport: a
// doLast is skipped when the report is UP-TO-DATE, so re-running `check` would silently print
// nothing. This task declares no outputs, so it never goes up-to-date and always prints.
tasks.register("printCoverageLink") {
    val reportDir = layout.buildDirectory.dir("reports/jacoco/jacocoTestReport")
    val testResultsDir = layout.buildDirectory.dir("test-results/jvmTest")
    // Where to append the run's summary block, resolved at CONFIGURATION time from a property the
    // CI client passes in -- not from this process's environment. `System.getenv` here reads the
    // Gradle DAEMON's environment, which is frozen when the daemon starts, and GITHUB_STEP_SUMMARY
    // is a fresh temp file PER STEP that the runner reads and discards as soon as that step ends.
    // In practice the daemon is started by the Detekt step and still serving 26 minutes later when
    // this task runs, so the block was being appended to a file belonging to a step that finished
    // long before -- written successfully, read by nobody. Observed on run 31944001329: daemon up
    // at 11:19:45 (Detekt), printCoverageLink at 11:45:25, same daemon.
    //
    // A `-P` property travels with each individual invocation, so it is the live path every time.
    // The environment stays as the fallback for anyone running the task by hand.
    val stepSummaryPath = providers.gradleProperty("stepSummary")
        .orElse(providers.environmentVariable("GITHUB_STEP_SUMMARY"))
        .orNull
    doLast {
        val dir = reportDir.get().asFile
        val htmlIndex = dir.resolve("html/index.html")
        if (!htmlIndex.exists()) return@doLast

        // Each per-suite TEST-*.xml carries its own totals on the root <testsuite> tag; sum them
        // for the whole-run figure rather than reading jvmTest's own summary (Gradle doesn't write
        // one -- only the per-suite files land under test-results).
        val testSummary = runCatching {
            val suiteAttrs = Regex("""tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"""")
            val files = testResultsDir.get().asFile.listFiles { f -> f.name.endsWith(".xml") }
            if (files.isNullOrEmpty()) return@runCatching null
            var tests = 0; var skipped = 0; var failures = 0; var errors = 0
            files.forEach { file ->
                val match = suiteAttrs.find(file.readText().lineSequence().take(2).joinToString("\n"))
                    ?: return@forEach
                tests += match.groupValues[1].toInt()
                skipped += match.groupValues[2].toInt()
                failures += match.groupValues[3].toInt()
                errors += match.groupValues[4].toInt()
            }
            "$tests run, ${failures + errors} failed, $skipped skipped"
        }.getOrNull()

        // Regex rather than a DOM parse: the JaCoCo XML declares an external DTD, which a
        // DocumentBuilder tries to resolve over the network. The report-wide totals are the LAST
        // <counter> of each type in the document (they appear per-package/-class first, then once
        // more on the closing </report> element), so the final match per type is the overall figure.
        val lines = runCatching {
            val xml = dir.resolve("jacocoTestReport.xml")
            if (!xml.exists()) return@runCatching null
            val text = xml.readText()
            // Order matches the JaCoCo HTML overview table's own column order.
            val labels = listOf(
                "INSTRUCTION" to "instructions",
                "BRANCH" to "branches",
                "LINE" to "lines",
                "COMPLEXITY" to "complexity",
                "METHOD" to "methods",
                "CLASS" to "classes",
            )
            labels.mapNotNull { (type, label) ->
                val last = Regex("""<counter type="$type" missed="(\d+)" covered="(\d+)"/>""")
                    .findAll(text).lastOrNull() ?: return@mapNotNull null
                val missed = last.groupValues[1].toInt()
                val covered = last.groupValues[2].toInt()
                val total = covered + missed
                if (total == 0) null
                else "%.1f%% of %s (%d/%d)".format(100.0 * covered / total, label, covered, total)
            }
        }.getOrNull()

        val summaryLines = buildList {
            if (testSummary != null) add("Tests:    $testSummary")
            if (lines != null) {
                add("Coverage:")
                lines.forEach { add("  $it") }
            }
        }

        logger.lifecycle("")
        summaryLines.forEach { logger.lifecycle(it) }
        // The HTML tree only exists on the machine that produced it. On a CI runner it is deleted
        // with the workspace when the job ends, so the line is noise there -- a `file://` path
        // pointing at a directory the reader has no way to open. Printed locally, skipped in CI.
        //
        // Three slashes: File.toURI() yields "file:/path", which many terminals refuse to linkify.
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            logger.lifecycle("Report:   file://${htmlIndex.absolutePath}")
        }

        // Also put it on the run's summary page. The job log is ~14 minutes of Gradle output and
        // the numbers land in the middle of it; the step summary is the page a reviewer actually
        // opens from a pull request. Best-effort -- a coverage print must never fail a build.
        stepSummaryPath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching {
                File(path).appendText(
                    buildString {
                        appendLine("### Unit test coverage")
                        appendLine()
                        appendLine("```")
                        summaryLines.forEach { appendLine(it) }
                        appendLine("```")
                        appendLine()
                    }
                )
            }
        }
    }
}

// `check` runs the tests anyway, so generating the coverage report from the same run is nearly
// free -- and it makes the link appear at the end of the standard pre-commit command. The gate runs
// off the same execution data, so enforcing the floor here costs nothing beyond the check itself.
tasks.named("check") {
    dependsOn("jacocoTestReport")
    dependsOn("jacocoTestCoverageVerification")
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

// prepareAppResources scans the entire appResourcesRootDir — exclude module build
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
        // Skips only when signing was never asked for. Once desktop.signing.repo.path is set, a
        // missing certificate or password fails the build instead of shipping an unsigned installer.
        onlyIf {
            val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
            if (!isWindows) logger.info("$taskName skipped: not running on Windows")
            if (desktopSigningRepoPath == null) logger.info("$taskName skipped: desktop.signing.repo.path not set")
            isWindows && desktopSigningRepoPath != null
        }
        doLast {
            check(winCertPath != null && File(winCertPath).exists()) {
                "$taskName: certificate not found (desktop.signing.repo.path=$desktopSigningRepoPath, " +
                    "certFile=$winCertFileRel)"
            }
            check(winCertPassword.isConfigured()) { "$taskName: certPassword not configured" }
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

// ── Microsoft Store (MSIX) ────────────────────────────────────────────────────
// Wraps the jpackage app image in an MSIX for Store submission. The Store signs the package
// itself, so the output is left unsigned. The identity must match the app's reservation in
// Partner Center: pass -Pmsix.identityName, -Pmsix.publisher and -Pmsix.publisherDisplayName.
// The defaults produce a package that builds, not one the Store accepts.
val msixIdentityName = providers.gradleProperty("msix.identityName").filter { it.isNotBlank() }.getOrElse("ChurchPresenter.ChurchPresenter")
val msixPublisher = providers.gradleProperty("msix.publisher").filter { it.isNotBlank() }.getOrElse("CN=Church Presenter")
val msixPublisherDisplayName = providers.gradleProperty("msix.publisherDisplayName").filter { it.isNotBlank() }.getOrElse("Church Presenter")

// How long makeappx may run before packageMsix gives up on it; the app image packs in a few.
val msixTimeoutMinutes = 15L
val OUTPUT_DRAIN_MS = 5_000L

fun String.xmlEscaped() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

tasks.register("packageMsix") {
    description = "Package the Windows app image as an MSIX for the Microsoft Store (needs makeappx on PATH)"
    group = "compose desktop"
    dependsOn("createDistributable")
    onlyIf {
        val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
        if (!isWindows) logger.info("packageMsix skipped: not running on Windows")
        isWindows
    }
    val commits = gitCommitCount()
    // MSIX takes four segments and the Store requires the last to be 0; the first three are the
    // MSI's, so both installers of one build carry the same version.
    val msixVersion = "$versionYear.${commits / 256}.${commits % 256}.0"
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/ChurchPresenter")
    val stagingDir = layout.buildDirectory.dir("msix/layout")
    val outDir = layout.buildDirectory.dir("compose/binaries/main/msix")
    val templateDir = layout.projectDirectory.dir("src/msix")
    doLast {
        val staging = stagingDir.get().asFile
        staging.deleteRecursively()
        appImage.get().asFile.copyRecursively(staging)
        templateDir.dir("Assets").asFile.copyRecursively(File(staging, "Assets"))
        val manifest = templateDir.file("AppxManifest.xml").asFile.readText()
            .replace("@IDENTITY_NAME@", msixIdentityName.xmlEscaped())
            .replace("@PUBLISHER@", msixPublisher.xmlEscaped())
            .replace("@PUBLISHER_DISPLAY_NAME@", msixPublisherDisplayName.xmlEscaped())
            .replace("@VERSION@", msixVersion)
        File(staging, "AppxManifest.xml").writeText(manifest)

        val out = outDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val msix = File(out, "ChurchPresenter-$msixVersion-WINDOWS-x64.msix")
        // Verbose, with its output read into the build log: inherited output goes to the Gradle
        // daemon's own console, which the CI log never shows. Stdin is closed at once, so a prompt
        // reads end-of-input instead of waiting, and the wait is bounded, so a stall fails the task.
        val process = ProcessBuilder("makeappx", "pack", "/v", "/d", staging.absolutePath, "/p", msix.absolutePath, "/o")
            .redirectErrorStream(true)
            .start()
        process.outputStream.close()
        val output = Thread { process.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) } }
            .apply { isDaemon = true; start() }
        val finished = process.waitFor(msixTimeoutMinutes, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            error("makeappx did not finish within $msixTimeoutMinutes minutes")
        }
        output.join(OUTPUT_DRAIN_MS)
        val result = process.exitValue()
        if (result != 0) error("makeappx failed with exit code $result")
        logger.lifecycle("MSIX packaged: ${msix.name}")
    }
}

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




// ── Bundled ffmpeg ────────────────────────────────────────────────────────────
// The app opens every camera and capture card by spawning ffmpeg, so it ships one. On macOS that
// is not merely a convenience: nothing in the JVM ever touches AVFoundation, so on a machine with
// no ffmpeg no camera is opened, macOS is never asked for permission, and the app never appears in
// System Settings → Privacy → Camera at all — which is exactly what issue #464 reported.
//
// The binary is NOT committed. The DeckLink JNI libraries beside it in appResources are, but they
// are ~200 KB; an ffmpeg is tens of megabytes and git keeps every version of a binary for ever.
// It is fetched from the pinned url + digest in gradle/ffmpeg-builds.properties instead, and
// written into the same per-OS appResources directory, which both packaging and `run` already read.
//
// A target with no url configured is not an error: the app falls back to a discovered ffmpeg
// exactly as it did before, and the Camera Capture card in Settings → Projection says which one it
// ended up using.
val ffmpegBuildProps = Properties().apply {
    val f = rootProject.file("gradle/ffmpeg-builds.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/** The manifest key for the machine being built for: `macos-aarch64`, `windows-x86_64`, … */
fun ffmpegTargetKey(): String {
    val os = System.getProperty("os.name", "").lowercase()
    val osPart = when {
        os.contains("mac") || os.contains("darwin") -> "macos"
        os.contains("win") -> "windows"
        else -> "linux"
    }
    val arch = when (val a = System.getProperty("os.arch", "").lowercase()) {
        "aarch64", "arm64" -> "aarch64"
        "x86_64", "amd64" -> "x86_64"
        else -> a
    }
    return "$osPart-$arch"
}

val fetchBundledFfmpeg = tasks.register("fetchBundledFfmpeg") {
    description = "Downloads the pinned ffmpeg for this platform into appResources."
    group = "build"

    val target = ffmpegTargetKey()
    val url = ffmpegBuildProps.getProperty("$target.url", "")
    val sha256 = ffmpegBuildProps.getProperty("$target.sha256", "")
    val archiveKind = ffmpegBuildProps.getProperty("$target.archive", "zip")
    val entry = ffmpegBuildProps.getProperty("$target.entry", "ffmpeg")
    val osDir = target.substringBefore('-')
    val outFile = layout.projectDirectory.file("src/jvmMain/appResources/$osDir/$entry").asFile
    val cacheDir = layout.buildDirectory.dir("ffmpeg").get().asFile

    // Inputs, so the task re-runs when the pin changes and is up to date when it has not.
    inputs.property("url", url)
    inputs.property("sha256", sha256)
    outputs.file(outFile)
    onlyIf { url.isNotBlank() }

    doLast {
        require(sha256.isNotBlank()) {
            "gradle/ffmpeg-builds.properties: $target.url is set but $target.sha256 is not. " +
                "An unverified download is not something this build will run."
        }
        cacheDir.mkdirs()
        val archive = File(cacheDir, "$target-${sha256.take(12)}." + if (archiveKind == "tar") "tar" else "zip")
        if (!archive.exists() || sha256Of(archive) != sha256) {
            logger.lifecycle("Downloading ffmpeg for $target from $url")
            uri(url).toURL().openStream().use { input ->
                archive.outputStream().use { input.copyTo(it) }
            }
        }
        val actual = sha256Of(archive)
        check(actual == sha256) {
            "ffmpeg download for $target does not match its pinned digest.\n" +
                "  expected $sha256\n  actual   $actual\n" +
                "Either the pin is stale or the download was tampered with; do not 'fix' this by " +
                "updating the digest without knowing which."
        }
        val extractDir = File(cacheDir, "$target-extracted").apply { deleteRecursively(); mkdirs() }
        if (archiveKind == "tar") {
            val tar = ProcessBuilder("tar", "-xf", archive.absolutePath, "-C", extractDir.absolutePath)
                .redirectErrorStream(true).start()
            val tarOutput = tar.inputStream.bufferedReader().readText()
            check(tar.waitFor() == 0) { "tar could not unpack the ffmpeg archive for $target:\n$tarOutput" }
        } else {
            copy {
                from(zipTree(archive))
                into(extractDir)
            }
        }
        // __MACOSX holds AppleDouble side files ("._ffmpeg") that are not the program; skipping the
        // whole directory is simpler than reasoning about which of its entries could match.
        val found = extractDir.walkTopDown()
            .firstOrNull { it.isFile && it.name == entry && "__MACOSX" !in it.path }
            ?: error("No '$entry' inside the ffmpeg archive for $target ($url)")
        outFile.parentFile.mkdirs()
        found.copyTo(outFile, overwrite = true)
        outFile.setExecutable(true)
        logger.lifecycle("Bundled ffmpeg: ${outFile.relativeTo(rootProject.projectDir)} (${outFile.length() / 1_048_576} MB)")
    }
}

fun sha256Of(file: File): String =
    MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().use { stream ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

// ── Signing the bundled ffmpeg ────────────────────────────────────────────────
// Compose signs the launcher, the runtime and the native libraries inside jars, but treats
// everything under appResources as data and never codesigns it — so the ffmpeg lands in the .app
// at Contents/app/resources/ffmpeg exactly as the publisher shipped it, and notarization rejects
// the whole DMG for it: no Developer ID signature, no secure timestamp, no hardened runtime.
// The signature lives inside the Mach-O file, so signing the fetched copy before
// prepareAppResources picks it up is enough; Compose does not touch it afterwards.
val signBundledFfmpeg = tasks.register("signBundledFfmpeg") {
    description = "Codesigns the bundled macOS ffmpeg with the Developer ID identity, for notarization."
    group = "signing"
    dependsOn(fetchBundledFfmpeg)

    val identity = macSigningProps.getProperty("identityName", "")
    val keychain = macSigningProps.getProperty("keychain", "")
    val ffmpeg = layout.projectDirectory.file("src/jvmMain/appResources/macos/ffmpeg").asFile
    val entitlements = rootProject.file("desktop/macos/ffmpeg.entitlements")

    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX && identity.isConfigured() && ffmpeg.isFile }
    // The output is the input, re-signed in place; `--force` makes that idempotent.
    outputs.upToDateWhen { false }

    doLast {
        val command = buildList {
            add("codesign")
            add("--force")
            add("--sign"); add(identity)
            add("--options"); add("runtime")
            add("--timestamp")
            add("--entitlements"); add(entitlements.absolutePath)
            if (keychain.isNotBlank()) { add("--keychain"); add(keychain) }
            add(ffmpeg.absolutePath)
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "codesign failed for the bundled ffmpeg:\n$output" }
        logger.lifecycle("Signed bundled ffmpeg with \"$identity\"")
    }
}

// ── The bundled OMT libraries ─────────────────────────────────────────────────
// libomt and libvmx are MIT, so unlike the NDI Runtime they ship inside the installer. Fetched, not
// committed — 8 to 18 MB a platform — from the pinned url + digest in gradle/omt-builds.properties,
// exactly as ffmpeg is, into appResources/<os>/omt/. Both files land in the one directory because
// libomt, a .NET NativeAOT library, finds libvmx beside itself at run time.
val omtBuildProps = Properties().apply {
    val f = rootProject.file("gradle/omt-builds.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val fetchBundledOmt = tasks.register("fetchBundledOmt") {
    description = "Downloads the pinned libomt and libvmx for this platform into appResources."
    group = "build"

    val target = ffmpegTargetKey()
    val url = omtBuildProps.getProperty("$target.url", "")
    val sha256 = omtBuildProps.getProperty("$target.sha256", "")
    val archiveKind = omtBuildProps.getProperty("$target.archive", "zip")
    val entries = omtBuildProps.getProperty("$target.entries", "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    // The MIT notice, which has to travel with every copy of the libraries.
    val license = omtBuildProps.getProperty("$target.license", "")
    val osDir = target.substringBefore('-')
    val outDir = layout.projectDirectory.dir("src/jvmMain/appResources/$osDir/omt").asFile
    val cacheDir = layout.buildDirectory.dir("omt").get().asFile

    inputs.property("url", url)
    inputs.property("sha256", sha256)
    inputs.property("entries", entries.joinToString(","))
    outputs.files(entries.map { File(outDir, it.substringAfterLast('/')) })
    inputs.property("license", license)
    onlyIf { url.isNotBlank() }

    doLast {
        require(sha256.isNotBlank()) {
            "gradle/omt-builds.properties: $target.url is set but $target.sha256 is not. " +
                "An unverified download is not something this build will run."
        }
        require(entries.isNotEmpty()) { "gradle/omt-builds.properties: $target.entries names no library files." }
        cacheDir.mkdirs()
        val archive = File(cacheDir, "$target-${sha256.take(12)}." + if (archiveKind == "tar") "tar" else "zip")
        if (!archive.exists() || sha256Of(archive) != sha256) {
            logger.lifecycle("Downloading OMT libraries for $target from $url")
            uri(url).toURL().openStream().use { input ->
                archive.outputStream().use { input.copyTo(it) }
            }
        }
        val actual = sha256Of(archive)
        check(actual == sha256) {
            "OMT download for $target does not match its pinned digest.\n" +
                "  expected $sha256\n  actual   $actual\n" +
                "Either the pin is stale or the download was tampered with; do not 'fix' this by " +
                "updating the digest without knowing which."
        }
        // Said out loud rather than implied: a reviewer checking the bundle wants to see it happened.
        logger.lifecycle("OMT archive for $target matches its pinned SHA-256 (${sha256.take(12)}...)")
        val extractDir = File(cacheDir, "$target-extracted").apply { deleteRecursively(); mkdirs() }
        if (archiveKind == "tar") {
            val tar = ProcessBuilder("tar", "-xf", archive.absolutePath, "-C", extractDir.absolutePath)
                .redirectErrorStream(true).start()
            val tarOutput = tar.inputStream.bufferedReader().readText()
            check(tar.waitFor() == 0) { "tar could not unpack the OMT archive for $target:\n$tarOutput" }
        } else {
            copy {
                from(zipTree(archive))
                into(extractDir)
            }
        }
        outDir.mkdirs()
        for (entry in entries) {
            val found = File(extractDir, entry).takeIf { it.isFile }
                ?: error("No '$entry' inside the OMT archive for $target ($url)")
            found.copyTo(File(outDir, found.name), overwrite = true)
        }
        if (license.isNotBlank()) {
            val notice = File(extractDir, license).takeIf { it.isFile }
                ?: error("No '$license' inside the OMT archive for $target ($url)")
            notice.copyTo(File(outDir, "LICENSE.txt"), overwrite = true)
        }
        logger.lifecycle("Bundled OMT: ${outDir.relativeTo(rootProject.projectDir)} (${entries.joinToString { it.substringAfterLast('/') }})")
    }
}

// The same reason signBundledFfmpeg exists: Compose never codesigns anything under appResources,
// and the publisher's dylibs are only ad-hoc signed, which notarization rejects. Dylibs load into
// the app's own process, so they take no entitlements of their own — the app's apply.
val signBundledOmt = tasks.register("signBundledOmt") {
    description = "Codesigns the bundled macOS OMT libraries with the Developer ID identity, for notarization."
    group = "signing"
    dependsOn(fetchBundledOmt)

    val identity = macSigningProps.getProperty("identityName", "")
    val keychain = macSigningProps.getProperty("keychain", "")
    val libraries = listOf("libomt.dylib", "libvmx.dylib").map {
        layout.projectDirectory.file("src/jvmMain/appResources/macos/omt/$it").asFile
    }

    onlyIf {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX && identity.isConfigured() &&
            libraries.all { it.isFile }
    }
    outputs.upToDateWhen { false }

    doLast {
        for (library in libraries) {
            val command = buildList {
                add("codesign")
                add("--force")
                add("--sign"); add(identity)
                add("--options"); add("runtime")
                add("--timestamp")
                if (keychain.isNotBlank()) { add("--keychain"); add(keychain) }
                add(library.absolutePath)
            }
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "codesign failed for the bundled ${library.name}:\n$output" }
        }
        logger.lifecycle("Signed bundled OMT libraries with \"$identity\"")
    }
}

// prepareAppResources copies the per-OS directory into the bundle, and `run` reads the same one.
tasks.matching { it.name == "prepareAppResources" || it.name == "run" }.configureEach {
    dependsOn(fetchBundledFfmpeg)
    dependsOn(fetchBundledOmt)
}
tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(signBundledFfmpeg)
    dependsOn(signBundledOmt)
}

// ── The runtime jpackage will bundle has to be self-contained ─────────────────
// jpackage copies the JDK it is given into the .app, and a JDK from a package manager is not
// self-contained: Homebrew's builds `libfontmanager.dylib` against Homebrew's own harfbuzz and
// links it by absolute path. On the machine that built it everything works; on anyone else's the
// first thing to touch a font dies with
//   Library not loaded: /usr/local/opt/harfbuzz/lib/libharfbuzz.0.dylib
// and takes the app with it. That reached Sentry from a bundle built exactly that way.
//
// The toolchain API resolves *any* registered JDK 21, Homebrew's included, so which one is picked
// is not something the build states — it is whatever the machine happens to have. This checks the
// one that was actually chosen, and checks the thing that matters rather than the vendor's name:
// every absolute load path must be macOS's own, because nothing else travels inside the bundle.
val checkMacRuntimeSelfContained = tasks.register("checkMacRuntimeSelfContained") {
    description = "Fails when the JDK about to be bundled links to libraries outside macOS's own."
    group = "verification"

    val javaHome = resolvedJdk21Home
    onlyIf { System.getProperty("os.name", "").lowercase().contains("mac") && javaHome != null }

    doLast {
        val fontManager = File(javaHome!!, "lib/libfontmanager.dylib")
        if (!fontManager.exists()) return@doLast   // A JDK laid out differently; nothing to judge.

        val otool = ProcessBuilder("otool", "-L", fontManager.absolutePath)
            .redirectErrorStream(true).start()
        val output = otool.inputStream.bufferedReader().readText()
        if (otool.waitFor() != 0) {
            logger.warn("Could not check the bundled runtime: otool said\n$output")
            return@doLast
        }

        // Skip the first line, which is the file's own name rather than a dependency.
        val foreign = output.lineSequence().drop(1)
            .map { it.trim().substringBefore(" (") }
            .filter { it.startsWith("/") }
            .filterNot { it.startsWith("/usr/lib/") || it.startsWith("/System/") }
            .toList()

        check(foreign.isEmpty()) {
            """
            The JDK this build would bundle is not self-contained, and the app would crash on every
            machine but this one. $javaHome
            links to:
            ${foreign.joinToString("\n            ")}

            Those paths do not exist inside a packaged .app. Use a distribution JDK — Temurin 21 —
            rather than one from Homebrew or MacPorts, and check `/usr/libexec/java_home -V`.
            """.trimIndent()
        }
    }
}

tasks.matching { it.name in setOf("packageDmg", "packageReleaseDmg", "createDistributable", "createReleaseDistributable") }
    .configureEach { dependsOn(checkMacRuntimeSelfContained) }
