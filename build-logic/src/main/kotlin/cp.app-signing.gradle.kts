import org.churchpresenter.buildlogic.desktopSigningRepoPath
import org.churchpresenter.buildlogic.gitCommitCount
import org.churchpresenter.buildlogic.isConfigured
import org.churchpresenter.buildlogic.signingProps
import org.churchpresenter.buildlogic.versionYear
import org.churchpresenter.buildlogic.webEngineRecommends
import org.churchpresenter.buildlogic.withPreT64Alternatives
import java.io.File
import java.util.concurrent.TimeUnit

// :composeApp's installers after packaging: the DMG's volume icon, signtool on the Windows
// installers, the Microsoft Store MSIX and the Linux .deb's GPG signature. macOS app signing is the
// Compose plugin's, configured in the app's `compose.desktop` block.

val signingRepoPath: String? = desktopSigningRepoPath()
val winSigningProps = signingProps("windows")
val linuxSigningProps = signingProps("linux")

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

// ── Windows Code Signing ──────────────────────────────────────────────────────
// Runs signtool.exe on the packaged .msi / .exe after Compose Desktop packaging.
// Only executes on Windows with a configured certificate.
val winCertFileRel = winSigningProps.getProperty("certFile", "")
val winCertPath = if (signingRepoPath != null && winCertFileRel.isConfigured())
    "$signingRepoPath/windows/$winCertFileRel" else null
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
            if (signingRepoPath == null) logger.info("$taskName skipped: desktop.signing.repo.path not set")
            isWindows && signingRepoPath != null
        }
        doLast {
            check(winCertPath != null && File(winCertPath).exists()) {
                "$taskName: certificate not found (desktop.signing.repo.path=$signingRepoPath, " +
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

// ── Linux .deb dependencies ───────────────────────────────────────────────────
// Every package the .deb needs that Debian renamed for 64-bit time_t also accepts its old name, so
// the .deb can install on releases from before the rename, Ubuntu 22.04 among them (issue #875) --
// see withPreT64Alternatives. jpackage has no option to say this, so the control file is rewritten
// after packaging and before signing. The same rewrite adds a Recommends: of the web engine's system
// libraries, which JCEF, downloaded on first use, needs and jpackage cannot see (webEngineRecommends).
fun rewriteDebDepends(deb: File) {
    val root = java.nio.file.Files.createTempDirectory("deb-depends").toFile()
    try {
        runCommand("dpkg-deb", "-R", deb.absolutePath, root.absolutePath)
        val control = File(root, "DEBIAN/control")
        val lines = control.readLines()
        val fixed = lines.filterNot { it.startsWith("Recommends:") }.flatMap { line ->
            if (line.startsWith("Depends:")) {
                listOf(
                    "Depends: " + withPreT64Alternatives(line.removePrefix("Depends:")),
                    "Recommends: $webEngineRecommends",
                )
            } else listOf(line)
        }
        if (fixed == lines) return
        control.writeText(fixed.joinToString("\n", postfix = "\n"))
        // Root-owned, as jpackage's own build under fakeroot left every file.
        runCommand("dpkg-deb", "--root-owner-group", "-b", root.absolutePath, deb.absolutePath)
        logger.lifecycle("${deb.name}: ${fixed.first { it.startsWith("Depends:") }}")
        logger.lifecycle("${deb.name}: ${fixed.first { it.startsWith("Recommends:") }}")
    } finally {
        root.deleteRecursively()
    }
}

tasks.matching { it.name == "packageDeb" }.configureEach {
    doLast {
        layout.buildDirectory.dir("compose/binaries/main/deb").get().asFile
            .listFiles { f -> f.extension.equals("deb", ignoreCase = true) }
            ?.forEach(::rewriteDebDepends)
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
