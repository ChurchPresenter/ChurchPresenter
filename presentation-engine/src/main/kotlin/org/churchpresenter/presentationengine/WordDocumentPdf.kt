package org.churchpresenter.presentationengine

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Uses the installed office renderer so document pagination, fonts and images reach the PDF. */
internal object WordDocumentPdf {
    private const val CONVERSION_TIMEOUT_SECONDS = 90L
    private val conversionLock = Any()

    fun convert(file: File): File = synchronized(conversionLock) {
        val result = cacheFile(file)
        val cache = result.parentFile
        if (result.isFile && result.length() > 0) return@synchronized result
        check(cache.isDirectory || cache.mkdirs()) { "Cannot create document conversion cache" }
        val work = Files.createTempDirectory(cache.toPath(), "conversion-").toFile()
        try {
            val converted = render(file, work)
            check(converted.isFile && converted.length() > 0) { "Office did not produce a PDF" }
            Files.move(converted.toPath(), result.toPath(), StandardCopyOption.REPLACE_EXISTING)
            result
        } finally {
            work.deleteRecursively()
        }
    }

    fun cacheFile(file: File): File {
        val identity = "${file.canonicalPath}:${file.lastModified()}:${file.length()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cache = File(System.getProperty("user.home"), ".churchpresenter/document-pdf/$digest")
        return File(cache, "document.pdf")
    }

    private fun render(file: File, work: File): File {
        val office = libreOfficeExecutable()
        if (office != null) {
            val profile = File(work, "profile").toURI().toString()
            runConversion(ProcessBuilder(office, "-env:UserInstallation=$profile", "--headless",
                "--convert-to", "pdf", "--outdir", work.absolutePath, file.absolutePath))
            return File(work, "${file.nameWithoutExtension}.pdf")
        }
        check(System.getProperty("os.name").startsWith("Windows")) {
            "Install LibreOffice to project Word documents."
        }
        val output = File(work, "document.pdf")
        val builder = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", WORD_EXPORT_SCRIPT)
        builder.environment()["CHURCHPRESENTER_WORD_SOURCE"] = file.absolutePath
        builder.environment()["CHURCHPRESENTER_WORD_PDF"] = output.absolutePath
        runConversion(builder)
        return output
    }

    private fun libreOfficeExecutable(): String? {
        val candidates = listOf(
            "C:/Program Files/LibreOffice/program/soffice.com",
            "C:/Program Files/LibreOffice/program/soffice.exe",
            "C:/Program Files (x86)/LibreOffice/program/soffice.com",
            "C:/Program Files (x86)/LibreOffice/program/soffice.exe",
            "/Applications/LibreOffice.app/Contents/MacOS/soffice",
        ) + System.getenv("PATH").orEmpty().split(File.pathSeparator).flatMap {
            listOf(File(it, "soffice").path, File(it, "soffice.com").path, File(it, "soffice.exe").path)
        }
        return candidates.firstOrNull { File(it).isFile }
    }

    private fun runConversion(builder: ProcessBuilder) {
        val process = builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            check(process.waitFor(CONVERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Word to PDF conversion timed out" }
            if (process.exitValue() != 0) {
                throw IOException(
                    "Word to PDF conversion failed. Check the document and the installed office renderer."
                )
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private val WORD_EXPORT_SCRIPT = """
        ${'$'}ErrorActionPreference = 'Stop'
        ${'$'}office = ${'$'}null
        ${'$'}document = ${'$'}null
        try {
            try { ${'$'}office = New-Object -ComObject Word.Application }
            catch { ${'$'}office = New-Object -ComObject KWPS.Application }
            ${'$'}office.Visible = ${'$'}false
            ${'$'}office.DisplayAlerts = 0
            ${'$'}office.AutomationSecurity = 3
            ${'$'}document = ${'$'}office.Documents.Open(${'$'}env:CHURCHPRESENTER_WORD_SOURCE, ${'$'}false, ${'$'}true)
            ${'$'}document.ExportAsFixedFormat(${'$'}env:CHURCHPRESENTER_WORD_PDF, 17)
        } finally {
            if (${'$'}null -ne ${'$'}document) { ${'$'}document.Close(0) }
            if (${'$'}null -ne ${'$'}office) { ${'$'}office.Quit() }
        }
    """.trimIndent()
}
