package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences

data class RepositoryStatus(
    val kind: String,
    val branch: String,
    val staged: Int,
    val unstaged: Int,
    val ahead: Int,
    val behind: Int,
    val message: String
)

data class ServiceEntry(val date: String, val directory: Path, val plan: Path?)
data class ConflictEntry(val path: String)
data class RepositoryConfiguration(val root: Path, val remote: String, val branch: String)

/** JVM implementation of the repository and service actions previously provided by the updater. */
class ContentRepositoryManager(private val root: Path) {
    private fun git(vararg args: String, allowFailure: Boolean = false): String {
        val process = ProcessBuilder(listOf("git", "-C", root.toString()) + args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val code = process.waitFor()
        if (code != 0 && !allowFailure) error(output.ifBlank { "Git command failed ($code)." })
        return output
    }

    /** Clones the selected GitHub repository into the chosen parent folder. */
    fun clone(remote: String, branch: String): Path {
        require(remote.matches(Regex("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\\.git")))
        require(branch.matches(Regex("[A-Za-z0-9._/-]+")) && !branch.startsWith('-'))
        Files.createDirectories(root)
        val repositoryName = remote.substringAfterLast('/').removeSuffix(".git")
        val destination = root.resolve(repositoryName)
        if (Files.exists(destination)) {
            val existingContent = Files.list(destination)
            val isEmpty = try { !existingContent.findAny().isPresent } finally { existingContent.close() }
            require(isEmpty) { "The destination already contains files: $destination" }
        }
        val process = ProcessBuilder("git", "clone", "--branch", branch, "--single-branch", remote, destination.toString())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val code = process.waitFor()
        check(code == 0) { output.ifBlank { "Could not clone the GitHub repository." } }
        val cloneManager = ContentRepositoryManager(destination)
        cloneManager.git("branch", "--set-upstream-to=origin/$branch", branch, allowFailure = true)
        saveConfiguration(RepositoryConfiguration(destination.toAbsolutePath(), remote, branch))
        return destination
    }

    fun status(): RepositoryStatus {
        val branch = git("branch", "--show-current", allowFailure = true).ifBlank { "detached" }
        val porcelain = git("status", "--porcelain", allowFailure = true)
        val staged = porcelain.lineSequence().count { it.length >= 2 && it[0] != ' ' }
        val unstaged = porcelain.lineSequence().count { it.length >= 2 && it[1] != ' ' }
        val counts = git("rev-list", "--left-right", "--count", "HEAD...@{upstream}", allowFailure = true)
            .split(Regex("\\s+"))
        val ahead = counts.getOrNull(0)?.toIntOrNull() ?: 0
        val behind = counts.getOrNull(1)?.toIntOrNull() ?: 0
        val conflicts = git("diff", "--name-only", "--diff-filter=U", allowFailure = true).lineSequence().count { it.isNotBlank() }
        val kind = when {
            conflicts > 0 -> "conflict"
            staged + unstaged > 0 && behind > 0 -> "both"
            ahead > 0 && behind > 0 -> "both"
            staged + unstaged > 0 || ahead > 0 -> "local"
            behind > 0 -> "remote"
            else -> "synced"
        }
        return RepositoryStatus(kind, branch, staged, unstaged, ahead, behind, git("log", "-1", "--pretty=%s", allowFailure = true))
    }

    fun commit(message: String) {
        require(message.isNotBlank())
        git("add", "--all")
        git("commit", "-m", message)
    }

    fun synchronize(commitMessage: String = "chore: sync ChurchPresenter content") {
        val current = status()
        if (current.kind == "conflict") error("Resolve the existing merge conflicts first.")
        if (current.staged + current.unstaged > 0) commit(commitMessage)
        if (current.behind > 0) git("merge", "--no-edit", "origin/${current.branch}")
        if (current.ahead > 0 || current.behind > 0) git("push", "origin", "HEAD:${current.branch}")
        git("lfs", "pull", allowFailure = true)
    }

    fun conflicts(): List<ConflictEntry> = git("diff", "--name-only", "--diff-filter=U", allowFailure = true)
        .lineSequence().filter { it.isNotBlank() }.map(::ConflictEntry).toList()

    fun resolveConflict(path: String, useRemote: Boolean) {
        require(path.isNotBlank() && !path.contains("..") && !path.startsWith('/'))
        git("checkout", if (useRemote) "--theirs" else "--ours", "--", path)
        git("add", "--", path)
    }

    fun finishConflictMerge() {
        check(conflicts().isEmpty()) { "There are unresolved conflicts." }
        git("commit", "--no-edit")
    }

    fun services(): List<ServiceEntry> = root.resolve("Services").toFile().let { services ->
        if (!services.isDirectory) return emptyList()
        services.listFiles().orEmpty().asSequence()
            .filter { it.isDirectory }
            .map { directory -> ServiceEntry(directory.name, directory.toPath(), directory.resolve("plan.cps").takeIf(File::isFile)?.toPath()) }
            .sortedByDescending { it.date }
            .toList()
    }

    fun createService(date: String): Path {
        val plan = ServiceFolders.create(root.resolve("Services"), date)
        return plan
    }

    fun connect(service: ServiceEntry, contentRoot: Path, saveSettings: (Path, Path, Path, Path, Path) -> Unit) {
        val plan = requireNotNull(service.plan) { "The selected service has no plan.cps." }
        val pictures = service.directory.resolve("Pictures").also(Files::createDirectories)
        val presentations = service.directory.resolve("Presentations").also(Files::createDirectories)
        val media = service.directory.resolve("Media").also(Files::createDirectories)
        Files.createDirectories(contentRoot.resolve("Songs"))
        Files.createDirectories(contentRoot.resolve("Bibles"))
        saveSettings(contentRoot.resolve("Songs"), contentRoot.resolve("Bibles"), pictures, presentations, media)
    }

    companion object {
        private val preferences = Preferences.userNodeForPackage(ContentRepositoryManager::class.java)

        fun savedConfiguration(): RepositoryConfiguration? {
            val root = preferences.get("root", "").trim()
            val remote = preferences.get("remote", "").trim()
            val branch = preferences.get("branch", "").trim()
            if (root.isEmpty() || remote.isEmpty() || branch.isEmpty()) return null
            return RepositoryConfiguration(Path.of(root), remote, branch)
        }

        /** Forgets the app's repository link while leaving the working copy untouched. */
        fun clearSavedConfiguration() {
            preferences.remove("root")
            preferences.remove("remote")
            preferences.remove("branch")
            preferences.flush()
        }

        private fun saveConfiguration(configuration: RepositoryConfiguration) {
            preferences.put("root", configuration.root.toString())
            preferences.put("remote", configuration.remote)
            preferences.put("branch", configuration.branch)
            preferences.flush()
        }
    }
}
