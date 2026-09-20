package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_problem_book_missing
import org.churchpresenter.calendar.generated.resources.calendar_problem_chapter
import org.churchpresenter.calendar.generated.resources.calendar_problem_missing_file
import org.churchpresenter.calendar.generated.resources.calendar_problem_missing_folder
import org.churchpresenter.calendar.generated.resources.calendar_problem_song_missing
import org.churchpresenter.calendar.generated.resources.calendar_problem_verse
import org.churchpresenter.calendar.model.PreflightProblem
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
