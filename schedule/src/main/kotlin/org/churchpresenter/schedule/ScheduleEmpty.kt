package org.churchpresenter.schedule

import androidx.compose.runtime.Composable
import org.churchpresenter.icons.generated.resources.ic_storage_schedule
import org.churchpresenter.sharedui.composables.EmptyStateContent
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.schedule_empty_body
import org.churchpresenter.strings.generated.resources.schedule_empty_title
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/** An empty schedule: what goes in it, and how. */
@Composable
internal fun ScheduleEmpty() {
    EmptyStateContent(
        icon = IconRes.drawable.ic_storage_schedule,
        neutral = false,
        compact = true,
        title = stringResource(Res.string.schedule_empty_title),
        body = stringResource(Res.string.schedule_empty_body),
    )
}
