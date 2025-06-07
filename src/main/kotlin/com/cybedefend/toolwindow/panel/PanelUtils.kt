// Fichier : src/main/kotlin/com/cybedefend/toolwindow/panel/PanelUtils.kt
package com.cybedefend.toolwindow.panel

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.Color
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

// --- GESTION CENTRALISÉE DE LA SÉVÉRITÉ ---
data class SeverityInfo(val letter: String, val color: Color)

val severityMap = mapOf(
    "CRITICAL" to SeverityInfo("C", JBColor.decode("#9C27B0")),
    "HIGH" to SeverityInfo("H", JBColor.decode("#E53E3E")),
    "MEDIUM" to SeverityInfo("M", JBColor.decode("#DD6B20")),
    "LOW" to SeverityInfo("L", JBColor.decode("#3182CE")),
    "INFO" to SeverityInfo("I", JBColor.GRAY),
    "UNKNOWN" to SeverityInfo("?", JBColor.GRAY)
)

/**
 * Crée un panneau qui convertit et affiche correctement du Markdown.
 * Gère la police, les couleurs du thème et le retour à la ligne.
 */
fun createMarkdownPane(markdown: String): JComponent {
    val flavour = CommonMarkFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
    val html = HtmlGenerator(markdown, parsedTree, flavour).generateHtml()

    val ss = StyleSheet()
    val font = UIUtil.getLabelFont()
    val textColor = ColorUtil.toHtmlColor(UIUtil.getLabelForeground())
    val codeBlockBackground = ColorUtil.toHtmlColor(UIUtil.getPanelBackground().brighter())

    ss.addRule("body { font-family: ${font.family}; font-size: ${font.size}pt; color: $textColor; word-wrap: break-word; }")
    ss.addRule("pre { background-color: $codeBlockBackground; padding: 8px; border-radius: 4px; white-space: pre-wrap; }")
    ss.addRule("code { font-family: Monospaced; }")

    val editorKit = HTMLEditorKit()
    editorKit.styleSheet = ss

    val editorPane = JEditorPane().apply {
        this.editorKit = editorKit
        contentType = "text/html"
        text = "<html><body>$html</body></html>"
        isEditable = false
        background = UIUtil.getPanelBackground()
        border = null
        isOpaque = true
    }
    return editorPane
}

/**
 * Un JPanel qui implémente l'interface Scrollable pour forcer le retour à la ligne vertical.
 * En retournant 'true' pour 'getScrollableTracksViewportWidth', il indique au JScrollPane
 * parent de ne jamais autoriser le défilement horizontal.
 */
class ScrollablePanel(layout: LayoutManager) : JPanel(layout), Scrollable {
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 16
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 16
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
}