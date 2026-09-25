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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import org.churchpresenter.app.churchpresenter.dialogs.tabs.titleSlideOffset
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.utils.combinedTextDecoration
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.app.churchpresenter.utils.styledDisplayText
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.text.TextOutline
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
    /** Which of the song's languages a title line is in, `0` the primary; the credits have none. */
    val language: Int = 0,
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
 * output shows -- [languages] are positions into the song's languages, `0` the primary, the same
 * list `SongPresenter` draws the lyrics by -- so a bilingual song opens with both its titles on an
 * output showing both languages; the number joins the first of them.
 */
internal fun titleSlideLines(
    section: LyricSection,
    settings: SongSettings,
    languages: List<Int> = listOf(0),
): List<TitleSlideLine> {
    fun shown(element: SongStyleElement) = settings.shownOnTitleSlide(element) == true
    val number = section.songNumber.takeIf { it > 0 && shown(SongStyleElement.TITLE_SLIDE_NUMBER) }?.toString()
    val titles = titleSlideTitles(section, languages)
        .takeIf { shown(SongStyleElement.TITLE) }
        .orEmpty()
        .map { (language, title) -> TitleSlideLine(SongStyleElement.TITLE, title, language = language) }
    val heading = when {
        number == null -> titles
        settings.titleSlideNumberBeforeTitle && titles.isNotEmpty() ->
            listOf(titles.first().copy(number = number)) + titles.drop(1)
        else -> listOf(TitleSlideLine(SongStyleElement.TITLE_SLIDE_NUMBER, number)) + titles
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

/**
 * The song's title in each of [languages], in that order, once each.
 *
 * A language the song has no title in falls back to the primary's -- a second language is often
 * lyrics with no separate title -- and a title equal to one already listed is not shown twice, so
 * an output showing every language of such a song still opens with one title, not two copies.
 */
private fun titleSlideTitles(section: LyricSection, languages: List<Int>): List<Pair<Int, String>> {
    val titles = section.allLanguageTitles()
    return languages
        .map { it to (titles.getOrNull(it)?.takeIf { title -> title.isNotBlank() } ?: section.title) }
        .filter { (_, title) -> title.isNotBlank() }
        .distinctBy { (_, title) -> title }
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
    languages: List<Int>,
    isKey: Boolean,
    scaleFactor: Float,
    contentAlignment: Alignment,
    modifier: Modifier = Modifier,
) {
    val allLines = titleSlideLines(section, settings, languages)
    val titleSlideNumber = settings.layoutExtras.titleSlideNumber
    val numberCorner = titleSlideNumber.cornerFor(target.isLowerThird)
    val cornered = numberCorner != Constants.NONE
    // The number as the flow would have drawn it, kept aside for the overlay below.
    val corneredNumber = allLines.firstOrNull { it.element == SongStyleElement.TITLE_SLIDE_NUMBER }?.text
        ?: allLines.firstNotNullOfOrNull { it.number }
    // Pinned to a corner, the number comes out of the flow -- from its own row *and* from the
    // title's row, which carries it as `leading`. Filtered here at the draw site rather than in
    // `titleSlideLines`, because that is the shared definition the stage monitor and the companion
    // app read as plain text and the number belongs in what they read.
    val lines = if (!cornered) allLines else allLines
        .filterNot { it.element == SongStyleElement.TITLE_SLIDE_NUMBER }
        .map { it.copy(number = null) }
    val fallbackTitleFont =
        if (target.isLowerThird) settings.titleLowerThirdFontType else settings.titleFontType
    // Fills the box it is given -- the whole slide, or the band -- so the vertical alignment the
    // rail configures places the block, exactly as it places the lyrics.
    // Which lines the operator has taken out of the stack, and where each goes. A positioned line
    // floats over the slide and costs the stack no height, which is the same trade the cornered
    // number below makes -- and it is why the gaps are measured over `stacked` rather than over
    // every line: a credit whose neighbour has floated away is no longer between two credits.
    val offsets = lines.associateWith { settings.titleSlideOffset(it.element, target) }
    val stacked = lines.filter { offsets[it] == null }
    val positioned = lines.filter { offsets[it] != null }
    val fallbackFont = if (target.isLowerThird) settings.titleLowerThirdFontType else settings.titleFontType
    // One line, drawn the same way whether it is stacked or floating, so the two cannot come apart.
    // A floating one does not fill the width: filling leaves no room for the offset to move it
    // through, and X would silently do nothing.
    val drawLine: @Composable (TitleSlideLine, Boolean) -> Unit = { line, fillWidth ->
        TitleSlideText(
            line = line,
            // A title in a second language takes that language's own title profile, as it
            // does above a verse; the number and the credits have one whatever the language.
            style = settings.elementStyle(line.element, target, line.language),
            // The number ahead of the title in the same paragraph, in its own style, so a
            // long title wraps under it as one line of text would -- laid out as two
            // boxes side by side, the title centred in what was left beside the number.
            leading = line.number?.let {
                it to settings.elementStyle(SongStyleElement.TITLE_SLIDE_NUMBER, target)
            },
            fallbackFont = fallbackFont,
            isKey = isKey,
            scaleFactor = scaleFactor,
            fillWidth = fillWidth,
        )
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = contentAlignment) {
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            stacked.forEachIndexed { index, line ->
                if (index > 0) {
                    // Credits sit close together; the heading stands apart from them.
                    val betweenCredits = line.element.isCredit && stacked[index - 1].element.isCredit
                    val gap = if (betweenCredits) CREDIT_GAP_PX else HEADING_GAP_PX
                    Spacer(Modifier.height((gap * scaleFactor).dp))
                }
                drawLine(line, true)
            }
        }
        positioned.forEach { line ->
            Box(modifier = Modifier.elementOffset(offsets[line])) { drawLine(line, false) }
        }
        // The number pinned to a corner, over the slide rather than in the flow -- the same
        // placement the lyric slides give theirs, through the same modifier, and the reason this
        // element has a profile of its own: top-left on every lyric slide and bottom-left here.
        if (cornered && !corneredNumber.isNullOrBlank()) {
            TitleSlideText(
                line = TitleSlideLine(SongStyleElement.TITLE_SLIDE_NUMBER, corneredNumber),
                style = settings.elementStyle(SongStyleElement.TITLE_SLIDE_NUMBER, target),
                fallbackFont = fallbackTitleFont,
                isKey = isKey,
                scaleFactor = scaleFactor,
                modifier = Modifier.songNumberCornerOffset(
                    numberCorner,
                    titleSlideNumber.offsetFor(target.isLowerThird),
                ),
                fillWidth = false,
            )
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
    modifier: Modifier = Modifier,
    /**
     * False for a line pinned to a corner rather than laid out in the flow.
     *
     * All three draw calls below honour it. They each hard-coded `fillMaxWidth()`, and a cornered
     * number that still filled the width would be placed nowhere -- filling leaves no room for the
     * offset to move it through.
     */
    fillWidth: Boolean = true,
) {
    fun lineWidth(): Modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    val painter = rememberTextBackdropPainter(style.backdrop)
    val font = if (line.element == SongStyleElement.TITLE_SLIDE_NUMBER) {
        style.fontType.ifBlank { fallbackFont }
    } else {
        style.fontType
    }
    // A line can carry the number's span as well as its own, and a span is where a colour lives --
    // so the stroke pass takes its own copy with every span painted the outline's colour. Building
    // it twice rather than restyling one: `AnnotatedString` spans are not editable in place.
    fun buildText(outlineColor: Color?, dropShadow: Boolean = false) = buildAnnotatedString {
        leading?.let { (number, numberStyle) ->
            val numberSpan = spanStyleOf(numberStyle, numberStyle.fontType.ifBlank { fallbackFont }, isKey, scaleFactor)
                .let { if (dropShadow) it.copy(shadow = null) else it }
            withStyle(outlineColor?.let { numberSpan.copy(color = it) } ?: numberSpan) {
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
    // The number's outline where it shares this line: the two elements are drawn as one paragraph,
    // and `drawStyle` is a property of the whole text rather than of a span, so they cannot be
    // stroked at two different widths. The line's own element wins.
    val outline = titleSlideOutline(style, isKey)
    val textStyle = TextStyle(
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(style.underline, style.strikethrough),
        letterSpacing = spacingEm(style.letterSpacing, style.fontSize).em,
        shadow = shadowOf(style, scaleFactor),
    )
    val textAlign = when (style.horizontalAlignment) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }
    if (!outline.isVisible) {
        Text(
            modifier = modifier.then(lineWidth()).then(painter.modifier),
            onTextLayout = painter::onTextLayout,
            textAlign = textAlign,
            fontFamily = systemFontFamilyOrDefault(font),
            fontSize = (style.fontSize * scaleFactor).sp,
            text = buildText(null),
            color = if (isKey) Color.White else parseHexColor(style.color),
            style = textStyle,
        )
        return
    }
    val strokeColor = parseHexColor(outline.color)
    Box(modifier = modifier.then(lineWidth()).then(painter.modifier)) {
        Text(
            modifier = Modifier.matchParentSize(),
            textAlign = textAlign,
            fontFamily = systemFontFamilyOrDefault(font),
            fontSize = (style.fontSize * scaleFactor).sp,
            text = buildText(strokeColor),
            color = strokeColor,
            style = textStyle.copy(drawStyle = Stroke(width = outline.width * scaleFactor)),
        )
        Text(
            modifier = lineWidth(),
            onTextLayout = painter::onTextLayout,
            textAlign = textAlign,
            fontFamily = systemFontFamilyOrDefault(font),
            fontSize = (style.fontSize * scaleFactor).sp,
            text = buildText(null, dropShadow = true),
            color = if (isKey) Color.White else parseHexColor(style.color),
            style = textStyle.copy(shadow = null),
        )
    }
}

/** The element's outline, painted white on a key output so the matte keeps the outlined shape. */
private fun titleSlideOutline(style: SongElementStyle, isKey: Boolean): TextOutline =
    if (isKey && style.outline.isVisible) style.outline.copy(color = "#FFFFFF") else style.outline

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
