package com.cybedefend.settings

import com.cybedefend.services.AuthService
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField

class CybeDefendSettingsConfig : SearchableConfigurable {

    private val apiField = JPasswordField()
    private val idField = JTextField()

    /* --- helpers ------------------------------------------------------------ */

    private val activeProject
        get() = ProjectManager.getInstance().openProjects.firstOrNull()

    private fun auth() = activeProject?.let { AuthService.getInstance(it) }

    /* --- Configurable API --------------------------------------------------- */

    override fun getId() = "cybedefend.settings"
    override fun getDisplayName() = "CybeDefend"

    override fun createComponent(): JComponent = panel {
        // set a sane width so we don't get a horizontal scroll
        apiField.columns = 30
        idField.columns = 20

        group("Global") {
            row("API Key:") {
                cell(apiField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
        }
        group("Project (current window)") {
            row("Project ID:") {
                cell(idField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
        }
    }

    override fun isModified(): Boolean {
        val a = auth() ?: return false
        return String(apiField.password) != (a.getApiKey() ?: "") ||
                idField.text.trim() != (a.getWorkspaceProjectId() ?: "")
    }

    override fun apply() {
        auth()?.let { service ->
            // 1) API Key – write trimmed value so the header is always valid.
            val enteredKey = String(apiField.password).trim()
            if (enteredKey.isNotEmpty()) {
                service.setApiKey(enteredKey)
            }

            // 2) Project ID – update (blank means “unset on purpose”).
            service.storeWorkspaceProjectId(idField.text.trim())
        }
    }

    override fun reset() {
        auth()?.let { a ->
            apiField.text = a.getApiKey().orEmpty()
            idField.text = a.getWorkspaceProjectId().orEmpty()
        }
    }
}
