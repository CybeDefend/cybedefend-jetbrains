package com.cybedefend.services

import AddMessageConversationRequestDto
import GetProjectVulnerabilitiesResponseDto
import GetProjectVulnerabilityByIdResponseDto
import GetRepositoriesResponseDto
import InitiateConversationResponse
import OrganizationInformationsResponseDto
import PaginatedProjectsAllInformationsResponseDto
import ProjectInformationsResponseDto
import RepositoryDto
import ScanResponseDto
import StartConversationRequestDto
import StartScanResponseDto
import TeamInformationsResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.io.IOException

/**
 * Retrofit API definitions matching the backend endpoints.
 */
private interface ApiServiceApi {
    @Multipart
    @POST("project/{projectId}/scan/start")
    suspend fun startScan(
        @Path("projectId") projectId: String,
        @Part("scan") scan: MultipartBody.Part
    ): StartScanResponseDto

    @GET("project/{projectId}/results/{scanType}")
    suspend fun getScanResults(
        @Path("projectId") projectId: String,
        @Path("scanType") scanType: String,
        @Query("pageNumber") pageNumber: Int,
        @Query("pageSizeNumber") pageSizeNumber: Int,
        @Query("severity") severity: List<String>?
    ): GetProjectVulnerabilitiesResponseDto

    @GET("project/{projectId}/results/{scanType}/{vulnerabilityId}")
    suspend fun getVulnerabilityDetails(
        @Path("projectId") projectId: String,
        @Path("scanType") scanType: String,
        @Path("vulnerabilityId") vulnerabilityId: String
    ): GetProjectVulnerabilityByIdResponseDto

    @GET("project/{projectId}/scan/{scanId}")
    suspend fun getScanStatus(
        @Path("projectId") projectId: String,
        @Path("scanId") scanId: String
    ): ScanResponseDto

    @POST("project/{projectId}/ai/conversation/start")
    suspend fun startConversation(
        @Path("projectId") projectId: String,
        @Body body: StartConversationRequestDto
    ): InitiateConversationResponse

    @POST("project/{projectId}/ai/conversation/{conversationId}/message")
    suspend fun continueConversation(
        @Path("projectId") projectId: String,
        @Path("conversationId") conversationId: String,
        @Body body: AddMessageConversationRequestDto
    ): InitiateConversationResponse

    @GET("organizations")
    suspend fun getOrganizations(): List<OrganizationInformationsResponseDto>

    @GET("organization/{organizationId}/github/repositories")
    suspend fun getRepositories(
        @Path("organizationId") organizationId: String
    ): GetRepositoriesResponseDto

    @GET("organization/{organizationId}/teams")
    suspend fun getTeams(
        @Path("organizationId") organizationId: String
    ): List<TeamInformationsResponseDto>

    @POST("team/{teamId}/project")
    suspend fun createProject(
        @Path("teamId") teamId: String,
        @Body body: Map<String, String>
    ): ProjectInformationsResponseDto

    @POST("organization/{organizationId}/project/{projectId}/github/link")
    suspend fun linkProject(
        @Path("organizationId") organizationId: String,
        @Path("projectId") projectId: String,
        @Body body: Map<String, String>
    ): RepositoryDto

    @GET("organization/{organizationId}/projects")
    suspend fun getProjectsOrganization(
        @Path("organizationId") organizationId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("search") search: String?
    ): PaginatedProjectsAllInformationsResponseDto
}

/**
 * ApiService wraps the Retrofit API and handles authentication and error mapping.
 */
class ApiService(
    private val authService: AuthService
) {
    private val api: ApiServiceApi
    private val baseUrl: String = "https://api-preprod.cybedefend.com"

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()

                val apiKey = runBlocking { authService.getApiKey() }
                if (apiKey.isNullOrBlank()) {
                    throw IllegalStateException("API Key is not configured.")
                }
                builder.header("X-API-Key", apiKey)

                if (original.body !is MultipartBody) {
                    builder.header("Content-Type", "application/json")
                }
                chain.proceed(builder.build())
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())  // <- au lieu de JacksonConverterFactory
            .build()

        api = retrofit.create(ApiServiceApi::class.java)
    }

    suspend fun startScan(projectId: String, zipFilePath: String): StartScanResponseDto =
        withContext(Dispatchers.IO) {
            val file = File(zipFilePath)
            val requestBody = file.asRequestBody("application/zip".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData(
                "scan",
                file.name,
                requestBody
            )
            try {
                api.startScan(projectId, part)
            } catch (e: Throwable) {
                throw mapToApiError(e, "startScan", projectId)
            }
        }

    suspend fun getScanResults(
        projectId: String,
        scanType: String,
        pageNumber: Int? = null,
        pageSizeNumber: Int? = null,
        severity: List<String>? = null
    ): GetProjectVulnerabilitiesResponseDto = withContext(Dispatchers.IO) {
        try {
            api.getScanResults(
                projectId,
                scanType,
                pageNumber ?: 1,
                pageSizeNumber ?: 500,
                severity
            )
        } catch (e: Throwable) {
            throw mapToApiError(e, "getScanResults ($scanType)", projectId)
        }
    }

    suspend fun getVulnerabilityDetails(
        projectId: String,
        vulnerabilityId: String,
        scanType: String
    ): GetProjectVulnerabilityByIdResponseDto = withContext(Dispatchers.IO) {
        try {
            api.getVulnerabilityDetails(projectId, scanType, vulnerabilityId)
        } catch (e: Throwable) {
            throw mapToApiError(e, "getVulnerabilityDetails ($scanType)", projectId)
        }
    }

    suspend fun getScanStatus(projectId: String, scanId: String): ScanResponseDto =
        withContext(Dispatchers.IO) {
            try {
                api.getScanStatus(projectId, scanId)
            } catch (e: Throwable) {
                throw mapToApiError(e, "getScanStatus", projectId)
            }
        }

    suspend fun startConversation(request: StartConversationRequestDto): InitiateConversationResponse =
        withContext(Dispatchers.IO) {
            val projectId = request.projectId
                ?: throw IllegalArgumentException("Project ID is required for startConversation.")
            try {
                api.startConversation(projectId, request)
            } catch (e: Throwable) {
                throw mapToApiError(e, "startConversation", projectId)
            }
        }

    suspend fun continueConversation(
        request: AddMessageConversationRequestDto
    ): InitiateConversationResponse = withContext(Dispatchers.IO) {
        if (request.idConversation.isBlank() || request.projectId.isBlank()) {
            throw IllegalArgumentException("Project ID and Conversation ID are required for continueConversation.")
        }
        try {
            api.continueConversation(request.projectId, request.idConversation, request)
        } catch (e: Throwable) {
            throw mapToApiError(e, "continueConversation", request.projectId)
        }
    }

    suspend fun getOrganizations(): List<OrganizationInformationsResponseDto> =
        withContext(Dispatchers.IO) {
            try {
                api.getOrganizations()
            } catch (e: Throwable) {
                throw mapToApiError(e, "getOrganizations")
            }
        }

    suspend fun getRepositories(organizationId: String): GetRepositoriesResponseDto =
        withContext(Dispatchers.IO) {
            try {
                api.getRepositories(organizationId)
            } catch (e: Throwable) {
                throw mapToApiError(e, "getRepositories", "Org: $organizationId")
            }
        }

    suspend fun getTeams(organizationId: String): List<TeamInformationsResponseDto> =
        withContext(Dispatchers.IO) {
            try {
                api.getTeams(organizationId)
            } catch (e: Throwable) {
                throw mapToApiError(e, "getTeams", "Org: $organizationId")
            }
        }

    suspend fun createProject(teamId: String, projectName: String): ProjectInformationsResponseDto =
        withContext(Dispatchers.IO) {
            try {
                api.createProject(teamId, mapOf("name" to projectName))
            } catch (e: Throwable) {
                throw mapToApiError(e, "createProject", "Team: $teamId")
            }
        }

    suspend fun linkProject(
        organizationId: String,
        projectId: String,
        repositoryId: String
    ): RepositoryDto = withContext(Dispatchers.IO) {
        try {
            api.linkProject(
                organizationId,
                projectId,
                mapOf("repositoryId" to repositoryId)
            )
        } catch (e: Throwable) {
            throw mapToApiError(e, "linkProject", "Org: $organizationId, Proj: $projectId")
        }
    }

    suspend fun getProjectsOrganization(
        organizationId: String,
        pageSize: Int = 100,
        page: Int = 1,
        searchQuery: String = ""
    ): PaginatedProjectsAllInformationsResponseDto = withContext(Dispatchers.IO) {
        try {
            api.getProjectsOrganization(
                organizationId,
                page,
                pageSize,
                if (searchQuery.isBlank()) null else searchQuery
            )
        } catch (e: Throwable) {
            throw mapToApiError(e, "getProjectsOrganization", "Org: $organizationId")
        }
    }

    private fun mapToApiError(error: Throwable, operation: String, context: String? = null): Exception {
        return when (error) {
            is HttpException -> {
                val code = error.code()
                val msgBody = error.response()?.errorBody()?.string()
                val detail = msgBody ?: error.message()
                when (code) {
                    401 -> Exception("API Authentication Failed: Invalid or missing API Key. Please check settings.")
                    403 -> Exception("API Authorization Failed for $operation: Access Denied. Check permissions.")
                    404 -> Exception("API Error: Resource not found for $operation. Context: ${context ?: "N/A"}.")
                    400 -> Exception("API Error: Invalid Request for $operation. $detail")
                    429 -> Exception("API Rate Limit Exceeded for $operation. Please try later.")
                    in 500..599 -> Exception("Server Error ($code) during $operation. Please try later.")
                    else -> Exception("API Error ($code) during $operation: $detail")
                }
            }
            is IOException -> Exception("Network Error for $operation: Could not reach server at $baseUrl.")
            else -> Exception("Unexpected Error during $operation: ${error.message}")
        }
    }
}
