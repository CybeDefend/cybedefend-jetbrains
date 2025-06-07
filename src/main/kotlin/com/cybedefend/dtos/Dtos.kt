// ======================================
// Enums
// ======================================
enum class VulnerabilitySeverityEnum { CRITICAL, HIGH, MEDIUM, LOW, INFO, UNKNOWN }
enum class VulnerabilityStatusEnum { TO_VERIFY, NOT_EXPLOITABLE, PROPOSED_NOT_EXPLOITABLE, RESOLVED, CONFIRMED }
enum class VulnerabilityPriorityEnum { CRITICAL_URGENT, URGENT, NORMAL, LOW, VERY_LOW, UNKNOWN }
enum class ScanState { QUEUED, RUNNING, COMPLETED, COMPLETED_DEGRADED, FAILED }
enum class VulnerabilityType { CRITICAL, HIGH, MEDIUM, LOW }

// ======================================
// DTOs: AI - Requests
// ======================================

data class AddMessageConversationRequestDto(
    var idConversation: String,
    var message: String,
    var projectId: String = ""
)

data class StartConversationRequestDto(
    var isVulnerabilityConversation: Boolean = false,
    var projectId: String? = null,
    var vulnerabilityId: String? = null,
    var vulnerabilityType: String? = null,
    var language: String? = null
)

data class InitiateConversationResponse(
    val conversationId: String
)

/**
 * Configuration de projet retournée après setup.
 */
data class ProjectConfig(
    val apiKey: String,
    val projectId: String,
    val workspaceRoot: String,
    val organizationId: String
)


// ======================================
// DTOs: AI - Responses
// ======================================

data class MessageDto(
    val role: String,
    val content: String,
    val createdAt: String
)

data class ConversationResponseDto(
    val conversationId: String,
    val messages: List<MessageDto>
)

// ======================================
// DTOs: Organization
// ======================================

data class OrganizationInformationsResponseDto(
    val id: String,
    val name: String,
    val description: String,
    val website: String,
    val email: String,
    val monthlyScanCount: Int = 0,
    val monthlyScanResetAt: String = "", // Changed String to String and provided a default empty string
    val concurrentScanLimit: Int = 1,
    val monthlyScanLimit: Int = 1000
)

// ======================================
// DTOs: Team
// ======================================

data class TeamInformationsResponseDto(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: String, // Changed String to String
    val updatedAt: String  // Changed String to String
)

// ======================================
// DTOs: Project
// ======================================

data class MainStatisticsResponseDto(
    val highRiskProjects: Int,
    val highRiskProjectsInLast7Days: Int,
    val solvedIssues: Int,
    val solvedIssuesInLast7Days: Int,
    val newIssues: Int,
    val newIssuesInLast7Days: Int,
    val criticalIssues: Int,
    val highIssues: Int,
    val mediumIssues: Int,
    val lowIssues: Int
)

data class IssueCountResponseDto(
    val critical: Int,
    val high: Int,
    val medium: Int,
    val low: Int
)

data class AnalysisTypeResponseDto(
    val type: String,
    val lastScan: String,
    val source: String,
    val issuesCount: IssueCountResponseDto
)

data class ProjectAllInformationsResponseDto(
    val projectId: String,
    val teamId: String,
    val teamName: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val riskLevel: String,
    val issuesCount: IssueCountResponseDto,
    val analyses: List<AnalysisTypeResponseDto>
)

data class PaginatedProjectsAllInformationsResponseDto(
    val projects: List<ProjectAllInformationsResponseDto>,
    val totalProjects: Int,
    val totalPages: Int,
    val mainStatistics: MainStatisticsResponseDto
)

data class ProjectInformationsResponseDto(
    val projectId: String,
    val teamId: String,
    val teamName: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val applicationType: String? = null,
    val analysisFrequency: String? = null,
    val emailAlertEnabled: Boolean? = null,
    val monthlyReportEnabled: Boolean? = null,
    val weeklyReportEnabled: Boolean? = null,
    val sastEnabled: Boolean? = null,
    val dastEnabled: Boolean? = null,
    val scaEnabled: Boolean? = null,
    val containerEnabled: Boolean? = null,
    val apiEnabled: Boolean? = null,
    val iacEnabled: Boolean? = null,
    val sastFastScanEnabled: Boolean? = null,
    val aiDataflowEnabled: Boolean? = null,
    val sastSeverities: List<String>? = null,
    val scaSeverities: List<String>? = null,
    val iacSeverities: List<String>? = null,
    val incidentCreationOption: String? = null,
    val aiMergeRequestEnabled: Boolean? = null,
    val improvingResultsEnabled: Boolean? = null,
    val sortsVulnerabilitiesEnabled: Boolean? = null
)

data class ProjectCreateRequestDto(
    val name: String,
    val teamId: String,
    val creatorId: String
)

// ======================================
// DTOs: Repository
// ======================================

data class RepositoryDto(
    val name: String,
    val fullName: String,
    val id: String,
    val githubId: Int,
    val private: Boolean,
    val projectId: String
)

data class RepositoryInstallationDto(
    val repository: List<RepositoryDto>,
    val installationId: Int
)

data class LinkRepositoryRequestDto(
    val organizationId: String,
    val projectId: String,
    val repositoryId: String
)

data class GetRepositoriesResponseDto(
    val repositories: List<RepositoryInstallationDto>,
    val organizationId: String
)

// ======================================
// DTOs: Result - Requests
// ======================================

data class GetMainStatisticsRequestDto(val projectIds: List<String>)

data class GetProjectOverviewRequestDto(val projectId: String)

data class GetProjectScaVulnerabilitiesRequestDto(
    val projectId: String,
    val query: String,
    val page: Int,
    val limit: Int,
    val sort: String,
    val order: String,
    val severity: List<String>,
    val status: List<String>,
    val language: String,
    val priority: List<String>,
    val pageNumber: Int,
    val pageSizeNumber: Int,
    val searchQuery: String
)

data class GetProjectVulnerabilitiesRequestDto(
    val projectId: String,
    val sort: String,
    val order: String,
    val severity: List<String>,
    val status: List<String>,
    val language: String,
    val priority: List<String>,
    val pageNumber: Int,
    val pageSizeNumber: Int,
    val searchQuery: String
)

data class GetProjectVulnerabilityByIdRequestDto(
    val projectId: String,
    val vulnerabilityId: String
)

// ======================================
// DTOs: Result - Responses
// ======================================

data class GetLastScanStatusResponseDto(
    val id: String,
    val state: String,
    val projectId: String,
    val createAt: String,
    val scanType: String?
)

data class CountVulnerabilitiesCountByType(
    val sast: Int,
    val iac: Int,
    val sca: Int
)

data class ScanProjectInfoDto(
    val scanId: String,
    val state: String,
    val createAt: String,
    val scanType: String?
)

// Detailed vulnerability response types

data class UserDto(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val picture: String
)

data class CodeLineDto(
    val line: Int,
    val content: String
)

data class ScaDetectedLibraryDto(
    val id: String,
    val projectId: String,
    val packageName: String,
    val packageVersion: String,
    val fileName: String,
    val ecosystem: String
)

data class VulnerabilityScaCweDto(val id: String, val cweId: String)

data class VulnerabilityScaAliasDto(val id: String, val alias: String)

data class VulnerabilityScaReferenceDto(val id: String, val type: String, val url: String)

data class VulnerabilityScaSeverityDto(val id: String, val type: String, val score: String)

data class VulnerabilityScaPackageDto(
    val id: String,
    val ecosystem: String,
    val packageName: String,
    val introduced: String,
    val fixed: String,
    val fixAvailable: Boolean
)

data class VulnerabilityMetadataDto(
    val id: String,
    val cwe: List<String>,
    val name: String,
    val shortDescription: String,
    val description: String,
    val howToPrevent: String,
    val cweArray: List<String>? = null,
    val owaspTop10: List<String>? = null,
    val severity: String,
    val language: String,
    val vulnerabilityType: String
)

data class HistoryItemDto(
    val id: String,
    val type: String,
    val value: String,
    val date: String,
    val userId: String?,
    val user: UserDto?
)

data class CodeSnippetDto(
    val id: String,
    val vulnerableStartLine: Int,
    val vulnerableEndLine: Int,
    val startLine: Int,
    val endLine: Int,
    val code: List<CodeLineDto>,
    val language: String,
    val fixAnalysis: String? = null,
    val fixAnalysisDescription: String? = null
)

data class DataFlowItemDto(
    val id: String,
    val nameHighlight: String,
    val line: Int,
    val language: String,
    val code: List<CodeLineDto>,
    val type: String,
    val order: Int
)

data class VulnerabilitySastDto(
    override val id: String,
    override val projectId: String,
    override val createdAt: String,
    override val updateAt: String,
    override val timeToFix: String? = null,
    override val currentState: String,
    override val currentSeverity: String,
    override val currentPriority: String,
    override val contextualExplanation: String? = null,
    override val language: String,
    override val path: String,
    override val vulnerableStartLine: Int,
    override val vulnerableEndLine: Int,
    override val vulnerability: VulnerabilityMetadataDto,
    override val historyItems: List<HistoryItemDto>,
    override val codeSnippets: List<CodeSnippetDto>,
    override val vulnerabilityType: String,
    val dataFlowItems: List<DataFlowItemDto>
) : VulnerabilityDtoResponse(id, projectId, createdAt, updateAt, timeToFix, currentState, currentSeverity, currentPriority, contextualExplanation, language, path, vulnerableStartLine, vulnerableEndLine, vulnerability, historyItems, codeSnippets, vulnerabilityType)

data class VulnerabilityIacDto(
    override val id: String,
    override val projectId: String,
    override val createdAt: String,
    override val updateAt: String,
    override val timeToFix: String? = null,
    override val currentState: String,
    override val currentSeverity: String,
    override val currentPriority: String,
    override val contextualExplanation: String? = null,
    override val language: String,
    override val path: String,
    override val vulnerableStartLine: Int,
    override val vulnerableEndLine: Int,
    override val vulnerability: VulnerabilityMetadataDto,
    override val historyItems: List<HistoryItemDto>,
    override val codeSnippets: List<CodeSnippetDto>,
    override val vulnerabilityType: String
) : VulnerabilityDtoResponse(id, projectId, createdAt, updateAt, timeToFix, currentState, currentSeverity, currentPriority, contextualExplanation, language, path, vulnerableStartLine, vulnerableEndLine, vulnerability, historyItems, codeSnippets, vulnerabilityType)

data class GetProjectVulnerabilityByIdResponseDto(
    val projectId: String,
    val vulnerabilityId: String,
    val vulnerability: VulnerabilityDtoResponse
)

// ======================================
// DTOs: Security Scanning
// ======================================

data class GetScanRequestDto(val scanId: String)

data class UpdateScanRequestDto(
    val scanId: String,
    val state: String,
    val startedAt: String,
    val clusterId: String,
    val podId: String,
    val podName: String,
    val image: String,
    val version: String,
    val totalVulnerabilities: Int,
    val processedVulnerabilities: Int,
    val step: String
)

data class StartScanRequestDto(
    val scanId: String,
    val url: String,
    val projectId: String,
    val filename: String,
    val privateScan: Boolean,
    val vulnerabilityTypes: List<VulnerabilityType>
)

data class ContainerDto(
    val id: String,
    val status: String,
    val createdAt: String,
    val startedAt: String,
    val finishedAt: String,
    val scanId: String
)

data class ScanResponseDto(
    val id: String,
    val name: String,
    val state: String,
    val language: List<String>,
    val projectId: String,
    val private: Boolean,
    val initializerUserId: String?,
    val createAt: String,
    val updatedAt: String,
    val scanType: String?,
    val startTime: String?,
    val endTime: String?,
    val containers: List<ContainerDto>,
    val progress: Int,
    val step: String,
    val vulnerabilityDetected: Int?
)

data class StartScanResponseDto(
    val url: String,
    val scanId: String
)

// End of DTOs

// Migration GRPC to DTOs

// -----------------------------------------------------------------
// 1. MODÈLE POUR L'INTERFACE UTILISATEUR (NE CHANGE PAS BEAUCOUP)
// C'est notre format interne unifié. C'est une "open class" car d'autres DTOs en héritaient.
// On la garde ainsi pour limiter les régressions.
// -----------------------------------------------------------------
typealias DetailedVulnerability = VulnerabilityDtoResponse

open class VulnerabilityDtoResponse(
    open val id: String,
    open val projectId: String,
    open val createdAt: String,
    open val updateAt: String,
    open val timeToFix: String?,
    open val currentState: String,
    open val currentSeverity: String,
    open val currentPriority: String,
    open val contextualExplanation: String?,
    open val language: String,
    open val path: String,
    open val vulnerableStartLine: Int,
    open val vulnerableEndLine: Int,
    open val vulnerability: VulnerabilityMetadataDto,
    open val historyItems: List<HistoryItemDto>,
    open val codeSnippets: List<CodeSnippetDto>,
    open val vulnerabilityType: String
)

// Base unifiée pour les réponses SAST et IAC, comme dans le proto
// Note: nous allons mapper ceci vers votre `VulnerabilityDtoResponse` existant
data class VulnerabilitySastIacResponse(
    val id: String,
    val projectId: String,
    val createdAt: String,
    val updateAt: String,
    val timeToFix: String?,
    val currentState: String,
    val currentSeverity: String,
    val currentPriority: String,
    val contextualExplanation: String?,
    val language: String,
    val path: String,
    val vulnerableStartLine: Int,
    val vulnerableEndLine: Int,
    val vulnerability: VulnerabilityMetadataDto?, // Peut être nul
    val historyItems: HistoryItemsWrapper?, // Wrapper
    val codeSnippets: List<CodeSnippetDto>?, // Peut être nul
    val vulnerabilityType: String
)


data class HistoryItemsWrapper(val items: List<HistoryItemDto>)

// --- Conteneurs par type ---
data class VulnerabilitySast(
    val base: VulnerabilitySastIacResponse?,
    val dataFlowItems: List<DataFlowItemDto>
)

data class VulnerabilityIac(
    val base: VulnerabilitySastIacResponse?
)

data class GetOrganizationsResponseDto(
    val organizations: List<OrganizationInformationsResponseDto>
)

data class GetTeamsResponseDto(
    val teams: List<TeamInformationsResponseDto>
)

data class VulnerabilityScaMetadataDto(
    val cve: String,
    val internalId: String,
    val summary: String,
    val severityGh: String,
    val schemaVersion: String,
    val modifiedAt: String? = null,
    val publishedAt: String? = null,
    val githubReviewedAt: String? = null,
    val nvdPublishedAt: String? = null,
    val aliases: List<VulnerabilityScaAliasDto> = emptyList(),
    val details: String? = null,
    val cwes: List<VulnerabilityScaCweDto> = emptyList(),
    val references: List<VulnerabilityScaReferenceDto> = emptyList(),
    val severities: List<VulnerabilityScaSeverityDto> = emptyList(),
    val packages: List<VulnerabilityScaPackageDto> = emptyList()
)

data class VulnerabilitySca(
    val base: VulnerabilitySastIacResponse?,
    val library: ScaDetectedLibraryDto?,
    val cvssScore: Double?,
    val metadata: VulnerabilityScaMetadataDto?
)

// --- DTOs de réponse de liste ---
data class GetProjectSastVulnerabilitiesResponse(
    val vulnerabilities: List<VulnerabilitySast>,
    val total: Int,
    val scanProjectInfo: ScanProjectInfoDto?
)

data class GetProjectIacVulnerabilitiesResponse(
    val vulnerabilities: List<VulnerabilityIac>,
    val total: Int,
    val scanProjectInfo: ScanProjectInfoDto?
)

data class GetProjectScaVulnerabilitiesResponse(
    val vulnerabilities: List<VulnerabilitySca>,
    val total: Int,
    val scanProjectInfo: ScanProjectInfoDto?
)

// --- DTO de réponse détaillée ---
data class GetProjectVulnerabilityByIdResponse(
    val projectId: String,
    val vulnerabilityId: String,
    val sast: VulnerabilitySast?,
    val iac: VulnerabilityIac?,
    val sca: VulnerabilitySca?
)


// -----------------------------------------------------------------
// 3. MAPPERS : LE PONT ENTRE LES DTOS D'API ET LE MODÈLE DE VUE
// -----------------------------------------------------------------

/**
 * Mapper pour une vulnérabilité SAST.
 */
fun VulnerabilitySast.toUnified(): DetailedVulnerability {
    val base = this.base!!
    return VulnerabilityDtoResponse(
        id = base.id,
        projectId = base.projectId,
        createdAt = base.createdAt,
        updateAt = base.updateAt,
        timeToFix = base.timeToFix,
        currentState = base.currentState,
        currentSeverity = base.currentSeverity,
        currentPriority = base.currentPriority,
        contextualExplanation = base.contextualExplanation,
        language = base.language,
        path = base.path,
        vulnerableStartLine = base.vulnerableStartLine,
        vulnerableEndLine = base.vulnerableEndLine,
        vulnerability = base.vulnerability!!, // Pour SAST/IAC, ce n'est jamais null
        historyItems = base.historyItems?.items ?: emptyList(),
        codeSnippets = base.codeSnippets ?: emptyList(),
        vulnerabilityType = base.vulnerabilityType
    )
}

/**
 * Mapper pour une vulnérabilité IAC.
 */
fun VulnerabilityIac.toUnified(): DetailedVulnerability {
    val base = this.base!!
    return VulnerabilityDtoResponse(
        id = base.id,
        projectId = base.projectId,
        createdAt = base.createdAt,
        updateAt = base.updateAt,
        timeToFix = base.timeToFix,
        currentState = base.currentState,
        currentSeverity = base.currentSeverity,
        currentPriority = base.currentPriority,
        contextualExplanation = base.contextualExplanation,
        language = base.language,
        path = base.path,
        vulnerableStartLine = base.vulnerableStartLine,
        vulnerableEndLine = base.vulnerableEndLine,
        vulnerability = base.vulnerability!!, // Pour SAST/IAC, ce n'est jamais null
        historyItems = base.historyItems?.items ?: emptyList(),
        codeSnippets = base.codeSnippets ?: emptyList(),
        vulnerabilityType = base.vulnerabilityType
    )
}

/**
 * Mapper pour une vulnérabilité SCA. Gère le cas où `base.vulnerability` est null.
 */
fun VulnerabilitySca.toUnified(): DetailedVulnerability {
    val base = this.base!!
    // Pour SCA, les métadonnées de la vulnérabilité peuvent être nulles dans la base
    // et doivent être reconstruites à partir de `sca.metadata`.
    val vulnerabilityMetadata = base.vulnerability ?: VulnerabilityMetadataDto(
        id = this.metadata?.internalId ?: base.id,
        name = this.metadata?.summary ?: "N/A",
        description = this.metadata?.details ?: "",
        shortDescription = this.metadata?.summary ?: "",
        howToPrevent = "Update the dependency.",
        cwe = this.metadata?.cwes?.map { it.cweId } ?: emptyList(),
        severity = base.currentSeverity,
        language = this.library?.ecosystem ?: "N/A",
        vulnerabilityType = "sca"
    )

    return VulnerabilityDtoResponse(
        id = base.id,
        projectId = base.projectId,
        createdAt = base.createdAt,
        updateAt = base.updateAt,
        timeToFix = base.timeToFix,
        currentState = base.currentState,
        currentSeverity = base.currentSeverity,
        currentPriority = base.currentPriority,
        contextualExplanation = base.contextualExplanation,
        language = this.library?.ecosystem ?: base.language,
        path = this.library?.fileName ?: base.path,
        vulnerableStartLine = base.vulnerableStartLine,
        vulnerableEndLine = base.vulnerableEndLine,
        vulnerability = vulnerabilityMetadata,
        historyItems = base.historyItems?.items ?: emptyList(),
        codeSnippets = base.codeSnippets ?: emptyList(),
        vulnerabilityType = base.vulnerabilityType
    )
}
