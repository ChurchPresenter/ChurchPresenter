package org.churchpresenter.helper.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.strings.generated.resources.helper_send_chat
import org.churchpresenter.strings.generated.resources.helper_not_sure
import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.helperText
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_did_you_mean
import org.churchpresenter.strings.generated.resources.helper_did_you_mean_no
import org.churchpresenter.strings.generated.resources.helper_did_you_mean_others
import org.churchpresenter.strings.generated.resources.helper_did_you_mean_yes
import org.jetbrains.compose.resources.stringResource

/**
 * Wick's best guess, asked about: Yes does it, No says it isn't sure. The next closest chips sit under it,
 * so a wrong first guess still leaves the right request one click away.
 */
@Composable
internal fun DidYouMeanBody(
    state: HelperState,
    reply: HelperReply.DidYouMean,
    executor: HelperActionExecutor,
    ask: Ask,
) {
    Said(helperText(Res.string.helper_did_you_mean, reply.label), Modifier.testTag("helper.didYouMean"))
    val yes = stringResource(Res.string.helper_did_you_mean_yes)
    val no = stringResource(Res.string.helper_did_you_mean_no)
    Actions(
        Res.string.helper_did_you_mean_no to {
            state.answer(no)
            state.show(HelperReply.Unknown)
        },
        primary = Res.string.helper_did_you_mean_yes to {
            state.answer(yes)
            state.request(reply.action, executor)
        },
        primaryLeads = true,
    )
    if (reply.others.isNotEmpty()) RequestChips(reply.others, ask, Res.string.helper_did_you_mean_others)
}

/** "I'm not sure", the [closest] chips when any came near, and the way to send the chat. */
@Composable
internal fun NotSureBody(state: HelperState, inputs: HelperInputs, closest: List<SuggestedRequest>, ask: Ask) {
    Said(HelperText.Res(Res.string.helper_not_sure))
    if (closest.isNotEmpty()) RequestChips(closest, ask, Res.string.helper_did_you_mean_others)
    if (inputs.onSendChat != null && !state.sharingChat) {
        QuietLink(
            Res.string.helper_send_chat,
            onClick = { state.sharingChat = true },
            modifier = Modifier.testTag("helper.shareChatLink"),
        )
    }
}
