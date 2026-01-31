package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.SwingPanel
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.data.SongSettings
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.awt.*
import javax.swing.*
import kotlinx.coroutines.runBlocking

// Helper function to get string resources in Swing context
@OptIn(ExperimentalResourceApi::class)
private fun getStringResource(resource: org.jetbrains.compose.resources.StringResource): String {
    return runBlocking {
        org.jetbrains.compose.resources.getString(resource)
    }
}

@Composable
fun SongSettingsTab(
    settings: SongSettings = SongSettings(),
    onSettingsChange: (SongSettings) -> Unit = {}
) {
    val currentSettings = remember { settings }

    SwingPanel(
        factory = {
            createNativeSongSettingsPanel(currentSettings, onSettingsChange)
        }
    )
}

private fun createNativeSongSettingsPanel(
    settings: SongSettings,
    onSettingsChange: (SongSettings) -> Unit
): JPanel {
    val panel = JPanel(BorderLayout())

    // Create main content panel with 2 columns
    val contentPanel = JPanel(GridBagLayout())
    val gbc = GridBagConstraints()

    // Get available fonts
    val availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames

    // LEFT COLUMN - Title Section
    val leftPanel = JPanel(GridBagLayout())
    val leftGbc = GridBagConstraints()
    leftGbc.anchor = GridBagConstraints.WEST
    leftGbc.insets = Insets(5, 10, 5, 5)

    // Title Section
    leftGbc.gridx = 0; leftGbc.gridy = 0; leftGbc.gridwidth = 2
    leftPanel.add(JLabel("<html><b>${getStringResource(Res.string.title_section)}</b></html>"), leftGbc)

    // Show title
    leftGbc.gridx = 0; leftGbc.gridy = 1; leftGbc.gridwidth = 1
    leftGbc.insets = Insets(5, 20, 5, 5)
    leftPanel.add(JLabel(getStringResource(Res.string.show_title)), leftGbc)

    val titleDisplayCombo = JComboBox(arrayOf(
        getStringResource(Res.string.none),
        getStringResource(Res.string.first_page),
        getStringResource(Res.string.every_page)
    )).apply {
        selectedItem = when (settings.titleDisplay) {
            "None" -> getStringResource(Res.string.none)
            "First Page" -> getStringResource(Res.string.first_page)
            "Every Page" -> getStringResource(Res.string.every_page)
            else -> getStringResource(Res.string.first_page)
        }
        addActionListener {
            val value = when (selectedItem as String) {
                getStringResource(Res.string.none) -> "None"
                getStringResource(Res.string.first_page) -> "First Page"
                getStringResource(Res.string.every_page) -> "Every Page"
                else -> "First Page"
            }
            onSettingsChange(settings.copy(titleDisplay = value))
        }
    }
    leftGbc.gridx = 1
    leftPanel.add(titleDisplayCombo, leftGbc)

    // Title font size
    leftGbc.gridx = 0; leftGbc.gridy = 2
    leftPanel.add(JLabel(getStringResource(Res.string.font_size)), leftGbc)

    val titleFontSizeSpinner = JSpinner(SpinnerNumberModel(settings.titleFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(titleFontSize = value as Int))
        }
    }
    leftGbc.gridx = 1
    leftPanel.add(titleFontSizeSpinner, leftGbc)

    // Title font type
    leftGbc.gridx = 0; leftGbc.gridy = 3
    leftPanel.add(JLabel(getStringResource(Res.string.font_type)), leftGbc)

    val titleFontCombo = JComboBox(availableFonts).apply {
        selectedItem = settings.titleFontType
        addActionListener {
            onSettingsChange(settings.copy(titleFontType = selectedItem as String))
        }
    }
    leftGbc.gridx = 1
    leftPanel.add(titleFontCombo, leftGbc)

    // Title font min
    leftGbc.gridx = 0; leftGbc.gridy = 4
    leftPanel.add(JLabel(getStringResource(Res.string.min)), leftGbc)

    val titleMinFontSpinner = JSpinner(SpinnerNumberModel(settings.titleMinFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(titleMinFontSize = value as Int))
        }
    }
    leftGbc.gridx = 1
    leftPanel.add(titleMinFontSpinner, leftGbc)

    // Title font max
    leftGbc.gridx = 0; leftGbc.gridy = 5
    leftPanel.add(JLabel(getStringResource(Res.string.max)), leftGbc)

    val titleMaxFontSpinner = JSpinner(SpinnerNumberModel(settings.titleMaxFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(titleMaxFontSize = value as Int))
        }
    }
    leftGbc.gridx = 1
    leftPanel.add(titleMaxFontSpinner, leftGbc)

    // Title alpha
    leftGbc.gridx = 0; leftGbc.gridy = 6
    leftPanel.add(JLabel("Font Alpha:"), leftGbc)

    val titleAlphaSlider = JSlider(0, 100, settings.titleAlpha.toInt()).apply {
        addChangeListener {
            onSettingsChange(settings.copy(titleAlpha = value.toFloat()))
        }
        majorTickSpacing = 25
        paintTicks = true
        paintLabels = true
    }
    leftGbc.gridx = 1; leftGbc.fill = GridBagConstraints.HORIZONTAL
    leftPanel.add(titleAlphaSlider, leftGbc)

    // Title alignment
    leftGbc.gridx = 0; leftGbc.gridy = 7; leftGbc.fill = GridBagConstraints.NONE
    leftPanel.add(JLabel(getStringResource(Res.string.title_alignment)), leftGbc)

    val titleAlignmentCombo = JComboBox(arrayOf(
        getStringResource(Res.string.top),
        getStringResource(Res.string.middle),
        getStringResource(Res.string.bottom)
    )).apply {
        selectedItem = when (settings.titleAlignment) {
            "Top" -> getStringResource(Res.string.top)
            "Middle" -> getStringResource(Res.string.middle)
            "Bottom" -> getStringResource(Res.string.bottom)
            else -> getStringResource(Res.string.middle)
        }
        addActionListener {
            val value = when (selectedItem as String) {
                getStringResource(Res.string.top) -> "Top"
                getStringResource(Res.string.middle) -> "Middle"
                getStringResource(Res.string.bottom) -> "Bottom"
                else -> "Middle"
            }
            onSettingsChange(settings.copy(titleAlignment = value))
        }
    }
    leftGbc.gridx = 1
    leftPanel.add(titleAlignmentCombo, leftGbc)

    // RIGHT COLUMN - Lyrics Section and Song Number Section
    val rightPanel = JPanel(GridBagLayout())
    val rightGbc = GridBagConstraints()
    rightGbc.anchor = GridBagConstraints.WEST
    rightGbc.insets = Insets(5, 10, 5, 5)

    // Lyrics Section
    rightGbc.gridx = 0; rightGbc.gridy = 0; rightGbc.gridwidth = 2
    rightPanel.add(JLabel("<html><b>${getStringResource(Res.string.lyrics_section)}</b></html>"), rightGbc)

    // Lyrics font size
    rightGbc.gridx = 0; rightGbc.gridy = 1; rightGbc.gridwidth = 1
    rightGbc.insets = Insets(5, 20, 5, 5)
    rightPanel.add(JLabel(getStringResource(Res.string.font_size)), rightGbc)

    val lyricsFontSizeSpinner = JSpinner(SpinnerNumberModel(settings.lyricsFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(lyricsFontSize = value as Int))
        }
    }
    rightGbc.gridx = 1
    rightPanel.add(lyricsFontSizeSpinner, rightGbc)

    // Lyrics font type
    rightGbc.gridx = 0; rightGbc.gridy = 2
    rightPanel.add(JLabel(getStringResource(Res.string.font_type)), rightGbc)

    val lyricsFontCombo = JComboBox(availableFonts).apply {
        selectedItem = settings.lyricsFontType
        addActionListener {
            onSettingsChange(settings.copy(lyricsFontType = selectedItem as String))
        }
    }
    rightGbc.gridx = 1
    rightPanel.add(lyricsFontCombo, rightGbc)

    // Word wrap
    rightGbc.gridx = 0; rightGbc.gridy = 3; rightGbc.gridwidth = 2
    val wordWrapCheckBox = JCheckBox(getStringResource(Res.string.word_wrap), settings.wordWrap).apply {
        addActionListener {
            onSettingsChange(settings.copy(wordWrap = isSelected))
        }
    }
    rightPanel.add(wordWrapCheckBox, rightGbc)

    // Lyrics alignment
    rightGbc.gridx = 0; rightGbc.gridy = 4; rightGbc.gridwidth = 1
    rightPanel.add(JLabel(getStringResource(Res.string.lyrics_alignment)), rightGbc)

    val lyricsAlignmentCombo = JComboBox(arrayOf(
        getStringResource(Res.string.top),
        getStringResource(Res.string.middle),
        getStringResource(Res.string.bottom)
    )).apply {
        selectedItem = when (settings.lyricsAlignment) {
            "Top" -> getStringResource(Res.string.top)
            "Middle" -> getStringResource(Res.string.middle)
            "Bottom" -> getStringResource(Res.string.bottom)
            else -> getStringResource(Res.string.middle)
        }
        addActionListener {
            val value = when (selectedItem as String) {
                getStringResource(Res.string.top) -> "Top"
                getStringResource(Res.string.middle) -> "Middle"
                getStringResource(Res.string.bottom) -> "Bottom"
                else -> "Middle"
            }
            onSettingsChange(settings.copy(lyricsAlignment = value))
        }
    }
    rightGbc.gridx = 1
    rightPanel.add(lyricsAlignmentCombo, rightGbc)

    // Song Number Section
    rightGbc.gridx = 0; rightGbc.gridy = 5; rightGbc.gridwidth = 2
    rightGbc.insets = Insets(20, 10, 5, 10)
    rightPanel.add(JLabel("<html><b>${getStringResource(Res.string.song_number)}</b></html>"), rightGbc)

    // Song number font size
    rightGbc.gridx = 0; rightGbc.gridy = 6; rightGbc.gridwidth = 1
    rightGbc.insets = Insets(5, 20, 5, 5)
    rightPanel.add(JLabel(getStringResource(Res.string.font_size)), rightGbc)

    val songNumberFontSizeSpinner = JSpinner(SpinnerNumberModel(settings.songNumberFontSize, 8, 48, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(songNumberFontSize = value as Int))
        }
    }
    rightGbc.gridx = 1
    rightPanel.add(songNumberFontSizeSpinner, rightGbc)

    // First page only
    rightGbc.gridx = 0; rightGbc.gridy = 7; rightGbc.gridwidth = 2
    val firstPageOnlyCheckBox = JCheckBox(getStringResource(Res.string.show_on_first_page_only), settings.songNumberFirstPageOnly).apply {
        addActionListener {
            onSettingsChange(settings.copy(songNumberFirstPageOnly = isSelected))
        }
    }
    rightPanel.add(firstPageOnlyCheckBox, rightGbc)

    // Position
    rightGbc.gridx = 0; rightGbc.gridy = 8; rightGbc.gridwidth = 1
    rightPanel.add(JLabel(getStringResource(Res.string.position_on_screen)), rightGbc)

    val positionCombo = JComboBox(arrayOf(
        getStringResource(Res.string.top_left),
        getStringResource(Res.string.top_right),
        getStringResource(Res.string.bottom_left),
        getStringResource(Res.string.bottom_right)
    )).apply {
        selectedItem = when (settings.songNumberPosition) {
            "Top Left" -> getStringResource(Res.string.top_left)
            "Top Right" -> getStringResource(Res.string.top_right)
            "Bottom Left" -> getStringResource(Res.string.bottom_left)
            "Bottom Right" -> getStringResource(Res.string.bottom_right)
            else -> getStringResource(Res.string.bottom_right)
        }
        addActionListener {
            val value = when (selectedItem as String) {
                getStringResource(Res.string.top_left) -> "Top Left"
                getStringResource(Res.string.top_right) -> "Top Right"
                getStringResource(Res.string.bottom_left) -> "Bottom Left"
                getStringResource(Res.string.bottom_right) -> "Bottom Right"
                else -> "Bottom Right"
            }
            onSettingsChange(settings.copy(songNumberPosition = value))
        }
    }
    rightGbc.gridx = 1
    rightPanel.add(positionCombo, rightGbc)

    // Add left and right panels to main content panel
    gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.5; gbc.fill = GridBagConstraints.BOTH
    gbc.anchor = GridBagConstraints.NORTHWEST
    contentPanel.add(leftPanel, gbc)

    gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.5
    contentPanel.add(rightPanel, gbc)

    panel.add(contentPanel, BorderLayout.CENTER)
    return panel
}