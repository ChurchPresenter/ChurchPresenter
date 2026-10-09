package org.churchpresenter.helper.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.helperText
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_did_you_mean
import org.churchpresenter.strings.generated.resources.helper_did_you_mean_no
import org.churchpresenter.strings.generated.resources.helper_did_you_mean_yes
import org.jetbrains.compose.resources.stringResource

/** Wick's one best guess, asked about: Yes does it, No says it isn't sure. */
@Composable
internal fun DidYouMeanBody(state: HelperState, reply: HelperReply.DidYouMean, executor: HelperActionExecutor) {
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
}
