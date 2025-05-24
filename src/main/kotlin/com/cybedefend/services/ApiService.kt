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
import java.util.concurrent.TimeUnit

/**
 * Retrofit API definitions matching the backend endpoints.
 */
private interface ApiServiceApi {
    @Multipart
    @POST("project/{projectId}/scan/start")
    suspend fun startScan(
        @Path("projectId") projectId: String,
        @Part scan: MultipartBody.Part
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
    private val baseUrl: String = "http://localhost:3000/" // Ensure trailing slash

    init {
        println("ApiService init: Starting initialization.")


        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Pull the key once per request from AuthService
                val apiKey = authService.getApiKey()
                val req = if (!apiKey.isNullOrBlank()) {
                    chain.request().newBuilder()
                        // adapt the header name/value to your backend
                        .addHeader("X-API-Key", apiKey)      // <- use this line instead if your API expects it
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(req)
            }
            // ----------------------------------------------------------------
            // keep any other interceptors you already had
            //.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl) // Set the base URL
            .client(client)   // Set the custom OkHttpClient
            .addConverterFactory(GsonConverterFactory.create()) // Add Gson converter
            .build()
        println("ApiService init: Retrofit instance built with base URL, client, and Gson converter.")

        api = retrofit.create(ApiServiceApi::class.java)
        println("ApiService init: Retrofit API interface created. Initialization complete.")
    }

    suspend fun startScan(projectId: String, zipFilePath: String): StartScanResponseDto =
        withContext(Dispatchers.IO) {
            try {
                val file = File(zipFilePath)
                val requestFile = file.asRequestBody("application/zip".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("scan", file.name, requestFile)
                api.startScan(projectId, body) // Corrected to pass projectId
            } catch (e: Throwable) {
                throw mapToApiError(e, "startScan", "ProjectId: $projectId")
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
            api.getScanResults(projectId, scanType, pageNumber ?: 1, pageSizeNumber ?: 50, severity)
        } catch (e: Throwable) {
            throw mapToApiError(e, "getScanResults", "ProjectId: $projectId, ScanType: $scanType")
        }
    }

    suspend fun getVulnerabilityDetails(
        projectId: String,
        vulnerabilityId: String,
        scanType: String // scanType was missing as a parameter here, but present in the interface call
    ): GetProjectVulnerabilityByIdResponseDto = withContext(Dispatchers.IO) {
        try {
            api.getVulnerabilityDetails(projectId, scanType, vulnerabilityId)
        } catch (e: Throwable) {
            throw mapToApiError(e, "getVulnerabilityDetails", "VulnerabilityId: $vulnerabilityId")
        }
    }

    suspend fun getScanStatus(projectId: String, scanId: String): ScanResponseDto =
        withContext(Dispatchers.IO) {
            try {
                api.getScanStatus(projectId, scanId)
            } catch (e: Throwable) {
                throw mapToApiError(e, "getScanStatus", "ScanId: $scanId")
            }
        }

    suspend fun startConversation(projectId: String, request: StartConversationRequestDto): InitiateConversationResponse = // Added projectId
        withContext(Dispatchers.IO) {
            try {
                api.startConversation(projectId, request)
            } catch (e: Throwable) {
                throw mapToApiError(e, "startConversation", "ProjectId: $projectId")
            }
        }

    suspend fun continueConversation(
        projectId: String, // Added projectId
        conversationId: String, // Added conversationId
        request: AddMessageConversationRequestDto
    ): InitiateConversationResponse = withContext(Dispatchers.IO) {
        // The request DTO already contains projectId and idConversation,
        // but the API path requires them separately.
        if (request.idConversation.isBlank() || request.projectId.isBlank()) {
            // This check might be redundant if we enforce projectId and conversationId as parameters to this function
            throw IllegalArgumentException("Project ID and Conversation ID must be set in the request for continueConversation.")
        }
        try {
            api.continueConversation(projectId, conversationId, request)
        } catch (e: Throwable) {
            throw mapToApiError(e, "continueConversation", "ConversationId: $conversationId")
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
                throw mapToApiError(e, "getRepositories", "OrganizationId: $organizationId")
            }
        }

    suspend fun getTeams(organizationId: String): List<TeamInformationsResponseDto> =
        withContext(Dispatchers.IO) {
            try {
                api.getTeams(organizationId)
            } catch (e: Throwable) {
                throw mapToApiError(e, "getTeams", "OrganizationId: $organizationId")
            }
        }

    suspend fun createProject(teamId: String, projectName: String): ProjectInformationsResponseDto =
        withContext(Dispatchers.IO) {
            try {
                api.createProject(teamId, mapOf("name" to projectName))
            } catch (e: Throwable) {
                throw mapToApiError(e, "createProject", "TeamId: $teamId")
            }
        }

    suspend fun linkProject(
        organizationId: String,
        projectId: String,
        repositoryId: String // repositoryId was missing
    ): RepositoryDto = withContext(Dispatchers.IO) {
        try {
            api.linkProject(organizationId, projectId, mapOf("repositoryId" to repositoryId))
        } catch (e: Throwable) {
            throw mapToApiError(e, "linkProject", "ProjectId: $projectId, RepositoryId: $repositoryId")
        }
    }

    suspend fun getProjectsOrganization(
        organizationId: String, // Added organizationId
        page: Int = 1,          // Added page with default
        pageSize: Int = 20,     // Added pageSize with default
        searchQuery: String? = null // searchQuery was String = "", changed to String? = null
    ): PaginatedProjectsAllInformationsResponseDto = withContext(Dispatchers.IO) {
        try {
            api.getProjectsOrganization(organizationId, page, pageSize, searchQuery)
        } catch (e: Throwable) {
            throw mapToApiError(e, "getProjectsOrganization", "OrganizationId: $organizationId")
        }
    }

    private fun mapToApiError(error: Throwable, operation: String, context: String? = null): Exception {
        // Consider adding HttpLoggingInterceptor for detailed request/response logs during development
        // e.g. HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        println("ApiService Error in operation \'$operation\' (Context: $context): ${error.javaClass.simpleName} - ${error.message}")
        error.printStackTrace() // For more detailed logs in the console

        return when (error) {
            is HttpException -> {
                val code = error.code()
                val errorBody = error.response()?.errorBody()?.string()
                val detail = if (errorBody.isNullOrBlank()) error.message() else errorBody
                val errorMessage = when (code) {
                    400 -> "API Error: Invalid Request for \'$operation\'. $detail (Context: $context)"
                    401 -> "API Authentication Failed: Invalid or missing API Key. Please check settings. (Operation: \'$operation\')"
                    403 -> "API Authorization Failed for \'$operation\': Access Denied. Check permissions. (Context: $context)"
                    404 -> "API Error: Resource not found for \'$operation\'. (Context: $context)"
                    429 -> "API Rate Limit Exceeded for \'$operation\'. Please try later. (Context: $context)"
                    in 500..599 -> "Server Error ($code) during \'$operation\'. Please try later. (Context: $context)"
                    else -> "API Error ($code) during \'$operation\': $detail (Context: $context)"
                }
                println("Mapped HttpException ($code) for \'$operation\': $errorMessage")
                Exception(errorMessage, error)
            }
            is IOException -> {
                // This often means network connectivity issues or server not reachable
                val networkErrorMessage = "Network Error for \'$operation\': Could not reach server at $baseUrl. Please check your internet connection and VPN settings. (Context: $context)"
                println("Mapped IOException for \'$operation\': $networkErrorMessage")
                Exception(networkErrorMessage, error)
            }
            else -> {
                val unexpectedErrorMessage = "Unexpected Error during \'$operation\': ${error.message ?: "Unknown error"}. (Context: $context)"
                println("Mapped Unexpected Error for \'$operation\': $unexpectedErrorMessage")
                Exception(unexpectedErrorMessage, error)
            }
        }
    }
}

// Add missing import for TimeUnit if not present at the top of the file
// import java.util.concurrent.TimeUnit
// Add missing import for HttpLoggingInterceptor if you uncomment it
// import okhttp3.logging.HttpLoggingInterceptor
