package org.churchpresenter.detektrules

import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockingCallOnEventThreadTest {

    private fun findings(code: String, allowlist: LiveSafeAllowlist = LiveSafeAllowlist.EMPTY): List<Finding> =
        BlockingCallOnEventThread(allowlist = allowlist).lint(code)

    private fun messages(code: String, allowlist: LiveSafeAllowlist = LiveSafeAllowlist.EMPTY) =
        findings(code, allowlist).map { it.message }

    @Test
    fun `a file check in a composable's body is reported`() {
        val found = messages(
            """
            @Composable
            fun Background(path: String) {
                if (File(path).exists()) Text(path)
            }
            """
        )

        assertEquals(1, found.size)
        assertTrue("`exists` in the composable `Background`" in found.single(), found.single())
    }

    @Test
    fun `a decode in remember, a safe call and a sleep in an effect are each reported`() {
        val found = messages(
            """
            @Composable
            fun Picture(file: File?) {
                val bytes = remember(file) { file?.readBytes() }
                val image = remember(file) { ImageIO.read(file) }
                LaunchedEffect(file) { Thread.sleep(10) }
            }
            """
        )

        assertEquals(3, found.size)
        assertTrue(found.any { "`readBytes`" in it })
        assertTrue(found.any { "`ImageIO.read`" in it })
        assertTrue(found.any { "`Thread.sleep`" in it })
    }

    @Test
    fun `runBlocking and every call on Files are reported, other calls on Thread are not`() {
        val found = messages(
            """
            @Composable
            fun Settings(path: Path) {
                val text = runBlocking { load() }
                val bytes = Files.readAllBytes(path)
                val name = Thread.currentThread().name
                val ok = exists()
            }
            """
        )

        assertEquals(listOf("`runBlocking`", "`Files.readAllBytes`"), found.map { it.substringBefore(" in ") })
    }

    @Test
    fun `work handed to another thread or dispatcher is not reported`() {
        val found = messages(
            """
            @Composable
            fun Loader(file: File) {
                LaunchedEffect(file) {
                    val a = withContext(Dispatchers.IO) { file.readBytes() }
                    launch(Dispatchers.Default) { file.exists() }
                    val b = async(ioDispatcher) { file.readText() }
                    thread { file.listFiles() }
                    executor.execute { file.lastModified() }
                }
            }
            """
        )

        assertTrue(found.isEmpty(), found.toString())
    }

    @Test
    fun `work left on the main dispatcher, or launched without one, is reported`() {
        val found = messages(
            """
            @Composable
            fun Loader(file: File, scope: CoroutineScope) {
                LaunchedEffect(file) {
                    withContext(Dispatchers.Main) { file.exists() }
                    scope.launch { file.readText() }
                    launch(context = Dispatchers.Main.immediate) { file.isFile() }
                    launch(start = CoroutineStart.LAZY) { file.mkdirs() }
                }
            }
            """
        )

        assertEquals(4, found.size, found.toString())
    }

    @Test
    fun `code outside any composable is not this rule's to judge`() {
        val found = messages(
            """
            fun load(file: File) = file.readBytes()

            @Composable
            fun Outer(file: File) {
                fun helper() = file.exists()
            }
            """
        )

        assertTrue(found.isEmpty(), found.toString())
    }

    @Test
    fun `an allowed call passes, and an entry whose call is gone is reported`() {
        val allowlist = LiveSafeAllowlist.parse(
            """
            Test.kt: Picture exists  # reviewed
            Test.kt: Picture readText  # this function, but a call it no longer makes
            Test.kt: Gone readBytes  # the call was removed
            Other.kt: Picture readText  # another file's
            """,
            source = "live-safe-allowed.txt",
        )
        val found = messages(
            """
            @Composable
            fun Picture(file: File) {
                if (file.exists()) Text("here")
            }
            """,
            allowlist,
        )

        assertEquals(2, found.size, found.toString())
        assertTrue(found.any { "`readText` in `Picture`" in it }, found.toString())
        assertTrue(found.any { "`readBytes` in `Gone`" in it }, found.toString())
        assertTrue(found.all { "live-safe-allowed.txt" in it }, found.toString())
    }

    @Test
    fun `a lambda handed over inside the parentheses is judged like a trailing one`() {
        val found = messages(
            """
            @Composable
            fun Loader(file: File) {
                LaunchedEffect(file) {
                    withContext(Dispatchers.IO, { file.readBytes() })
                    run({ file.readText() })
                }
            }
            """
        )

        assertEquals(listOf("`readText`"), found.map { it.substringBefore(" in ") })
    }

    @Test
    fun `a lambda kept for later, a top-level call and an annotation that is not Composable are judged as written`() {
        val found = messages(
            """
            val present = File("a").exists()

            @Preview
            fun Shown(file: File) = file.exists()

            @Composable
            fun Kept(file: File) {
                val check = { file.exists() }
                val first = listOf(file)[0].exists()
            }
            """
        )

        assertEquals(2, found.size, found.toString())
        assertTrue(found.all { "`Kept`" in it })
    }

    @Test
    fun `a call made on a call's result is not taken for a blocking name`() {
        val found = messages(
            """
            @Composable
            fun Factory(make: () -> () -> Unit) {
                make()()
                runner()({ File("a").exists() })
            }
            """
        )

        assertEquals(listOf("`exists`"), found.map { it.substringBefore(" in ") })
    }

    @Test
    fun `a stale entry names the allowlist even when it was given no file name`() {
        val found = messages(
            """
            @Composable
            fun Plain() = Text("nothing blocks")
            """,
            LiveSafeAllowlist.parse("Test.kt: Plain exists\n"),
        )

        assertTrue("(the allowlist)" in found.single(), found.single())
    }

    @Test
    fun `with no allowlist given, the rule reads the one its config names, and none found allows nothing`() {
        val rule = BlockingCallOnEventThread(TestConfig("allowlist" to "no/such/list-${System.nanoTime()}.txt"))

        val found = rule.lint(
            """
            @Composable
            fun Background(file: File) = file.exists()
            """
        )

        assertEquals(1, found.size)
    }

    @Test
    fun `the rule set is live-safe, with this rule in it`() {
        val ruleSet = LiveSafeRuleSetProvider().instance(io.gitlab.arturbosch.detekt.api.Config.empty)

        assertEquals("live-safe", ruleSet.id)
        assertEquals(listOf("BlockingCallOnEventThread"), ruleSet.rules.map { it.ruleId })
    }
}
