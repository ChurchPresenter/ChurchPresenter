package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_lottie_gen_window_title
import churchpresenter.composeapp.generated.resources.bible_font
import churchpresenter.composeapp.generated.resources.image_files_filter
import churchpresenter.composeapp.generated.resources.lower_third_animation_file
import churchpresenter.composeapp.generated.resources.lower_third_animation_generate
import churchpresenter.composeapp.generated.resources.lower_third_animation_none
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.dialogs.PanelCaption
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.lottiegen.band.BandColorField
import org.churchpresenter.lottiegen.band.BandContentKind
import org.churchpresenter.lottiegen.band.BandFontPicker
import org.churchpresenter.lottiegen.band.BibleLottieGenApp
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.ReferencePlacement
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.app.churchpresenter.presenter.invalidateBibleLottieTemplates
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.components.DropdownSelector
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.roundToInt

private val PICKER_ROW_HEIGHT = 32.dp
private val GENERATOR_WINDOW_WIDTH = 1240.dp
private val GENERATOR_WINDOW_HEIGHT = 820.dp
private const val PERCENT = 100f
private const val REFERENCE_OUTPUT_HEIGHT = 1080f

/**
 * The Lottie template a Bible lower third plays, picked from the templates in [templatesDir] by
 * name — with "no template" at the top — and, where the host has a folder to save into, a way to
 * make a new one. A file set from elsewhere is still listed, so nothing already chosen is lost.
 */
@Composable
internal fun LottieBandPickerRow(
    path: String,
    onPathChange: (String) -> Unit,
    templatesDir: File?,
    onGenerate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val none = stringResource(Res.string.lower_third_animation_none)
    // Listed afresh whenever the choice changes, which is also when the generator has just
    // written a new file into the folder.
    val options = remember(templatesDir, path, none) { templateOptions(templatesDir, path, none) }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DropdownSelector(
            label = "",
            value = path,
            options = options,
            onValueChange = onPathChange,
            modifier = Modifier.weight(1f),
            compact = true,
        )
        if (onGenerate != null) {
            // Shaped like the app's other settings buttons — the Browse button beside a folder —
            // not Material's default pill.
            RaisedButton(
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
            templatesDir = bibleLowerThirdsDir,
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
                // The generator saves over the path it loaded from, so nothing below this
                // would notice the file changed on its own.
                invalidateBibleLottieTemplates()
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
    val fonts = rememberSystemFonts()
    val fontLabel = stringResource(Res.string.bible_font)
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
            colorField = hostColorField,
            fontPicker = hostFontPicker(fonts, fontLabel),
        )
    }
}

/** The app's colour field, lent to the generator: a click anywhere on it opens the app's Choose Color dialog. */
internal val hostColorField: BandColorField = { label, color, onColorChange, modifier ->
    ColorPickerField(color = color, onColorChange = onColorChange, modifier = modifier, label = label)
}

/** The app's font picker, lent to the generator: the machine's [fonts], each shown in its own face. */
internal fun hostFontPicker(fonts: List<String>, label: String): BandFontPicker = { value, onValueChange, modifier ->
    FontSettingsDropdown(
        modifier = modifier,
        label = label,
        value = value,
        fonts = fonts,
        fillWidth = true,
        onValueChange = onValueChange,
    )
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
            previewItalic = song.lyricsLowerThirdItalic,
            previewShadow = song.lyricsLowerThirdShadow,
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
            previewItalic = t0.lowerThirdTextItalic,
            previewShadow = t0.lowerThirdTextShadow,
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

/** The template files in [dir] by name, after the no-template entry; [current] is kept even from outside it. */
internal fun templateOptions(dir: File?, current: String, noneLabel: String): List<Pair<String, String>> {
    val files = dir?.listFiles { f -> f.isFile && f.extension.equals(TEMPLATE_EXTENSION, ignoreCase = true) }
        .orEmpty()
        .sortedBy { it.nameWithoutExtension.lowercase() }
        .map { it.absolutePath to it.nameWithoutExtension }
    val listed = files.any { it.first == current }
    val extra =
        if (current.isNotBlank() && !listed) listOf(current to File(current).nameWithoutExtension) else emptyList()
    return listOf("" to noneLabel) + extra + files
}

private const val TEMPLATE_EXTENSION = "json"
