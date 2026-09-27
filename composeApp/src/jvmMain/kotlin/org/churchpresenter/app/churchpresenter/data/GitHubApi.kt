package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.IOException
import java.util.prefs.Preferences

@Serializable
data class GitHubRepository(val full_name: String, val default_branch: String)

@Serializable
data class GitHubBranch(val name: String)

data class GitHubSession(val login: String, val token: String)

class GitCredentialManagerUnavailableException : IllegalStateException()

/** Uses the same Git Credential Manager browser flow as churchpresenter-updater. */
object GitHubApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient.newHttpClient()
    private val preferences = Preferences.userNodeForPackage(GitHubApi::class.java)

    fun signIn(): GitHubSession {
        requireGitCredentialManager()
        val process = try {
            ProcessBuilder(
            "git", "-c", "credential.helper=", "-c", "credential.helper=manager",
            "-c", "credential.interactive=true", "-c", "credential.guiPrompt=true",
            "-c", "credential.gitHubAuthModes=browser", "credential", "fill"
            ).redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw GitCredentialManagerUnavailableException().also { it.initCause(e) }
        }
        process.outputStream.bufferedWriter().use { it.write("protocol=https\nhost=github.com\n\n") }
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        check(process.waitFor() == 0) { "GitHub sign-in failed." }
        val token = lines.firstOrNull { it.startsWith("password=") }?.removePrefix("password=")
        check(!token.isNullOrBlank()) { "GitHub did not return an access token." }
        val login = get("user", token).let { json.decodeFromString<GitHubUser>(it).login }
        preferences.put("account", login)
        preferences.flush()
        return GitHubSession(login, token)
    }

    fun savedLogin(): String? = preferences.get("account", null)

    /** Removes Git Credential Manager's cached GitHub account and forgets its displayed identity. */
    fun signOut(): Boolean {
        val login = savedLogin()
        val credentialsRemoved = try {
            val process = ProcessBuilder(
                "git", "-c", "credential.helper=", "-c", "credential.helper=manager",
                "credential", "reject"
            ).redirectErrorStream(true).start()
            process.outputStream.bufferedWriter().use {
                it.write("protocol=https\nhost=github.com\n")
                if (!login.isNullOrBlank()) it.write("username=$login\n")
                it.write("\n")
            }
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() == 0
        } catch (_: IOException) {
            false
        }
        try {
            preferences.remove("account")
            preferences.flush()
        } catch (_: Exception) {
            return false
        }
        return credentialsRemoved
    }

    fun repositories(session: GitHubSession): List<GitHubRepository> =
        json.decodeFromString<List<GitHubRepository>>(get("user/repos?per_page=100&sort=updated", session.token))

    fun branches(session: GitHubSession, repository: GitHubRepository): List<String> =
        json.decodeFromString<List<GitHubBranch>>(get("repos/${repository.full_name}/branches?per_page=100", session.token)).map { it.name }

    private fun requireGitCredentialManager() {
        val process = try {
            ProcessBuilder("git", "credential-manager", "--version").redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw GitCredentialManagerUnavailableException().also { it.initCause(e) }
        }
        process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) throw GitCredentialManagerUnavailableException()
    }

    private fun get(endpoint: String, token: String): String {
        val request = HttpRequest.newBuilder(URI("https://api.github.com/$endpoint"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ChurchPresenter")
            .GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "GitHub API request failed (${response.statusCode()})." }
        return response.body()
    }
}

@Serializable
private data class GitHubUser(val login: String)
