package com.cybedefend.toolwindow.panel

import com.cybedefend.services.ApiService
import com.cybedefend.services.AuthService
import com.cybedefend.services.scan.ScanStateService
import com.cybedefend.toolwindow.settings.CybeDefendSettingsConfig
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.*
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import toUnified

/**
 * Swing-native dashboard with: • vertical sidebar (green ▶︎) to start scans, • per-tab severity
 * filter drop-downs, • summary label showing total vulns + last scan state.
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
            val table =
                    JBTable(model).apply {
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
                val sorter =
                        table.rowSorter as? TableRowSorter<*>
                                ?: TableRowSorter(table.model).also { table.rowSorter = it }
                sorter.rowFilter =
                        if (sel == "ALL") {
                            null
                        } else {
                            object : RowFilter<TableModel, Int>() {
                                override fun include(
                                        entry: Entry<out TableModel, out Int>
                                ): Boolean {
                                    val model = entry.model as VulnerabilityTableModel
                                    return model.getSeverityAt(entry.identifier) == sel
                                }
                            }
                        }
            }

            return JPanel(BorderLayout())
                    .apply {
                        add(combo, BorderLayout.NORTH)
                        add(JBScrollPane(table), BorderLayout.CENTER)
                    }
                    .also { combo.selectedIndex = 0 }
                    .let { it.also {} }
        }

        val tabs =
                JBTabbedPane().apply {
                    addTab("Static Analysis", buildTab("SAST", sastModel))
                    addTab("Infrastructure as Code", buildTab("IaC", iacModel))
                    addTab("Software Composition Analysis", buildTab("SCA", scaModel))
                }

        // panneau où s’afficheront les détails de la vulnérabilité
        val detailsPanel = DetailsContainerPanel(project)

        // pour chaque onglet, on récupère le JBTable et on ajoute un listener
        for (i in 0 until tabs.tabCount) {
            val panel = tabs.getComponentAt(i) as JPanel
            val scroll = panel.getComponent(1) as JBScrollPane
            val table = scroll.viewport.view as JBTable
            table.addMouseListener(
                    object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.clickCount == 1) {
                                val row = table.rowAtPoint(e.point)
                                if (row >= 0) {
                                    val modelRow = table.convertRowIndexToModel(row)
                                    val vulnId =
                                            (table.model as VulnerabilityTableModel).getIdAt(
                                                    modelRow
                                            )
                                    val scanType =
                                            when (i) {
                                                0 -> "sast"
                                                1 -> "iac"
                                                else -> "sca"
                                            }
                                    CoroutineScope(Dispatchers.IO).launch {
                                        // 1) Récupère/valide la config (API key + projectId)
                                        val auth = AuthService.getInstance(project)
                                        val cfg =
                                                auth.ensureProjectConfigurationIsSet(
                                                        ApiService(auth)
                                                )
                                        if (cfg == null) return@launch

                                        // 2) Appel sécurisé avec projectId issu de cfg
                                        val api = ApiService(auth)
                                        val dto =
                                                api.getVulnerabilityDetails(
                                                        cfg.projectId,
                                                        vulnId,
                                                        scanType
                                                )

                                        // 3) Update UI dans l’EDT
                                        ApplicationManager.getApplication().invokeLater {
                                            // Le 'detailsPanel' est maintenant notre
                                            // 'DetailsContainerPanel'
                                            if (dto.sca != null) {
                                                // Appelle la méthode maintenant existante !
                                                detailsPanel.showScaDetails(dto.sca)
                                            } else {
                                                // Appelle l'autre méthode maintenant existante !
                                                detailsPanel.showSastIacDetails(dto)

                                                val baseVulnerability =
                                                        if (dto.sast != null) dto.sast.toUnified()
                                                        else dto.iac!!.toUnified()

                                                // Logique pour ouvrir et surligner le fichier (ne
                                                // s'exécute que pour SAST/IAC)
                                                val relativePath = baseVulnerability.path
                                                var vf =
                                                        VfsUtil.findFile(
                                                                Paths.get(
                                                                        project.basePath!!,
                                                                        relativePath
                                                                ),
                                                                true
                                                        )

                                                vf?.let { file ->
                                                    val descriptor =
                                                            OpenFileDescriptor(
                                                                    project,
                                                                    file,
                                                                    baseVulnerability
                                                                            .vulnerableStartLine -
                                                                            1,
                                                                    0
                                                            )
                                                    val editor =
                                                            FileEditorManager.getInstance(project)
                                                                    .openTextEditor(
                                                                            descriptor,
                                                                            true
                                                                    )
                                                    editor?.markupModel?.addLineHighlighter(
                                                            baseVulnerability.vulnerableStartLine -
                                                                    1,
                                                            HighlighterLayer.ERROR,
                                                            TextAttributes(
                                                                    null,
                                                                    null,
                                                                    JBColor.RED,
                                                                    EffectType.LINE_UNDERSCORE,
                                                                    Font.PLAIN
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            )
        }

        val loader = AnimatedIcon.Default()
        /* ---------- 3. summary label ---------- */
        val summary = JLabel("Ready").apply { border = JBUI.Borders.empty(4, 8) }

        /* ---------- 4. actions ---------- */
        val startScanAction =
                object : AnAction("Start Scan", "Run a CybeDefend scan", AllIcons.Actions.Execute) {
                    override fun actionPerformed(e: AnActionEvent) {
                        if (!scanState.isLoading)
                                launchScanAsync(project) // guard against double-click
                    }

                    override fun update(e: AnActionEvent) {
                        val running = scanState.isLoading
                        e.presentation.isEnabled = !running
                        e.presentation.icon = AllIcons.Actions.Execute
                    }

                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                }

        /** Red trash-can: wipe current results and reset the dashboard. */
        val clearResultsAction =
                object :
                        AnAction(
                                /* text         = */ "Clear Results", // title shown in tooltip
                                /* description  = */ "Reset the last scan results", // subtitle
                                /* icon         = */ AllIcons.Actions.GC
                        ) {
                    override fun actionPerformed(e: AnActionEvent) = scanState.reset()

                    override fun update(e: AnActionEvent) {
                        val hasResults =
                                scanState.sastResults != null ||
                                        scanState.iacResults != null ||
                                        scanState.scaResults != null
                        e.presentation.isEnabled = hasResults && !scanState.isLoading
                    }

                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                }

        /** Cog icon: open the CybeDefend settings page. */
        val settingsAction =
                object : AnAction("Settings", "CybeDefend settings", AllIcons.General.Settings) {
                    override fun actionPerformed(e: AnActionEvent) {
                        ApplicationManager.getApplication().invokeLater {
                            ShowSettingsUtil.getInstance()
                                    .showSettingsDialog(
                                            project,
                                            CybeDefendSettingsConfig::class.java
                                    )
                        }
                    }

                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                }

        /* ---------- 5. vertical sidebar (left) ---------- */
        val leftGroup =
                DefaultActionGroup().apply {
                    add(startScanAction)
                    add(clearResultsAction)
                    addSeparator()
                    add(settingsAction)
                }

        val leftToolbar =
                ActionManager.getInstance()
                        .createActionToolbar("CybeDefend.Left", leftGroup, /* horizontal = */ false)
                        .apply {
                            targetComponent = null // no specific context component
                            orientation = SwingConstants.VERTICAL
                        }

        /* ---------- 6. top toolbar (settings) ---------- */
        val dummyTable = JBTable() // ToolbarDecorator needs any component
        val topBar =
                ToolbarDecorator.createDecorator(dummyTable)
                        .disableAddAction()
                        .disableRemoveAction()
                        .disableUpDownActions()
                        .addExtraActions(settingsAction) // non-deprecated overload
                        .createPanel()
                        .apply { layout = BorderLayout() }

        /* Update toolbars whenever ScanState changes so loader + enablement stay in sync */
        scanState.addListener {
            ApplicationManager.getApplication().invokeLater {
                leftToolbar.updateActionsAsync()
                topBar.repaint()
            }
        }

        /* ---------- 7. root panel ---------- */
        val rightPanelWrapper =
                JPanel(BorderLayout()).apply {
                    add(JBScrollPane(detailsPanel), BorderLayout.CENTER)
                }

        val split =
                JSplitPane(
                                JSplitPane.HORIZONTAL_SPLIT,
                                JPanel(BorderLayout()).apply {
                                    // ... (la partie gauche ne change pas)
                                    add(leftToolbar.component, BorderLayout.WEST)
                                    add(topBar, BorderLayout.NORTH)
                                    add(tabs, BorderLayout.CENTER)
                                    add(summary, BorderLayout.SOUTH)
                                },
                                rightPanelWrapper // <-- On utilise notre nouveau conteneur ici.
                        )
                        .apply {
                            dividerLocation = 600
                            dividerSize = JBUI.scale(4)
                            border = null
                        }
        val root = JPanel(BorderLayout()).apply { add(split, BorderLayout.CENTER) }

        /* ---------- 8. register ---------- */
        val content = ContentFactory.getInstance().createContent(root, "CybeDefend", false)
        toolWindow.contentManager.addContent(content)

        /* ---------- 9. refresh logic ---------- */
        fun refresh() {
            // Les 'sastResults' sont maintenant directement les listes
            sastModel.setData(scanState.sastResults)
            iacModel.setData(scanState.iacResults)
            scaModel.setData(scanState.scaResults)

            // On utilise les nouvelles propriétés de ScanStateService
            val total = scanState.totalVulnerabilities

            summary.icon = if (scanState.isLoading) loader else null
            summary.text =
                    if (scanState.isLoading) {
                        "Scanning…"
                    } else {
                        "Total vulnerabilities: $total • Last scan state: ${scanState.error ?: scanState.lastScanState}"
                    }
        }
        refresh()
        scanState.addListener { ApplicationManager.getApplication().invokeLater { refresh() } }
    }

    /* ---------- helpers ---------- */

    private fun launchScanAsync(project: Project) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val auth = AuthService.getInstance(project)
                val api = ApiService(auth)

                auth.ensureProjectConfigurationIsSet(api)?.let { cfg ->
                    ScanStateService.getInstance(project)
                            .startScan(cfg.projectId, cfg.workspaceRoot)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                            project,
                            "Failed to configure project: ${e.message}",
                            "Configuration Error"
                    )
                }
                com.intellij.openapi.diagnostic.Logger.getInstance(
                                UnifiedToolWindowFactory::class.java
                        )
                        .warn("Configuration failed", e)
            }
        }
    }
}
