package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

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

/** JVM implementation of the repository and service actions previously provided by the updater. */
class ContentRepositoryManager(private val root: Path) {
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    private fun git(vararg args: String, allowFailure: Boolean = false): String {
        val process = ProcessBuilder(listOf("git", "-C", root.toString()) + args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val code = process.waitFor()
        if (code != 0 && !allowFailure) error(output.ifBlank { "Git command failed ($code)." })
        return output
    }

    fun initialize(remote: String, branch: String) {
        require(remote.isNotBlank() && branch.matches(Regex("[A-Za-z0-9._/-]+")))
        Files.createDirectories(root)
        if (!Files.exists(root.resolve(".git"))) {
            git("init", "--quiet")
            git("remote", "add", "origin", remote)
        } else if (git("remote", "get-url", "origin") != remote) {
            git("remote", "set-url", "origin", remote)
        }
        git("fetch", "origin", branch)
        git("checkout", "-B", branch, "FETCH_HEAD")
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

    fun synchronize() {
        val current = status()
        if (current.kind == "conflict") error("Resolve the existing merge conflicts first.")
        if (current.staged + current.unstaged > 0) commit("chore: sync ChurchPresenter content")
        if (current.behind > 0) git("merge", "--no-edit", "origin/${current.branch}")
        if (current.ahead > 0 || current.behind > 0) git("push", "origin", "HEAD:${current.branch}")
        git("lfs", "pull", allowFailure = true)
    }

    fun services(): List<ServiceEntry> = root.resolve("Services").toFile().let { services ->
        if (!services.isDirectory) return emptyList()
        services.listFiles().orEmpty().asSequence()
            .filter { it.isDirectory && isDate(it.name) }
            .map { directory -> ServiceEntry(directory.name, directory.toPath(), directory.resolve("plan.cps").takeIf(File::isFile)?.toPath()) }
            .sortedByDescending { it.date }
            .toList()
    }

    fun connect(service: ServiceEntry, contentRoot: Path, saveSettings: (Path, Path, Path, Path, Path) -> Unit) {
        val plan = requireNotNull(service.plan) { "The selected service has no plan.cps." }
        val pictures = service.directory.resolve("Pictures").also(Files::createDirectories)
        val presentations = service.directory.resolve("Presentations").also(Files::createDirectories)
        val media = service.directory.resolve("Media").also(Files::createDirectories)
        Files.createDirectories(contentRoot.resolve("Songs"))
        Files.createDirectories(contentRoot.resolve("Bibles"))
        saveSettings(contentRoot.resolve("Songs"), contentRoot.resolve("Bibles"), pictures, presentations, media)
        java.awt.Desktop.getDesktop().open(plan.toFile())
    }

    private fun isDate(value: String): Boolean = try {
        LocalDate.parse(value, formatter)
        true
    } catch (_: DateTimeParseException) {
        false
    }
}
