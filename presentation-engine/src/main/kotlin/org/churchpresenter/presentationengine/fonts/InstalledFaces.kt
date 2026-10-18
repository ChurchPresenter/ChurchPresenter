package org.churchpresenter.presentationengine.fonts

import java.awt.GraphicsEnvironment

/**
 * Installed faces by the words of their name in any order, so the name PowerPoint writes
 * ("Helvetica Neue Bold Condensed") finds the face the system knows ("HelveticaNeue-CondensedBold").
 */
internal object InstalledFaces {

    /** Built once, on first need: listing every face costs a moment on a font-heavy machine. */
    private val byWords: Map<String, String> by lazy {
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts
                .associate { wordsKey(it.fontName) to it.fontName }
        } catch (_: Throwable) {
            // As in SlideFontRegistry's family index: a headless machine without fontconfig
            // lists nothing, and an [Error] from the native font manager must not escape.
            emptyMap()
        }
    }

    private fun wordsKey(name: String): String = PostScriptName.words(name).sorted().joinToString(" ")

    /** The installed face whose name has exactly [requested]'s words, if there is one. */
    fun find(requested: String): String? = byWords[wordsKey(requested)]
}
