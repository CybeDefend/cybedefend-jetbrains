// File: src/main/kotlin/com/cybedefend/toolwindow/chatbot/ChatBotPanel.kt
package com.cybedefend.toolwindow.chatbot

import AddMessageConversationRequestDto
import StartConversationRequestDto
import com.cybedefend.services.ApiService
import com.cybedefend.services.ChatBotService
import com.cybedefend.services.scan.ScanStateService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Panel Swing for AI chat with optional vulnerability context.
 */
class ChatBotPanel(
    private val project: Project,
    apiService: ApiService,
    private val projectId: String
) : JPanel(BorderLayout(8, 8)) {

    private val chatService = ChatBotService(apiService)

    // 1) Combo for selecting vulnerability context
    private val vulnModel = DefaultComboBoxModel<VulnerabilityEntry>()
    private val vulnCombo = com.intellij.openapi.ui.ComboBox(vulnModel)

    // 2) Message container
    private val messagesContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(4)
    }
    private val scrollPane = JBScrollPane(messagesContainer)

    // 3) Input area and buttons
    private val inputArea = JTextArea(3, 40).apply { lineWrap = true; wrapStyleWord = true }
    private val sendButton = JButton("Envoyer")
    private val resetButton = JButton("Nouveau")

    // Conversation state
    private var conversationId: String? = null
    private var streamingLabel: JLabel? = null
    private val streamBuffer = StringBuilder()

    init {
        // North: vulnerability combo + reset
        val north = JPanel(BorderLayout(4, 4)).apply {
            add(vulnCombo, BorderLayout.CENTER)
            add(resetButton, BorderLayout.EAST)
        }
        add(north, BorderLayout.NORTH)

        // Center: messages
        add(scrollPane, BorderLayout.CENTER)

        // South: input + send
        val south = JPanel(BorderLayout(4, 4)).apply {
            add(JBScrollPane(inputArea), BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }
        add(south, BorderLayout.SOUTH)

        border = JBUI.Borders.empty(8)

        // Button setups
        sendButton.isEnabled = false
        inputArea.document.addDocumentListener(SimpleDocumentListener {
            sendButton.isEnabled = inputArea.text.trim().isNotEmpty()
        })
        sendButton.addActionListener { onSendMessage() }
        resetButton.addActionListener { onResetConversation() }

        // Load and refresh vulnerabilities
        loadVulnerabilities()
        ScanStateService.getInstance(project).addListener {
            ApplicationManager.getApplication().invokeLater { loadVulnerabilities() }
        }
    }

    /** Populate the vulnerability combo from ScanStateService data. */
    private fun loadVulnerabilities() {
        vulnModel.removeAllElements()
        vulnModel.addElement(VulnerabilityEntry(null, null, "Aucune vulnérabilité"))
        val state = ScanStateService.getInstance(project)
        listOfNotNull(state.sastResults, state.iacResults, state.scaResults)
            .flatMap { it.vulnerabilities }
            .forEach { v ->
                val name = v.vulnerability.name.takeIf { !it.isNullOrBlank() } ?: v.id
                val path = v.path
                vulnModel.addElement(
                    VulnerabilityEntry(
                        v.id,
                        v.vulnerability.vulnerabilityType,
                        "$name (${path.substringAfterLast('/')})"
                    )
                )
            }
        vulnCombo.selectedIndex = 0
    }

    /** Handle send button: start/continue conversation, then SSE stream. */
    private fun onSendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) return

        sendButton.isEnabled = false
        inputArea.isEditable = false
        appendMessage("user", text)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val convId = if (conversationId == null) {
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

                // Prepare streaming placeholder
                ApplicationManager.getApplication().invokeLater { prepareStreamingLabel() }

                val isContinuation = conversationId != null

                // Stream SSE deltas
                chatService.streamConversation(
                    projectId,
                    convId,
                    if (isContinuation) text else null,
                    onDelta = { delta -> ApplicationManager.getApplication().invokeLater { updateStreaming(delta) } },
                    onComplete = { /* you can signal fin de stream ici */ },
                    onError = { err ->
                        ApplicationManager.getApplication()
                            .invokeLater { appendMessage("error", err.message ?: "Stream error") }
                    }
                )
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater { appendMessage("error", t.message ?: "Erreur") }
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    inputArea.text = ""
                    inputArea.isEditable = true
                }
            }
        }
    }

    /** Add an empty label to show incoming stream. */
    private fun prepareStreamingLabel() {
        streamBuffer.clear()
        streamingLabel = JLabel().apply { border = JBUI.Borders.empty(4) }
        messagesContainer.add(streamingLabel)
        messagesContainer.revalidate()
    }

    /** Append each delta chunk to the streaming label. */
    private fun updateStreaming(delta: String) {
        streamBuffer.append(delta)
        streamingLabel?.text = "<html>${streamBuffer.toString().replace("\n", "<br/>")}</html>"
        SwingUtilities.invokeLater {
            scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
        }
    }

    /** Append a finished message bubble. */
    private fun appendMessage(role: String, content: String) {
        val safe = content.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>")
        val lbl = JLabel("<html><b>${role.replaceFirstChar { it.uppercase() }}:</b> $safe</html>").apply {
            border = JBUI.Borders.empty(4)
        }
        messagesContainer.add(lbl)
        messagesContainer.revalidate()
        SwingUtilities.invokeLater {
            scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
        }
    }

    /** Reset conversation state and clear UI. */
    private fun onResetConversation() {
        conversationId = null
        messagesContainer.removeAll()
        messagesContainer.revalidate()
        messagesContainer.repaint()
    }

    /** Data class for combo entries. */
    private data class VulnerabilityEntry(val id: String?, val type: String?, val label: String) {
        override fun toString() = label
    }

    /** Simplified DocumentListener to toggle send button. */
    private fun interface SimpleDocumentListener : DocumentListener {
        fun onChange()
        override fun insertUpdate(e: DocumentEvent) = onChange()
        override fun removeUpdate(e: DocumentEvent) = onChange()
        override fun changedUpdate(e: DocumentEvent) = onChange()
    }
}
