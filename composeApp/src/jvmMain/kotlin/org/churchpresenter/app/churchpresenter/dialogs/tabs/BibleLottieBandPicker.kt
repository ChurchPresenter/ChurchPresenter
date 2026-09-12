package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_lottie_gen_window_title
import churchpresenter.composeapp.generated.resources.bible_lottie_unsupported_note
import churchpresenter.composeapp.generated.resources.song_lottie_unsupported_note
import churchpresenter.composeapp.generated.resources.image_files_filter
import churchpresenter.composeapp.generated.resources.lottie_files_filter
import churchpresenter.composeapp.generated.resources.lower_third_animation
import churchpresenter.composeapp.generated.resources.lower_third_animation_clear
import churchpresenter.composeapp.generated.resources.lower_third_animation_file
import churchpresenter.composeapp.generated.resources.lower_third_animation_generate
import churchpresenter.composeapp.generated.resources.lower_third_animation_none
import churchpresenter.composeapp.generated.resources.lower_third_animation_use_lottie
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.LabeledSwitch
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.dialogs.PanelCaption
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.lottiegen.band.BandContentKind
import org.churchpresenter.lottiegen.band.BibleLottieGenApp
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.ReferencePlacement
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.math.roundToInt

private val PICKER_ROW_HEIGHT = 32.dp
private val GENERATOR_WINDOW_WIDTH = 1240.dp
private val GENERATOR_WINDOW_HEIGHT = 820.dp
private const val PERCENT = 100f
private const val REFERENCE_OUTPUT_HEIGHT = 1080f

/**
 * The Lottie template a Bible lower third plays: the file, a way to pick another, a way to drop
 * it, and — where the host has a folder to save into — a way to make a new one. [startDir] is
 * where the chooser opens when nothing is picked yet.
 */
@Composable
internal fun LottieBandPickerRow(
    path: String,
    onPathChange: (String) -> Unit,
    startDir: File?,
    onGenerate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val filterLabel = stringResource(Res.string.lottie_files_filter)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(PICKER_ROW_HEIGHT)
                .clickable {
                    scope.launch {
                        val start = when {
                            path.isNotBlank() -> Path(path)
                            else -> startDir?.toPath() ?: Path(System.getProperty("user.home"))
                        }
                        val file = FileChooser.platformInstance.chooseSingle(
                            path = start,
                            filters = listOf(FileNameExtensionFilter(filterLabel, "json")),
                            title = "",
                            selectDirectory = false,
                        )
                        if (file != null) onPathChange(file.absolutePathString())
                    }
                }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (path.isBlank()) stringResource(Res.string.lower_third_animation_none) else File(path).name,
                style = MaterialTheme.typography.bodySmall,
                color = if (path.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        if (path.isNotBlank()) {
            IconButton(onClick = { onPathChange("") }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.lower_third_animation_clear))
            }
        }
        if (onGenerate != null) {
            // Shaped like the app's other settings buttons — the Browse button beside a folder —
            // not Material's default pill.
            Button(
                onClick = onGenerate,
                modifier = Modifier.height(PICKER_ROW_HEIGHT),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(
                    stringResource(Res.string.lower_third_animation_generate),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * A content tab's own doorway to the same setting the Background tab's lower-third surface edits:
 * the switch turns the band's type to Lottie and back, the row picks the file, and the generator
 * writes a new one straight into it. [scope] says whose band — the Bible's or the songs'.
 */
@Composable
internal fun LowerThirdAnimationSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    generatorDir: File?,
    scope: BackgroundScope = BackgroundScope.BIBLE_LOWER_THIRD,
) {
    val config = settings.backgroundSettings.configFor(scope)
    val usesLottie = config.backgroundType == Constants.BACKGROUND_LOTTIE
    fun update(transform: (BackgroundConfig) -> BackgroundConfig) = onSettingsChange { s ->
        val backgrounds = s.backgroundSettings
        s.copy(backgroundSettings = backgrounds.withConfigFor(scope, transform(backgrounds.configFor(scope))))
    }
    var showGenerator by remember { mutableStateOf(false) }
    SettingsSection(title = stringResource(Res.string.lower_third_animation)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledSwitch(
                checked = usesLottie,
                onCheckedChange = { on ->
                    val type = if (on) Constants.BACKGROUND_LOTTIE else Constants.BACKGROUND_DEFAULT
                    update { it.copy(backgroundType = type) }
                },
                label = stringResource(Res.string.lower_third_animation_use_lottie),
                controlAtEnd = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (usesLottie) {
                Text(
                    stringResource(Res.string.lower_third_animation_file),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LottieBandPickerRow(
                    path = config.backgroundLottie,
                    onPathChange = { path -> update { it.copy(backgroundLottie = path) } },
                    startDir = generatorDir,
                    onGenerate = if (generatorDir != null) ({ showGenerator = true }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(
                        if (scope == BackgroundScope.SONG_LOWER_THIRD) Res.string.song_lottie_unsupported_note
                        else Res.string.bible_lottie_unsupported_note,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showGenerator && generatorDir != null) {
        BibleLottieGeneratorWindow(
            outputDir = generatorDir,
            seed = lottieBandSeed(settings, scope),
            onSaved = { file ->
                update { it.copy(backgroundType = Constants.BACKGROUND_LOTTIE, backgroundLottie = file.absolutePath) }
                showGenerator = false
            },
            onClose = { showGenerator = false },
        )
    }
}

/** The template picker, and the generator that writes a new template straight into it. */
@Composable
internal fun LottieBandSourceSection(
    scope: BackgroundScope,
    settings: AppSettings,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit,
    bibleLowerThirdsDir: File?,
) {
    var showGenerator by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelCaption(stringResource(Res.string.lower_third_animation_file))
        LottieBandPickerRow(
            path = config.backgroundLottie,
            onPathChange = { onConfigChange(config.copy(backgroundLottie = it)) },
            startDir = bibleLowerThirdsDir,
            onGenerate = if (bibleLowerThirdsDir != null) ({ showGenerator = true }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (showGenerator && bibleLowerThirdsDir != null) {
        BibleLottieGeneratorWindow(
            outputDir = bibleLowerThirdsDir,
            seed = lottieBandSeed(settings, scope),
            onSaved = { file ->
                onConfigChange(config.copy(backgroundLottie = file.absolutePath))
                showGenerator = false
            },
            onClose = { showGenerator = false },
        )
    }
}

/** The generator in a window of its own, over whichever dialog opened it. */
@Composable
internal fun BibleLottieGeneratorWindow(
    outputDir: File,
    seed: BibleLottieGenConfig,
    onSaved: (File) -> Unit,
    onClose: () -> Unit,
) {
    val mainWindowState = LocalMainWindowState.current
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, GENERATOR_WINDOW_WIDTH, GENERATOR_WINDOW_HEIGHT),
        width = GENERATOR_WINDOW_WIDTH,
        height = GENERATOR_WINDOW_HEIGHT,
    )
    val imageFilter = stringResource(Res.string.image_files_filter)
    DialogWindow(
        onCloseRequest = onClose,
        state = dialogState,
        title = stringResource(Res.string.bible_lottie_gen_window_title),
        resizable = true,
    ) {
        BibleLottieGenApp(
            outputDir = outputDir,
            onFileSaved = onSaved,
            seed = seed,
            embedded = true,
            // The app's native chooser, which shows the pictures themselves, opened on the stock
            // library so the bundled and downloaded backgrounds are the first thing offered.
            pickImage = {
                FileChooser.platformInstance.chooseSingle(
                    path = stockBackgroundsDir(),
                    filters = listOf(FileNameExtensionFilter(imageFilter, "jpg", "jpeg", "png", "webp")),
                    title = "",
                    selectDirectory = false,
                )?.toFile()
            },
        )
    }
}

/**
 * What the generator starts from: the band the outputs actually draw — the first output's size at
 * that content's band height — and the content's lower-third typography, so the sample in the
 * preview is set the way the live text will be.
 */
internal fun lottieBandSeed(settings: AppSettings, scope: BackgroundScope): BibleLottieGenConfig {
    val output = previewOutputSize(settings)
    val pxPerPoint = output.height / REFERENCE_OUTPUT_HEIGHT
    return if (scope == BackgroundScope.SONG_LOWER_THIRD) {
        val song = settings.songSettings
        val canvasH = (output.height * song.lowerThirdHeightPercent / PERCENT).roundToInt().coerceAtLeast(1)
        BibleLottieGenConfig(
            canvasW = output.width,
            canvasH = canvasH,
            kind = BandContentKind.SONG,
            layout = SlotLayout.SINGLE,
            referencePlacement = placementOf(song.titleLowerThirdPosition == Constants.ABOVE_VERSE),
            previewFontFamily = song.lyricsLowerThirdFontType,
            previewTextSizePx = (song.lyricsLowerThirdFontSize * pxPerPoint).roundToInt(),
            previewReferenceSizePx = (song.titleLowerThirdFontSize * pxPerPoint).roundToInt(),
            previewTextColor = song.lyricsLowerThirdColor,
            previewReferenceColor = song.titleLowerThirdColor,
            previewBold = song.lyricsLowerThirdBold,
            previewText1 = SONG_SAMPLE_LINES,
            previewReference1 = SONG_SAMPLE_TITLE,
            previewText2 = SONG_SAMPLE_LINES_2,
            previewReference2 = SONG_SAMPLE_TITLE_2,
        )
    } else {
        val bible = settings.bibleSettings
        val canvasH = (output.height * bible.lowerThirdHeightPercent / PERCENT).roundToInt().coerceAtLeast(1)
        val stack = bible.translationList()
        val t0 = stack.firstOrNull() ?: return BibleLottieGenConfig(canvasW = output.width, canvasH = canvasH)
        BibleLottieGenConfig(
            canvasW = output.width,
            canvasH = canvasH,
            layout = if (stack.size >= 2) SlotLayout.SIDE_BY_SIDE else SlotLayout.SINGLE,
            referencePlacement = placementOf(t0.lowerThirdReferencePosition == Constants.POSITION_ABOVE),
            previewFontFamily = t0.lowerThirdTextFontType,
            previewTextSizePx = (t0.lowerThirdTextFontSize * pxPerPoint).roundToInt(),
            previewReferenceSizePx = (t0.lowerThirdReferenceFontSize * pxPerPoint).roundToInt(),
            previewTextColor = t0.lowerThirdTextColor,
            previewReferenceColor = t0.lowerThirdReferenceColor,
            previewBold = t0.lowerThirdTextBold,
        )
    }
}

private fun placementOf(above: Boolean): ReferencePlacement =
    if (above) ReferencePlacement.ABOVE else ReferencePlacement.BELOW

/** The sample a song band is previewed with: two lines of a public-domain hymn, its title, and the same in Spanish. */
private const val SONG_SAMPLE_LINES = "Amazing grace, how sweet the sound\nThat saved a wretch like me"
private const val SONG_SAMPLE_TITLE = "Amazing Grace"
private const val SONG_SAMPLE_LINES_2 = "Sublime gracia del Señor\nQue a un pecador salvó"
private const val SONG_SAMPLE_TITLE_2 = "Sublime Gracia"

/** Where the app keeps downloaded and materialised stock backgrounds; the home directory if it has none yet. */
private fun stockBackgroundsDir(): java.nio.file.Path {
    val dir = File(System.getProperty("user.home"), STOCK_BACKGROUNDS_DIR)
    return (if (dir.isDirectory) dir else File(System.getProperty("user.home"))).toPath()
}

private const val STOCK_BACKGROUNDS_DIR = ".churchpresenter/stock-backgrounds"
