package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.churchpresenter.sharedui.models.Presenting

@Composable
internal fun MainDesktopScope.OutputStepEffects() {
    LaunchedEffect(this) {
        presenterManager.outputSteps.collect { forward ->
            if (presenterManager.isLive(Presenting.PRESENTATION) && !presentationViewModel.previewOnly) {
                keyContext().actions.clickPresentation(forward)
            }
        }
    }
}
