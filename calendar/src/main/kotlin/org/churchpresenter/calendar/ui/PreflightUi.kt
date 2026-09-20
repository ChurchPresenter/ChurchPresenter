package org.churchpresenter.calendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_fix_locate_file
import org.churchpresenter.calendar.generated.resources.calendar_fix_locate_folder
import org.churchpresenter.calendar.generated.resources.calendar_fix_pick_again
import org.churchpresenter.calendar.generated.resources.calendar_problem_book_missing
import org.churchpresenter.calendar.generated.resources.calendar_problem_chapter
import org.churchpresenter.calendar.generated.resources.calendar_problem_missing_file
import org.churchpresenter.calendar.generated.resources.calendar_problem_missing_folder
import org.churchpresenter.calendar.generated.resources.calendar_problem_song_missing
import org.churchpresenter.calendar.generated.resources.calendar_problem_verse
import org.churchpresenter.calendar.model.PreflightProblem
import org.churchpresenter.calendar.model.ProblemFix
import org.churchpresenter.calendar.model.fix
import org.jetbrains.compose.resources.stringResource

/** What a [PreflightProblem] says to the planner -- the hint on the row's warning mark. */
@Composable
internal fun problemText(problem: PreflightProblem): String = stringResource(
    when (problem) {
        PreflightProblem.MISSING_FILE -> Res.string.calendar_problem_missing_file
        PreflightProblem.MISSING_FOLDER -> Res.string.calendar_problem_missing_folder
        PreflightProblem.SONG_NOT_IN_LIBRARY -> Res.string.calendar_problem_song_missing
        PreflightProblem.BOOK_NOT_IN_BIBLE -> Res.string.calendar_problem_book_missing
        PreflightProblem.CHAPTER_OUT_OF_RANGE -> Res.string.calendar_problem_chapter
        PreflightProblem.VERSE_OUT_OF_RANGE -> Res.string.calendar_problem_verse
    }
)

/** What is wrong and what a click does about it -- `File not found — click to find the file`. */
@Composable
internal fun problemHint(problem: PreflightProblem, fixable: Boolean): String {
    val what = problemText(problem)
    if (!fixable) return what
    val how = stringResource(
        when (problem.fix) {
            ProblemFix.LOCATE_FILE -> Res.string.calendar_fix_locate_file
            ProblemFix.LOCATE_FOLDER -> Res.string.calendar_fix_locate_folder
            ProblemFix.PICK_AGAIN -> Res.string.calendar_fix_pick_again
        }
    )
    return "$what — $how"
}

/**
 * The warning beside a row that will not go on screen on the day: what is wrong as its hint, and
 * the fix as its click -- a file dialog for a moved file, the picker for a song or a verse. With
 * no [onFix] it only explains.
 */
@Composable
internal fun ProblemMark(problem: PreflightProblem, onFix: (() -> Unit)? = null) {
    val hint = problemHint(problem, fixable = onFix != null)
    Hint(hint) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = hint,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(16.dp)
                .clip(CalendarMetrics.smallRadius)
                .then(if (onFix != null) Modifier.clickable(onClick = onFix) else Modifier)
                .padding(2.dp),
        )
    }
}
