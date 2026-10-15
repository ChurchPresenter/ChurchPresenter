package org.churchpresenter.detektrules

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One blocking call allowed on the event thread: in [function] of the file at [path], the call [call]. */
data class AllowedCall(val path: String, val function: String, val call: String)

/**
 * The calls [BlockingCallOnEventThread] lets through, read from a file written by hand: one line
 * each, `<path from the repo root>: <function> <call>  # why it stays`. Nothing writes the file, a
 * line whose call is gone is reported, and a line naming a file that is gone fails
 * `checkLiveSafeAllowlist`, so the list only shrinks. [source] names the file in a finding.
 */
class LiveSafeAllowlist(val entries: List<AllowedCall>, val source: String = "") {

    companion object {
        val EMPTY = LiveSafeAllowlist(emptyList())

        private val cache = ConcurrentHashMap<String, LiveSafeAllowlist>()

        /** The entries in [text]; a line that does not parse is an error, not a silent skip. */
        fun parse(text: String, source: String = ""): LiveSafeAllowlist {
            val entries = text.lines().mapIndexedNotNull { index, line -> entryOf(line, index + 1, source) }
            return LiveSafeAllowlist(entries, source)
        }

        /**
         * The allowlist at [relativePath] in the nearest directory above [filePath] that holds one --
         * the repository root, for a source file in it. Empty when [relativePath] is blank or no
         * directory above holds it.
         */
        fun near(filePath: String, relativePath: String): LiveSafeAllowlist {
            if (relativePath.isBlank()) return EMPTY
            var dir: File? = File(filePath).absoluteFile.parentFile
            while (dir != null) {
                val candidate = File(dir, relativePath)
                if (candidate.isFile) {
                    // Keyed on the file's size and time as well: detekt's class loader outlives a
                    // build in the Gradle daemon, and an edited list must be read again.
                    val key = "${candidate.path}|${candidate.length()}|${candidate.lastModified()}"
                    return cache.getOrPut(key) { parse(candidate.readText(), relativePath) }
                }
                dir = dir.parentFile
            }
            return EMPTY
        }

        private fun entryOf(line: String, number: Int, source: String): AllowedCall? {
            val body = line.substringBefore('#').trim()
            if (body.isEmpty()) return null
            val path = body.substringBefore(':', missingDelimiterValue = "").trim()
            val names = body.substringAfter(':', missingDelimiterValue = "").trim().split(Regex("\\s+"))
            require(path.isNotEmpty() && names.size == 2) {
                "$source line $number is not `<path>: <function> <call>  # reason`: $line"
            }
            return AllowedCall(path = path, function = names[0], call = names[1])
        }
    }
}
