package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.FrameWindowScope
import org.churchpresenter.theme.LocalThemeCustomization
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI
import javax.swing.plaf.basic.BasicMenuBarUI
import javax.swing.plaf.basic.BasicMenuItemUI
import javax.swing.plaf.basic.BasicMenuUI
import javax.swing.plaf.basic.BasicPopupMenuSeparatorUI
import javax.swing.plaf.basic.BasicPopupMenuUI
import javax.swing.plaf.basic.BasicRadioButtonMenuItemUI
import java.awt.Color as AwtColor

/** What the menu bar is painted with: the theme's colours as AWT colours, and the menus' font. */
internal data class MenuBarStyle(
    val background: AwtColor,
    val foreground: AwtColor,
    val disabled: AwtColor,
    val selectionBackground: AwtColor,
    val selectionForeground: AwtColor,
    val popupBackground: AwtColor,
    val outline: AwtColor,
    val font: Font,
)

/** Pixels of the menus' own font at the default interface size, where the look and feel gives none. */
private const val DEFAULT_MENU_FONT_SIZE = 12

/** Marks a menu bar already painted with a style, so an unchanged style is not installed again. */
private const val STYLE_PROPERTY = "churchpresenter.menuBarStyle"

/**
 * Paints the main window's menu bar in the theme on Windows and Linux, at the interface size.
 *
 * The menu bar is Swing's, not Compose's, so neither the theme nor `ProvideUiScale`
 * reaches it: it is drawn by the platform look and feel — white with black text on the Metal look a
 * MATE desktop gets, GTK's colours on GNOME, Windows' own on Windows — whatever the app's theme. Each
 * menu, popup and item is given Swing's basic painters, which take their colours from the component,
 * so every look and feel ends up the same. macOS is left alone: its menus are in the system menu bar.
 *
 * Restyled after every composition of the menu bar, on the event queue so the menus that composition
 * made are in place; a menu that appears later (Custom, once a custom theme exists) is styled then.
 */
@Composable
internal fun FrameWindowScope.ThemedMenuBar() {
    ThemedMenuBar(menuBar = { window.jMenuBar })
}

/** [ThemedMenuBar] for the bar [menuBar] finds; nothing on [macOs]. */
@Composable
internal fun ThemedMenuBar(menuBar: () -> JMenuBar?, macOs: Boolean = isMacOs) {
    if (macOs) return
    val style = themedMenuBarStyle()
    SideEffect {
        SwingUtilities.invokeLater { menuBar()?.let { styleMenuBar(it, style) } }
    }
}

/** The theme in effect, as the menu bar's colours, and the menus' font at the interface size. */
@Composable
internal fun themedMenuBarStyle(): MenuBarStyle {
    val colors = MaterialTheme.colorScheme
    return MenuBarStyle(
        background = colors.surface.awt(),
        foreground = colors.onSurface.awt(),
        disabled = colors.onSurfaceVariant.awt(),
        selectionBackground = colors.secondaryContainer.awt(),
        selectionForeground = colors.onSecondaryContainer.awt(),
        popupBackground = colors.surfaceContainer.awt(),
        outline = colors.outlineVariant.awt(),
        font = menuFont(LocalThemeCustomization.current.uiScale),
    )
}

private val isMacOs: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

private fun Color.awt(): AwtColor = AwtColor(toArgb(), true)

/** The look and feel's menu font, grown by [uiScale]. */
internal fun menuFont(uiScale: Float, lookAndFeelFont: Font? = UIManager.getFont("Menu.font")): Font {
    val base = lookAndFeelFont ?: Font(Font.DIALOG, Font.PLAIN, DEFAULT_MENU_FONT_SIZE)
    return base.deriveFont(base.size2D * uiScale)
}

/**
 * Paints [bar], its menus and everything in them with [style]. Idempotent: a bar already in [style]
 * is only walked for menus or items that have not been styled yet.
 */
internal fun styleMenuBar(bar: JMenuBar, style: MenuBarStyle) {
    val restyle = bar.getClientProperty(STYLE_PROPERTY) != style
    if (restyle || bar.ui !is BasicMenuBarUI) bar.setUI(BasicMenuBarUI())
    bar.putClientProperty(STYLE_PROPERTY, style)
    bar.paint(style.background, style.foreground, style.font)
    bar.isOpaque = true
    bar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, style.outline)
    for (index in 0 until bar.menuCount) {
        bar.getMenu(index)?.let { styleMenu(it, style, restyle) }
    }
}

private fun styleMenu(menu: JMenu, style: MenuBarStyle, restyle: Boolean) {
    if (restyle || menu.ui !is ThemedMenuUI) menu.setUI(ThemedMenuUI(style))
    menu.paint(style.background, style.foreground, style.font)
    menu.isOpaque = false
    stylePopup(menu.popupMenu, style, restyle)
}

private fun stylePopup(popup: JPopupMenu, style: MenuBarStyle, restyle: Boolean) {
    if (restyle || popup.ui !is BasicPopupMenuUI) popup.setUI(BasicPopupMenuUI())
    popup.paint(style.popupBackground, style.foreground, style.font)
    popup.border = BorderFactory.createLineBorder(style.outline)
    for (child in popup.components) styleEntry(child, style, restyle)
}

private fun styleEntry(child: Component, style: MenuBarStyle, restyle: Boolean) {
    when (child) {
        is JMenu -> {
            if (restyle || child.ui !is ThemedMenuUI) child.setUI(ThemedMenuUI(style))
            child.paint(style.popupBackground, style.foreground, style.font)
            stylePopup(child.popupMenu, style, restyle)
        }
        is JRadioButtonMenuItem -> {
            if (restyle || child.ui !is ThemedRadioItemUI) child.setUI(ThemedRadioItemUI(style))
            child.paint(style.popupBackground, style.foreground, style.font)
        }
        is JCheckBoxMenuItem -> {
            if (restyle || child.ui !is ThemedCheckItemUI) child.setUI(ThemedCheckItemUI(style))
            child.paint(style.popupBackground, style.foreground, style.font)
        }
        is JMenuItem -> {
            if (restyle || child.ui !is ThemedItemUI) child.setUI(ThemedItemUI(style))
            child.paint(style.popupBackground, style.foreground, style.font)
        }
        is JPopupMenu.Separator -> {
            if (restyle || child.ui !is BasicPopupMenuSeparatorUI) child.setUI(BasicPopupMenuSeparatorUI())
            child.foreground = style.outline
            child.background = style.popupBackground
        }
    }
    if (child is JMenuItem && child !is JMenu) showKeySymbol(child)
}

private fun JComponent.paint(background: AwtColor, foreground: AwtColor, font: Font) {
    this.background = background
    this.foreground = foreground
    this.font = font
}

private class ThemedMenuUI(private val style: MenuBarStyle) : BasicMenuUI() {
    override fun installDefaults() {
        super.installDefaults()
        selectionBackground = style.selectionBackground
        selectionForeground = style.selectionForeground
        disabledForeground = style.disabled
        acceleratorForeground = style.disabled
        acceleratorSelectionForeground = style.selectionForeground
        acceleratorFont = style.font
        arrowIcon = ArrowIcon(style.foreground)
    }
}

private class ThemedItemUI(private val style: MenuBarStyle) : BasicMenuItemUI() {
    override fun installDefaults() {
        super.installDefaults()
        selectionBackground = style.selectionBackground
        selectionForeground = style.selectionForeground
        disabledForeground = style.disabled
        acceleratorForeground = style.disabled
        acceleratorSelectionForeground = style.selectionForeground
        acceleratorFont = style.font
        checkIcon = null
    }
}

private class ThemedRadioItemUI(private val style: MenuBarStyle) : BasicRadioButtonMenuItemUI() {
    override fun installDefaults() {
        super.installDefaults()
        selectionBackground = style.selectionBackground
        selectionForeground = style.selectionForeground
        disabledForeground = style.disabled
        acceleratorForeground = style.disabled
        acceleratorSelectionForeground = style.selectionForeground
        acceleratorFont = style.font
        checkIcon = MarkIcon(style.font.size, round = true)
    }
}

private class ThemedCheckItemUI(private val style: MenuBarStyle) : BasicCheckBoxMenuItemUI() {
    override fun installDefaults() {
        super.installDefaults()
        selectionBackground = style.selectionBackground
        selectionForeground = style.selectionForeground
        disabledForeground = style.disabled
        acceleratorForeground = style.disabled
        acceleratorSelectionForeground = style.selectionForeground
        acceleratorFont = style.font
        checkIcon = MarkIcon(style.font.size, round = false)
    }
}

/**
 * A selected radio or check item's mark, in the item's own text colour so it reads on the theme. The
 * look and feel's icons paint in colours of their own, which vanish on a dark theme.
 */
private class MarkIcon(private val size: Int, private val round: Boolean) : Icon {
    override fun getIconWidth(): Int = size
    override fun getIconHeight(): Int = size

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        val selected = (c as? JMenuItem)?.isSelected == true
        if (!selected) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = c.foreground
            val inset = size / MARK_INSET_DIVISOR
            val mark = size - 2 * inset
            if (round) {
                g2.fillOval(x + inset, y + inset, mark, mark)
            } else {
                g2.fillRect(x + inset, y + inset, mark, mark)
            }
        } finally {
            g2.dispose()
        }
    }
}

/** A submenu's arrow, drawn in the text colour. */
private class ArrowIcon(private val color: AwtColor) : Icon {
    override fun getIconWidth(): Int = ARROW_SIZE
    override fun getIconHeight(): Int = ARROW_SIZE

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            val half = ARROW_SIZE / 2
            val left = x + ARROW_INSET
            g2.fillPolygon(
                intArrayOf(left, x + ARROW_SIZE - ARROW_INSET, left),
                intArrayOf(y + 1, y + half, y + ARROW_SIZE - 1),
                TRIANGLE_POINTS,
            )
        } finally {
            g2.dispose()
        }
    }
}

private const val MARK_INSET_DIVISOR = 3
private const val ARROW_SIZE = 8
private const val ARROW_INSET = 2
private const val TRIANGLE_POINTS = 3
