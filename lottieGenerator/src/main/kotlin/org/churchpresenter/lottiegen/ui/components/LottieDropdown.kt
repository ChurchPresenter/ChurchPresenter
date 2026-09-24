package org.churchpresenter.lottiegen.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.ui.Tokens
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.VerticalScrollbar
import org.churchpresenter.theme.dropdownField

/** The chevron points down when closed and is turned over, not spun, when open. */
private const val CHEVRON_FLIPPED_DEGREES = 180f


/**
 * A dropdown anchor styled as the app's sunken field: a tiny uppercase label above the current value,
 * with a caret that flips when the menu is open.
 */
@Composable
fun LottieDropdown(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    Row(
        modifier = modifier
            .height(Tokens.FieldHeight)
            // The app's sunken field; the accent rim while its menu is open.
            .dropdownField(Tokens.FieldShape, open = expanded)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = Tokens.FieldLabelTracking,
                color = Tokens.FieldLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.5.sp,
                color = Tokens.PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(13.dp).rotate(if (expanded) CHEVRON_FLIPPED_DEGREES else 0f),
            tint = Tokens.FieldLabel
        )
    }
}

/** Material's menu item height; a menu is sized from it so a short one stays short. */
private val MENU_ITEM_HEIGHT = 48.dp
private val MENU_MAX_HEIGHT = 380.dp
private val MENU_SCROLLBAR_GUTTER = 10.dp

/**
 * A dropdown menu's items, with a visible scrollbar once there are more than fit. Material's menu
 * scrolls a long list but draws no bar, so nothing said the Style list went on past the fold -- the
 * same fix [org.churchpresenter.theme.components.DropdownSelector] carries. The height is explicit
 * rather than a cap because a scrollbar in an uncapped box reports an infinite height.
 */
@Composable
fun ScrollingMenuItems(itemCount: Int, content: @Composable ColumnScope.() -> Unit) {
    if (MENU_ITEM_HEIGHT * itemCount <= MENU_MAX_HEIGHT) {
        Column(content = content)
        return
    }
    val state = rememberScrollState()
    Box(Modifier.height(MENU_MAX_HEIGHT)) {
        Column(Modifier.verticalScroll(state).padding(end = MENU_SCROLLBAR_GUTTER), content = content)
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp),
        )
    }
}
