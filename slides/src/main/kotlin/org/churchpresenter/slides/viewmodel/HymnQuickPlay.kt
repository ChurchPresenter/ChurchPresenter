package org.churchpresenter.slides.viewmodel

import java.io.File

internal object HymnQuickPlay {
    private const val OTHER_FORMAT_RANK = 3
    private val leadingNumber = Regex("^\\s*0*(\\d+)(?=\\D|$)")

    fun book(file: File, root: File): String =
        file.parentFile.relativeToOrNull(root)?.path.orEmpty().substringBefore(File.separatorChar)

    fun find(files: List<File>, root: File, query: String, preferredBook: String, format: String): List<File> {
        val text = query.trim()
        val number = text.toIntOrNull()
        val matches = files.filter { file ->
            val inBook = preferredBook.isEmpty() || book(file, root) == preferredBook
            val matchesName = if (number != null) {
                leadingNumber.find(file.nameWithoutExtension)?.groupValues?.get(1)?.toIntOrNull() == number
            } else {
                file.nameWithoutExtension.contains(text, ignoreCase = true) ||
                    HymnInitials.matches(file.nameWithoutExtension, text)
            }
            inBook && matchesName
        }
        val grouped = matches.groupBy { book(it, root) to it.nameWithoutExtension.lowercase() }
        return grouped.values.map { variants ->
            variants.minWith(
                compareBy<File> { formatRank(it.extension.lowercase(), format) }.thenBy { it.absolutePath }
            )
        }.sortedWith(compareBy<File> { !it.nameWithoutExtension.equals(text, ignoreCase = true) }.thenBy { it.name })
    }

    private fun formatRank(extension: String, preferred: String): Int = when {
        extension == preferred -> 0
        preferred == "pptx" && extension in setOf("ppt", "pptm", "ppsx", "pps") -> 0
        preferred == "docx" && extension == "doc" -> 0
        extension == "pptx" -> 1
        extension == "pdf" -> 2
        else -> OTHER_FORMAT_RANK
    }
}
