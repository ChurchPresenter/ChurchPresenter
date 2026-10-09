package org.churchpresenter.lottiegen

import androidx.compose.ui.Modifier

/**
 * The generator's controls a host may point at — ChurchPresenter's helper rings them in its "how do
 * I make a lower third" tour. The generator only hands each one the host's [ControlTag]; it knows
 * nothing of what the host does with it.
 */
enum class GuidedControl { NAME, INFO, SAVE }

/** The modifier a host puts on each [GuidedControl]; none by default. */
typealias ControlTag = (GuidedControl) -> Modifier

/** A [ControlTag] that tags nothing. */
val NoControlTag: ControlTag = { Modifier }
