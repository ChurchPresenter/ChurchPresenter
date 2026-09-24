package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault

/**
 * How the font picker turns a family name into the face it draws that name in.
 *
 * The picker shows each font in itself — that is the whole point of it, and it stays that way. But
 * it means the picture depends on which fonts the machine has installed: a box missing one of them
 * draws that row in a fallback face, so the same state renders differently from machine to machine
 * and the committed screenshots disagreed with everyone.
 *
 * So the *resolution* is hoisted rather than the rendering changed. In the app this is
 * [systemFontFamilyOrDefault] and nothing about the picker moves; a screenshot test provides a
 * resolver that answers the same face for every name, and its images stop depending on the host's
 * font book. This is the same remedy the git hash, the wall clock and the camera list got — take
 * the value from outside the composition as something the test can pin — and the last of the four.
 *
 * `static` because it never changes inside a running app: swapping it is a whole-subtree concern,
 * not something worth tracking reads for.
 */
internal val LocalFontPreviewFace = staticCompositionLocalOf<(String) -> FontFamily> {
    { name -> systemFontFamilyOrDefault(name) }
}
