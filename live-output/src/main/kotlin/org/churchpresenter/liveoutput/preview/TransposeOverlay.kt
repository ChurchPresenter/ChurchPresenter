package org.churchpresenter.liveoutput.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.icons.generated.resources.ic_remove
import org.churchpresenter.strings.generated.resources.preview_transpose
import org.churchpresenter.strings.generated.resources.song_transpose_down
import org.churchpresenter.strings.generated.resources.song_transpose_up
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyIconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A Browser Source tile's musician transpose, drawn over its live preview: a step down, the
 * current offset, and a step up. Pressing the offset puts the chords back in the key they are
 * written in. [onStep] takes a step of ±1, or null to reset.
 *
 * It moves the same offset the output's page buttons do, so the operator and the band's tablets
 * always see one value.
 */
@Composable
internal fun TransposeOverlay(steps: Int, onStep: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val ink = Color.White
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), AppShape(4.dp))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        KeyIconButton(onClick = { onStep(-1) }, modifier = Modifier.size(22.dp)) {
            Icon(
                painter = painterResource(IconRes.drawable.ic_remove),
                contentDescription = stringResource(Res.string.song_transpose_down),
                tint = ink,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = stringResource(Res.string.preview_transpose, if (steps > 0) "+$steps" else "$steps"),
            color = if (steps == 0) ink.copy(alpha = 0.6f) else ink,
            fontSize = 9.sp,
            modifier = Modifier
                .clickable(enabled = steps != 0) { onStep(null) }
                .padding(horizontal = 3.dp, vertical = 2.dp),
        )
        KeyIconButton(onClick = { onStep(1) }, modifier = Modifier.size(22.dp)) {
            Icon(
                painter = painterResource(IconRes.drawable.ic_add),
                contentDescription = stringResource(Res.string.song_transpose_up),
                tint = ink,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
