package com.cybedefend.services

import AddMessageConversationRequestDto
import GetOrganizationsResponseDto
import GetProjectIacVulnerabilitiesResponse
import GetProjectSastVulnerabilitiesResponse
import GetProjectScaVulnerabilitiesResponse
import GetProjectVulnerabilityByIdResponse
import GetRepositoriesResponseDto
import GetTeamsResponseDto
import InitiateConversationResponse
import OrganizationInformationsResponseDto
import PaginatedProjectsAllInformationsResponseDto
import ProjectInformationsResponseDto
import RepositoryDto
import ScanResponseDto
import StartConversationRequestDto
import StartScanResponseDto
import TeamInformationsResponseDto
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import retrofit2.http.POST
import retrofit2.http.Path

/** Retrofit API definitions matching the backend endpoints. */
private interface ApiServiceApi {
    @POST("project/{projectId}/scan/start")
    suspend fun startScan(@Path("projectId") projectId: String): StartScanResponseDto

    @PUT
    suspend fun uploadFile(
            @Url url: String,
            @Body body: RequestBody,
            @HeaderMap headers: Map<String, String>
    ): Response<Void>

    // AJOUTEZ ces trois fonctions spécifiques
    @GET("project/{projectId}/results/sast")
    suspend fun getSastResults(
            @Path("projectId") projectId: String,
            @Query("pageNumber") pageNumber: Int = 1,
            @Query("pageSizeNumber") pageSizeNumber: Int = 50,
            @Query("severity") severity: List<String>? = null
    ): GetProjectSastVulnerabilitiesResponse

    @GET("project/{projectId}/results/iac")
    suspend fun getIacResults(
            @Path("projectId") projectId: String,
            @Query("pageNumber") pageNumber: Int = 1,
            @Query("pageSizeNumber") pageSizeNumber: Int = 50,
            @Query("severity") severity: List<String>? = null
    ): GetProjectIacVulnerabilitiesResponse

    @GET("project/{projectId}/results/sca")
    suspend fun getScaResults(
            @Path("projectId") projectId: String,
            @Query("pageNumber") pageNumber: Int = 1,
            @Query("pageSizeNumber") pageSizeNumber: Int = 50,
            @Query("severity") severity: List<String>? = null
    ): GetProjectScaVulnerabilitiesResponse

    @GET("project/{projectId}/results/sast/{vulnerabilityId}")
    suspend fun getSastVulnerabilityDetails(
            @Path("projectId") projectId: String,
            @Path("vulnerabilityId") vulnerabilityId: String
    ): GetProjectVulnerabilityByIdResponse

    @GET("project/{projectId}/results/iac/{vulnerabilityId}")
    suspend fun getIacVulnerabilityDetails(
            @Path("projectId") projectId: String,
            @Path("vulnerabilityId") vulnerabilityId: String
    ): GetProjectVulnerabilityByIdResponse

    @GET("project/{projectId}/results/sca/{vulnerabilityId}")
    suspend fun getScaVulnerabilityDetails(
            @Path("projectId") projectId: String,
            @Path("vulnerabilityId") vulnerabilityId: String
    ): GetProjectVulnerabilityByIdResponse

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

    @GET("organizations") suspend fun getOrganizations(): GetOrganizationsResponseDto

    @GET("organization/{organizationId}/github/repositories")
    suspend fun getRepositories(
            @Path("organizationId") organizationId: String
    ): GetRepositoriesResponseDto

    @GET("organization/{organizationId}/teams")
    suspend fun getTeams(@Path("organizationId") organizationId: String): GetTeamsResponseDto

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

/** ApiService wraps the Retrofit API and handles authentication and error mapping. */
class ApiService(val authService: AuthService) {
    private val api: ApiServiceApi
    val baseUrl: String = "https://api-us.cybedefend.com/" // Ensure trailing slash

    init {
        println("ApiService init: Starting initialization.")

        val client =
                OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            // Pull the key once per request from AuthService
                            val apiKey = authService.getApiKey()
                            val req =
                                    if (!apiKey.isNullOrBlank()) {
                                        chain.request()
                                                .newBuilder()
                                                // adapt the header name/value to your backend
                                                .addHeader(
                                                        "X-API-Key",
                                                        apiKey
                                                ) // <- use this line instead if your API expects it
                                                .build()
                                    } else {
                                        chain.request()
                                    }
                            chain.proceed(req)
                        }
                        // ----------------------------------------------------------------
                        // keep any other interceptors you already had
                        // .addInterceptor(HttpLoggingInterceptor().apply { level =
                        // HttpLoggingInterceptor.Level.BODY })
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .writeTimeout(120, TimeUnit.SECONDS)
                        .build()

        val retrofit =
                Retrofit.Builder()
                        .baseUrl(baseUrl) // Set the base URL
                        .client(client) // Set the custom OkHttpClient
                        .addConverterFactory(GsonConverterFactory.create()) // Add Gson converter
                        .build()
        println(
                "ApiService init: Retrofit instance built with base URL, client, and Gson converter."
        )

        api = retrofit.create(ApiServiceApi::class.java)
        println("ApiService init: Retrofit API interface created. Initialization complete.")
    }

    suspend fun startScan(projectId: String): StartScanResponseDto =
            withContext(Dispatchers.IO) {
                try {
                    api.startScan(projectId)
                } catch (e: Throwable) {
                    throw mapToApiError(e, "startScan", "ProjectId: $projectId")
                }
            }

    suspend fun uploadFileToSignedUrl(url: String, file: File) =
            withContext(Dispatchers.IO) {
                try {
                    val requestBody = file.asRequestBody("application/zip".toMediaTypeOrNull())

                    // Déterminer les en-têtes nécessaires en fonction du fournisseur de cloud
                    val headers = mutableMapOf("Content-Type" to "application/zip")
                    if (url.contains("storage.googleapis.com")) {
                        headers["x-goog-if-generation-match"] = "0"
                        headers["x-goog-content-length-range"] = "0,5368709120"
                    }

                    val response = api.uploadFile(url = url, body = requestBody, headers = headers)

                    // La version corrigée
                    if (!response.isSuccessful) {
                        // response.code() est une fonction, response.errorBody() aussi.
                        val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                        throw IOException("Upload failed with code ${response.code()}: $errorMsg")
                    }
                } catch (e: Throwable) {
                    throw mapToApiError(e, "uploadFileToSignedUrl")
                }
            }

    suspend fun getSastResults(
            projectId: String,
            pageNumber: Int = 1,
            pageSizeNumber: Int = 50,
            severity: List<String>? = null
    ): GetProjectSastVulnerabilitiesResponse =
            withContext(Dispatchers.IO) {
                try {
                    api.getSastResults(projectId, pageNumber, pageSizeNumber, severity)
                } catch (e: Throwable) {
                    throw mapToApiError(e, "getSastResults", "ProjectId: $projectId")
                }
            }

    suspend fun getIacResults(
            projectId: String,
            pageNumber: Int = 1,
            pageSizeNumber: Int = 50,
            severity: List<String>? = null
    ): GetProjectIacVulnerabilitiesResponse =
            withContext(Dispatchers.IO) {
                try {
                    api.getIacResults(projectId, pageNumber, pageSizeNumber, severity)
                } catch (e: Throwable) {
                    throw mapToApiError(e, "getIacResults", "ProjectId: $projectId")
                }
            }

    suspend fun getScaResults(
            projectId: String,
            pageNumber: Int = 1,
            pageSizeNumber: Int = 50,
            severity: List<String>? = null
    ): GetProjectScaVulnerabilitiesResponse =
            withContext(Dispatchers.IO) {
                try {
                    api.getScaResults(projectId, pageNumber, pageSizeNumber, severity)
                } catch (e: Throwable) {
                    throw mapToApiError(e, "getScaResults", "ProjectId: $projectId")
                }
            }

    suspend fun getVulnerabilityDetails(
            projectId: String,
            vulnerabilityId: String,
            scanType: String
    ): GetProjectVulnerabilityByIdResponse =
            withContext(Dispatchers.IO) {
                try {
                    // Ce "when" agit comme un routeur pour appeler le bon endpoint
                    when (scanType.lowercase()) {
                        "sast" -> api.getSastVulnerabilityDetails(projectId, vulnerabilityId)
                        "iac" -> api.getIacVulnerabilityDetails(projectId, vulnerabilityId)
                        "sca" -> api.getScaVulnerabilityDetails(projectId, vulnerabilityId)
                        else ->
                                throw IllegalArgumentException(
                                        "Unknown scanType for details: $scanType"
                                )
                    }
                } catch (e: Throwable) {
                    throw mapToApiError(
                            e,
                            "getVulnerabilityDetails",
                            "VulnerabilityId: $vulnerabilityId"
                    )
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

    suspend fun startConversation(
            projectId: String,
            request: StartConversationRequestDto
    ): InitiateConversationResponse = // Added projectId
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
    ): InitiateConversationResponse =
            withContext(Dispatchers.IO) {
                // The request DTO already contains projectId and idConversation,
                // but the API path requires them separately.
                if (request.idConversation.isBlank() || request.projectId.isBlank()) {
                    // This check might be redundant if we enforce projectId and conversationId as
                    // parameters to this function
                    throw IllegalArgumentException(
                            "Project ID and Conversation ID must be set in the request for continueConversation."
                    )
                }
                try {
                    api.continueConversation(projectId, conversationId, request)
                } catch (e: Throwable) {
                    throw mapToApiError(
                            e,
                            "continueConversation",
                            "ConversationId: $conversationId"
                    )
                }
            }

    suspend fun getOrganizations(): List<OrganizationInformationsResponseDto> =
            withContext(Dispatchers.IO) {
                try {
                    api.getOrganizations().organizations
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
                    api.getTeams(organizationId).teams
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
    ): RepositoryDto =
            withContext(Dispatchers.IO) {
                try {
                    api.linkProject(
                            organizationId,
                            projectId,
                            mapOf("repositoryId" to repositoryId)
                    )
                } catch (e: Throwable) {
                    throw mapToApiError(
                            e,
                            "linkProject",
                            "ProjectId: $projectId, RepositoryId: $repositoryId"
                    )
                }
            }

    suspend fun getProjectsOrganization(
            organizationId: String, // Added organizationId
            page: Int = 1, // Added page with default
            pageSize: Int = 20, // Added pageSize with default
            searchQuery: String? = null // searchQuery was String = "", changed to String? = null
    ): PaginatedProjectsAllInformationsResponseDto =
            withContext(Dispatchers.IO) {
                try {
                    api.getProjectsOrganization(organizationId, page, pageSize, searchQuery)
                } catch (e: Throwable) {
                    throw mapToApiError(
                            e,
                            "getProjectsOrganization",
                            "OrganizationId: $organizationId"
                    )
                }
            }

    private fun mapToApiError(
            error: Throwable,
            operation: String,
            context: String? = null
    ): Exception {
        // Consider adding HttpLoggingInterceptor for detailed request/response logs during
        // development
        // e.g. HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        println(
                "ApiService Error in operation \'$operation\' (Context: $context): ${error.javaClass.simpleName} - ${error.message}"
        )
        error.printStackTrace() // For more detailed logs in the console

        return when (error) {
            is HttpException -> {
                val code = error.code()
                val errorBody = error.response()?.errorBody()?.string()
                val detail = if (errorBody.isNullOrBlank()) error.message() else errorBody
                val errorMessage =
                        when (code) {
                            400 ->
                                    "API Error: Invalid Request for \'$operation\'. $detail (Context: $context)"
                            401 ->
                                    "API Authentication Failed: Invalid or missing API Key. Please check settings. (Operation: \'$operation\')"
                            403 ->
                                    "API Authorization Failed for \'$operation\': Access Denied. Check permissions. (Context: $context)"
                            404 ->
                                    "API Error: Resource not found for \'$operation\'. (Context: $context)"
                            429 ->
                                    "API Rate Limit Exceeded for \'$operation\'. Please try later. (Context: $context)"
                            in 500..599 ->
                                    "Server Error ($code) during \'$operation\'. Please try later. (Context: $context)"
                            else ->
                                    "API Error ($code) during \'$operation\': $detail (Context: $context)"
                        }
                println("Mapped HttpException ($code) for \'$operation\': $errorMessage")
                Exception(errorMessage, error)
            }
            is IOException -> {
                // This often means network connectivity issues or server not reachable
                val networkErrorMessage =
                        "Network Error for \'$operation\': Could not reach server at $baseUrl. Please check your internet connection and VPN settings. (Context: $context)"
                println("Mapped IOException for \'$operation\': $networkErrorMessage")
                Exception(networkErrorMessage, error)
            }
            else -> {
                val unexpectedErrorMessage =
                        "Unexpected Error during \'$operation\': ${error.message ?: "Unknown error"}. (Context: $context)"
                println("Mapped Unexpected Error for \'$operation\': $unexpectedErrorMessage")
                Exception(unexpectedErrorMessage, error)
            }
        }
    }
}
