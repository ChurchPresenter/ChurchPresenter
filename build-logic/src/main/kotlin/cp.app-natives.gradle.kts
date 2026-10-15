import org.churchpresenter.buildlogic.isConfigured
import org.churchpresenter.buildlogic.resolvedJdk21Home
import org.churchpresenter.buildlogic.signingProps
import java.io.File
import java.security.MessageDigest
import java.util.Properties

// :composeApp's bundled natives -- ffmpeg and the OMT libraries, fetched against pinned digests and
// codesigned for notarization -- and the check that the JDK jpackage bundles is self-contained.

val macSigningProps = signingProps("macos")
val resolvedJdk21Home: String? = resolvedJdk21Home()

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
