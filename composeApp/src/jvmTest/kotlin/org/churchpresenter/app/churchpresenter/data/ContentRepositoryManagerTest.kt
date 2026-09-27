package org.churchpresenter.app.churchpresenter.data

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentRepositoryManagerTest {
    @Test
    fun statusFetchesRemoteChangesBeforeComparingBranches() {
        withRepositories { seed, client ->
            Files.writeString(seed.resolve("shared.txt"), "initial")
            git(seed, "add", "shared.txt")
            git(seed, "commit", "-m", "initial")
            git(seed, "push", "-u", "origin", "main")
            git(client, "fetch", "origin")
            git(client, "reset", "--hard", "origin/main")

            Files.writeString(seed.resolve("shared.txt"), "remote update")
            git(seed, "commit", "-am", "remote update")
            git(seed, "push", "origin", "main")

            val status = ContentRepositoryManager(client).status()

            assertEquals(RepositoryState.REMOTE, status.kind)
            assertEquals(1, status.behind)
        }
    }

    @Test
    fun resolvingLastConflictFinishesMergeAndAllowsPush() {
        withRepositories { seed, client ->
            Files.writeString(seed.resolve("shared.txt"), "base")
            git(seed, "add", "shared.txt")
            git(seed, "commit", "-m", "base")
            git(seed, "push", "-u", "origin", "main")
            git(client, "fetch", "origin")
            git(client, "reset", "--hard", "origin/main")

            Files.writeString(seed.resolve("shared.txt"), "remote")
            git(seed, "commit", "-am", "remote change")
            git(seed, "push", "origin", "main")
            Files.writeString(client.resolve("shared.txt"), "local")
            git(client, "commit", "-am", "local change")

            val manager = ContentRepositoryManager(client)
            assertFails { manager.synchronize("sync") }
            assertEquals(listOf(ConflictEntry("shared.txt")), manager.conflicts())

            manager.resolveConflict("shared.txt", useRemote = false)

            assertTrue(git(client, "rev-parse", "-q", "--verify", "MERGE_HEAD", allowFailure = true).isEmpty())
            assertEquals("local", Files.readString(client.resolve("shared.txt")))
            assertEquals(RepositoryState.LOCAL, manager.status().kind)
            manager.synchronize("finish synchronization")
            assertEquals(RepositoryState.SYNCED, manager.status().kind)
        }
    }

    @Test
    fun missingRepositoryIsReportedAsUnavailableRatherThanAsAConflict() {
        val directory = Files.createTempDirectory("missing-repository-test")
        try {
            assertFailsWith<IllegalStateException> { ContentRepositoryManager(directory).status() }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun withRepositories(test: (seed: Path, client: Path) -> Unit) {
        val root = Files.createTempDirectory("content-repository-test")
        try {
            val remote = root.resolve("remote.git")
            val seed = root.resolve("seed")
            val client = root.resolve("client")
            Files.createDirectories(remote)
            Files.createDirectories(seed)
            git(remote, "init", "--bare", "--initial-branch=main")
            git(seed, "init", "--initial-branch=main")
            git(seed, "config", "user.name", "ChurchPresenter Test")
            git(seed, "config", "user.email", "test@example.invalid")
            git(seed, "remote", "add", "origin", remote.toString())
            git(listOf("clone", remote.toString(), client.toString()), root)
            git(client, "config", "user.name", "ChurchPresenter Test")
            git(client, "config", "user.email", "test@example.invalid")
            test(seed, client)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun git(directory: Path, vararg arguments: String, allowFailure: Boolean = false): String =
        git(listOf("-C", directory.toString()) + arguments, directory, allowFailure)

    private fun git(arguments: List<String>, workingDirectory: Path, allowFailure: Boolean = false): String {
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()
        check(allowFailure || exitCode == 0) { output.ifBlank { "git command failed ($exitCode)" } }
        return output
    }
}
