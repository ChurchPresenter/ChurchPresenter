package org.churchpresenter.converter.song

import java.io.File

/** Extensions whose "file" can be a macOS package directory instead of a single file. */
private val PACKAGE_EXTENSIONS = setOf("key")

/**
 * Every input in [dir] and below that [format] can read.
 *
 * This is what makes "select folder" a bulk convert rather than a partial one: it matches **all** of
 * a format's extensions instead of only its first, so pointing at a documents folder no longer
 * quietly picks up the PDFs and leaves the .docx and .pptx files behind.
 *
 * A Keynote `.key` may be a **package directory** rather than a single file. One of those is an
 * input in its own right, so it is taken whole and never descended into — otherwise it contributes
 * nothing itself and the walk wanders through its `Index/` and `Data/` innards instead.
 *
 * That last part is why this is a hand-rolled walk rather than `walkTopDown().onEnter { … }`.
 * `onEnter` returning `false` does not mean "yield this directory but do not descend"; it means the
 * directory is **not visited at all**, itself included. Pruning bundles that way dropped every one
 * of them, which is the opposite of the intent and left the package branch of the filter
 * unreachable.
 */
internal fun findFormatInputs(dir: File, format: SongFormatConverter): List<File> {
    val found = mutableListOf<File>()
    fun visit(current: File) {
        for (child in current.listFiles().orEmpty()) {
            when {
                // A package is an input in its own right: take it whole, innards untouched.
                isPackageInput(child, format) -> found += child
                child.isDirectory -> visit(child)
                matchesFormat(child, format) -> found += child
            }
        }
    }
    visit(dir)
    return found.sortedBy { it.absolutePath }
}

/**
 * True when [file] is a directory that *is* one of [format]'s inputs, rather than a folder holding
 * some.
 *
 * Kept to [PACKAGE_EXTENSIONS] rather than the format's whole extension list, so no other format's
 * folder scan changes: a directory that merely happens to be named `stuff.xml` stays a directory to
 * search. And extensionless matching is deliberately not honoured here — an OpenSong song carries no
 * extension, so honouring it would make **every** plain folder a package and halt the walk at the
 * first subdirectory.
 *
 * Note this already implies [matchesFormat]: the extension has to be one of [format]'s to get here.
 */
internal fun isPackageInput(file: File, format: SongFormatConverter): Boolean =
    file.isDirectory && format.extensions.any {
        it in PACKAGE_EXTENSIONS && file.extension.equals(it, ignoreCase = true)
    }

internal fun matchesFormat(file: File, format: SongFormatConverter): Boolean =
    format.extensions.any { file.extension.equals(it, ignoreCase = true) } ||
        (format.acceptsExtensionlessFiles && file.extension.isEmpty())

/**
 * The size to show for a chosen input.
 *
 * `File.length()` on a directory is filesystem-defined — a few dozen bytes on APFS — so a Keynote
 * package would otherwise list as "96 B" beside the real sizes of its neighbours.
 */
internal fun inputSize(file: File): Long =
    if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
