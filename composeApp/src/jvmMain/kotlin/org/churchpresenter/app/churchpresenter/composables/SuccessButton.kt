package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.theme.AppShape
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.components.RaisedButton

@Composable
fun SuccessButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    RaisedButton(
        shape = AppShape(6.dp),
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        modifier = modifier,
        enabled = enabled
    ) {
        Text(text)
    }
}
