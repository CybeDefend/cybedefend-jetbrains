package com.cybedefend.services

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Paths

/**
 * Configuration de projet retournée après authentification.
 */
data class ProjectConfig(
    val apiKey: String,
    val projectId: String,
    val workspaceRoot: String,
    val organizationId: String
)

/**
 * Service gérant l'authentification et la sélection de projet.
 *
 * Toutes les boîtes de dialogue Swing (Messages.*) doivent être affichées sur l’EDT,
 * donc on les encapsule systématiquement dans `withContext(Dispatchers.Main)`.
 */
@Service(Level.PROJECT)
class AuthService(private val project: Project) {

    companion object {
        private const val SECRET_API_KEY = "cybedefendScannerApiKey"
        private const val PROJECT_ID_KEY_PREFIX = "cybedefendWorkspaceProjectId:"
        private const val ORG_ID_KEY_PREFIX = "cybedefendWorkspaceOrgId:"

        fun getInstance(project: Project): AuthService =
            project.getService(AuthService::class.java)
    }

    private val properties = PropertiesComponent.getInstance(project)
    private val passwordSafe = PasswordSafe.instance

    /**
     * Assure que la configuration (API-Key + Project) est en place.
     * Affiche plusieurs dialogues si nécessaire ; tous sont forcés sur l’EDT.
     */
    suspend fun ensureProjectConfigurationIsSet(apiService: ApiService): ProjectConfig? =
        withContext(Dispatchers.Default) {
            /* ---------- 1. API-Key ---------- */
            var apiKey = getApiKey()
            if (apiKey.isNullOrBlank()) {
                apiKey = withContext(Dispatchers.Main) {
                    Messages.showPasswordDialog(
                        project,
                        "Enter API Key",
                        "Password",
                        Messages.getQuestionIcon()
                    )
                }
                if (apiKey.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        Messages.showErrorDialog(
                            project,
                            "API key not provided. Configuration cancelled.",
                            "Error"
                        )
                    }
                    return@withContext null
                }
                setApiKey(apiKey)
            }

            /* ---------- 2. Workspace ---------- */
            val workspaceRoot = project.basePath ?: return@withContext null
            getCurrentWorkspaceProjectId(workspaceRoot)?.let { existingId ->
                val existingOrg = properties.getValue(ORG_ID_KEY_PREFIX + workspaceRoot, "")
                return@withContext ProjectConfig(apiKey, existingId, workspaceRoot, existingOrg)
            }

            /* ---------- 3. Organisation ---------- */
            val orgs = try {
                apiService.getOrganizations()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Messages.showErrorDialog(
                        project,
                        "Error retrieving organizations: ${e.message}",
                        "Error"
                    )
                }
                return@withContext null
            }
            if (orgs.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Messages.showErrorDialog(
                        project,
                        "No organizations found for your account.",
                        "Error"
                    )
                }
                return@withContext null
            }

            val selectedOrg = if (orgs.size == 1) {
                orgs.first()
            } else {
                val names = orgs.map { it.name }.toTypedArray()
                val chosenName = withContext(Dispatchers.Main) {
                    Messages.showEditableChooseDialog(
                        "Select an organization:",
                        "Organization Selection",
                        null,
                        names,
                        names.first(),
                        null
                    )
                } ?: return@withContext null
                orgs.first { it.name == chosenName }
            }
            val organizationId = selectedOrg.id

            /* ---------- 4. Détection repo Git ---------- */
            detectGitRepoName(workspaceRoot)?.let { repoName ->
                withContext(Dispatchers.Main) {
                    Messages.showInfoMessage(project, "Detected git repo: $repoName", "Info")
                }
                val repoData = try {
                    apiService.getRepositories(organizationId)
                } catch (_: Exception) {
                    null
                }
                val match = repoData?.repositories
                    ?.asSequence()
                    ?.flatMap { it.repository.asSequence() }
                    ?.find { it.name == repoName || it.fullName.endsWith("/$repoName") }

                if (match != null) {
                    /* --- repo déjà lié à un projet --- */
                    if (!match.projectId.isNullOrBlank()) {
                        setWorkspaceProjectId(workspaceRoot, match.projectId)
                        return@withContext ProjectConfig(apiKey, match.projectId, workspaceRoot, organizationId)
                    }
                    /* --- proposer la création + liaison --- */
                    val linkChoice = withContext(Dispatchers.Main) {
                        Messages.showYesNoDialog(
                            project,
                            "Link repository '$repoName' to a new CybeDefend project?",
                            "Link Repository",
                            null
                        )
                    }
                    if (linkChoice == Messages.YES) {
                        val newCfg = linkRepoToNewProject(
                            apiService,
                            organizationId,
                            workspaceRoot,
                            repoName,
                            match.id,
                            apiKey
                        ) ?: return@withContext null
                        return@withContext newCfg
                    }
                }
            }

            /* ---------- 5. Liste / création manuelle ---------- */
            fallbackToListProjects(apiKey, organizationId, workspaceRoot, apiService)
        }

    /* === Helpers ====================================================================== */

    fun getApiKey(): String? =
        PasswordSafe.instance.getPassword(CredentialAttributes(SECRET_API_KEY, SECRET_API_KEY))

    private fun setApiKey(apiKey: String) {
        PasswordSafe.instance.setPassword(CredentialAttributes(SECRET_API_KEY, SECRET_API_KEY), apiKey)
    }

    private fun getCurrentWorkspaceProjectId(root: String): String? =
        properties.getValue(PROJECT_ID_KEY_PREFIX + root)

    private fun setWorkspaceProjectId(root: String, projectId: String) {
        properties.setValue(PROJECT_ID_KEY_PREFIX + root, projectId)
    }

    private fun detectGitRepoName(root: String): String? {
        val cfg = Paths.get(root, ".git", "config").toFile().takeIf { it.exists() } ?: return null
        val url = Regex("url\\s*=\\s*(.*)").find(cfg.readText())?.groupValues?.get(1) ?: return null
        return Regex("[:/]([^/]+/[^/]+?)(\\.git)?$").find(url)?.groupValues?.get(1)
            ?.substringAfterLast('/')
    }

    /* ---------- repo → nouveau projet + lien ---------- */
    private suspend fun linkRepoToNewProject(
        api: ApiService,
        orgId: String,
        workspaceRoot: String,
        repoName: String,
        repoId: String,
        apiKey: String
    ): ProjectConfig? = withContext(Dispatchers.Default) {

        /* équipe */
        val teams = try { api.getTeams(orgId) } catch (_: Exception) { emptyList() }
        val selTeam = if (teams.size == 1) {
            teams.first()
        } else {
            val names = teams.map { it.name }.toTypedArray()
            val chosen = withContext(Dispatchers.Main) {
                Messages.showEditableChooseDialog(
                    "Select a team:",
                    "Team Selection",
                    null,
                    names,
                    names.first(),
                    null
                )
            } ?: return@withContext null
            teams.first { it.name == chosen }
        }

        /* création projet */
        val newProj = try { api.createProject(selTeam.id, repoName) } catch (_: Exception) { null }
            ?: return@withContext null

        /* liaison repo ←→ projet */
        try { api.linkProject(orgId, newProj.projectId, repoId) } catch (_: Exception) { /* best-effort */ }

        setWorkspaceProjectId(workspaceRoot, newProj.projectId)
        ProjectConfig(apiKey, newProj.projectId, workspaceRoot, orgId)
    }

    /* ---------- liste des projets existants ou création ---------- */
    private suspend fun fallbackToListProjects(
        apiKey: String,
        orgId: String,
        workspaceRoot: String,
        api: ApiService
    ): ProjectConfig? = withContext(Dispatchers.Default) {

        val page = try { api.getProjectsOrganization(orgId) } catch (_: Exception) {
            return@withContext fallbackToManualInput(apiKey, workspaceRoot)
        }

        val options = mutableListOf("Create New Project...").apply {
            addAll(page.projects.map { it.name })
        }.toTypedArray()

        val chosen = withContext(Dispatchers.Main) {
            Messages.showEditableChooseDialog(
                "Select or create project:",
                "Project Selection",
                null,
                options,
                options.first(),
                null
            )
        } ?: return@withContext null

        if (chosen == "Create New Project...") {
            handleCreateNewProject(apiKey, orgId, workspaceRoot, api)
        } else {
            val sel = page.projects.first { it.name == chosen }
            setWorkspaceProjectId(workspaceRoot, sel.projectId)
            ProjectConfig(apiKey, sel.projectId, workspaceRoot, orgId)
        }
    }

    /* ---------- création manuelle d’un nouveau projet ---------- */
    private suspend fun handleCreateNewProject(
        apiKey: String,
        orgId: String,
        workspaceRoot: String,
        api: ApiService
    ): ProjectConfig? = withContext(Dispatchers.Default) {

        /* équipe */
        val teams = try { api.getTeams(orgId) } catch (_: Exception) { emptyList() }
        val selTeam = if (teams.size == 1) {
            teams.first()
        } else {
            val names = teams.map { it.name }.toTypedArray()
            val chosen = withContext(Dispatchers.Main) {
                Messages.showEditableChooseDialog(
                    "Select a team for the new project:",
                    "Team Selection",
                    null,
                    names,
                    names.first(),
                    null
                )
            } ?: return@withContext null
            teams.first { it.name == chosen }
        }

        /* nom du projet */
        val projName = withContext(Dispatchers.Main) {
            Messages.showInputDialog(project, "Enter new project name:", "New Project", null)
        }?.trim().takeIf { !it.isNullOrBlank() } ?: return@withContext null

        val newProj = try { api.createProject(selTeam.id, projName) } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Messages.showErrorDialog(project, "Error creating project: ${e.message}", "Error")
            }
            return@withContext null
        }

        setWorkspaceProjectId(workspaceRoot, newProj.projectId)
        ProjectConfig(apiKey, newProj.projectId, workspaceRoot, orgId)
    }

    /* ---------- saisie manuelle de l’ID projet ---------- */
    private suspend fun fallbackToManualInput(
        apiKey: String,
        workspaceRoot: String
    ): ProjectConfig? {
        val id = withContext(Dispatchers.Main) {
            Messages.showInputDialog(
                project,
                "Enter the project ID manually:",
                "Project ID Required",
                null
            )
        }?.trim().takeIf { !it.isNullOrBlank() } ?: return null

        setWorkspaceProjectId(workspaceRoot, id)
        return ProjectConfig(apiKey, id, workspaceRoot, "")
    }

    /* ---------- utilitaire public ---------- */
    fun ensureApiKeyIsSet(): Boolean = getApiKey()?.isNotBlank() == true
}
