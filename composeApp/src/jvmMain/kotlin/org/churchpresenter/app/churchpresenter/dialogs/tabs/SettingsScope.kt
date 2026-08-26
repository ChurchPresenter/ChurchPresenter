package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.settings.OutputStyleScope

/**
 * Which style profiles the Bible/Song settings surface being composed should show.
 *
 * [OutputStyleScope.BOTH] by default, so the Options-dialog tabs render every field exactly as they
 * did before per-output customization existed. The Customize dialog provides the narrower scope for
 * the output it is editing, and each paired full-screen/lower-third control asks this before drawing
 * itself.
 *
 * Ambient rather than a parameter on purpose. The pairs are spread across a dozen private
 * composables in four files, and threading a scope down to them would change the signatures of
 * `LeftColumn`, `RightColumn`, `LookAheadColumn`, `TranslationTextSection` and
 * `TranslationReferenceSection` — all five of which carry `LongMethod` entries in
 * `config/detekt/baseline.xml`, which are keyed by signature. Renaming them out from under their
 * entries surfaces five build-failing findings that the baseline may not be extended to cover.
 */
internal val LocalOutputStyleScope = staticCompositionLocalOf { OutputStyleScope.BOTH }
