package org.churchpresenter.slides.viewmodel

import java.io.File

internal object HymnQuickPlay {
    private const val OTHER_FORMAT_RANK = 3
    private val leadingNumber = Regex("^\\s*0*(\\d+)(?=\\D|$)")

    fun book(file: File, root: File, roots: List<File> = listOf(root)): String {
        val path = file.absoluteFile.toPath().normalize()
        val owner = roots.firstOrNull { path.startsWith(it.absoluteFile.toPath().normalize()) }
            ?: root
        return file.parentFile.relativeToOrNull(owner)?.path.orEmpty().substringBefore(File.separatorChar)
    }

    fun find(files: List<File>, root: File, query: String, preferredBook: String, format: String,
        roots: List<File> = listOf(root),
    ): List<File> {
        val text = query.trim()
        val number = text.toIntOrNull()
        val matches = files.filter { file ->
            val inBook = preferredBook.isEmpty() || book(file, root, roots) == preferredBook
            val matchesName = if (number != null) {
                leadingNumber.find(file.nameWithoutExtension)?.groupValues?.get(1)?.toIntOrNull() == number
            } else {
                file.nameWithoutExtension.contains(text, ignoreCase = true) ||
                    HymnInitials.matches(file.nameWithoutExtension, text)
            }
            inBook && matchesName
        }
        val grouped = matches.groupBy { book(it, root, roots) to it.nameWithoutExtension.lowercase() }
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
