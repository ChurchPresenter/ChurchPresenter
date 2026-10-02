package org.churchpresenter.slides

import java.io.File

/** A presentation's slides, rendered and ready to serve to remote clients. */
typealias PresentationSlidesLoaded = (
    id: String,
    filePath: String,
    fileName: String,
    fileType: String,
    slideFiles: List<File>,
    slideNotes: List<String>,
) -> Unit
