package com.cybedefend.services.scan

import DetailedVulnerability
import GetProjectSastVulnerabilitiesResponse
import VulnerabilityDtoResponse
import VulnerabilitySastIacResponse
import com.cybedefend.services.ApiService
import com.cybedefend.services.AuthService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import toUnified
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

    var sastResults: List<DetailedVulnerability>? = null
        private set
    var iacResults: List<DetailedVulnerability>? = null
        private set
    var scaResults: List<DetailedVulnerability>? = null
        private set

    var totalVulnerabilities: Int = 0
        private set
    var lastScanState: String? = "N/A"
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
        sast: List<DetailedVulnerability>,
        iac: List<DetailedVulnerability>,
        sca: List<DetailedVulnerability>,
        // On passe une des réponses originales pour récupérer les méta-données
        originalResponse: GetProjectSastVulnerabilitiesResponse?
    ) {
        sastResults = sast
        iacResults = iac
        scaResults = sca

        // On calcule et stocke les nouvelles informations
        totalVulnerabilities = sast.size + iac.size + sca.size
        lastScanState = originalResponse?.scanProjectInfo?.state ?: "COMPLETED"

        error = null
        isLoading = false
        notifyListeners()
    }

    // Assurez-vous aussi de réinitialiser les nouvelles propriétés
    fun reset() {
        isLoading = false
        error = null
        totalVulnerabilities = 0 // <-- AJOUTER
        lastScanState = "N/A"    // <-- AJOUTER
        clearResults()
        notifyListeners()
    }

    private fun clearResults() {
        sastResults = null
        iacResults = null
        scaResults = null
    }

    /**
     * Lance tout le workflow de scan (archive, API, polling, fetch, notify).
     * @param projectId ID CybeDefend du projet
     * @param workspaceRoot chemin local du workspace à zipper
     */
    fun startScan(projectId: String, workspaceRoot: String) {
        object : Task.Backgroundable(project, "CybeDefend: Scanning…", true) {
            override fun run(ind: ProgressIndicator) {
                runBlocking {
                    try {
                        // A) Archive (inchangé)
                        ind.text = "Archiving project…"
                        val zipFile = createWorkspaceZip(workspaceRoot, ind)

                        setLoading(true)
                        ind.text = "Initiating scan..."
                        val api = ApiService(authService = AuthService.getInstance(project))

                        val startScanResponse = api.startScan(projectId)
                        val signedUrl = startScanResponse.url
                        val scanId = startScanResponse.scanId

                        ind.text = "Uploading project files..."
                        api.uploadFileToSignedUrl(signedUrl, zipFile)

                        // C) Poll du statut (inchangé, mais utilise le scanId de l'étape 1)
                        ind.text = "Waiting for scan to complete…"
                        val final = pollScanStatus(projectId, scanId, api, ind)
                        if (!final.contains("COMPLETED")) throw Exception("Final status: $final")

                        // D) Fetch
                        ind.text = "Fetching SAST results…"
                        val sastResponse = api.getSastResults(projectId)
                        val sastVulns = sastResponse.vulnerabilities.mapNotNull { it.base?.toUnified() }

                        ind.text = "Fetching IaC results…"
                        val iacResponse = api.getIacResults(projectId)
                        val iacVulns = iacResponse.vulnerabilities.mapNotNull { it.base?.toUnified() }

                        ind.text = "Fetching SCA results…"
                        val scaResponse = api.getScaResults(projectId)
                        val scaVulns = scaResponse.vulnerabilities.map { it.toUnified() }

                        // E) Update UI
                        updateResults(sastVulns, iacVulns, scaVulns, sastResponse)
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
        ind: ProgressIndicator
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
        indicator: ProgressIndicator
    ): String {
        val maxAttempts = 60
        repeat(maxAttempts) { attempt ->
            if (indicator.isCanceled) throw InterruptedException("Cancelled")
            indicator.text2 = "Polling status (${attempt + 1}/$maxAttempts)…"

            val raw = runBlocking { apiService.getScanStatus(projectId, scanId).state }
            val status = raw.uppercase()        // normalise once

            if (status in listOf("COMPLETED", "COMPLETED_DEGRADED", "FAILED")) {
                return status                   // finished → exit loop
            }
            TimeUnit.SECONDS.sleep(5)
        }
        throw Exception("Polling timeout after $maxAttempts attempts")
    }

    // Cette fonction convertit la nouvelle structure vers l'ancienne, que votre UI comprend
    private fun VulnerabilitySastIacResponse.toUnified(): DetailedVulnerability {
        return VulnerabilityDtoResponse(
            id = this.id,
            projectId = this.projectId,
            createdAt = this.createdAt,
            updateAt = this.updateAt,
            timeToFix = this.timeToFix,
            currentState = this.currentState,
            currentSeverity = this.currentSeverity,
            currentPriority = this.currentPriority,
            contextualExplanation = this.contextualExplanation,
            language = this.language,
            path = this.path,
            vulnerableStartLine = this.vulnerableStartLine,
            vulnerableEndLine = this.vulnerableEndLine,
            vulnerability = this.vulnerability!!, // Assumant non-nul pour la vue
            historyItems = this.historyItems?.items ?: emptyList(),
            codeSnippets = this.codeSnippets ?: emptyList(),
            vulnerabilityType = this.vulnerabilityType
        )
    }
}
