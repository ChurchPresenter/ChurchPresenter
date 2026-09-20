package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
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
