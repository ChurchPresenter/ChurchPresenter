package org.churchpresenter.dictionary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.strings.generated.resources.dictionary_definition
import org.churchpresenter.strings.generated.resources.dictionary_filter_greek
import org.churchpresenter.strings.generated.resources.dictionary_filter_hebrew
import org.churchpresenter.strings.generated.resources.dictionary_back
import org.churchpresenter.strings.generated.resources.dictionary_forward
import org.churchpresenter.strings.generated.resources.dictionary_switch_language
import org.churchpresenter.icons.generated.resources.ic_redo
import org.churchpresenter.icons.generated.resources.ic_undo
import org.churchpresenter.strings.generated.resources.dictionary_kjv_usage
import org.churchpresenter.strings.generated.resources.dictionary_pronunciation
import org.churchpresenter.strings.generated.resources.dictionary_select_entry
import org.churchpresenter.strings.generated.resources.dictionary_transliteration
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.sharedui.composables.ActionIconButton
import org.churchpresenter.sharedui.composables.AddToScheduleButton
import org.churchpresenter.sharedui.composables.GoLiveButton
import org.churchpresenter.dictionary.data.InterlinearVerse
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.keyboardFocusRing
import org.churchpresenter.theme.sunken

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DictionaryDetailPane(
    modifier: Modifier,
    entry: StrongsEntry?,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    dictLanguage: String,
    onToggleDictLanguage: () -> Unit,
    interlinearVerses: List<InterlinearVerse>,
    totalInterlinearCount: Int,
    isInterlinearLoading: Boolean,
    interlinearDisplayLimit: Int,
    onShowMore: () -> Unit,
    cardBookFilter: Int?,
    cardChapterFilter: Int?,
    cardAvailableBooks: List<Int>,
    cardAvailableChapters: List<Int>,
    onFilterCardsByBook: (Int?) -> Unit,
    onFilterCardsByChapter: (Int?) -> Unit,
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)?,
    getBookName: ((bookId: Int) -> String?)?,
    onWordClick: ((String) -> Unit)?,
    onVerseClick: ((bookId: Int, chapter: Int, verse: Int) -> Unit)?,
    getEntry: ((strongsNumber: String) -> StrongsEntry?)?,
    onAddToSchedule: ((StrongsEntry) -> Unit)?,
    onGoLive: ((StrongsEntry) -> Unit)?,
) {
    Column(modifier = modifier) {
        DictionaryDetailActionRow(
            entry = entry,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onGoBack = onGoBack,
            onGoForward = onGoForward,
            dictLanguage = dictLanguage,
            onToggleDictLanguage = onToggleDictLanguage,
            onAddToSchedule = onAddToSchedule,
            onGoLive = onGoLive,
        )

        HorizontalDivider()

        if (entry == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.dictionary_select_entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            // The scrolling scaffold stays here rather than travelling with either section:
            // `scrollState` is the only thing the column and the scrollbar share, so keeping both
            // in one place leaves the two extracted sections sharing no state at all.
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StrongsEntrySummary(entry = entry, onStrongsClick = onWordClick)
                    InScriptureSection(
                        highlightedNumber = entry.number,
                        interlinearVerses = interlinearVerses,
                        totalInterlinearCount = totalInterlinearCount,
                        isInterlinearLoading = isInterlinearLoading,
                        interlinearDisplayLimit = interlinearDisplayLimit,
                        onShowMore = onShowMore,
                        cardBookFilter = cardBookFilter,
                        cardChapterFilter = cardChapterFilter,
                        cardAvailableBooks = cardAvailableBooks,
                        cardAvailableChapters = cardAvailableChapters,
                        onFilterCardsByBook = onFilterCardsByBook,
                        onFilterCardsByChapter = onFilterCardsByChapter,
                        getVerseText = getVerseText,
                        getBookName = getBookName,
                        onWordClick = onWordClick,
                        onVerseClick = onVerseClick,
                        getEntry = getEntry,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * The detail pane's toolbar: history, the dictionary's own language, and the two live actions.
 *
 * Its own composable because the pane it sits in is long enough to be over detekt's `LongMethod`
 * threshold with it inline, and because the gear below belongs beside Go Live rather than in a
 * toolbar of its own -- an earlier attempt floated it over the pane's top-right corner, where its
 * bounds covered Go Live and swallowed clicks meant for it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DictionaryDetailActionRow(
    entry: StrongsEntry?,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    dictLanguage: String,
    onToggleDictLanguage: () -> Unit,
    onAddToSchedule: ((StrongsEntry) -> Unit)?,
    onGoLive: ((StrongsEntry) -> Unit)?,
) {
    val addScheduleStr = stringResource(Res.string.add_to_schedule)
    val goLiveStr = stringResource(Res.string.go_live)
    val backStr = stringResource(Res.string.dictionary_back)
    val forwardStr = stringResource(Res.string.dictionary_forward)
    val switchLangStr = stringResource(Res.string.dictionary_switch_language)

    // Wraps rather than squeezes: the pane is whatever the list beside it leaves, and a Row would
    // measure the last buttons -- Go Live among them -- into zero width once it runs out.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIconButton(
            onClick = onGoBack,
            enabled = canGoBack,
            tooltipText = backStr,
            painter = painterResource(IconRes.drawable.ic_undo),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ActionIconButton(
            onClick = onGoForward,
            enabled = canGoForward,
            tooltipText = forwardStr,
            painter = painterResource(IconRes.drawable.ic_redo),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        switchLangStr,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp)
            ),
        ) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .keyboardFocusRing(AppShape(8.dp))
                    .sunken(AppShape(8.dp), elevationPalette())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleDictLanguage,
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (dictLanguage == "en") "EN" else "RU",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onAddToSchedule != null) {
            AddToScheduleButton(
                onClick = { entry?.let { onAddToSchedule(it) } },
                enabled = entry != null,
                tooltipText = addScheduleStr
            )
        }
        // Go Live stays last, as it does on every other tab: it is the button the operator
        // reaches for under pressure, so it keeps the same end of the row everywhere.
        if (onGoLive != null) {
            GoLiveButton(
                onClick = { entry?.let { onGoLive(it) } },
                enabled = entry != null,
                tooltipText = goLiveStr,
                showsShortcut = true,
            )
        }
    }
}

/** The entry itself: its number and language, the original word, and what it means. */
@Composable
private fun StrongsEntrySummary(
    entry: StrongsEntry,
    onStrongsClick: ((String) -> Unit)?,
) {
    val numberColor = if (entry.isHebrew) MaterialTheme.semantic.hebrew else MaterialTheme.semantic.greek
    val languageLabel = if (entry.isHebrew)
        stringResource(Res.string.dictionary_filter_hebrew).uppercase()
    else
        stringResource(Res.string.dictionary_filter_greek).uppercase()

    // Header: number + language badge
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = entry.number,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = numberColor,
        )
        Surface(
            shape = AppShape(4.dp),
            color = numberColor.copy(alpha = 0.12f),
        ) {
            Text(
                text = languageLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = numberColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    HorizontalDivider()

    // Original word — large display
    Text(
        text = entry.word,
        fontSize = 52.sp,
        fontWeight = FontWeight.Light,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 60.sp,
    )

    // Transliteration + pronunciation
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow(
            label = stringResource(Res.string.dictionary_transliteration),
            value = entry.transliteration,
        )
        DetailRow(
            label = stringResource(Res.string.dictionary_pronunciation),
            value = entry.pronunciation,
        )
    }

    HorizontalDivider()

    DetailSection(
        label = stringResource(Res.string.dictionary_definition),
        body = entry.definition,
        onStrongsClick = onStrongsClick,
    )

    // KJV Usage (only if present)
    if (entry.kjvUsage.isNotBlank()) {
        DetailSection(
            label = stringResource(Res.string.dictionary_kjv_usage),
            body = entry.kjvUsage,
            onStrongsClick = onStrongsClick,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val strongsPattern = Regex("[HGhg]\\d+")

// `internal` so the link-splitting can be tested directly: it is a pure function over the text, and
// driving it through the composable would assert on a rendered tree instead of on the ranges.
//
// The two link colours are passed in rather than read here: this is not a composable, so it cannot
// reach the theme, and hard-coding them would put the app's only Hebrew/Greek accent outside it.
internal fun buildStrongsAnnotatedString(
    text: String,
    hebrew: Color,
    greek: Color,
    onClick: (String) -> Unit,
) = buildAnnotatedString {
    var lastEnd = 0
    for (match in strongsPattern.findAll(text)) {
        append(text.substring(lastEnd, match.range.first))
        val upper = match.value.uppercase()
        val linkColor = if (upper.startsWith("H")) hebrew else greek
        withLink(
            LinkAnnotation.Clickable(
                tag = upper,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = { onClick(upper) },
            ),
        ) { append(match.value) }
        lastEnd = match.range.last + 1
    }
    append(text.substring(lastEnd))
}

@Composable
private fun DetailSection(
    label: String,
    body: String,
    onStrongsClick: ((String) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onStrongsClick != null) {
            val cb = onStrongsClick
            val hebrew = MaterialTheme.semantic.hebrew
            val greek = MaterialTheme.semantic.greek
            val annotated = remember(body, hebrew, greek) { buildStrongsAnnotatedString(body, hebrew, greek, cb) }
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )
        } else {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )
        }
    }
}
