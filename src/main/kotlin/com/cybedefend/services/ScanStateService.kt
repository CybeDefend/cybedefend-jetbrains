package com.cybedefend.services

import GetProjectVulnerabilitiesResponseDto
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service(Level.PROJECT)
class ScanStateService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): ScanStateService =
            project.getService(ScanStateService::class.java)
    }

    var isLoading: Boolean = false
        private set
    var error: String? = null
        private set

    var sastResults: GetProjectVulnerabilitiesResponseDto? = null
        private set
    var iacResults: GetProjectVulnerabilitiesResponseDto? = null
        private set
    var scaResults: GetProjectVulnerabilitiesResponseDto? = null
        private set

    private val listeners = mutableSetOf<() -> Unit>()
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun notifyListeners() = listeners.forEach { it() }

    fun setLoading(loading: Boolean) {
        if (isLoading == loading) return
        isLoading = loading
        if (loading) error = null
        notifyListeners()
    }

    fun setError(message: String) {
        error = message
        isLoading = false
        clearResults()
        notifyListeners()
    }

    fun updateResults(
        sast: GetProjectVulnerabilitiesResponseDto,
        iac: GetProjectVulnerabilitiesResponseDto,
        sca: GetProjectVulnerabilitiesResponseDto
    ) {
        sastResults = sast
        iacResults = iac
        scaResults = sca
        error = null
        isLoading = false
        notifyListeners()
    }

    fun reset() {
        isLoading = false
        error = null
        clearResults()
        notifyListeners()
    }

    private fun clearResults() {
        sastResults = null
        iacResults = null
        scaResults = null
    }

    /**
     * Démarre le scan complet en tâche de fond.
     */
    /**
     * Lance tout le workflow de scan (archive, API, polling, fetch, notify).
     * @param projectId ID CybeDefend du projet
     * @param workspaceRoot chemin local du workspace à zipper
     */
    fun startScan(projectId: String, workspaceRoot: String) {
        object : Task.Backgroundable(project, "CybeDefend: Scanning…", true) {
            override fun run(ind: com.intellij.openapi.progress.ProgressIndicator) {
                runBlocking {
                    try {
                        // A) Archive
                        ind.text = "Archiving project…"
                        val zipFile = createWorkspaceZip(workspaceRoot, ind)

                        // B) Initiate
                        setLoading(true)
                        ind.text = "Initiating scan…"
                        val api = ApiService(authService = AuthService.getInstance(project))
                        val resp = api.startScan(projectId, zipFile.absolutePath)
                        val scanId = resp.message

                        // C) Poll
                        ind.text = "Waiting for scan to complete…"
                        val final = pollScanStatus(projectId, scanId, api, ind)
                        if (!final.contains("COMPLETED")) throw Exception("Final status: $final")

                        // D) Fetch
                        ind.text = "Fetching SAST results…"
                        val s = api.getScanResults(projectId, "sast")
                        ind.text = "Fetching IaC results…"
                        val i = api.getScanResults(projectId, "iac")
                        ind.text = "Fetching SCA results…"
                        val c = api.getScanResults(projectId, "sca")

                        // E) Update UI
                        updateResults(s, i, c)

                        // F) Notification
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("CybeDefend")
                            .createNotification(
                                "Scan completed",
                                "Vulnerabilities found: ${s.total + i.total + c.total}",
                                NotificationType.INFORMATION
                            ).notify(project)

                    } catch (e: Exception) {
                        setError(e.message ?: "Unknown error")
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("CybeDefend")
                            .createNotification(
                                "Scan failed",
                                e.message ?: "Unknown error",
                                NotificationType.ERROR
                            ).notify(project)
                    }
                }
            }
        }.queue()
    }

    /**
     * Crée un zip du workspace en excluant node_modules, .git, etc.
     */
    private fun createWorkspaceZip(
        workspaceRoot: String,
        ind: com.intellij.openapi.progress.ProgressIndicator
    ): File {
        val ignore = listOf("node_modules", ".git", "dist", "build", "out", "target", ".gradle")
        val zipFile = Files.createTempFile("cdscan-", ".zip").toFile()
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            val root = Paths.get(workspaceRoot)
            Files.walk(root).use { stream ->
                stream.forEach { path ->
                    if (ind.isCanceled) throw InterruptedException("Cancelled")
                    if (Files.isDirectory(path)) return@forEach
                    val rel = root.relativize(path).toString()
                    if (ignore.any { rel.startsWith(it) || rel.contains("${File.separator}$it") }) return@forEach
                    zipOut.putNextEntry(ZipEntry(rel.replace(File.separatorChar, '/')))
                    Files.copy(path, zipOut)
                    zipOut.closeEntry()
                }
            }
        }
        return zipFile
    }

    /**
     * Polling de getScanStatus() jusqu'à COMPLETED ou timeout.
     */
    private fun pollScanStatus(
        projectId: String,
        scanId: String,
        apiService: ApiService,
        indicator: com.intellij.openapi.progress.ProgressIndicator
    ): String {
        val maxAttempts = 60
        repeat(maxAttempts) { attempt ->
            if (indicator.isCanceled) throw InterruptedException("Cancelled")
            indicator.text2 = "Polling status (${attempt + 1}/$maxAttempts)…"
            val status = runBlocking { apiService.getScanStatus(projectId, scanId).state }
            if (status.toString() == "COMPLETED" || status.toString() == "COMPLETED_DEGRADED" || status.toString() == "FAILED") {
                return status.toString()
            }
            TimeUnit.SECONDS.sleep(5)
        }
        throw Exception("Polling timeout after $maxAttempts attempts")
    }
}
