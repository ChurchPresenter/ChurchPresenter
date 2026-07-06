package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.data.StockMediaClient

enum class StockSearchError { INVALID_KEY, RATE_LIMITED, NETWORK_ERROR, FAILURE }
enum class StockDownloadError { NETWORK_ERROR, FAILURE }

/**
 * Owned by [org.churchpresenter.app.churchpresenter.dialogs.StockMediaBrowserDialog] only —
 * one instance per source tab (Pexels/Pixabay), created fresh each time the dialog opens and
 * disposed when it closes. The API key is passed in at call time (not held in the constructor)
 * since the dialog lets the user edit the key live while it's open.
 */
class StockMediaViewModel(
    private val mediaType: StockMediaClient.StockMediaType,
    private val source: StockMediaClient.StockSource
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var query by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var items: List<StockMediaClient.StockMediaItem> by mutableStateOf(emptyList())
        private set
    var searchError by mutableStateOf<StockSearchError?>(null)
        private set
    var downloadingId by mutableStateOf<String?>(null)
        private set
    var downloadError by mutableStateOf<StockDownloadError?>(null)
        private set

    private var currentPage = 1
    var hasMore by mutableStateOf(false)
        private set

    fun search(apiKey: String) {
        if (apiKey.isBlank() || query.isBlank()) return
        currentPage = 1
        viewModelScope.launch {
            isLoading = true
            searchError = null
            when (val outcome = StockMediaClient.search(source, apiKey, mediaType, query, currentPage)) {
                is StockMediaClient.SearchOutcome.Success -> {
                    items = outcome.items.distinctBy { it.id }
                    hasMore = outcome.hasMore
                }
                StockMediaClient.SearchOutcome.InvalidKey -> searchError = StockSearchError.INVALID_KEY
                StockMediaClient.SearchOutcome.RateLimited -> searchError = StockSearchError.RATE_LIMITED
                StockMediaClient.SearchOutcome.NetworkError -> searchError = StockSearchError.NETWORK_ERROR
                StockMediaClient.SearchOutcome.Failure -> searchError = StockSearchError.FAILURE
            }
            isLoading = false
        }
    }

    fun loadMore(apiKey: String) {
        if (apiKey.isBlank() || query.isBlank() || isLoading || !hasMore) return
        val nextPage = currentPage + 1
        viewModelScope.launch {
            isLoading = true
            when (val outcome = StockMediaClient.search(source, apiKey, mediaType, query, nextPage)) {
                is StockMediaClient.SearchOutcome.Success -> {
                    currentPage = nextPage
                    items = (items + outcome.items).distinctBy { it.id }
                    hasMore = outcome.hasMore
                }
                else -> hasMore = false
            }
            isLoading = false
        }
    }

    fun download(item: StockMediaClient.StockMediaItem, onDownloaded: (String) -> Unit) {
        viewModelScope.launch {
            downloadingId = item.id
            downloadError = null
            when (val outcome = StockMediaClient.download(item)) {
                is StockMediaClient.DownloadOutcome.Success -> onDownloaded(outcome.file.absolutePath)
                StockMediaClient.DownloadOutcome.NetworkError -> downloadError = StockDownloadError.NETWORK_ERROR
                StockMediaClient.DownloadOutcome.Failure -> downloadError = StockDownloadError.FAILURE
            }
            downloadingId = null
        }
    }

    fun dispose() {
        viewModelScope.cancel()
    }
}
