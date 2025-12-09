package com.cybedefend.services

import OrganizationInformationsResponseDto
import PaginatedProjectsAllInformationsResponseDto
import ProjectConfig
import ProjectInformationsResponseDto
import TeamInformationsResponseDto
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class AuthService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(AuthService::class.java)
        private const val PROJECT_ID_KEY_PREFIX = "cybedefendProjectId_"
        private const val ORG_ID_KEY_PREFIX = "cybedefendOrgId_"
        private const val SERVICE_NAME = "CybeDefend"
        private const val REGION_KEY_PREFIX = "cybedefendApiRegion_"
        private const val KEY_NAME = "APIKey"
        fun getInstance(project: Project): AuthService = project.service<AuthService>()
    }

    private val properties = PropertiesComponent.getInstance(project)
    private val passwordSafe = PasswordSafe.instance

    @Volatile private var memoryCachedApiKey: String? = null

    private val credentialAttributes =
        CredentialAttributes(generateServiceName(SERVICE_NAME, KEY_NAME))

    suspend fun ensureProjectConfigurationIsSet(api: ApiService): ProjectConfig? {
        LOG.debug("Ensuring project configuration is set")
        var apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            LOG.debug("API key is null or blank, prompting user.")
            apiKey =
                    withContext(Dispatchers.Main) {
                        Messages.showPasswordDialog(
                                project,
                                "Enter CybeDefend API Key",
                                "API Key",
                                Messages.getQuestionIcon()
                        )
                    }
            if (apiKey.isNullOrBlank()) {
                LOG.warn("API key not provided by user. Configuration cancelled.")
                quickError("API key not provided. Configuration cancelled.")
                return null
            }
            LOG.debug("API key obtained from user input.")
            setApiKey(apiKey)
        } else {
            LOG.debug("API key found.")
        }

        val currentProjectRoot =
                project.basePath
                        ?: return run {
                            LOG.error("Project base path is null. Cannot configure.")
                            quickError("Project base path is null. Cannot configure.")
                            null
                        }
        LOG.debug("Project root: $currentProjectRoot")

        getCurrentWorkspaceProjectId(currentProjectRoot)?.let { storedProjectId ->
            val storedOrgId = properties.getValue(ORG_ID_KEY_PREFIX + currentProjectRoot, "")
            LOG.debug(
                    "Found stored project ID: $storedProjectId and org ID: $storedOrgId for root: $currentProjectRoot"
            )
            return ProjectConfig(apiKey, storedProjectId, currentProjectRoot, storedOrgId)
        }
        LOG.debug(
                "No stored project ID found for root: $currentProjectRoot. Proceeding with full configuration flow."
        )

        val selectedOrgId =
                selectOrganization(api)
                        ?: return run {
                            LOG.warn("Organization selection failed or was cancelled.")
                            null
                        }
        LOG.debug("Selected organization ID: $selectedOrgId")

        detectGitRepoName(currentProjectRoot)?.let { repoName ->
            LOG.debug("Detected git repo: $repoName at root $currentProjectRoot")
            quickInfo("Detected git repo: $repoName")
            val config = tryAutoLinkRepo(api, selectedOrgId, currentProjectRoot, repoName, apiKey)
            if (config != null) {
                LOG.debug("Auto-linked repo successfully. Project ID: ${config.projectId}")
                return config
            }
            LOG.debug("Auto-linking failed or no matching project found for repo: $repoName")
        }

        LOG.debug("Falling back to list projects for manual selection/creation.")
        return fallbackToListProjects(apiKey, selectedOrgId, currentProjectRoot, api)
    }

    /**
     * Returns the selected API region for the current workspace.
     * Defaults to US when not set or path is missing.
     */
    fun getWorkspaceApiRegion(): ApiRegion { // NEW
        val root = project.basePath ?: return ApiRegion.US
        val persisted = properties.getValue(REGION_KEY_PREFIX + root)
        val region = ApiRegion.fromPersisted(persisted)
        LOG.debug("getWorkspaceApiRegion for '$root' -> $region")
        return region
    }

    /**
     * Persists the selected API region for the current workspace.
     */
    fun storeWorkspaceApiRegion(region: ApiRegion) { // NEW
        val root = project.basePath ?: run {
            LOG.warn("Cannot store API region, project base path is null.")
            return
        }
        LOG.debug("storeWorkspaceApiRegion for '$root' -> $region")
        properties.setValue(REGION_KEY_PREFIX + root, region.name)
    }

    /**
     * Returns the effective base URL for API calls.
     * Considers debug mode (CYBEDEFEND_DEBUG=true) which overrides to localhost.
     */
    fun getBaseApiUrl(): String {
        val region = getWorkspaceApiRegion()
        return ApiRegion.getEffectiveBaseUrl(region)
    }

    fun getApiKey(): String? {
        LOG.debug("Attempting to get API key.")
        memoryCachedApiKey?.let {
            LOG.debug("Returning API key from memory cache.")
            return it
        }
        LOG.debug("Memory cache miss. Trying PasswordSafe.")
        val keyFromPasswordSafe = passwordSafe.getPassword(credentialAttributes)
        if (keyFromPasswordSafe != null) {
            LOG.debug("API key retrieved from PasswordSafe.")
            this.memoryCachedApiKey = keyFromPasswordSafe
        } else {
            LOG.debug("API key not found in PasswordSafe.")
        }
        return keyFromPasswordSafe
    }

    fun setApiKey(apiKey: String) {
        LOG.debug("Setting API key. Storing in PasswordSafe and memory cache.")
        passwordSafe.setPassword(credentialAttributes, apiKey)
        this.memoryCachedApiKey = apiKey
        LOG.info("API key has been set.")
    }

    fun resetCredentials() {
        LOG.debug("Resetting credentials.")
        passwordSafe.setPassword(credentialAttributes, null)
        memoryCachedApiKey = null
        project.basePath?.let { root ->
            LOG.debug("Clearing stored project ID and org ID for root: $root")
            properties.unsetValue("$PROJECT_ID_KEY_PREFIX$root")
            properties.unsetValue("$ORG_ID_KEY_PREFIX$root")
        }
        LOG.info("Credentials have been reset.")
    }

    fun getWorkspaceProjectId(): String? {
        val currentProjectRoot =
                project.basePath
                        ?: return run {
                            LOG.warn("Cannot get workspace project ID, project base path is null.")
                            null
                        }
        val projectId = properties.getValue(PROJECT_ID_KEY_PREFIX + currentProjectRoot)
        LOG.debug("Retrieved workspace project ID: $projectId for path: $currentProjectRoot")
        return projectId
    }

    fun storeWorkspaceProjectId(id: String) {
        val currentProjectRoot =
                project.basePath
                        ?: return run {
                            LOG.warn(
                                    "Cannot store workspace project ID, project base path is null."
                            )
                        }
        LOG.debug("Storing workspace project ID: $id for path: $currentProjectRoot")
        properties.setValue(PROJECT_ID_KEY_PREFIX + currentProjectRoot, id.trim())
    }

    private fun getCurrentWorkspaceProjectId(root: String): String? {
        val projectId = properties.getValue(PROJECT_ID_KEY_PREFIX + root)
        LOG.debug("Getting current workspace project ID for root '$root': $projectId")
        return projectId
    }

    private fun setWorkspaceProjectId(root: String, projectId: String, orgId: String) {
        LOG.debug(
                "Setting workspace project ID for root '$root' to '$projectId' and orgId to '$orgId'"
        )
        properties.setValue(PROJECT_ID_KEY_PREFIX + root, projectId)
        properties.setValue(ORG_ID_KEY_PREFIX + root, orgId)
    }

    private suspend fun quickInfo(message: String) =
            withContext(Dispatchers.Main) {
                LOG.debug("Displaying quick info: $message")
                Messages.showInfoMessage(project, message, "CybeDefend Info")
            }

    private suspend fun quickError(message: String) =
            withContext(Dispatchers.Main) {
                LOG.warn("Displaying quick error: $message")
                Messages.showErrorDialog(project, message, "CybeDefend Error")
            }

    private suspend fun selectOrganization(api: ApiService): String? {
        LOG.debug("Attempting to select organization.")
        return withContext(Dispatchers.Default) {
            val orgDtos: List<OrganizationInformationsResponseDto> =
                    try {
                        LOG.debug("Fetching organizations from API.")
                        api.getOrganizations() // This is a suspend call
                    } catch (e: Exception) {
                        LOG.error("Error retrieving organizations: ${e.message}", e)
                        quickError("Error retrieving organizations: ${e.message}")
                        return@withContext null
                    }

            if (orgDtos.isEmpty()) {
                LOG.warn("No organizations found for the account.")
                quickError("No organizations found for your account.")
                return@withContext null
            }
            LOG.debug("Found ${orgDtos.size} organizations.")

            if (orgDtos.size == 1) {
                val org = orgDtos.first()
                LOG.debug("Auto-selected single organization: ID ${org.id}, Name ${org.name}")
                return@withContext org.id
            }

            val names = orgDtos.map { it.name }.toTypedArray()
            LOG.debug("Prompting user to select from ${names.size} organizations.")
            val chosenName =
                    withContext(Dispatchers.Main) {
                        Messages.showEditableChooseDialog(
                                "Select an organization:",
                                "Organization Selection",
                                Messages.getQuestionIcon(),
                                names,
                                names.first(),
                                null
                        )
                    }

            if (chosenName == null) {
                LOG.debug("User cancelled organization selection.")
                return@withContext null
            }

            val chosenOrg = orgDtos.firstOrNull { it.name == chosenName }
            if (chosenOrg != null) {
                LOG.debug("User selected organization: ID ${chosenOrg.id}, Name ${chosenOrg.name}")
            } else {
                LOG.warn(
                        "Selected organization name '$chosenName' not found in the list. This shouldn't happen."
                )
            }
            chosenOrg?.id
        }
    }

    private fun detectGitRepoName(workspaceRoot: String): String? {
        LOG.debug("Detecting git repo name for workspace root: $workspaceRoot")
        return try {
            val gitDir = Paths.get(workspaceRoot, ".git")
            if (gitDir.toFile().exists() && gitDir.toFile().isDirectory) {
                val repoName = Paths.get(workspaceRoot).fileName.toString()
                LOG.debug("Git repository detected: $repoName")
                repoName
            } else {
                LOG.debug("No .git directory found at $workspaceRoot")
                null
            }
        } catch (e: Exception) {
            LOG.warn("Error detecting git repo name: ${e.message}", e)
            null
        }
    }

    private suspend fun tryAutoLinkRepo(
            api: ApiService,
            orgId: String,
            root: String,
            repoName: String,
            apiKey: String
    ): ProjectConfig? {
        LOG.debug("Attempting to auto-link repo '$repoName' in org '$orgId' at root '$root'.")
        try {
            val projectsDto =
                    api.getProjectsOrganization(orgId, pageSize = 20, page = 1) // Suspend call (API max is 20)
            val matchingProject =
                    projectsDto.projects.firstOrNull { it.name.equals(repoName, ignoreCase = true) }

            if (matchingProject != null) {
                LOG.info(
                        "Auto-linking to existing project '${matchingProject.name}' (ID: ${matchingProject.projectId})"
                )
                setWorkspaceProjectId(root, matchingProject.projectId, orgId)
                quickInfo("Auto-linked to existing project: ${matchingProject.name}")
                return ProjectConfig(apiKey, matchingProject.projectId, root, orgId)
            }
            LOG.debug("No project found with name matching '$repoName' for auto-linking.")
        } catch (e: Exception) {
            LOG.error("Error during auto-linking repo '$repoName': ${e.message}", e)
            quickError("Error trying to auto-link repository: ${e.message}")
        }
        return null
    }

    private suspend fun fallbackToListProjects(
            apiKey: String,
            orgId: String,
            root: String,
            api: ApiService
    ): ProjectConfig? {
        LOG.debug(
                "Fallback: Listing projects for manual selection/creation for org '$orgId' at root '$root'."
        )

        val teamId =
                selectTeam(api, orgId)
                        ?: return run {
                            LOG.warn("Team selection failed or was cancelled during fallback.")
                            null
                        }
        LOG.debug("Selected team ID: $teamId for fallback.")

        val existingProjectConfig = selectExistingProject(api, orgId, teamId, root, apiKey)
        if (existingProjectConfig != null) {
            LOG.debug(
                    "Selected existing project: ${existingProjectConfig.projectId} during fallback."
            )
            return existingProjectConfig
        }
        LOG.debug(
                "No existing project selected or user opted to create new. Proceeding to create new project flow."
        )
        return createNewProject(apiKey, orgId, root, api, teamId)
    }

    private suspend fun selectTeam(api: ApiService, orgId: String): String? =
            withContext(Dispatchers.Default) {
                LOG.debug("Attempting to select team for organization ID: $orgId")
                val teamDtos: List<TeamInformationsResponseDto> =
                        try {
                            LOG.debug("Fetching teams for org ID $orgId from API.")
                            api.getTeams(orgId) // Suspend call
                        } catch (e: Exception) {
                            LOG.error("Failed to get teams for org $orgId: ${e.message}", e)
                            quickError("Failed to get teams: ${e.message}")
                            return@withContext null
                        }

                if (teamDtos.isEmpty()) {
                    LOG.warn("No teams found in organization $orgId.")
                    quickError("No teams found in organization $orgId")
                    return@withContext null
                }
                LOG.debug("Found ${teamDtos.size} teams for org $orgId.")

                if (teamDtos.size == 1) {
                    val team = teamDtos.first()
                    LOG.debug("Auto-selected single team: ID ${team.id}, Name ${team.name}")
                    return@withContext team.id
                }

                val teamNames = teamDtos.map { it.name }.toTypedArray()
                LOG.debug("Prompting user to select from ${teamNames.size} teams.")
                val chosenName =
                        withContext(Dispatchers.Main) {
                            Messages.showEditableChooseDialog(
                                    "Select a team:",
                                    "Team Selection",
                                    Messages.getQuestionIcon(),
                                    teamNames,
                                    teamNames.first(),
                                    null
                            )
                        }

                if (chosenName == null) {
                    LOG.debug("User cancelled team selection.")
                    return@withContext null
                }

                val chosenTeam = teamDtos.firstOrNull { it.name == chosenName }
                if (chosenTeam != null) {
                    LOG.debug("User selected team: ID ${chosenTeam.id}, Name ${chosenTeam.name}")
                } else {
                    LOG.warn("Selected team name '$chosenName' not found in the list.")
                }
                chosenTeam?.id
            }

    private suspend fun selectExistingProject(
            api: ApiService,
            orgId: String,
            teamId: String,
            rootPath: String,
            apiKey: String
    ): ProjectConfig? =
            withContext(Dispatchers.Default) {
                LOG.debug(
                        "Attempting to select existing project for org ID '$orgId', team ID '$teamId', root '$rootPath'."
                )

                val projectsDto: PaginatedProjectsAllInformationsResponseDto =
                        try {
                            LOG.debug(
                                    "Fetching projects for org $orgId (team filter might be implicit or applied if API supports it, page 1, size 20)."
                            )
                            api.getProjectsOrganization(
                                    orgId,
                                    pageSize = 20,
                                    page = 1
                            ) // Suspend call (API max is 20)
                        } catch (e: Exception) {
                            LOG.error("Failed to get projects for org $orgId: ${e.message}", e)
                            quickError("Failed to get projects: ${e.message}")
                            return@withContext null
                        }

                // Placeholder: If ProjectsResponseDto.ProjectShort doesn't have teamId, this
                // filtering won't work.
                // Actual filtering might be needed if API doesn't support teamId for project
                // listing or if DTOs need adjustment.
                val teamProjects =
                        projectsDto.projects // .filter { it.teamId == teamId } // Assuming
                // ProjectShort has teamId

                val projectNames = teamProjects.map { it.name }.toTypedArray()
                val choices = projectNames + "[Create New Project]"

                LOG.debug(
                        "Prompting user to select from ${projectNames.size} existing projects or create new."
                )
                val choice =
                        withContext(Dispatchers.Main) {
                            Messages.showEditableChooseDialog(
                                    "Select an existing project or choose '[Create New Project]':",
                                    "Project Selection",
                                    Messages.getQuestionIcon(),
                                    choices,
                                    if (choices.size == 1) choices.first()
                                    else (projectNames.firstOrNull() ?: choices.first()),
                                    null
                            )
                        }
                                ?: return@withContext run {
                                    LOG.debug("User cancelled project selection.")
                                    null
                                }

                if (choice == "[Create New Project]") {
                    LOG.debug("User opted to create a new project.")
                    return@withContext null
                }

                val selectedProject = teamProjects.firstOrNull { it.name == choice }
                if (selectedProject != null) {
                    LOG.debug(
                            "User selected existing project: ID ${selectedProject.projectId}, Name ${selectedProject.name}"
                    )
                    setWorkspaceProjectId(rootPath, selectedProject.projectId, orgId)
                    quickInfo("Linked to existing project: ${selectedProject.name}")
                    return@withContext ProjectConfig(
                            apiKey,
                            selectedProject.projectId,
                            rootPath,
                            orgId
                    )
                }

                LOG.warn(
                        "Selected project name '$choice' not found in the list. This shouldn't happen if not creating new."
                )
                return@withContext null
            }

    private suspend fun createNewProject(
            apiKey: String,
            orgId: String,
            rootPath: String,
            api: ApiService,
            teamId: String
    ): ProjectConfig? =
            withContext(Dispatchers.Default) {
                LOG.debug(
                        "Attempting to create new project for org ID '$orgId', team ID '$teamId', root '$rootPath'."
                )

                val projectNameFromInput =
                        withContext(Dispatchers.Main) {
                            Messages.showInputDialog(
                                    project,
                                    "Enter new project name:",
                                    "Create New Project",
                                    Messages.getQuestionIcon()
                            )
                        }
                if (projectNameFromInput.isNullOrBlank()) {
                    LOG.warn("New project name is blank, creation cancelled.")
                    quickError("Project name cannot be empty.")
                    return@withContext null
                }
                LOG.debug("User entered project name: '$projectNameFromInput'")

                try {
                    LOG.debug(
                            "Calling API to create project '$projectNameFromInput' for team '$teamId'."
                    )
                    val newProjectDto: ProjectInformationsResponseDto =
                            api.createProject(teamId, projectNameFromInput) // Suspend call
                    LOG.info(
                            "Project '${newProjectDto.name}' (ID: ${newProjectDto.projectId}) created successfully via API."
                    )
                    setWorkspaceProjectId(rootPath, newProjectDto.projectId, orgId)
                    quickInfo("Project '${newProjectDto.name}' created and linked.")
                    return@withContext ProjectConfig(
                            apiKey,
                            newProjectDto.projectId,
                            rootPath,
                            orgId
                    )
                } catch (e: Exception) {
                    LOG.error("Failed to create project '$projectNameFromInput': ${e.message}", e)
                    quickError("Failed to create project '$projectNameFromInput': ${e.message}")
                    return@withContext null
                }
            }
}
