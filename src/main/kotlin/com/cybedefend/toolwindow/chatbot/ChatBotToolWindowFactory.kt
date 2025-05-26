package com.cybedefend.toolwindow.chatbot

import com.cybedefend.services.ApiService
import com.cybedefend.services.AuthService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Factory pour accrocher le ChatBot dans la ToolWindow “CybeDefendChat”.
 * S’assure d’abord de la config via AuthService, puis crée le panel.
 */
class ChatBotToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 1) S’assurer que API key + projectId sont configurés
        val auth = AuthService.getInstance(project)
        CoroutineScope(Dispatchers.Default).launch {
            val cfg = auth.ensureProjectConfigurationIsSet(ApiService(auth))
            if (cfg == null) return@launch

            // 2) Crée le service et le panel
            val apiService = ApiService(auth)
            val panel = ChatBotPanel(project, apiService, cfg.projectId)

            // 3) Ajout au ToolWindow (sur l’EDT)
            toolWindow.contentManager.run {
                val content = ContentFactory.getInstance()
                    .createContent(panel, "ChatBot AI", false)
                ApplicationManager.getApplication().invokeLater {
                    addContent(content)
                }
            }
        }
    }
}
