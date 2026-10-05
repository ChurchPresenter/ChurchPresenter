package org.churchpresenter.showcontrol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Running action lists: in order, with waits, macros, failures and cancelling. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActionRunnerTest {

    /** Writes down every call, with the virtual time it came at. */
    private class Recorder(
        private val clock: () -> Long,
        private val macros: Map<String, List<Action>> = emptyMap(),
        private val failing: Set<Action> = emptySet(),
    ) : ShowHost {
        val calls = mutableListOf<String>()
        val errors = mutableListOf<Pair<Action, String>>()

        private fun note(call: String, action: Action? = null) {
            if (action in failing) {
                if (action is Action.ObsScene) throw IOException("OBS is gone")
                error("$call failed")
            }
            calls += "$call@${clock()}"
        }

        override suspend fun goLive(action: Action.GoLive) =
            note("goLive:${action.rowId}${action.item?.id.orEmpty()}x${action.plays}", action)
        override suspend fun toPreview(action: Action.ToPreview) =
            note("preview:${action.rowId}${action.item?.id.orEmpty()}")
        override suspend fun take(layer: String) = note("take:$layer")
        override suspend fun clear(layer: String) = note("clear:$layer")
        override suspend fun clearAll() = note("clearAll")
        override suspend fun clearGroup(group: String) = note("clearGroup:$group")
        override suspend fun message(action: Action.Message) =
            note("message:${action.text}${action.template}${action.tokens.values}${action.durationSeconds}")
        override suspend fun prop(action: Action.Prop) = note("prop:${action.prop}=${action.on}")
        override suspend fun lowerThird(preset: String) = note("lowerThird:$preset")
        override suspend fun timer(action: Action.Timer) =
            note("timer:${action.mode}/${action.seconds}/${action.until}")
        override suspend fun media(command: MediaCommand) = note("media:$command")
        override suspend fun obsScene(scene: String) = note("obs:$scene", Action.ObsScene(scene))
        override suspend fun atemKey(action: Action.AtemKey) =
            note("atemKey:${action.downstream}/${action.mixEffect}/${action.keyer}=${action.on}", action)
        override suspend fun atemMacro(index: Int) = note("atemMacro:$index")
        override suspend fun companion(action: Action.CompanionPress) =
            note("companion:${action.connection}/${action.placement}/${action.button}")
        override suspend fun next() = note("next")
        override suspend fun previous() = note("previous")
        override fun macro(name: String): List<Action>? = macros[name]
        override fun reportError(action: Action, error: Throwable) {
            errors += action to error.message.orEmpty()
        }
    }

    private fun TestScope.recorder(
        macros: Map<String, List<Action>> = emptyMap(),
        failing: Set<Action> = emptySet(),
    ) = Recorder({ currentTime }, macros, failing)

    @Test
    fun `every action reaches its call, in order`() = runTest {
        val host = recorder()
        ActionRunner(host, this).perform(
            listOf(
                Action.GoLive("row1", plays = 2), Action.ToPreview("row2"), Action.Take("SLIDE"),
                Action.Clear("PROPS"),
                Action.ClearAll, Action.ClearGroup("Text"), Action.Message("Hi"), Action.Prop("Logo", true),
                Action.Prop("Badge"),
                Action.LowerThird("Pastor"), Action.Timer(), Action.Media(MediaCommand.STOP), Action.ObsScene("Wide"),
                Action.AtemKey(keyer = 2), Action.AtemMacro(1), Action.CompanionPress("S", 5), Action.NextItem,
                Action.PreviousItem,
            ),
        )
        assertEquals(
            listOf(
                "goLive:row1x2", "preview:row2", "take:SLIDE", "clear:PROPS", "clearAll", "clearGroup:Text",
                "message:Hi[]null", "prop:Logo=true", "prop:Badge=null", "lowerThird:Pastor", "timer:duration/0/",
                "media:STOP", "obs:Wide", "atemKey:false/0/2=true", "atemMacro:1", "companion:S//5", "next", "previous",
            ).map { "$it@0" },
            host.calls,
        )
    }

    @Test
    fun `flow is the runner's, so a host is never handed it`() = runTest {
        val host = recorder()
        val flow = listOf(Action.Wait(1.0), Action.RunMacro("m"), Action.Unknown(JsonObject(emptyMap())))
        flow.forEach { host.dispatch(it) }
        assertEquals(emptyList(), host.calls)
    }

    @Test
    fun `a wait holds the rest of its list, and an unknown action is passed over`() = runTest {
        val host = recorder()
        val unknown = Action.Unknown(JsonObject(emptyMap()))
        ActionRunner(host, this).run(
            listOf(Action.ClearAll, Action.Wait(1.5), unknown, Action.NextItem, Action.Wait(-1.0)),
        )
        advanceUntilIdle()
        assertEquals(listOf("clearAll@0", "next@1500"), host.calls)
    }

    @Test
    fun `a macro runs in place, and nests no deeper than the limit`() = runTest {
        val macros = mapOf(
            "walk in" to listOf(Action.ObsScene("Wide"), Action.RunMacro("lights")),
            "lights" to listOf(Action.AtemMacro(0)),
            "forever" to listOf(Action.NextItem, Action.RunMacro("forever")),
        )
        val host = recorder(macros)
        val runner = ActionRunner(host, this)
        runner.perform(listOf(Action.RunMacro("walk in"), Action.ClearAll))
        assertEquals(listOf("obs:Wide@0", "atemMacro:0@0", "clearAll@0"), host.calls)
        host.calls.clear()
        runner.perform(listOf(Action.RunMacro("forever")))
        assertEquals(ActionRunner.MAX_MACRO_DEPTH, host.calls.size, "one call per macro level")
        val refused: Pair<Action, String> = Action.RunMacro("forever") to "Macros nested deeper than 8"
        assertEquals(listOf(refused), host.errors)
    }

    @Test
    fun `a missing macro or a failing action is reported, and the list runs on`() = runTest {
        val failingKey = Action.AtemKey(keyer = 9)
        val host = recorder(failing = setOf(Action.ObsScene("Gone"), failingKey, Action.ClearGroup("x")))
        ActionRunner(host, this).perform(
            listOf(Action.RunMacro("nope"), Action.ObsScene("Gone"), failingKey, Action.NextItem),
        )
        assertEquals(listOf("next@0"), host.calls)
        assertEquals(
            listOf<Pair<Action, String>>(
                Action.RunMacro("nope") to "No macro called nope",
                Action.ObsScene("Gone") to "OBS is gone",
                failingKey to "atemKey:false/0/9=true failed",
            ),
            host.errors,
        )
    }

    @Test
    fun `a run under a key replaces the one still going under it`() = runTest {
        val host = recorder()
        val runner = ActionRunner(host, this)
        runner.run(listOf(Action.Wait(1.0), Action.ObsScene("Old")), key = "row1")
        runCurrent()
        assertTrue(runner.isRunning("row1"))
        runner.run(listOf(Action.Wait(1.0), Action.ObsScene("New")), key = "row1")
        advanceUntilIdle()
        assertEquals(listOf("obs:New@1000"), host.calls)
        assertFalse(runner.isRunning("row1"))
    }

    @Test
    fun `a run can be stopped on its own or with every other`() = runTest {
        val host = recorder()
        val runner = ActionRunner(host, this)
        runner.run(listOf(Action.Wait(1.0), Action.ObsScene("A")), key = "a")
        runner.run(listOf(Action.Wait(1.0), Action.ObsScene("B")), key = "b")
        runner.run(listOf(Action.Wait(1.0), Action.ObsScene("C")), key = "c")
        runner.run(listOf(Action.Wait(1.0), Action.ObsScene("D")))
        advanceTimeBy(500)
        runner.cancel("a")
        runner.cancel("nothing")
        runner.cancelAll()
        advanceUntilIdle()
        assertEquals(listOf("obs:D@1000"), host.calls)
        assertFalse(runner.isRunning("b"))
    }
}
