package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal that provides [MediaViewModel] to [MediaPresenter] and [VideoPlayer]
 * without passing it as a parameter across class boundaries.
 *
 * Provided by [MediaTab] — the sole owner of [MediaViewModel].
 * Consumed by [MediaPresenter] in the presenter window.
 */
val LocalMediaViewModel = compositionLocalOf<MediaViewModel?> { null }

