@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QATabSubmitterInfoTest {

    @Test
    fun `a question asked under a name shows that name on its row`() {
        qaTab(
            seed = {
                toggleSession()
                submitQuestion("Where is the nursery?", name = "Sam", clientIp = "10.0.0.1", cooldownSeconds = 0)
            },
        ) { _, _, _ ->
            assertTrue(showsContainingText("Sam"), renderedText().toString())
        }
    }

    @Test
    fun `a question asked from an identified device names it only on hover`() {
        qaTab(
            seed = {
                toggleSession()
                submitQuestion(
                    "Where is the nursery?",
                    clientIp = "10.0.0.1",
                    cooldownSeconds = 0,
                    deviceId = "pew-tablet",
                )
            },
        ) { _, _, _ ->
            // The row shows the question and nothing else: a device id is reported by the phone
            // rather than typed by whoever asked, so the queue does not put it in front of the
            // moderator until they hover the row and ask.
            assertFalse(showsContainingText("pew-tablet"), renderedText().toString())

            hoverQuestionRow("Where is the nursery?")

            assertTrue(showsContainingText("Device: pew-tablet"), renderedText().toString())
        }
    }

    @Test
    fun `a question carrying both a name and a device shows the name on the row and both on hover`() {
        qaTab(
            seed = {
                toggleSession()
                submitQuestion(
                    "Where is the nursery?",
                    name = "Sam",
                    clientIp = "10.0.0.1",
                    cooldownSeconds = 0,
                    deviceId = "pew-tablet",
                )
            },
        ) { _, _, _ ->
            assertTrue(showsContainingText("Sam"), renderedText().toString())
            assertFalse(showsContainingText("pew-tablet"), renderedText().toString())

            hoverQuestionRow("Where is the nursery?")

            assertTrue(showsContainingText("Sam"), renderedText().toString())
            assertTrue(showsContainingText("Device: pew-tablet"), renderedText().toString())
        }
    }

    @Test
    fun `an anonymous question shows the text and nothing about who asked`() {
        qaTab(
            seed = {
                toggleSession()
                submitQuestion("Where is the nursery?", clientIp = "10.0.0.1", cooldownSeconds = 0)
            },
        ) { _, _, _ ->
            assertTrue(showsContainingText("Where is the nursery?"), renderedText().toString())
            assertFalse(showsContainingText("pew-tablet"), renderedText().toString())
        }
    }

    @Test
    fun `a question the moderator added themselves carries no submitter`() {
        qaTab(
            seed = {
                toggleSession()
                addQuestion("Asked from the desk")
            },
        ) { _, _, _ ->
            assertTrue(showsContainingText("Asked from the desk"), renderedText().toString())
        }
    }

    @Test
    fun `an upvoted question shows its count on the row`() {
        qaTab(
            settings = org.churchpresenter.app.churchpresenter.data.settings.AppSettings(
                qaSettings = org.churchpresenter.app.churchpresenter.data.settings.QASettings(votingEnabled = true),
            ),
            seed = {
                askAll("Voted on")
                approveQuestion(questions.single().id)
                voteForQuestion(questions.single().id, clientIp = "10.1.0.1", direction = "up")
            },
        ) { qa, _, _ ->
            assertTrue(qa.questions.single().upvotes == 1, renderedText().toString())
            assertTrue(showsContainingText("▲"), renderedText().toString())
        }
    }

    @Test
    fun `a downvoted question shows its count on the row`() {
        qaTab(
            settings = org.churchpresenter.app.churchpresenter.data.settings.AppSettings(
                qaSettings = org.churchpresenter.app.churchpresenter.data.settings.QASettings(votingEnabled = true),
            ),
            seed = {
                askAll("Voted down")
                approveQuestion(questions.single().id)
                voteForQuestion(questions.single().id, clientIp = "10.1.0.2", direction = "down")
            },
        ) { qa, _, _ ->
            assertTrue(qa.questions.single().downvotes == 1, renderedText().toString())
            assertTrue(showsContainingText("▼"), renderedText().toString())
        }
    }
}
