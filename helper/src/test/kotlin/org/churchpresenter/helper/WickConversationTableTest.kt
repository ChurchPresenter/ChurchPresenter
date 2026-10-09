package org.churchpresenter.helper

import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.describe
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every request in `wick/conversation-requests.txt`, what Wick does with it and what it says back,
 * against the reviewed table in `wick/conversations.tsv`. After a deliberate change, regenerate the
 * table with `WICK_TABLE_WRITE=1 ./gradlew :helper:test --tests '*WickConversationTableTest*'` and
 * read the diff before committing it.
 */
class WickConversationTableTest {

    private val requests = File(DIR, "conversation-requests.txt").readLines().filter { it.isNotBlank() }
    private val table = File(DIR, "conversations.tsv")

    @Test
    fun `every request does and says what the table says`() {
        val rows = requests.map { it + "\t" + converse(it) }
        if (System.getenv("WICK_TABLE_WRITE") == "1") {
            table.writeText(HEADER + rows.joinToString("\n") + "\n")
            return
        }
        val expected = table.readLines().drop(1).filter { it.isNotBlank() }
        val missing = requests.filter { request -> expected.none { it.startsWith(request + "\t") } }
        assertEquals(emptyList(), missing, "requests with no row in conversations.tsv")
        val wrong = rows.filter { it !in expected }
        assertEquals(emptyList(), wrong, "rows that no longer match conversations.tsv")
    }

    private fun converse(request: String): String {
        val resolution = RuleIntentResolver().resolveNow(request, ResolveContext(language = "en"))
        val state = HelperState()
        state.onResolved(resolution, EXECUTOR)
        val action = (resolution as? Resolution.Act)?.action
        return render(action) + "\t" + render(state.reply)
    }

    private fun render(action: HelperAction?): String = when (action) {
        null -> "-"
        is HelperAction.Highlight -> "tour " + action.tour.steps.joinToString(" > ") { it.target.id }
        else -> action.toString()
    }

    private fun render(reply: HelperReply): String = when (reply) {
        is HelperReply.Confirm -> "asks: " + render(reply.action.describe())
        is HelperReply.Clarify -> "asks which: " + render(reply.question) + " (" + reply.options.size + " options)"
        is HelperReply.Message -> "says: " + render(reply.text) + if (reply.canUndo) " [undo]" else ""
        is HelperReply.Shortcut -> "shows the key for " + reply.action.name
        HelperReply.Unknown -> "isn't sure"
        is HelperReply.DidYouMean -> "asks: did you mean " + render(reply.label) + "? then " + render(reply.action)
        is HelperReply.Touring -> "points at step 1 of ${reply.tour.steps.size}: " + render(reply.tour.steps[0].hint)
        HelperReply.Greeting -> "says hello with examples"
        HelperReply.Commands -> "lists every command"
        HelperReply.DisplaySetup -> "starts display setup"
        HelperReply.Idle -> "nothing"
    }

    private fun render(text: HelperText): String = when (text) {
        is HelperText.Res ->
            text.res.key + if (text.args.isEmpty()) "" else text.args.joinToString(", ", "(", ")") { render(it) }
        is HelperText.Plain -> "\"" + text.text + "\""
        is HelperText.KeyFor -> "key of " + text.action.name
        is HelperText.Joined -> text.parts.joinToString(", ") { render(it) }
    }

    private companion object {
        val DIR = File("src/test/resources/wick")
        const val HEADER = "request\taction\treply\n"
        val EXECUTOR = HelperActionExecutor { ActionOutcome.Done() }
    }
}
