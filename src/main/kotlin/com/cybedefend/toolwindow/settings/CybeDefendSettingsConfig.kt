package com.cybedefend.toolwindow.settings

import com.cybedefend.services.ApiRegion
import com.cybedefend.services.ApiService
import com.cybedefend.services.AuthService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking

class CybeDefendSettingsConfig : SearchableConfigurable {

    private val apiField = JPasswordField()
    private val idField = JTextField()
    private lateinit var regionCombo: ComboBox<ApiRegion>

    /* --- helpers ------------------------------------------------------------ */

    private val activeProject: Project?
        get() = ProjectManager.getInstance().openProjects.firstOrNull()

    private fun auth() = activeProject?.let { AuthService.getInstance(it) }

    /* --- Configurable API --------------------------------------------------- */

    override fun getId() = "cybedefend.settings"
    override fun getDisplayName() = "CybeDefend"

    override fun createComponent(): JComponent = panel {
        apiField.columns = 30
        idField.columns = 20

        group("Global") {
            row("API Key:") {
                cell(apiField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
            row("API Region:") {
                val cell = comboBox(ApiRegion.values().toList())
                    .align(AlignX.FILL)
                    .resizableColumn()
                regionCombo = cell.component
            }
        }

        group("Project (current window)") {
            row("Project ID:") {
                cell(idField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
        }

        // === NEW: Maintenance actions =======================================
        group("Maintenance") {
            row {
                button("Reset credentials") {
                    auth()?.let { a ->
                        a.resetCredentials()
                        Messages.showInfoMessage(
                            activeProject,
                            "Credentials cleared for this workspace (API key, Project/Org IDs).",
                            "CybeDefend"
                        )
                        // refresh fields
                        reset()
                    } ?: run {
                        Messages.showErrorDialog(
                            "No active project found.",
                            "CybeDefend"
                        )
                    }
                }

                val reconfigInProgress = AtomicBoolean(false)

                button("Reset & Reconfigure…") {
                    val project = activeProject
                    val a = auth()
                    if (project == null || a == null) {
                        Messages.showErrorDialog(
                            "No active project found.",
                            "CybeDefend"
                        )
                        return@button
                    }
                    if (!reconfigInProgress.compareAndSet(false, true)) return@button

                    ProgressManager.getInstance().run(object :
                        Task.Backgroundable(project, "Reconfiguring CybeDefend", false) {
                        override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                            indicator.isIndeterminate = true
                            try {
                                // 1) reset credentials (clears API key + IDs)
                                a.resetCredentials()

                                // 2) run the existing setup flow (suspending)
                                runBlocking {
                                    val api = ApiService(a)
                                    a.ensureProjectConfigurationIsSet(api)
                                }

                                // 3) refresh the Settings UI on EDT
                                SwingUtilities.invokeLater {
                                    reset()
                                    Messages.showInfoMessage(
                                        project,
                                        "Reconfiguration complete.",
                                        "CybeDefend"
                                    )
                                }
                            } catch (t: Throwable) {
                                SwingUtilities.invokeLater {
                                    Messages.showErrorDialog(
                                        project,
                                        "Reconfiguration failed: ${t.message}",
                                        "CybeDefend"
                                    )
                                }
                            } finally {
                                reconfigInProgress.set(false)
                            }
                        }
                    })
                }
            }
        }
        // =====================================================================
    }

    override fun isModified(): Boolean {
        val a = auth() ?: return false
        val apiChanged = String(apiField.password) != (a.getApiKey() ?: "")
        val projectChanged = idField.text.trim() != (a.getWorkspaceProjectId() ?: "")
        val selectedRegion = (regionCombo.selectedItem as? ApiRegion) ?: ApiRegion.US
        val regionChanged = selectedRegion != a.getWorkspaceApiRegion()
        return apiChanged || projectChanged || regionChanged
    }

    override fun apply() {
        auth()?.let { service ->
            // 1) API Key
            val enteredKey = String(apiField.password).trim()
            if (enteredKey.isNotEmpty()) {
                service.setApiKey(enteredKey)
            }

            // 2) Project ID
            service.storeWorkspaceProjectId(idField.text.trim())

            // 3) API Region
            val selectedRegion = (regionCombo.selectedItem as? ApiRegion) ?: ApiRegion.US
            service.storeWorkspaceApiRegion(selectedRegion)
        }
    }

    override fun reset() {
        auth()?.let { a ->
            apiField.text = a.getApiKey().orEmpty()
            idField.text = a.getWorkspaceProjectId().orEmpty()
            regionCombo.selectedItem = a.getWorkspaceApiRegion()
        }
    }
}
