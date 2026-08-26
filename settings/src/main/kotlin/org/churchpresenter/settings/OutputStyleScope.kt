package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants

/**
 * Which of the two style profiles a Bible or Song settings surface shows.
 *
 * Bible and Song each carry two complete appearance profiles — a full-screen one and a
 * `*LowerThird*` one — and the settings tabs have always shown both, side by side in every row,
 * because one global document configures every output at once. A per-output surface knows which
 * profile that output can actually use, so it shows that one alone: a lower-third band has no
 * full-screen font size, and offering one is offering a control that does nothing.
 *
 * [BOTH] is what the Options-dialog tabs use, and is the default everywhere, so the global tabs are
 * unaffected by any of this.
 */
enum class OutputStyleScope {
    /** The global settings tab: every field, exactly as before per-output customization existed. */
    BOTH,

    /** One output in fullscreen mode: the full-screen profile alone. */
    FULL_SCREEN,

    /** One output in either lower-third orientation: the `*LowerThird*` profile alone. */
    LOWER_THIRD;

    val showsFullScreen: Boolean get() = this != LOWER_THIRD

    val showsLowerThird: Boolean get() = this != FULL_SCREEN

    /**
     * True for a per-output surface, where the library folder, the translation stack and the
     * browsing panels are out of scope — those stay one per install however many outputs there are.
     */
    val isOutputScoped: Boolean get() = this != BOTH

    companion object {
        /**
         * The profile an output in [mode] draws with.
         *
         * Both lower-third orientations answer [LOWER_THIRD]: the vertical strip differs from the
         * horizontal band only in geometry, and `BiblePresenter`/`SongPresenter` select the same
         * `*LowerThird*` fields for both. Stage monitor and anything unrecognized answer
         * [FULL_SCREEN], which is what `ScreenAssignment.displayMode` itself falls back to — a
         * stage monitor is customized through its own settings rather than these, so the answer
         * only has to be a safe one.
         */
        fun forDisplayMode(mode: String): OutputStyleScope = when (mode) {
            Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL -> LOWER_THIRD
            else -> FULL_SCREEN
        }
    }
}
