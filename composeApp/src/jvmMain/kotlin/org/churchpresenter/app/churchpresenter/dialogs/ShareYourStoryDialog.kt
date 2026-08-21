package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cormorant_garamond_italic
import churchpresenter.composeapp.generated.resources.ic_check
import churchpresenter.composeapp.generated.resources.ic_mail
import churchpresenter.composeapp.generated.resources.share_story_screenshot_dark
import churchpresenter.composeapp.generated.resources.share_story_screenshot_light
import churchpresenter.composeapp.generated.resources.story_prompt_badge
import churchpresenter.composeapp.generated.resources.story_prompt_body_1
import churchpresenter.composeapp.generated.resources.story_prompt_body_2
import churchpresenter.composeapp.generated.resources.story_prompt_body_3
import churchpresenter.composeapp.generated.resources.story_prompt_body_4
import churchpresenter.composeapp.generated.resources.story_prompt_example_1
import churchpresenter.composeapp.generated.resources.story_prompt_example_2
import churchpresenter.composeapp.generated.resources.story_prompt_example_3
import churchpresenter.composeapp.generated.resources.story_prompt_heading
import churchpresenter.composeapp.generated.resources.story_prompt_later
import churchpresenter.composeapp.generated.resources.story_prompt_quote
import churchpresenter.composeapp.generated.resources.story_prompt_reassurance
import churchpresenter.composeapp.generated.resources.story_prompt_screenshot
import churchpresenter.composeapp.generated.resources.story_prompt_share
import churchpresenter.composeapp.generated.resources.story_prompt_window_title
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.theme.isDarkScheme
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val STORY_IMAGE_COLUMN_WIDTH = 250.dp
private val STORY_BUTTON_HEIGHT = 42.dp
private const val STORY_MONITOR_NECK_FRACTION = 0.14f
private const val STORY_MONITOR_BASE_FRACTION = 0.42f
private const val STORY_GLOW_HEIGHT_FRACTION = 0.6f

@Immutable
private data class StoryPanelPalette(
    val top: Color,
    val bottom: Color,
    val glow: Color,
    val accent: Color,
    val quote: Color,
    val bezel: Color,
    val bezelBorder: Color,
    val standTop: Color,
    val standBottom: Color,
)

private val DarkStoryPanel = StoryPanelPalette(
    top = Color(0xFF101820),
    bottom = Color(0xFF0B0D10),
    glow = Color(0x2E4ADE80),
    accent = Color(0xFF4ADE80),
    quote = Color(0xFFF1F3F5),
    bezel = Color(0xFF1B2028),
    bezelBorder = Color(0x1FFFFFFF),
    standTop = Color(0xFF2A3038),
    standBottom = Color(0xFF151920),
)

private val LightStoryPanel = StoryPanelPalette(
    top = Color(0xFFF4F7FA),
    bottom = Color(0xFFE2E7ED),
    glow = Color(0x332E7D32),
    accent = Color(0xFF2E7D32),
    quote = Color(0xFF1B1F24),
    bezel = Color(0xFFCFD6DE),
    bezelBorder = Color(0x1F000000),
    standTop = Color(0xFFC3CBD5),
    standBottom = Color(0xFFA9B3BF),
)

@Composable
private fun storyPanelPalette(): StoryPanelPalette =
    if (isDarkScheme(MaterialTheme.colorScheme)) DarkStoryPanel else LightStoryPanel

@Composable
fun ShareYourStoryDialog(
    isVisible: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(
                mainWindowState, SHARE_STORY_DIALOG_WIDTH, SHARE_STORY_DIALOG_HEIGHT
            ),
            width = SHARE_STORY_DIALOG_WIDTH,
            height = SHARE_STORY_DIALOG_HEIGHT,
        ),
        title = stringResource(Res.string.story_prompt_window_title),
        resizable = false,
    ) {
        ShareYourStoryContent(onShare = onShare, onDismiss = onDismiss)
    }
}

@Composable
internal fun ShareYourStoryContent(
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                StoryIllustrationColumn()
                StoryProseColumn(modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
            StoryFooter(onShare = onShare, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun StoryIllustrationColumn() {
    val palette = storyPanelPalette()
    Box(
        modifier = Modifier
            .width(STORY_IMAGE_COLUMN_WIDTH)
            .fillMaxHeight()
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(STORY_GLOW_HEIGHT_FRACTION)
                .background(Brush.radialGradient(listOf(palette.glow, Color.Transparent)))
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            StoryMonitor(palette)

            Spacer(modifier = Modifier.height(22.dp))

            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(2.dp)
                    .background(palette.accent, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            val quoteFont = FontFamily(Font(Res.font.cormorant_garamond_italic))
            Text(
                text = stringResource(Res.string.story_prompt_quote),
                fontFamily = quoteFont,
                fontStyle = FontStyle.Italic,
                fontSize = 19.sp,
                lineHeight = 25.sp,
                color = palette.quote,
            )
        }
    }
}

@Composable
private fun StoryMonitor(palette: StoryPanelPalette) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(10.dp))
                .background(palette.bezel, RoundedCornerShape(10.dp))
                .border(1.dp, palette.bezelBorder, RoundedCornerShape(10.dp))
                .padding(5.dp)
        ) {
            val screenshot = if (isDarkScheme(MaterialTheme.colorScheme)) {
                Res.drawable.share_story_screenshot_dark
            } else {
                Res.drawable.share_story_screenshot_light
            }
            Image(
                painter = painterResource(screenshot),
                contentDescription = stringResource(Res.string.story_prompt_screenshot),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(STORY_MONITOR_NECK_FRACTION)
                .height(11.dp)
                .background(Brush.verticalGradient(listOf(palette.standTop, palette.standBottom)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(STORY_MONITOR_BASE_FRACTION)
                .height(5.dp)
                .background(palette.standTop, RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun StoryProseColumn(modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Box(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 24.dp, end = 30.dp, top = 20.dp, bottom = 20.dp),
        ) {
            StoryBadge()

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(Res.string.story_prompt_heading),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            StoryParagraph(stringResource(Res.string.story_prompt_body_1))
            Spacer(modifier = Modifier.height(10.dp))
            StoryParagraph(stringResource(Res.string.story_prompt_body_2))

            Spacer(modifier = Modifier.height(12.dp))

            StoryExamples()

            Spacer(modifier = Modifier.height(12.dp))

            StoryParagraph(stringResource(Res.string.story_prompt_body_3))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.story_prompt_body_4),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (scroll.maxValue > 0 && scroll.maxValue != Int.MAX_VALUE) {
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp),
                adapter = rememberScrollbarAdapter(scroll),
            )
        }
    }
}

@Composable
private fun StoryBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.semantic.successContainer)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.semantic.success, RoundedCornerShape(percent = 50))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.story_prompt_badge).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
            color = MaterialTheme.semantic.onSuccessContainer,
        )
    }
}

@Composable
private fun StoryExamples() {
    val examples = listOf(
        stringResource(Res.string.story_prompt_example_1),
        stringResource(Res.string.story_prompt_example_2),
        stringResource(Res.string.story_prompt_example_3),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        examples.forEach { example ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.semantic.success,
                    modifier = Modifier.size(14.dp).padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StoryParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StoryFooter(onShare: () -> Unit, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onShare,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.semantic.success,
                contentColor = MaterialTheme.semantic.onSuccess,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(STORY_BUTTON_HEIGHT),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_mail),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = stringResource(Res.string.story_prompt_share),
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.height(STORY_BUTTON_HEIGHT),
        ) {
            Text(stringResource(Res.string.story_prompt_later))
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(Res.string.story_prompt_reassurance),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}
