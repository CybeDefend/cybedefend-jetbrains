package com.cybedefend.toolwindow

import com.cybedefend.services.ApiService
import com.cybedefend.services.AuthService
import com.cybedefend.services.ScanStateService
import com.cybedefend.util.HtmlLoader
import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandler
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UnifiedToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        /* ----------------- 1. Browser ----------------- */
        val browser = JBCefBrowser()

        /* ----------------- 2. Intercept custom URI ----------------- */
        browser.jbCefClient.addRequestHandler(
            object : CefRequestHandlerAdapter() {          // 1. handler
                override fun onBeforeBrowse(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    userGesture: Boolean,
                    isRedirect: Boolean
                ): Boolean {
                    if (request.url == "cybedefend://startScan") {
                        launchScanAsync(project)
                        return true
                    }
                    return false
                }
            },
            browser.cefBrowser                              // 2. browser
        )

        /* ----------------- 3. Register tool-window ----------------- */
        val content = ContentFactory.getInstance()
            .createContent(browser.component, "CybeDefend", false)
        toolWindow.contentManager.addContent(content)

        /* ----------------- 4. Refresh HTML ----------------- */
        val scanState = ScanStateService.getInstance(project)
        val gson = Gson()

        val refreshHtml = {
            val sCount = scanState.sastResults?.total ?: 0
            val iCount = scanState.iacResults?.total ?: 0
            val cCount = scanState.scaResults?.total ?: 0
            val total = sCount + iCount + cCount

            val tpl = HtmlLoader.loadResource("/html/dashboard.html")
            val html = tpl
                .replace("%%TOTAL%%", total.toString())
                .replace("%%SAST_COUNT%%", sCount.toString())
                .replace("%%IAC_COUNT%%", iCount.toString())
                .replace("%%SCA_COUNT%%", cCount.toString())
                .replace(
                    "%%DATE%%",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
                .replace(
                    "%%SAST_JSON%%",
                    gson.toJson(scanState.sastResults?.vulnerabilities ?: emptyList<Any>())
                )
                .replace(
                    "%%IAC_JSON%%",
                    gson.toJson(scanState.iacResults?.vulnerabilities ?: emptyList<Any>())
                )
                .replace(
                    "%%SCA_JSON%%",
                    gson.toJson(scanState.scaResults?.vulnerabilities ?: emptyList<Any>())
                )

            ApplicationManager.getApplication().invokeLater { browser.loadHTML(html) }
        }

        refreshHtml()
        scanState.addListener(refreshHtml)
    }

    /* ---------- helper non bloquant ---------- */
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
