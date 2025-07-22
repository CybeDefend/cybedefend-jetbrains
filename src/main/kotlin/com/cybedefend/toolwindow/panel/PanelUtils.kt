// Fichier : src/main/kotlin/com/cybedefend/toolwindow/panel/PanelUtils.kt
package com.cybedefend.toolwindow.panel

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

// --- GESTION CENTRALISÉE DE LA SÉVÉRITÉ ---
data class SeverityInfo(val letter: String, val color: Color)

val severityMap =
        mapOf(
                "CRITICAL" to SeverityInfo("C", JBColor.decode("#9C27B0")),
                "HIGH" to SeverityInfo("H", JBColor.decode("#E53E3E")),
                "MEDIUM" to SeverityInfo("M", JBColor.decode("#DD6B20")),
                "LOW" to SeverityInfo("L", JBColor.decode("#3182CE")),
                "INFO" to SeverityInfo("I", JBColor.GRAY),
                "UNKNOWN" to SeverityInfo("?", JBColor.GRAY)
        )

/**
 * Crée un panneau qui convertit et affiche correctement du Markdown. Gère la police, les couleurs
 * du thème et le retour à la ligne. Version robuste qui évite les erreurs CSS lors du parsing.
 */
fun createMarkdownPane(markdown: String): JComponent {
    // Convertir le markdown en HTML basique
    val flavour = CommonMarkFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
    val html = HtmlGenerator(markdown, parsedTree, flavour).generateHtml()

    // Nettoyer et simplifier le HTML pour JBLabel
    val cleanHtml =
            html.replace("<h1>", "<b><font size='+2'>")
                    .replace("</h1>", "</font></b><br>")
                    .replace("<h2>", "<b><font size='+1'>")
                    .replace("</h2>", "</font></b><br>")
                    .replace("<h3>", "<b>")
                    .replace("</h3>", "</b><br>")
                    .replace("<strong>", "<b>")
                    .replace("</strong>", "</b>")
                    .replace("<em>", "<i>")
                    .replace("</em>", "</i>")
                    .replace("<pre>", "<code>")
                    .replace("</pre>", "</code>")
                    .replace("\n", "<br>")

    // Créer un JBLabel avec HTML et largeur fixe
    val label =
            JBLabel().apply {
                text =
                        "<html><div style='width: 540px; word-wrap: break-word;'>$cleanHtml</div></html>"
                background = UIUtil.getPanelBackground()
                foreground = UIUtil.getLabelForeground()
                isOpaque = true
                border = JBUI.Borders.empty(12)

                preferredSize = Dimension(580, preferredSize.height.coerceAtLeast(50))
                minimumSize = Dimension(300, 50)
                maximumSize = Dimension(580, Int.MAX_VALUE)

                putClientProperty("html.disable", false)
            }

    // Créer un wrapper panel pour contrôler le layout
    val wrapper =
            object : JPanel(BorderLayout()) {
                override fun getPreferredSize(): Dimension {
                    val labelSize = label.preferredSize
                    return Dimension(580, labelSize.height.coerceAtLeast(50).coerceAtMost(800))
                }

                override fun getMinimumSize(): Dimension = Dimension(300, 50)
                override fun getMaximumSize(): Dimension = Dimension(580, Int.MAX_VALUE)
            }

    wrapper.add(label, BorderLayout.CENTER)
    wrapper.isOpaque = false

    // Forcer le recalcul de la taille après que le composant soit ajouté
    javax.swing.SwingUtilities.invokeLater {
        val actualHeight = label.preferredSize.height.coerceAtLeast(50).coerceAtMost(800)
        label.preferredSize = Dimension(580, actualHeight)
        wrapper.preferredSize = Dimension(580, actualHeight)

        wrapper.revalidate()
        wrapper.repaint()
    }

    return wrapper
}

/**
 * Sanitizes CSS values to prevent parsing errors. Removes or escapes potentially problematic
 * characters.
 */
private fun sanitizeCssValue(value: String): String {
    return value.replace("\"", "'") // Replace double quotes with single quotes
            .replace(";", "") // Remove semicolons that could break CSS
            .trim()
            .let { if (it.contains(" ")) "\"$it\"" else it } // Quote multi-word values
}

/** Safely converts a Color to HTML color string. Returns a fallback color if conversion fails. */
private fun getSafeHtmlColor(color: Color): String {
    return try {
        ColorUtil.toHtmlColor(color)
    } catch (e: Exception) {
        // Fallback to manual RGB conversion if ColorUtil fails
        "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }
}

/**
 * Un JPanel qui implémente l'interface Scrollable pour forcer le retour à la ligne vertical. En
 * retournant 'true' pour 'getScrollableTracksViewportWidth', il indique au JScrollPane parent de ne
 * jamais autoriser le défilement horizontal.
 */
class ScrollablePanel(layout: LayoutManager) : JPanel(layout), Scrollable {
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int
    ): Int = 16
    override fun getScrollableBlockIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int
    ): Int = 16
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
}
