import java.time.Duration
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

// ── Build provenance ──────────────────────────────────────────────────────────
// The app is GPLv3 and hardcodes the live-map ping URL, so a fork built from
// source pings churchpresenter.org with a payload identical to an official
// install. Stamping the build's git origin and build kind lets the server tell
// them apart (see the build_channel column and api/ping.ts in the website
// repo). This is attribution for honest forks, not authentication — anyone can
// patch these constants out. Every helper falls back to "unknown"/"nogit"
// rather than failing the build.

/** Runs a git command, returning null when git is missing or exits non-zero. */
fun gitOutput(vararg args: String): String? {
    return try {
        val process = ProcessBuilder("git", *args)
            .directory(rootProject.projectDir)
            // Deliberately NOT redirectErrorStream(true): git writes "fatal: not a
            // git repository" to stderr, and merging it would hand back that text
            // as if it were a real value.
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() != 0) null else output
    } catch (_: Exception) { null }
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
    baseline = rootProject.file("config/detekt/baseline.xml")
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
            implementation(libs.sqlite.jdbc)
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
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)

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
            // The bundled study data: the Strong's dictionary and the interlinear index over it.
            implementation(projects.dictionary)
            implementation(projects.announcementsTab)
            implementation(projects.dictionaryTab)
            implementation(projects.dictionarySettingsTab)
            implementation(projects.lowerThirdTab)
            implementation(projects.sttTab)
            implementation(projects.qaTab)
            implementation(projects.lowerThirdSettingsTab)
            // The HTTP/WebSocket surface the app exposes: the wire format, the routes, TLS, the
            // tunnel and the instance-link client. What a remote request then does to the app is
            // `remote/` here.
            implementation(projects.companionServer)

            // Everything the app has ever put on the screen, counted: the tallies, the play log
            // behind them and the CSV/Excel export CCLI reporting needs. CCLIReportDialog draws it.
            implementation(projects.statistics)
            // The song library: the grid of every song in the library, opened from the Help menu.
            // Every asset the app draws or reads — icons, strings, fonts — see the :resources
            // module. One `Res` class, so `Res.drawable.x` and `Res.string.y` read exactly as they
            // did when the assets lived here.
            api(projects.resources)
            // The custom composables the tabs and dialogs are built from.
            implementation(projects.uiComponents)
            implementation(projects.songlibrary)
            implementation(projects.songChords)
            // The Companion Satellite protocol client: a real module rather than a mounted source
            // directory, wrapped by CompanionSatelliteViewModel.
            implementation(projects.companionSatellite)
            // The ATEM protocol client: the UDP conversation with the switcher — connect, state
            // dump, key control and media-pool upload. AtemBridge is the app-side wiring.
            implementation(projects.atem)
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
            implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
            implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.10.1")
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
            implementation("uk.co.caprica:vlcj:4.8.3")
            implementation("net.java.dev.jna:jna:5.18.1")
            implementation("net.java.dev.jna:jna-platform:5.18.1")
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
            implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
            implementation("com.github.hypfvieh:dbus-java-transport-junixsocket:5.2.0")
            // FileKit — native OS file dialogs (Windows Explorer dialog, macOS NSOpenPanel).
            // filekit-dialogs-jvm transitively pulls in dbus-java-transport-native-unixsocket,
            // which conflicts with the junixsocket transport above — TransportBuilder throws
            // TransportRegistrationException if both UNIX transport providers are on the
            // classpath at once, so the native one must be excluded here.
            implementation("io.github.vinceglb:filekit-dialogs:${libs.versions.filekit.get()}") {
                exclude(group = "com.github.hypfvieh", module = "dbus-java-transport-native-unixsocket")
            }
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
    // ScreenshotSupport: the theme-stacking/trim/capture machinery every screenshot suite uses.
    add("jvmTestImplementation", testFixtures(projects.uiComponents))
    add("jvmTestImplementation", testFixtures(projects.coreModels))
    // CrashReportSweep: the Bible tab and view-model failure tests exercise paths that really
    // write a crash report. It lives with :diagnostics because it exists for CrashReporter's own
    // design -- the report directory is resolved once per JVM and cannot be redirected after.
    add("jvmTestImplementation", testFixtures(projects.diagnostics))
    // SpbFixture: the .spb writer the Bible tab, view-model and server suites build fixtures with.
    // It stays with the module that owns the format.
    add("jvmTestImplementation", testFixtures(projects.bible))
    // DictionaryFixture: the four-entry dictionary and the interlinear index over it. It stays with
    // the module that owns the bundled files, so the tab, view-model and server suites here and the
    // module's own suite assert against one corpus rather than two.
    add("jvmTestImplementation", testFixtures(projects.dictionary))
    // pdfDeck(): Deck's constructor is internal to :presentation-engine, so tests that need a
    // synthetic deck build it through the module's own fixtures.
    add("jvmTestImplementation", testFixtures(projects.presentationEngine))
    // FakeAtemSwitcher: the loopback switcher the ATEM suites drive. It stays with the client it
    // was captured against, and the app's own ATEM tests (bridge, upload routes, lower third) borrow
    // it from there rather than keeping a second copy.
    add("jvmTestImplementation", testFixtures(projects.atem))
    // CompanionServerFixture: starts a server on a free port and hands back a client for it. The
    // app's remote-command and instance-link suites drive a real server, so they borrow the fixture
    // from the module that owns the wire format rather than standing one up twice.
    add("jvmTestImplementation", testFixtures(projects.companionServer))
    // The Lottie-folder fixtures -- what counts as an animation on disk, and the wait for the
    // scan that decides. The server tab's lower-third triggers are driven from a real folder
    // here, and the options page that lists the same folder now lives in
    // :lower-third-settings-tab, so both read one definition rather than a copy each.
    add("jvmTestImplementation", testFixtures(projects.lowerThirdTab))
    // SILENT_STT_URL: a loopback port that accepts and then says nothing. The Bible tab's
    // auto-follow tests need an STT server that never answers, and getting that wrong once
    // pushed jvmTest past CI's step budget -- so there is one definition, in the STT module.
    add("jvmTestImplementation", testFixtures(projects.sttTab))
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
        // Explicitly set Skiko GPU backend to prevent software fallback
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("mac") -> jvmArgs(
                "-Dskiko.renderApi=METAL",
                // Dev-mode has no .app bundle to source CFBundleName from, so macOS falls back to
                // the main class name ("MainKt") in the menu bar without this. macOS-only: an
                // unrecognized -Xdock option on other platforms aborts JVM startup entirely.
                "-Xdock:name=Church Presenter"
            )
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
    // Planning Center integration: one shared OAuth app for the whole project (per PCO's own
    // multi-tenant guidance — each church authorizes this one app, rather than registering their
    // own). Neither value is committed — both are read from env vars at build time, same pattern
    // as SENTRY_AUTH_TOKEN above. Local/dev builds without these set simply can't complete OAuth.
    // Build provenance for the live-map ping — see the helpers at the top of this
    // file. Resolved here at build time, never at runtime: a runtime git call
    // would read whatever directory the user happened to launch the app from.
    val repoSlug = gitRepoSlug()
    val buildType = gitBuildType(isRelease)
    val planningCenterClientId = System.getenv("PLANNING_CENTER_CLIENT_ID") ?: ""
    val planningCenterClientSecret = System.getenv("PLANNING_CENTER_CLIENT_SECRET") ?: ""
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
            |    const val PLANNING_CENTER_CLIENT_ID = "$planningCenterClientId"
            |    const val PLANNING_CENTER_CLIENT_SECRET = "$planningCenterClientSecret"
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
    timeout.set(Duration.ofMinutes(30))

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
val serialTestClasses = listOf(
    "*AtemUploadTracedTest",
    "*CompanionServerAtemKeyTest",
    "*CompanionServerAtemUploadTest",
    "*LowerThirdAtemUploadTest",
    "*LowerThirdSequencerKeyTest",
    // The other three suites that open the ATEM upload dialog. Doing so renders a Lottie frame and
    // encodes it for the switcher behind three 5s deadlines -- the upload button enabling, the
    // dialog's rows composing, and `waitForAtemPrepared` (LowerThirdTabTestSupport.kt:263). That is
    // real work against a wall clock, so on a runner with four forks competing for the CPU it is the
    // machine being measured, not the code: LowerThirdAtemDialogExtraTest timed out at exactly that
    // wait on main. LowerThirdAtemUploadTest above was in this list from the start and never failed,
    // which is the tell -- these three simply had not drawn the short straw yet.
    "*LowerThirdAtemDialogTest",
    "*LowerThirdAtemDialogExtraTest",
    "*LowerThirdTabScreenshotTest",
    // Here for a different reason: it binds a fixed port AND draws that port into the image (the
    // Server URL row, the connection QR). Shifting the port per fork would rewrite every one of its
    // committed screenshots on every run, so it keeps the literal and runs where nothing competes
    // for the port.
    "*ServerSettingsTabScreenshotTest",
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

val jvmTestSerial by tasks.registering(org.gradle.api.tasks.testing.Test::class) {
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

    // Restrict to this app's package root. Nothing else compiles into this output directory any
    // more — every module is a real Gradle module now — but the app's own generated and
    // synthetic classes are excluded below, and the modules are measured by their own builds.
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/jvm/main")) {
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
        }
    )
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
// Same execution/class/source wiring as jacocoTestReport (app package only; modules measured by
// their own builds), with ONE deliberate difference: the app-entry wiring is excluded here but NOT
// from the report. The report stays all-inclusive so nothing is hidden -- its HTML still shows
// main.kt at 0%, which is the truth. The gate excludes those files because they are 4,918 lines of
// window/menu/composable-tree construction that only runs under a real display, i.e. permanently
// uncoverable: they alone are 8.7% of the total, so gating on them would mean lowering the floor to
// ~68% and the number would stop meaning "well tested". The gate measures the code someone can
// actually cover; the report reports everything.
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
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/jvm/main")) {
            include("org/churchpresenter/**")
            exclude("**/BuildConfig*")
            exclude("**/ComposableSingletons*")
            // Kept in sync with jacocoTestReport above -- both are untestable-by-construction.
            exclude("**/MacWindowActivationKt*")
            exclude("**/KonamiEasterEggDialogKt*")
            // Kept in sync with jacocoTestReport above -- a hidden easter egg, out of scope for the
            // coverage floor regardless of how well tested it happens to be.
            exclude("**/CrosswordTabKt*")
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
        }
    )
    sourceDirectories.setFrom(files("src/jvmMain/kotlin", "src/commonMain/kotlin"))
    violationRules {
        // All six counters are at 70%. History: 85/80/85/75/85/85 until 2026-08-18, when CLASS
        // fell to 84.77% and the gate began blocking every merge and all six were dropped to 75%
        // in one step; lowered again to 70% on 2026-08-23 by request. Measured 2026-08-23 on the
        // gated scope, after :companion-server and :statistics were extracted out of this module:
        //
        //   counter      measured   floor   margin
        //   INSTRUCTION      0.89    0.70     +19
        //   BRANCH           0.79    0.70      +9
        //   LINE             0.89    0.70     +19
        //   COMPLEXITY       0.76    0.70      +6
        //   METHOD           0.85    0.70     +15
        //   CLASS            0.85    0.70     +15
        //
        // **Every counter now sits well clear of its floor, so this gate no longer catches a
        // regression** -- coverage could fall by six points on the tightest counter and nineteen on
        // the loosest before it says anything. It is a backstop against collapse, not a ratchet.
        // The measured column above is the real state of the module; read that, not the floor, when
        // judging whether this module is well tested. Raising the floors back toward the measured
        // values is what would make the gate mean something again.
        //
        // Note the two extractions did NOT move these numbers much (88.16/80.04/88.69/76.85/85.44/
        // 84.77 before them, above): the code that left took its own tests with it, so the ratios
        // held even though the denominator shrank.
        //
        // The notes below describe why the numbers sit where they do and are unchanged.
        //
        // LINE was 90% until 2026-08-10, when main.kt was split up. PresenterWindows.kt came out of
        // it: 535 lines of GraphicsEnvironment + AWT Window + DeckLink construction that throws
        // under java.awt.headless and so cannot be covered at all. Inside main.kt those lines were
        // invisible to this gate, because MainKt* is excluded above; in their own file they are
        // counted, and they cost 0.84 points on their own. The testable parts of that file were
        // extracted rather than left behind -- PresenterOutputContent, PresenterModeContent and
        // PresenterTransitionEffects all came out of it and are covered -- so what remains really
        // is display-only.
        //
        // The floor was lowered to 85% rather than excluding PresenterWindowsKt*, which would have
        // kept the number at ~90% by hiding the same lines the old arrangement hid. 85% is what
        // every other counter that can be honestly measured already sits at.
        //
        // BRANCH is now the tight one -- 0.8 points, a few dozen branches -- and WILL fail on a
        // small regression. That is the point, but it also means a PR that adds a chunk of
        // legitimately hard-to-cover code trips it. When that happens the fix is to cover it or to
        // argue the floor down, not to widen an exclusion above: exclusions decide what the number
        // means, floors decide how much of it we insist on. (COMPLEXITY used to be the tight one at
        // +0.4; it is at +3.5 now.)
        //
        // BRANCH and COMPLEXITY sit lowest and cannot be pushed to where LINE is, for a structural
        // reason rather than a testing gap: 396 classes are at 100% LINE and 88.6% BRANCH -- 757
        // branches missed on code where every line ran. Those are the Compose compiler's `$changed`
        // bitmask skip checks, emitted INSIDE each composable's own method, so no class-file
        // exclusion can remove them. They are ~3.5% of the branch denominator.
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
            limit {
                counter = "COMPLEXITY"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
            limit {
                counter = "METHOD"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
            limit {
                counter = "CLASS"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
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



