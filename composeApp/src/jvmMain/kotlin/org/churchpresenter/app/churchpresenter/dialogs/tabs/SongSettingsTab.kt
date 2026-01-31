package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
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
    SwingPanel(
        factory = {
            createNativeSongSettingsPanel(settings, onSettingsChange)
        }
    )
}

fun createNativeSongSettingsPanel(
    settings: SongSettings,
    onSettingsChange: (SongSettings) -> Unit
): JPanel {
    val panel = JPanel(BorderLayout())

    // Create main content panel with 2 columns
    val contentPanel = JPanel(GridBagLayout())
    val gbc = GridBagConstraints()

    // Get available fonts
    val availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames

    // LEFT COLUMN - Song Files and Title Section
    val leftPanel = JPanel(GridBagLayout())
    val leftGbc = GridBagConstraints()
    leftGbc.anchor = GridBagConstraints.WEST
    leftGbc.insets = Insets(2, 5, 2, 5)

    // Song Files Section
    leftGbc.gridx = 0; leftGbc.gridy = 0; leftGbc.gridwidth = 2
    leftGbc.insets = Insets(5, 5, 5, 5)
    leftPanel.add(JLabel("<html><b>${getStringResource(Res.string.song_files)}</b></html>"), leftGbc)

    // Song files list
    leftGbc.gridx = 0; leftGbc.gridy = 1; leftGbc.gridwidth = 2
    leftGbc.insets = Insets(2, 15, 2, 5)
    leftGbc.fill = GridBagConstraints.BOTH
    leftGbc.weightx = 1.0
    leftGbc.weighty = 0.3 // Give some height to the list

    val songFilesList = JList(settings.songFiles.toTypedArray()).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 3
        if (settings.songFiles.isEmpty()) {
            setListData(arrayOf(getStringResource(Res.string.no_song_files)))
        }
    }
    val scrollPane = JScrollPane(songFilesList)
    scrollPane.preferredSize = Dimension(300, 80)
    leftPanel.add(scrollPane, leftGbc)

    // Import/Remove buttons panel
    leftGbc.gridx = 0; leftGbc.gridy = 2; leftGbc.gridwidth = 2
    leftGbc.fill = GridBagConstraints.HORIZONTAL
    leftGbc.weighty = 0.0
    leftGbc.insets = Insets(5, 15, 10, 5)

    val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))

    val importButton = JButton(getStringResource(Res.string.import_song_file)).apply {
        addActionListener {
            val fileChooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = true
                fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                    "Song Files (*.spb, *.sps)", "spb", "sps"
                )
            }

            val result = fileChooser.showOpenDialog(this@apply)
            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFiles = fileChooser.selectedFiles
                val newFiles = selectedFiles.map { it.absolutePath }
                val updatedFiles = (settings.songFiles + newFiles).distinct()
                onSettingsChange(settings.copy(songFiles = updatedFiles))

                // Update the list display
                songFilesList.setListData(updatedFiles.toTypedArray())
            }
        }
    }
    buttonPanel.add(importButton)

    val removeButton = JButton(getStringResource(Res.string.remove_song_file)).apply {
        addActionListener {
            val selectedIndex = songFilesList.selectedIndex
            if (selectedIndex >= 0 && settings.songFiles.isNotEmpty()) {
                val updatedFiles = settings.songFiles.toMutableList()
                updatedFiles.removeAt(selectedIndex)
                onSettingsChange(settings.copy(songFiles = updatedFiles))

                // Update the list display
                if (updatedFiles.isEmpty()) {
                    songFilesList.setListData(arrayOf(getStringResource(Res.string.no_song_files)))
                } else {
                    songFilesList.setListData(updatedFiles.toTypedArray())
                }
            }
        }
    }
    buttonPanel.add(removeButton)

    leftPanel.add(buttonPanel, leftGbc)

    // Title Section
    leftGbc.gridx = 0; leftGbc.gridy = 3; leftGbc.gridwidth = 2
    leftGbc.insets = Insets(15, 5, 5, 5)
    leftGbc.weighty = 0.0
    leftGbc.fill = GridBagConstraints.NONE
    leftPanel.add(JLabel("<html><b>${getStringResource(Res.string.title)}</b></html>"), leftGbc)

    // Show title
    leftGbc.gridx = 0; leftGbc.gridy = 4; leftGbc.gridwidth = 1
    leftGbc.insets = Insets(2, 15, 2, 5)
    val showTitleLabel = JLabel(getStringResource(Res.string.show_title))
    showTitleLabel.preferredSize = Dimension(120, showTitleLabel.preferredSize.height)
    leftPanel.add(showTitleLabel, leftGbc)

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
    leftGbc.gridx = 0; leftGbc.gridy = 5
    val fontSizeLabel = JLabel(getStringResource(Res.string.font_size))
    fontSizeLabel.preferredSize = Dimension(120, fontSizeLabel.preferredSize.height)
    leftPanel.add(fontSizeLabel, leftGbc)

    val titleFontSizeSpinner = JSpinner(SpinnerNumberModel(settings.titleFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(titleFontSize = value as Int))
        }
    }
    leftGbc.gridx = 1
    leftPanel.add(titleFontSizeSpinner, leftGbc)

    // Title font type
    leftGbc.gridx = 0; leftGbc.gridy = 6
    val fontTypeLabel = JLabel(getStringResource(Res.string.font_type))
    fontTypeLabel.preferredSize = Dimension(120, fontTypeLabel.preferredSize.height)
    leftPanel.add(fontTypeLabel, leftGbc)

    val titleFontCombo = JComboBox(availableFonts).apply {
        selectedItem = settings.titleFontType
        addActionListener {
            onSettingsChange(settings.copy(titleFontType = selectedItem as String))
        }
    }
    leftGbc.gridx = 1
    leftPanel.add(titleFontCombo, leftGbc)

    // Title font min/max on same row
    leftGbc.gridx = 0; leftGbc.gridy = 7; leftGbc.gridwidth = 1
    val minLabel = JLabel(getStringResource(Res.string.min))
    minLabel.preferredSize = Dimension(120, minLabel.preferredSize.height)
    leftPanel.add(minLabel, leftGbc)

    val titleMinMaxPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
    val titleMinFontSpinner = JSpinner(SpinnerNumberModel(settings.titleMinFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(titleMinFontSize = value as Int))
        }
        preferredSize = Dimension(60, preferredSize.height)
    }
    titleMinMaxPanel.add(titleMinFontSpinner)

    titleMinMaxPanel.add(JLabel(getStringResource(Res.string.max)))
    val titleMaxFontSpinner = JSpinner(SpinnerNumberModel(settings.titleMaxFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(titleMaxFontSize = value as Int))
        }
        preferredSize = Dimension(60, preferredSize.height)
    }
    titleMinMaxPanel.add(titleMaxFontSpinner)

    leftGbc.gridx = 1
    leftPanel.add(titleMinMaxPanel, leftGbc)

    // Title alpha
    leftGbc.gridx = 0; leftGbc.gridy = 8
    val alphaLabel = JLabel("Font Alpha:")
    alphaLabel.preferredSize = Dimension(120, alphaLabel.preferredSize.height)
    leftPanel.add(alphaLabel, leftGbc)

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
    leftGbc.gridx = 0; leftGbc.gridy = 9; leftGbc.fill = GridBagConstraints.NONE
    val titleAlignmentLabel = JLabel(getStringResource(Res.string.vertical_alignment))
    titleAlignmentLabel.preferredSize = Dimension(120, titleAlignmentLabel.preferredSize.height)
    leftPanel.add(titleAlignmentLabel, leftGbc)

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

    // Title horizontal alignment
    leftGbc.gridx = 0; leftGbc.gridy = 10
    val titleHorizontalAlignmentLabel = JLabel(getStringResource(Res.string.horizontal_alignment))
    titleHorizontalAlignmentLabel.preferredSize = Dimension(120, titleHorizontalAlignmentLabel.preferredSize.height)
    leftPanel.add(titleHorizontalAlignmentLabel, leftGbc)

    val titleHorizontalAlignmentCombo = JComboBox(arrayOf(
        getStringResource(Res.string.left),
        getStringResource(Res.string.center),
        getStringResource(Res.string.right)
    )).apply {
        selectedItem = when (settings.titleHorizontalAlignment) {
            "Left" -> getStringResource(Res.string.left)
            "Center" -> getStringResource(Res.string.center)
            "Right" -> getStringResource(Res.string.right)
            else -> getStringResource(Res.string.center)
        }
        addActionListener {
            val value = when (selectedItem as String) {
                getStringResource(Res.string.left) -> "Left"
                getStringResource(Res.string.center) -> "Center"
                getStringResource(Res.string.right) -> "Right"
                else -> "Center"
            }
            onSettingsChange(settings.copy(titleHorizontalAlignment = value))
        }
    }
    leftGbc.gridx = 1
    leftPanel.add(titleHorizontalAlignmentCombo, leftGbc)

    // RIGHT COLUMN - Lyrics Section and Song Number Section
    val rightPanel = JPanel(GridBagLayout())
    val rightGbc = GridBagConstraints()
    rightGbc.anchor = GridBagConstraints.WEST
    rightGbc.insets = Insets(2, 5, 2, 5)

    // Lyrics Section
    rightGbc.gridx = 0; rightGbc.gridy = 0; rightGbc.gridwidth = 2
    rightGbc.insets = Insets(5, 5, 5, 5)
    rightPanel.add(JLabel("<html><b>${getStringResource(Res.string.lyrics)}</b></html>"), rightGbc)

    // Lyrics font size
    rightGbc.gridx = 0; rightGbc.gridy = 1; rightGbc.gridwidth = 1
    rightGbc.insets = Insets(2, 15, 2, 5)
    val lyricsFontSizeLabel = JLabel(getStringResource(Res.string.font_size))
    lyricsFontSizeLabel.preferredSize = Dimension(120, lyricsFontSizeLabel.preferredSize.height)
    rightPanel.add(lyricsFontSizeLabel, rightGbc)

    val lyricsFontSizeSpinner = JSpinner(SpinnerNumberModel(settings.lyricsFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(lyricsFontSize = value as Int))
        }
    }
    rightGbc.gridx = 1
    rightPanel.add(lyricsFontSizeSpinner, rightGbc)

    // Lyrics font type
    rightGbc.gridx = 0; rightGbc.gridy = 2
    val lyricsFontTypeLabel = JLabel(getStringResource(Res.string.font_type))
    lyricsFontTypeLabel.preferredSize = Dimension(120, lyricsFontTypeLabel.preferredSize.height)
    rightPanel.add(lyricsFontTypeLabel, rightGbc)

    val lyricsFontCombo = JComboBox(availableFonts).apply {
        selectedItem = settings.lyricsFontType
        addActionListener {
            onSettingsChange(settings.copy(lyricsFontType = selectedItem as String))
        }
    }
    rightGbc.gridx = 1
    rightPanel.add(lyricsFontCombo, rightGbc)

    // Lyrics font min/max on same row
    rightGbc.gridx = 0; rightGbc.gridy = 3; rightGbc.gridwidth = 1
    val lyricsMinLabel = JLabel(getStringResource(Res.string.min))
    lyricsMinLabel.preferredSize = Dimension(120, lyricsMinLabel.preferredSize.height)
    rightPanel.add(lyricsMinLabel, rightGbc)

    val lyricsMinMaxPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
    val lyricsMinFontSpinner = JSpinner(SpinnerNumberModel(settings.lyricsMinFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(lyricsMinFontSize = value as Int))
        }
        preferredSize = Dimension(60, preferredSize.height)
    }
    lyricsMinMaxPanel.add(lyricsMinFontSpinner)

    lyricsMinMaxPanel.add(JLabel(getStringResource(Res.string.max)))
    val lyricsMaxFontSpinner = JSpinner(SpinnerNumberModel(settings.lyricsMaxFontSize, 8, 72, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(lyricsMaxFontSize = value as Int))
        }
        preferredSize = Dimension(60, preferredSize.height)
    }
    lyricsMinMaxPanel.add(lyricsMaxFontSpinner)

    rightGbc.gridx = 1
    rightPanel.add(lyricsMinMaxPanel, rightGbc)

    // Lyrics alpha
    rightGbc.gridx = 0; rightGbc.gridy = 4; rightGbc.gridwidth = 1
    val lyricsAlphaLabel = JLabel(getStringResource(Res.string.font_alpha))
    lyricsAlphaLabel.preferredSize = Dimension(120, lyricsAlphaLabel.preferredSize.height)
    rightPanel.add(lyricsAlphaLabel, rightGbc)

    val lyricsAlphaSlider = JSlider(0, 100, settings.lyricsAlpha.toInt()).apply {
        addChangeListener {
            onSettingsChange(settings.copy(lyricsAlpha = value.toFloat()))
        }
        majorTickSpacing = 25
        paintTicks = true
        paintLabels = true
    }
    rightGbc.gridx = 1; rightGbc.fill = GridBagConstraints.HORIZONTAL
    rightPanel.add(lyricsAlphaSlider, rightGbc)

    // Word wrap
    rightGbc.gridx = 0; rightGbc.gridy = 5; rightGbc.gridwidth = 2; rightGbc.fill = GridBagConstraints.NONE
    val wordWrapCheckBox = JCheckBox(getStringResource(Res.string.word_wrap), settings.wordWrap).apply {
        addActionListener {
            onSettingsChange(settings.copy(wordWrap = isSelected))
        }
    }
    rightPanel.add(wordWrapCheckBox, rightGbc)

    // Lyrics alignment
    rightGbc.gridx = 0; rightGbc.gridy = 6; rightGbc.gridwidth = 1
    val lyricsAlignmentLabel = JLabel(getStringResource(Res.string.vertical_alignment))
    lyricsAlignmentLabel.preferredSize = Dimension(120, lyricsAlignmentLabel.preferredSize.height)
    rightPanel.add(lyricsAlignmentLabel, rightGbc)

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

    // Lyrics horizontal alignment
    rightGbc.gridx = 0; rightGbc.gridy = 7; rightGbc.gridwidth = 1
    val lyricsHorizontalAlignmentLabel = JLabel(getStringResource(Res.string.horizontal_alignment))
    lyricsHorizontalAlignmentLabel.preferredSize = Dimension(120, lyricsHorizontalAlignmentLabel.preferredSize.height)
    rightPanel.add(lyricsHorizontalAlignmentLabel, rightGbc)

    val lyricsHorizontalAlignmentCombo = JComboBox(arrayOf(
        getStringResource(Res.string.left),
        getStringResource(Res.string.center),
        getStringResource(Res.string.right)
    )).apply {
        selectedItem = when (settings.lyricsHorizontalAlignment) {
            "Left" -> getStringResource(Res.string.left)
            "Center" -> getStringResource(Res.string.center)
            "Right" -> getStringResource(Res.string.right)
            else -> getStringResource(Res.string.center)
        }
        addActionListener {
            val value = when (selectedItem as String) {
                getStringResource(Res.string.left) -> "Left"
                getStringResource(Res.string.center) -> "Center"
                getStringResource(Res.string.right) -> "Right"
                else -> "Center"
            }
            onSettingsChange(settings.copy(lyricsHorizontalAlignment = value))
        }
    }
    rightGbc.gridx = 1
    rightPanel.add(lyricsHorizontalAlignmentCombo, rightGbc)

    // Song Number Section
    rightGbc.gridx = 0; rightGbc.gridy = 8; rightGbc.gridwidth = 2
    rightGbc.insets = Insets(15, 5, 5, 5)
    rightPanel.add(JLabel("<html><b>${getStringResource(Res.string.song_number)}</b></html>"), rightGbc)

    // Song number font size
    rightGbc.gridx = 0; rightGbc.gridy = 9; rightGbc.gridwidth = 1
    rightGbc.insets = Insets(2, 15, 2, 5)
    val songNumberFontSizeLabel = JLabel(getStringResource(Res.string.font_size))
    songNumberFontSizeLabel.preferredSize = Dimension(120, songNumberFontSizeLabel.preferredSize.height)
    rightPanel.add(songNumberFontSizeLabel, rightGbc)

    val songNumberFontSizeSpinner = JSpinner(SpinnerNumberModel(settings.songNumberFontSize, 8, 48, 1)).apply {
        addChangeListener {
            onSettingsChange(settings.copy(songNumberFontSize = value as Int))
        }
    }
    rightGbc.gridx = 1
    rightPanel.add(songNumberFontSizeSpinner, rightGbc)

    // First page only
    rightGbc.gridx = 0; rightGbc.gridy = 10; rightGbc.gridwidth = 2
    val firstPageOnlyCheckBox = JCheckBox(getStringResource(Res.string.show_on_first_page_only), settings.songNumberFirstPageOnly).apply {
        addActionListener {
            onSettingsChange(settings.copy(songNumberFirstPageOnly = isSelected))
        }
    }
    rightPanel.add(firstPageOnlyCheckBox, rightGbc)

    // Position
    rightGbc.gridx = 0; rightGbc.gridy = 11; rightGbc.gridwidth = 1
    val positionLabel = JLabel(getStringResource(Res.string.position_on_screen))
    positionLabel.preferredSize = Dimension(120, positionLabel.preferredSize.height)
    rightPanel.add(positionLabel, rightGbc)

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

