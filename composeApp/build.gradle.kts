import java.time.Duration
import org.churchpresenter.buildlogic.appVersion
import org.churchpresenter.buildlogic.currentJcefPlatform
import org.churchpresenter.buildlogic.currentOsClassifier
import org.churchpresenter.buildlogic.isConfigured
import org.churchpresenter.buildlogic.resolvedJdk21Home
import org.churchpresenter.buildlogic.signingProps
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Calendar

// The app's build. What it shares with its `cp.app-*` plugins -- the version, git provenance, the
// signing credentials and the JDK it packages with -- is build-logic's AppBuild.kt; the BuildConfig,
// benchmarks, coverage, installer signing and bundled natives are those plugins, each applied below
// at the point its tasks have to be configured.

// The CycloneDX plugin's own Jackson carries two high advisories (GHSA-p6pp-m3f8-5c89,
// GHSA-7hhh-6rmp-j9qf) until 2.22.3; hold the build classpath at the fixed release. Build-time
// only -- nothing here reaches the app.
buildscript {
    dependencies {
        constraints {
            classpath(libs.jackson.core)
        }
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sentry)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.cyclonedx)
    id("cp.base")
    id("cp.detekt")
    jacoco
}

// Models from Compose-free modules that composables take, declared stable, as cp.compose does for
// every module: see the file.
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
}

// The software bill of materials: every library the shipped app runs with, and nothing it is only
// built or tested with. `build.yml` attaches the JSON to each release.
tasks.cyclonedxDirectBom {
    componentName.set("ChurchPresenter")
    componentVersion.set(appVersion())
    includeConfigs.set(listOf("jvmRuntimeClasspath"))
    jsonOutput.set(layout.buildDirectory.file("reports/cyclonedx/bom.json"))
    xmlOutput.set(layout.buildDirectory.file("reports/cyclonedx/bom.xml"))
}

// The Kotlin Multiplatform source sets, in place of the convention's `src/main` + `src/test`.
detekt {
    source.setFrom("src/jvmMain/kotlin", "src/jvmTest/kotlin")
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
            implementation(libs.kotest.property)

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
            implementation(projects.helper)
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
            implementation(projects.signage)
            implementation(projects.signageCore)
            // The OBS Studio integration: client, scene mapping and settings page.
            implementation(projects.obs)
            // The layer model: layers, cues, program and preview.
            implementation(projects.liveShow)
            implementation(projects.showControl)
            // The live output: PresenterManager, the output windows and the off-screen outputs.
            implementation(projects.liveOutput)
            implementation(projects.outputGuard)
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
            // The dialogs and small windows: About, Contact Us, keyboard shortcuts, remote, show control.
            implementation(projects.dialogs)
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
            implementation(libs.jcefmaven)
            // Bundle platform-specific Chromium binaries so no runtime download is needed
            val jcefNativesVersion = "jcef-cffac27+cef-143.0.14+gdd46a37+chromium-143.0.7499.193"
            runtimeOnly("me.friwi:jcef-natives-${currentJcefPlatform()}:$jcefNativesVersion")
            // JavaFX for WebView (website presenter)
            val jfxClassifier = currentOsClassifier()
            val jfxVersion = libs.versions.javafx.get()
            val jfxModules = listOf("javafx-base", "javafx-graphics", "javafx-media", "javafx-swing", "javafx-controls", "javafx-web")
            jfxModules.forEach { module ->
                implementation("org.openjfx:$module:$jfxVersion:$jfxClassifier")
            }
            // Windows-specific: also pull win natives when building on Windows
            if (jfxClassifier == "win") {
                jfxModules.forEach { module ->
                    runtimeOnly("org.openjfx:$module:$jfxVersion:win")
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

// The generated BuildConfig, added to jvmMain once `jvm()` above has made it.
apply(plugin = "cp.app-provenance")

val macSigningProps = signingProps("macos")

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

        resolvedJdk21Home()?.let { javaHome = it }

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
            // Windows MSI limits each version segment to 0-255, so split commit count across minor.patch
            packageVersion = appVersion()
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

// The DMG's volume icon, signtool on the Windows installers, the MSIX and the .deb's signature.
apply(plugin = "cp.app-signing")

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

val serialTestClasses = listOf(
    // The ATEM upload suites that used to lead this list moved to `:server`, whose `test` task forks
    // once, so they are alone in their JVM there.
    // The suite that opens the ATEM upload dialog, `LowerThirdTabScreenshotTest`, moved to
    // `:lower-third` with the tab's other suites; that `test` task forks once, so it is alone there.
    // All thirteen of these seed one fixed directory on disk -- AppPreviewSupport's
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

// The render, GPU, isolation and startup benchmarks, the UI monkey and the soak test -- after the
// Test settings above, so each task's own forks, heap and limits are the ones that stick.
apply(plugin = "cp.app-benchmarks")

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

// The coverage report and the six-counter floor over the app's own classes.
apply(plugin = "cp.app-coverage")

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

// ffmpeg and the OMT libraries, fetched against pinned digests and signed, and the check that the
// JDK jpackage bundles is self-contained.
apply(plugin = "cp.app-natives")
