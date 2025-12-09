package com.cybedefend.services.scan

import DetailedVulnerability
import GetProjectSastVulnerabilitiesResponse
import VulnerabilityDtoResponse
import VulnerabilitySastIacResponse
import com.cybedefend.services.ApiService
import com.cybedefend.services.AuthService
import com.cybedefend.utils.GitUtils
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.Logger
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
        private val LOG = Logger.getInstance(ScanStateService::class.java)
        
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
    
    /** The branch name used for the last scan, if available */
    var currentBranch: String? = null
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
        totalVulnerabilities = 0
        lastScanState = "N/A"
        currentBranch = null
        clearResults()
        notifyListeners()
    }

    private fun clearResults() {
        sastResults = null
        iacResults = null
        scaResults = null
    }

    /**
     * Launches the complete scan workflow (archive, API, polling, fetch, notify).
     * Automatically detects the current Git branch and associates it with the scan.
     * 
     * @param projectId CybeDefend project ID
     * @param workspaceRoot Local workspace path to zip and scan
     */
    fun startScan(projectId: String, workspaceRoot: String) {
        object : Task.Backgroundable(project, "CybeDefend: Scanning…", true) {
            override fun run(ind: ProgressIndicator) {
                runBlocking {
                    try {
                        // Detect current Git branch for branch-aware scanning
                        ind.text = "Detecting Git branch…"
                        val detectedBranch = detectGitBranch(workspaceRoot)
                        currentBranch = detectedBranch
                        
                        if (detectedBranch != null) {
                            LOG.info("Starting scan on branch: $detectedBranch")
                            ind.text2 = "Branch: $detectedBranch"
                        } else {
                            LOG.info("No Git branch detected, starting scan without branch association")
                        }
                        
                        // A) Archive project files
                        ind.text = "Archiving project…"
                        val zipFile = createWorkspaceZip(workspaceRoot, ind)

                        setLoading(true)
                        ind.text = "Initiating scan..."
                        val api = ApiService(authService = AuthService.getInstance(project))

                        // Start scan with branch information
                        val startScanResponse = api.startScan(projectId, detectedBranch)
                        val signedUrl = startScanResponse.url
                        val scanId = startScanResponse.scanId

                        ind.text = "Uploading project files..."
                        api.uploadFileToSignedUrl(signedUrl, zipFile)

                        // B) Poll scan status until completion
                        ind.text = "Waiting for scan to complete…"
                        val final = pollScanStatus(projectId, scanId, api, ind)
                        if (!final.contains("COMPLETED")) throw Exception("Final status: $final")

                        // C) Fetch results with branch filter
                        ind.text = "Fetching SAST results…"
                        val sastResponse = api.getSastResults(projectId, branch = detectedBranch)
                        val sastVulns = sastResponse.vulnerabilities.mapNotNull { it.base?.toUnified() }

                        ind.text = "Fetching IaC results…"
                        val iacResponse = api.getIacResults(projectId, branch = detectedBranch)
                        val iacVulns = iacResponse.vulnerabilities.mapNotNull { it.base?.toUnified() }

                        ind.text = "Fetching SCA results…"
                        val scaResponse = api.getScaResults(projectId, branch = detectedBranch)
                        val scaVulns = scaResponse.vulnerabilities.map { it.toUnified() }

                        // D) Update UI with results
                        updateResults(sastVulns, iacVulns, scaVulns, sastResponse)
                        
                        // Notify success with branch info
                        val branchInfo = if (detectedBranch != null) " on branch '$detectedBranch'" else ""
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("CybeDefend")
                            .createNotification(
                                "Scan completed",
                                "Found ${sastVulns.size + iacVulns.size + scaVulns.size} vulnerabilities$branchInfo",
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
     * Detects the current Git branch for the workspace.
     * Returns a sanitized branch name suitable for API calls.
     * 
     * @param workspaceRoot The workspace root directory
     * @return The branch name, or null if not in a Git repository or detection fails
     */
    private fun detectGitBranch(workspaceRoot: String): String? {
        return try {
            val branch = GitUtils.getCurrentBranch(workspaceRoot)
            
            // Skip detached HEAD states for API calls (they contain special characters)
            if (branch != null && branch.startsWith("HEAD (")) {
                LOG.debug("Skipping detached HEAD state for branch association")
                null
            } else {
                branch
            }
        } catch (e: Exception) {
            LOG.warn("Error detecting Git branch: ${e.message}", e)
            null
        }
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
