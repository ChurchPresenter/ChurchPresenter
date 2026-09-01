package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.settings.OutputStyleScope

/**
 * Which style profiles the per-output settings surface being composed should show.
 *
 * Bible and Song each carry two complete appearance profiles — a full-screen one and a
 * `*LowerThird*` one. A per-output surface knows which of them that output can actually use, so it
 * shows that one alone: a lower-third band has no full-screen font size, and offering one is
 * offering a control that does nothing.
 *
 * [OutputStyleScope.BOTH] by default, which is what the global Options-dialog tabs compose under —
 * they pick their profile from their own target selector and never read this. The Customize dialog
 * provides the narrower scope for the output it is editing, and each paired full-screen/lower-third
 * control in `CustomizePanes` asks this before drawing itself.
 *
 * Ambient rather than a parameter on purpose: the pairs are spread across a dozen private
 * composables, and threading a scope down to each of them would change signatures that
 * `config/detekt/baseline.xml` keys its entries by.
 */
internal val LocalOutputStyleScope = staticCompositionLocalOf { OutputStyleScope.BOTH }
