// File: src/main/kotlin/com/cybedefend/toolwindow/chatbot/ChatBotPanel.kt
package com.cybedefend.toolwindow.chatbot

import AddMessageConversationRequestDto
import StartConversationRequestDto
import com.cybedefend.services.ApiService
import com.cybedefend.services.ChatBotService
import com.cybedefend.services.scan.ScanStateService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ChatBotPanel(
    private val project: Project,
    apiService: ApiService,
    private val projectId: String
) : JPanel(BorderLayout(8, 8)) {

    private val chatService = ChatBotService(apiService)

    // Combo vuln
    private val vulnModel = DefaultComboBoxModel<VulnerabilityEntry>()
    private val vulnCombo = com.intellij.openapi.ui.ComboBox(vulnModel)

    // Container messages
    private val messagesContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(4)
    }
    private val scrollPane = JBScrollPane(messagesContainer).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    // Input + boutons
    private val inputArea = JTextArea(3, 40).apply {
        lineWrap = true; wrapStyleWord = true
    }
    private val sendButton = JButton("Send")
    private val resetButton = JButton("New")

    private var conversationId: String? = null
    private var streamBuffer = StringBuilder()

    init {
        // Nord
        val north = JPanel(BorderLayout(4, 4)).apply {
            add(vulnCombo, BorderLayout.CENTER)
            add(resetButton, BorderLayout.EAST)
        }
        add(north, BorderLayout.NORTH)

        // Centre
        add(scrollPane, BorderLayout.CENTER)

        // Sud
        val south = JPanel(BorderLayout(4, 4)).apply {
            add(JBScrollPane(inputArea), BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }
        add(south, BorderLayout.SOUTH)

        border = JBUI.Borders.empty(8)

        // Activation bouton
        sendButton.isEnabled = false
        inputArea.document.addDocumentListener(SimpleDocumentListener {
            sendButton.isEnabled = inputArea.text.trim().isNotEmpty()
        })

        // Envoi sur Entrée
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    if (sendButton.isEnabled) sendButton.doClick()
                }
            }
        })

        sendButton.addActionListener { onSendMessage() }
        resetButton.addActionListener { onResetConversation() }

        loadVulnerabilities()
        ScanStateService.getInstance(project).addListener {
            ApplicationManager.getApplication().invokeLater { loadVulnerabilities() }
        }
    }

    /** Simple Markdown → HTML (titres, gras, italique, code) */
    private fun markdownToHtml(md: String): String {
        // Échappement basique
        var t = md
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        // Titres
        t = t.replace(Regex("(?m)^(#{1,6})\\s*(.+)$")) {
            val lvl = it.groupValues[1].length.coerceIn(1, 6)
            "<h$lvl>${it.groupValues[2]}</h$lvl>"
        }
        // Gras
        t = t.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
        // Italique
        t = t.replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
        // Inline code
        t = t.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        // Fenced code
        t = t.replace(Regex("(?m)```[\\s\\S]*?```")) {
            "<pre>${it.value.removeSurrounding("```")}</pre>"
        }
        // Sauts de ligne
        return t.replace("\n", "<br/>")
    }

    /** Charge la combo vulnérabilités */
    private fun loadVulnerabilities() {
        vulnModel.removeAllElements()
        vulnModel.addElement(VulnerabilityEntry(null, null, "No vuln"))
        val state = ScanStateService.getInstance(project)
        listOfNotNull(state.sastResults, state.iacResults, state.scaResults)
            .flatMap { it.vulnerabilities }
            .forEach { v ->
                val name = v.vulnerability.name.takeIf { !it.isNullOrBlank() } ?: v.id
                val short = v.path.substringAfterLast('/')
                vulnModel.addElement(
                    VulnerabilityEntry(
                        v.id, v.vulnerability.vulnerabilityType, "$name ($short)"
                    )
                )
            }
        vulnCombo.selectedIndex = 0
    }

    /** Envoi + streaming */
    private fun onSendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) return

        // Efface immédiatement
        inputArea.text = ""
        inputArea.isEditable = false
        sendButton.isEnabled = false

        appendBubble("user", text)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isCont = conversationId != null
                val convId = if (!isCont) {
                    val sel = vulnCombo.selectedItem as VulnerabilityEntry
                    val req = StartConversationRequestDto(
                        projectId = projectId,
                        isVulnerabilityConversation = sel.id != null,
                        vulnerabilityId = sel.id,
                        vulnerabilityType = sel.type,
                        language = "en"
                    )
                    chatService.startConversation(req).also { conversationId = it }
                } else {
                    val req = AddMessageConversationRequestDto(
                        idConversation = conversationId!!,
                        message = text,
                        projectId = projectId
                    )
                    chatService.continueConversation(conversationId!!, req)
                }

                // Placeholder de stream
                ApplicationManager.getApplication().invokeLater {
                    streamBuffer.clear()
                    appendBubble("assistant", "") // vide pour commencer le flux
                }

                chatService.streamConversation(
                    projectId, convId,
                    if (isCont) text else null,
                    onDelta = { d ->
                        ApplicationManager.getApplication().invokeLater {
                            updateLastBubble(d)
                        }
                    },
                    onComplete = { /* rien */ },
                    onError = { err ->
                        ApplicationManager.getApplication().invokeLater {
                            appendBubble("error", err.message ?: "Stream error")
                        }
                    }
                )
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater {
                    appendBubble("error", t.message ?: "Erreur")
                }
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    inputArea.isEditable = true
                }
            }
        }
    }

    /** Ajoute une bulle complète (user / assistant / erreur) */
    private fun appendBubble(role: String, content: String) {
        val html = markdownToHtml(content)
        val pane = JEditorPane(
            "text/html",
            // un div avec padding et overflow caché pour un rendu « bulle »
            """
        <html>
          <body style="
            margin:0; padding:0;
            font-family: ${UIManager.getFont("Label.font").family};
            font-size: ${UIManager.getFont("Label.font").size}pt;
          ">
            <div style='
              white-space: pre-wrap;
              word-wrap: break-word;
              padding: 8px 12px;
            '>$html</div>
          </body>
        </html>
        """.trimIndent()
        ).apply {
            isEditable = false
            isOpaque = false
            // respecte les polices IntelliJ
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            // pour que la bulle se redimensionne en hauteur
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        // panel contenant la bulle, avec fond arrondi
        // À la place de votre ancien "panel = JPanel(BorderLayout()).apply { ... }"
        val bubble = object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val d = super.getPreferredSize()
                // largeur max 400px, hauteur naturelle
                return Dimension(d.width.coerceAtMost(400), d.height)
            }

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                // votre code de bulle arrondie (couleurs, etc.)
                val g2 = (g as Graphics2D).create()
                try {
                    val c = when (role) {
                        "user" -> JBColor(0x3a6ea5, 0x3a6ea5)
                        "assistant" -> JBColor(0x4a4a4a, 0x4a4a4a)
                        else -> JBColor.RED
                    }
                    g2.color = c
                    g2.fillRoundRect(0, 0, width, height, 16, 16)
                } finally {
                    g2.dispose()
                }
            }
        }.apply {
            isOpaque = false
            border = JBUI.Borders.empty(4)
            add(pane, BorderLayout.CENTER)
        }


        // wrapper pour aligner à droite/gauche
        val alignPanel = JPanel(FlowLayout(if (role == "user") FlowLayout.RIGHT else FlowLayout.LEFT)).apply {
            isOpaque = false
            add(bubble)
        }

        messagesContainer.add(alignPanel)
        messagesContainer.revalidate()
        SwingUtilities.invokeLater {
            scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
        }
    }


    /** Met à jour la dernière bulle (streaming) */
    private fun updateLastBubble(delta: String) {
        streamBuffer.append(delta)
        // Retire la dernière bulle (vide) et la remplace
        if (messagesContainer.componentCount > 0) {
            messagesContainer.remove(messagesContainer.componentCount - 1)
        }
        appendBubble("assistant", streamBuffer.toString())
    }

    private fun onResetConversation() {
        conversationId = null
        messagesContainer.removeAll()
        messagesContainer.revalidate()
        messagesContainer.repaint()
    }

    private data class VulnerabilityEntry(val id: String?, val type: String?, val label: String) {
        override fun toString() = label
    }

    private fun interface SimpleDocumentListener : DocumentListener {
        fun onChange()
        override fun insertUpdate(e: DocumentEvent) = onChange()
        override fun removeUpdate(e: DocumentEvent) = onChange()
        override fun changedUpdate(e: DocumentEvent) = onChange()
    }
}
