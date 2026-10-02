package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A tab's top section on its own card, inset to line up with the cards below it. */
fun Modifier.topBarCard(start: Dp = 4.dp, end: Dp = 4.dp): Modifier = this
    .padding(start = start, top = 4.dp, end = end, bottom = 8.dp)
    .bibleListCard()

/** [topBarCard] for a single search/toolbar row, with the row's own inset inside the card. */
fun Modifier.searchBarCard(end: Dp = 4.dp): Modifier =
    topBarCard(end = end).padding(horizontal = 12.dp, vertical = 10.dp)
