package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.app.churchpresenter.presenter.PresentedBlock
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings

/**
 * The languages the Songs Text strip offers for [element]: with two or more reaching this output
 * and a look per language, the first -- standing for All -- and each further one; otherwise only the
 * first, since the element is drawn the same in every language.
 */
internal fun songLanguagesOffered(profile: OutputProfile, element: SongStyleElement): List<SongStyleLanguage> {
    val shown = styleLanguagesFor(profile.songMode, profile.songTranslations)
    return if (shown.size > 1 && element in SECOND_LANGUAGE_ELEMENTS) {
        listOf(SongStyleLanguage.PRIMARY) + shown.filter { it.isTranslation }
    } else {
        listOf(shown.first())
    }
}

/** The Adjust handles on the Songs page: its margins and block, and the first language's [element]. */
internal fun songAdjustModel(
    draft: AppSettings,
    profile: OutputProfile,
    element: CustomizeElement,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /** Which language the Text rows edit, the first standing for All; the preview can pick another. */
    language: Adjustable<SongStyleLanguage>,
): AdjustModel {
    val song = draft.songSettings
    val lowerThird = profile.isLowerThird
    val target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
    val styleElement = element.toSongStyleElement()
    val offered = songLanguagesOffered(profile, styleElement)
    val editing = language.value.takeIf { it in offered } ?: offered.first()
    val edit = SongEdit(song, styleElement, target, editing, offered.size > 1, { t ->
        onSettingsChange { s -> s.copy(songSettings = t(s.songSettings)) }
    })
    val style = edit.style
    val update: ((SongSettings) -> SongSettings) -> Unit = { transform ->
        onSettingsChange { s -> s.copy(songSettings = transform(s.songSettings)) }
    }
    val margins = Margins(song.marginTop, song.marginBottom, song.marginLeft, song.marginRight)
    return AdjustModel(
        margins = Adjustable(margins) { m ->
            update { it.copy(marginTop = m.top, marginBottom = m.bottom, marginLeft = m.left, marginRight = m.right) }
        },
        alignment = Adjustable(song.lyricsAlignment) { a ->
            update {
                val extras = it.layoutExtras
                it.copy(
                    lyricsAlignment = a,
                    layoutExtras = extras.copy(contentRegion = extras.contentRegion.copy(yOffsetPercent = 0)),
                )
            }
        },
        region = if (lowerThird) {
            null
        } else {
            Adjustable(song.layoutExtras.contentRegion) { r ->
                update { it.copy(layoutExtras = it.layoutExtras.copy(contentRegion = r)) }
            }
        },
        textSize = Adjustable(style.fontSize) { v -> edit.write(style.copy(fontSize = v)) },
        band = if (lowerThird) {
            Adjustable(song.lowerThirdHeightPercent) { v -> update { it.copy(lowerThirdHeightPercent = v) } }
        } else {
            null
        },
        blocks = if (offered.size > 1) {
            BlockTargets(
                kind = PresentedBlock.Kind.LANGUAGE,
                keys = offered.map { it.translation.toString() },
                selected = offered.indexOf(editing).takeIf { edit.picked },
                onSelect = { language.onChange(offered[it]) },
                shift = if (edit.picked) Adjustable(edit.shiftNow) { (x, y) -> edit.shift(x, y) } else null,
            )
        } else {
            null
        },
    )
}
