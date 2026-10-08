package org.churchpresenter.songs

import org.churchpresenter.profiles.SongBackgroundButton

/**
 * The Background button the editor's suites draw in its slot: the real one from `:profiles`, wired
 * as the app's `songEditorBackgroundButton` wires it, so the suites drive and picture what ships.
 */
internal val testBackgroundButton: SongBackgroundButtonSlot = { button ->
    SongBackgroundButton(
        background = button.background,
        lowerThirdBackground = button.lowerThirdBackground,
        expanded = button.expanded,
        onExpandedChange = button::setExpanded,
        onBackgroundChange = button::setBackground,
        onLowerThirdBackgroundChange = button::setLowerThirdBackground,
        sampleLine = button.sampleLine,
        onApplyToSongbook = if (button.canApplyToSongbook) button::applyToSongbook else null,
        scopes = button.scopes,
        scopeIndex = button.scopeIndex,
        onScopeChange = button::setScope,
    )
}
