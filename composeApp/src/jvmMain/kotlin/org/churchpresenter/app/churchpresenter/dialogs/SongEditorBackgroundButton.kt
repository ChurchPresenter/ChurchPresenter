package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.profiles.SongBackgroundButton
import org.churchpresenter.songs.SongBackgroundButtonSlot

/**
 * The song editor's Background button: `:profiles`' panel, drawn in the slot `:songs`' editor
 * leaves for it. The editor lives in `:songs`, which does not depend on `:profiles`.
 */
val songEditorBackgroundButton: SongBackgroundButtonSlot = { button ->
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
