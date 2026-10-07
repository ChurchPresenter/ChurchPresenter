package org.churchpresenter.helper

import androidx.compose.runtime.Composable
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A line the helper says, kept as a string resource and its arguments until it is drawn — so the
 * logic that decides what to say stays plain Kotlin and is resolved in the operator's language.
 */
sealed interface HelperText {
    /** [res] formatted with [args], each resolved first. */
    data class Res(val res: StringResource, val args: List<HelperText> = emptyList()) : HelperText

    /** Text that is not translated: what the operator typed, a screen's name. */
    data class Plain(val text: String) : HelperText

    /** The key bound to [action], as this platform writes it, or blank when none is bound. */
    data class KeyFor(val action: ShortcutAction) : HelperText
}

/** [res] with [args], each a [HelperText] or anything else shown as it is. */
fun helperText(res: StringResource, vararg args: Any): HelperText =
    HelperText.Res(res, args.map { if (it is HelperText) it else HelperText.Plain(it.toString()) })

/** This line in the operator's language. */
// stringResource takes its arguments as a vararg, and they are only known here as a list.
@Suppress("SpreadOperator")
@Composable
fun HelperText.resolve(): String = when (this) {
    is HelperText.Plain -> text
    is HelperText.KeyFor -> LocalShortcuts.current.chordsFor(action).firstOrNull()?.label().orEmpty()
    is HelperText.Res -> {
        val resolved = args.map { it.resolve() }
        stringResource(res, *resolved.toTypedArray())
    }
}
