package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import java.io.File

/** A deck's slide count from a metadata parse -- no rasterization -- or 0 when it will not load. */
internal fun countDeckSlides(deck: File): Int =
    (PresentationLoader.load(deck) as? LoadResult.Success)?.deck?.slideCount ?: 0
