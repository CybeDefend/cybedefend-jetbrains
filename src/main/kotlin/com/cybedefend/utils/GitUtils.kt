package com.cybedefend.utils

import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Utility object for Git operations.
 * Provides methods to detect Git repository information such as the current branch name.
 */
object GitUtils {
    private val LOG = Logger.getInstance(GitUtils::class.java)

    /**
     * Detects the current Git branch name for the given workspace root.
     * 
     * This method uses multiple strategies to detect the branch:
     * 1. First tries to read from .git/HEAD file directly (fastest, no external process)
     * 2. Falls back to executing 'git rev-parse --abbrev-ref HEAD' command
     * 
     * @param workspaceRoot The root directory of the workspace/project
     * @return The current branch name, or null if:
     *         - The directory is not a Git repository
     *         - Unable to determine the branch name
     *         - Any error occurs during detection
     */
    fun getCurrentBranch(workspaceRoot: String): String? {
        LOG.debug("Detecting current Git branch for workspace: $workspaceRoot")
        
        // Strategy 1: Read from .git/HEAD file (preferred - no external process needed)
        val branchFromHead = readBranchFromGitHead(workspaceRoot)
        if (branchFromHead != null) {
            LOG.info("Git branch detected from HEAD file: $branchFromHead")
            return branchFromHead
        }

        // Strategy 2: Use git command (fallback - handles edge cases like worktrees)
        val branchFromCommand = readBranchFromGitCommand(workspaceRoot)
        if (branchFromCommand != null) {
            LOG.info("Git branch detected from git command: $branchFromCommand")
            return branchFromCommand
        }

        LOG.warn("Could not detect Git branch for workspace: $workspaceRoot")
        return null
    }

    /**
     * Reads the current branch name directly from the .git/HEAD file.
     * 
     * The HEAD file typically contains either:
     * - A symbolic reference: "ref: refs/heads/branch-name"
     * - A commit SHA (detached HEAD state)
     * 
     * @param workspaceRoot The root directory of the workspace
     * @return The branch name if found, null otherwise
     */
    private fun readBranchFromGitHead(workspaceRoot: String): String? {
        return try {
            val gitDir = Paths.get(workspaceRoot, ".git")
            
            // Handle both regular .git directory and worktree .git file
            val actualGitDir = if (Files.isRegularFile(gitDir)) {
                // Worktree: .git is a file containing "gitdir: /path/to/actual/git/dir"
                val content = Files.readString(gitDir).trim()
                if (content.startsWith("gitdir:")) {
                    Paths.get(content.substringAfter("gitdir:").trim())
                } else {
                    LOG.debug(".git file has unexpected format")
                    return null
                }
            } else if (Files.isDirectory(gitDir)) {
                gitDir
            } else {
                LOG.debug("No .git directory or file found at $workspaceRoot")
                return null
            }

            val headFile = actualGitDir.resolve("HEAD")
            if (!Files.exists(headFile)) {
                LOG.debug("HEAD file not found in $actualGitDir")
                return null
            }

            val headContent = Files.readString(headFile).trim()
            
            // Parse the HEAD content
            when {
                headContent.startsWith("ref: refs/heads/") -> {
                    // Normal branch reference
                    headContent.substringAfter("ref: refs/heads/")
                }
                headContent.startsWith("ref:") -> {
                    // Other reference (e.g., refs/remotes/...)
                    val ref = headContent.substringAfter("ref:").trim()
                    LOG.debug("HEAD points to non-local ref: $ref")
                    ref.substringAfterLast("/")
                }
                headContent.matches(Regex("^[0-9a-f]{40}$")) -> {
                    // Detached HEAD state - return the short SHA
                    LOG.debug("Detached HEAD state detected: ${headContent.take(8)}")
                    "HEAD (${headContent.take(8)})"
                }
                else -> {
                    LOG.debug("Unexpected HEAD content format: $headContent")
                    null
                }
            }
        } catch (e: Exception) {
            LOG.debug("Error reading .git/HEAD: ${e.message}")
            null
        }
    }

    /**
     * Reads the current branch name using the git command line tool.
     * 
     * Executes: git rev-parse --abbrev-ref HEAD
     * 
     * @param workspaceRoot The root directory of the workspace
     * @return The branch name if successfully retrieved, null otherwise
     */
    private fun readBranchFromGitCommand(workspaceRoot: String): String? {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(File(workspaceRoot))
                .redirectErrorStream(false)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val branch = reader.readLine()?.trim()
            
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                LOG.debug("Git command exited with code: $exitCode")
                return null
            }

            // Handle detached HEAD case from git command
            if (branch == "HEAD") {
                // Try to get the short SHA for detached HEAD
                val shaProcess = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .directory(File(workspaceRoot))
                    .start()
                val sha = BufferedReader(InputStreamReader(shaProcess.inputStream))
                    .readLine()?.trim()
                shaProcess.waitFor()
                
                if (!sha.isNullOrBlank()) {
                    "HEAD ($sha)"
                } else {
                    branch
                }
            } else {
                branch?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            LOG.debug("Error executing git command: ${e.message}")
            null
        }
    }

    /**
     * Checks if the given directory is a Git repository.
     * 
     * @param workspaceRoot The root directory to check
     * @return true if the directory contains a .git directory or file, false otherwise
     */
    fun isGitRepository(workspaceRoot: String): Boolean {
        return try {
            val gitPath = Paths.get(workspaceRoot, ".git")
            Files.exists(gitPath)
        } catch (e: Exception) {
            LOG.debug("Error checking Git repository: ${e.message}")
            false
        }
    }

    /**
     * Gets the repository name from the workspace root.
     * 
     * @param workspaceRoot The root directory of the workspace
     * @return The repository name (directory name), or null if detection fails
     */
    fun getRepositoryName(workspaceRoot: String): String? {
        return try {
            if (isGitRepository(workspaceRoot)) {
                Paths.get(workspaceRoot).fileName?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            LOG.debug("Error getting repository name: ${e.message}")
            null
        }
    }
}
