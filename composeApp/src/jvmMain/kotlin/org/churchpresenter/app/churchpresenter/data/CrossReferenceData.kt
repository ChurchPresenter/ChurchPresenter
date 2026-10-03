package org.churchpresenter.app.churchpresenter.data

import churchpresenter.composeapp.generated.resources.Res
import org.churchpresenter.bibletab.CrossReferenceRepository
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Where the bundled cross-reference dataset is in the app's resources. */
internal const val CROSS_REFERENCES_PATH = "files/bible/cross_references.json"

/** The app-wide instance. The dataset is immutable, so one copy serves every caller. */
@OptIn(ExperimentalResourceApi::class)
internal val sharedCrossReferences: CrossReferenceRepository by lazy {
    CrossReferenceRepository { Res.readBytes(CROSS_REFERENCES_PATH) }
}
