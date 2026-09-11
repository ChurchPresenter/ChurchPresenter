package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.composables.rememberTextBackdropPainter
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongElementStyle
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleTarget
import org.churchpresenter.app.churchpresenter.dialogs.tabs.elementStyle
import org.churchpresenter.app.churchpresenter.dialogs.tabs.isCredit
import org.churchpresenter.app.churchpresenter.dialogs.tabs.isLowerThird
import org.churchpresenter.app.churchpresenter.dialogs.tabs.shownOnTitleSlide
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.utils.combinedTextDecoration
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.app.churchpresenter.utils.styledDisplayText
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants

/** In the presenter's 1920x1080 reference space: between the heading and the credits, and between credits. */
private const val HEADING_GAP_PX = 22
private const val CREDIT_GAP_PX = 8

/** Between the number and the title when they share a line: two spaces, in the title's size. */
private const val NUMBER_GAP = "\u2002\u2002"

/** The shadow's reach at reference scale, the same numbers the lyric lines use. */
private const val SHADOW_OFFSET_PX = 6f
private const val SHADOW_BLUR_PX = 12f

/**
 * One line of a title slide: which element it is, and the text that element contributes.
 *
 * [number] is set on the title's line when the song number shares it -- drawn ahead of the title in
 * the number's own style, and read as one line by everything that only sees text.
 */
internal data class TitleSlideLine(
    val element: SongStyleElement,
    val text: String,
    val number: String? = null,
) {
    /** The line as plain text, for the stage monitor and the companion app. */
    val plainText: String get() = listOfNotNull(number, text).joinToString(" \u2013 ")
}

/**
 * What a title slide says, top to bottom, for [section] under [settings].
 *
 * One definition for every reader: the presenter draws these lines each in its element's own
 * profile, and the section's plain [LyricSection.lines] -- what the stage monitor and the companion
 * app show -- are these same lines' text. An element the slide is set not to show is simply absent,
 * as is one the song has nothing for: a credit is never drawn blank.
 *
 * The number shares the title's row, ahead of it, when [SongSettings.titleSlideNumberBeforeTitle]
 * says so, and otherwise has a row of its own above. The title is given in the language(s) the
 * output shows -- [langDisplay] is one of the `SONG_LANG_*` constants -- so a bilingual song opens
 * with both its titles on an output showing both languages; the number joins the first of them.
 */
internal fun titleSlideLines(
    section: LyricSection,
    settings: SongSettings,
    langDisplay: String = Constants.SONG_LANG_PRIMARY,
): List<TitleSlideLine> {
    fun shown(element: SongStyleElement) = settings.shownOnTitleSlide(element) == true
    val number = section.songNumber.takeIf { it > 0 && shown(SongStyleElement.NUMBER) }?.toString()
    val titles = titleSlideTitles(section, langDisplay)
        .takeIf { shown(SongStyleElement.TITLE) }
        .orEmpty()
        .map { TitleSlideLine(SongStyleElement.TITLE, it) }
    val heading = when {
        number == null -> titles
        settings.titleSlideNumberBeforeTitle && titles.isNotEmpty() ->
            listOf(titles.first().copy(number = number)) + titles.drop(1)
        else -> listOf(TitleSlideLine(SongStyleElement.NUMBER, number)) + titles
    }
    val credits = listOf(
        SongStyleElement.AUTHOR to section.author,
        SongStyleElement.COMPOSER to section.composer,
        SongStyleElement.CCLI to section.ccli.takeIf { it.isNotBlank() }?.let { "$CCLI_PREFIX$it" }.orEmpty(),
        SongStyleElement.TEMPO to section.bpm.takeIf { it > 0 }?.let { "$TEMPO_PREFIX$it$TEMPO_SUFFIX" }.orEmpty(),
    ).filter { (element, text) -> text.isNotBlank() && shown(element) }
        .map { (element, text) -> TitleSlideLine(element, text) }
    return heading + credits
}

/** The song's title in whichever language(s) [langDisplay] asks for, the primary first. */
private fun titleSlideTitles(section: LyricSection, langDisplay: String): List<String> {
    val secondary = section.secondaryTitle.takeIf { it.isNotBlank() && it != section.title }
    return when (langDisplay) {
        Constants.SONG_LANG_SECONDARY -> listOf(secondary ?: section.title)
        Constants.SONG_LANG_BOTH -> listOfNotNull(section.title, secondary)
        else -> listOf(section.title)
    }.filter { it.isNotBlank() }
}

/** The CCLI number is drawn as the licence line reads on a printed sheet: "CCLI #22025". */
private const val CCLI_PREFIX = "CCLI #"

/** The tempo as a metronome marking: "♩ = 84 BPM". */
private const val TEMPO_PREFIX = "♩ = "
private const val TEMPO_SUFFIX = " BPM"

/**
 * The title slide, drawn line by line in each element's own profile.
 *
 * Replaces the lyric machinery for a section of the title-slide type, which has no verse to fit,
 * no look-ahead and no chart: just a heading -- the number and the title -- and the credits under
 * it. Each line is styled exactly as the lyric slides style the same element, read through
 * [elementStyle], so the Title tab of the settings dialog restyles the title here as well as above
 * a verse. The credits are the four elements only this slide has.
 *
 * Sits in the same box the lyrics would, so the margins, the band and the vertical alignment the
 * rail configures all apply unchanged.
 */
@Composable
internal fun SongTitleSlideContent(
    section: LyricSection,
    settings: SongSettings,
    target: SongStyleTarget,
    langDisplay: String,
    isKey: Boolean,
    scaleFactor: Float,
    contentAlignment: Alignment,
    modifier: Modifier = Modifier,
) {
    val lines = titleSlideLines(section, settings, langDisplay)
    // Fills the box it is given -- the whole slide, or the band -- so the vertical alignment the
    // rail configures places the block, exactly as it places the lyrics.
    Box(modifier = modifier.fillMaxSize(), contentAlignment = contentAlignment) {
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    // Credits sit close together; the heading stands apart from them.
                    val betweenCredits = line.element.isCredit && lines[index - 1].element.isCredit
                    val gap = if (betweenCredits) CREDIT_GAP_PX else HEADING_GAP_PX
                    Spacer(Modifier.height((gap * scaleFactor).dp))
                }
                val fallbackFont = if (target.isLowerThird) settings.titleLowerThirdFontType else settings.titleFontType
                TitleSlideText(
                    line = line,
                    style = settings.elementStyle(line.element, target),
                    // The number ahead of the title in the same paragraph, in its own style, so a
                    // long title wraps under it as one line of text would -- laid out as two
                    // boxes side by side, the title centred in what was left beside the number.
                    leading = line.number?.let { it to settings.elementStyle(SongStyleElement.NUMBER, target) },
                    fallbackFont = fallbackFont,
                    isKey = isKey,
                    scaleFactor = scaleFactor,
                )
            }
        }
    }
}

/**
 * One line, drawn the way `SongPresenter` draws the same element on a lyric slide.
 *
 * [leading] is the song number and its style, when the number shares this line: it goes ahead of
 * the text as a span of its own, so the line keeps the title's alignment and wraps as one.
 */
@Composable
private fun TitleSlideText(
    line: TitleSlideLine,
    style: SongElementStyle,
    /** The title's face, which a number with no face of its own borrows -- as it does above a verse. */
    fallbackFont: String,
    isKey: Boolean,
    scaleFactor: Float,
    leading: Pair<String, SongElementStyle>? = null,
) {
    val painter = rememberTextBackdropPainter(style.backdrop)
    val font = if (line.element == SongStyleElement.NUMBER) style.fontType.ifBlank { fallbackFont } else style.fontType
    val text = buildAnnotatedString {
        leading?.let { (number, numberStyle) ->
            withStyle(spanStyleOf(numberStyle, numberStyle.fontType.ifBlank { fallbackFont }, isKey, scaleFactor)) {
                append(styledDisplayText(
                    number,
                    numberStyle.transform,
                    spacingEm(numberStyle.letterSpacing, numberStyle.fontSize),
                    spacingEm(numberStyle.wordSpacing, numberStyle.fontSize),
                ))
            }
            append(NUMBER_GAP)
        }
        append(styledDisplayText(
            line.text,
            style.transform,
            spacingEm(style.letterSpacing, style.fontSize),
            spacingEm(style.wordSpacing, style.fontSize),
        ))
    }
    Text(
        modifier = Modifier.fillMaxWidth().then(painter.modifier),
        onTextLayout = painter::onTextLayout,
        textAlign = when (style.horizontalAlignment) {
            Constants.LEFT -> TextAlign.Start
            Constants.RIGHT -> TextAlign.End
            else -> TextAlign.Center
        },
        fontFamily = systemFontFamilyOrDefault(font),
        fontSize = (style.fontSize * scaleFactor).sp,
        text = text,
        color = if (isKey) Color.White else parseHexColor(style.color),
        style = TextStyle(
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = combinedTextDecoration(style.underline, style.strikethrough),
            letterSpacing = spacingEm(style.letterSpacing, style.fontSize).em,
            shadow = shadowOf(style, scaleFactor),
        ),
    )
}

/** The number's look as a span inside the title's paragraph. */
private fun spanStyleOf(style: SongElementStyle, font: String, isKey: Boolean, scaleFactor: Float) = SpanStyle(
    color = if (isKey) Color.White else parseHexColor(style.color),
    fontSize = (style.fontSize * scaleFactor).sp,
    fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
    fontFamily = systemFontFamilyOrDefault(font),
    letterSpacing = spacingEm(style.letterSpacing, style.fontSize).em,
    textDecoration = combinedTextDecoration(style.underline, style.strikethrough),
    shadow = shadowOf(style, scaleFactor),
)

/** The element's shadow at this output's scale, or none. */
private fun shadowOf(style: SongElementStyle, scaleFactor: Float): Shadow? {
    if (!style.shadow) return null
    val mul = style.shadowSize / PERCENT
    return Shadow(
        color = parseHexColor(style.shadowColor).copy(alpha = (style.shadowOpacity / PERCENT).coerceIn(0f, 1f)),
        offset = Offset(SHADOW_OFFSET_PX * scaleFactor * mul, SHADOW_OFFSET_PX * scaleFactor * mul),
        blurRadius = SHADOW_BLUR_PX * scaleFactor * mul,
    )
}
