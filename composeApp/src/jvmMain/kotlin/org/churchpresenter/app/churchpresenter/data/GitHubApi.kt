package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Serializable
data class GitHubRepository(val full_name: String, val default_branch: String)

@Serializable
data class GitHubBranch(val name: String)

data class GitHubSession(val login: String, val token: String)

/** Uses the same Git Credential Manager browser flow as churchpresenter-updater. */
object GitHubApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient.newHttpClient()

    fun signIn(): GitHubSession {
        val process = ProcessBuilder(
            "git", "-c", "credential.helper=", "-c", "credential.helper=manager",
            "-c", "credential.interactive=true", "-c", "credential.guiPrompt=true",
            "-c", "credential.gitHubAuthModes=browser", "credential", "fill"
        ).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { it.write("protocol=https\nhost=github.com\n\n") }
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        check(process.waitFor() == 0) { "GitHub sign-in failed." }
        val token = lines.firstOrNull { it.startsWith("password=") }?.removePrefix("password=")
        check(!token.isNullOrBlank()) { "GitHub did not return an access token." }
        val login = get("user", token).let { json.decodeFromString<GitHubUser>(it).login }
        return GitHubSession(login, token)
    }

    fun repositories(session: GitHubSession): List<GitHubRepository> =
        json.decodeFromString<List<GitHubRepository>>(get("user/repos?per_page=100&sort=updated", session.token))

    fun branches(session: GitHubSession, repository: GitHubRepository): List<String> =
        json.decodeFromString<List<GitHubBranch>>(get("repos/${repository.full_name}/branches?per_page=100", session.token)).map { it.name }

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
