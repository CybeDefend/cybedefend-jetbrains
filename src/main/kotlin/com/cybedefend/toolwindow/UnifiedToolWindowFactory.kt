package com.cybedefend.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.cybedefend.services.*
import com.cybedefend.settings.CybeDefendSettingsConfig
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.IconManager
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

/**
 * Swing-native dashboard with:
 *  • vertical sidebar (green ▶︎) to start scans,
 *  • per-tab severity filter drop-downs,
 *  • summary label showing total vulns + last scan state.
 */
class UnifiedToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val scanState = ScanStateService.getInstance(project)

        /* ---------- 1. models ---------- */
        val sastModel = VulnerabilityTableModel()
        val iacModel = VulnerabilityTableModel()
        val scaModel = VulnerabilityTableModel()

        /* ---------- 2. table builder with filter ---------- */
        fun buildTab(title: String, model: VulnerabilityTableModel): JComponent {
            val table = com.intellij.ui.table.JBTable(model).apply {
                autoCreateRowSorter = true
                setShowGrid(false)
                columnModel.getColumn(0).apply {
                    minWidth = 80
                    maxWidth = 100
                    preferredWidth = 100
                }
            }

            // severity filter
            val severities = arrayOf("ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO")
            val combo = JComboBox(severities)
            combo.addActionListener {
                val sel = combo.selectedItem as String
                val sorter = table.rowSorter as? TableRowSorter<*>
                    ?: TableRowSorter(table.model).also { table.rowSorter = it }
                sorter.rowFilter = if (sel == "ALL") {
                    null
                } else {
                    object : RowFilter<TableModel, Int>() {
                        override fun include(entry: Entry<out TableModel, out Int>): Boolean {
                            val model = entry.model as VulnerabilityTableModel
                            return model.getSeverityAt(entry.identifier) == sel
                        }
                    }
                }
            }

            return JPanel(BorderLayout()).apply {
                add(combo, BorderLayout.NORTH)
                add(JBScrollPane(table), BorderLayout.CENTER)
            }.also { combo.selectedIndex = 0 }.let { it.also { } }
        }

        val tabs = com.intellij.ui.components.JBTabbedPane().apply {
            addTab("Static Analysis", buildTab("SAST", sastModel))
            addTab("Infrastructure as Code", buildTab("IaC", iacModel))
            addTab("Software Composition Analysis", buildTab("SCA", scaModel))
        }

        /* ---------- 3. summary label ---------- */
        val summary = JLabel("Ready").apply { border = JBUI.Borders.empty(4, 8) }

        /* ---------- 4. actions ---------- */
        val startScanAction = object : AnAction("Start Scan", "Run a CybeDefend scan", AllIcons.Actions.Execute) {
            private val loader = com.intellij.ui.AnimatedIcon.Default()

            override fun actionPerformed(e: AnActionEvent) {
                if (!scanState.isLoading) launchScanAsync(project)     // guard against double-click
            }

            override fun update(e: AnActionEvent) {
                val running = scanState.isLoading
                e.presentation.isEnabled = !running
                e.presentation.icon = if (running) loader else AllIcons.Actions.Execute
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }

        /** Red trash-can: wipe current results and reset the dashboard. */
        val clearResultsAction = object : AnAction(
            /* text         = */ "Clear Results",          // title shown in tooltip
            /* description  = */ "Reset the last scan results", // subtitle
            /* icon         = */ AllIcons.Actions.GC
        ) {
            override fun actionPerformed(e: AnActionEvent) = scanState.reset()

            override fun update(e: AnActionEvent) {
                val hasResults = scanState.sastResults != null ||
                        scanState.iacResults != null ||
                        scanState.scaResults != null
                e.presentation.isEnabled = hasResults && !scanState.isLoading
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }


        /** Cog icon: open the CybeDefend settings page. */
        val settingsAction = object : AnAction("Settings", "CybeDefend settings", AllIcons.General.Settings) {
            override fun actionPerformed(e: AnActionEvent) {
                ApplicationManager.getApplication().invokeLater {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, CybeDefendSettingsConfig::class.java)
                }
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }

        /* ---------- 5. vertical sidebar (left) ---------- */
        val leftGroup = DefaultActionGroup().apply {
            add(startScanAction)
            add(clearResultsAction)
            addSeparator()
            add(settingsAction)
        }

        val leftToolbar = ActionManager.getInstance()
            .createActionToolbar("CybeDefend.Left", leftGroup, /* horizontal = */ false).apply {
                targetComponent = null          // no specific context component
                orientation = SwingConstants.VERTICAL
            }

        /* ---------- 6. top toolbar (settings) ---------- */
        val dummyTable = com.intellij.ui.table.JBTable()          // ToolbarDecorator needs any component
        val topBar = ToolbarDecorator.createDecorator(dummyTable)
            .disableAddAction().disableRemoveAction().disableUpDownActions()
            .addExtraActions(settingsAction)                      // non-deprecated overload
            .createPanel().apply { layout = BorderLayout() }

        /* Update toolbars whenever ScanState changes so loader + enablement stay in sync */
        scanState.addListener {
            ApplicationManager.getApplication().invokeLater {
                leftToolbar.updateActionsImmediately()
                topBar.repaint()
            }
        }


        /* ---------- 7. root panel ---------- */
        val root = JPanel(BorderLayout()).apply {
            add(leftToolbar.component, BorderLayout.WEST)
            add(topBar, BorderLayout.NORTH)
            add(tabs, BorderLayout.CENTER)
            add(summary, BorderLayout.SOUTH)
        }

        /* ---------- 8. register ---------- */
        val content = ContentFactory.getInstance()
            .createContent(root, "CybeDefend", false)
        toolWindow.contentManager.addContent(content)

        /* ---------- 9. refresh logic ---------- */
        fun refresh() {
            sastModel.setData(scanState.sastResults?.vulnerabilities)
            iacModel.setData(scanState.iacResults?.vulnerabilities)
            scaModel.setData(scanState.scaResults?.vulnerabilities)

            val total = listOf(
                scanState.sastResults?.total ?: 0,
                scanState.iacResults?.total ?: 0,
                scanState.scaResults?.total ?: 0
            ).sum()

            summary.text = when {
                scanState.isLoading -> "Scanning…"
                else -> "Total vulnerabilities: $total • Last scan state: ${scanState.error ?: scanState.scaResults?.scanProjectInfo?.state ?: "N/A"}"
            }
        }
        refresh()
        scanState.addListener { ApplicationManager.getApplication().invokeLater { refresh() } }
    }

    /* ---------- helpers ---------- */

    private fun launchScanAsync(project: Project) {
        CoroutineScope(Dispatchers.Default).launch {
            val auth = AuthService.getInstance(project)
            val api = ApiService(auth)
            auth.ensureProjectConfigurationIsSet(api)?.let { cfg ->
                ScanStateService.getInstance(project)
                    .startScan(cfg.projectId, cfg.workspaceRoot)
            }
        }
    }
}
