package org.churchpresenter.bibleeditor.ui

import androidx.compose.runtime.Composable
import org.churchpresenter.bibleeditor.Numbering
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_catholic
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_german
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_hebrew
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_kjv
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_luther
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_nrsv
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_orthodox
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_segond
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_septuagint
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_synodal
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_synodal_protestant
import org.churchpresenter.strings.generated.resources.bible_editor_numbering_vulgate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val NAMES: Map<Numbering, StringResource> = mapOf(
    Numbering.KJV to Res.string.bible_editor_numbering_kjv,
    Numbering.SYNODAL to Res.string.bible_editor_numbering_synodal,
    Numbering.SYNODAL_PROTESTANT to Res.string.bible_editor_numbering_synodal_protestant,
    Numbering.SEPTUAGINT to Res.string.bible_editor_numbering_septuagint,
    Numbering.HEBREW to Res.string.bible_editor_numbering_hebrew,
    Numbering.VULGATE to Res.string.bible_editor_numbering_vulgate,
    Numbering.LUTHER to Res.string.bible_editor_numbering_luther,
    Numbering.GERMAN to Res.string.bible_editor_numbering_german,
    Numbering.NRSV to Res.string.bible_editor_numbering_nrsv,
    Numbering.CATHOLIC to Res.string.bible_editor_numbering_catholic,
    Numbering.ORTHODOX to Res.string.bible_editor_numbering_orthodox,
    Numbering.SEGOND to Res.string.bible_editor_numbering_segond,
)

/** What the operator calls [numbering]: "KJV", "Synodal". */
@Composable
internal fun numberingName(numbering: Numbering): String = stringResource(NAMES.getValue(numbering))
