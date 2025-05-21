package com.cybedefend.actions

import com.cybedefend.services.ApiService
import com.cybedefend.services.AuthService
import com.cybedefend.services.ScanStateService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StartScanAction : AnAction("Start CybeDefend Scan") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        /* lancer hors-EDT pour éviter tout blocage */
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
