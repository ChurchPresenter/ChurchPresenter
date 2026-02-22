package org.churchpresenter.app.churchpresenter.utils

import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * Creates a JFileChooser with the system Look & Feel applied first.
 * On Windows this prevents the empty/blank dialog bug caused by Compose
 * overriding the default L&F before Swing renders the chooser.
 */
fun createFileChooser(block: JFileChooser.() -> Unit = {}): JFileChooser {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (_: Exception) {}
    return JFileChooser().apply(block)
}

