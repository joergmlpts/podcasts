package org.joergmlpts.podcasts.ui.screens.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.joergmlpts.podcasts.data.api.PodcastSearchResult
import org.joergmlpts.podcasts.data.local.PodcastEntity
import org.joergmlpts.podcasts.data.repository.PodcastRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class AddFeedDialogState(
    val isOpen: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<PodcastSearchResult> = emptyList(),
    val subscribingFeedUrl: String? = null,
    val error: String? = null,
)

class PodcastGridViewModel(
    private val repository: PodcastRepository
) : ViewModel() {

    val podcasts: StateFlow<List<PodcastEntity>> = repository.allPodcasts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _isRefreshing = MutableStateFlow(value = false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _addDialogState = MutableStateFlow(AddFeedDialogState())
    val addDialogState: StateFlow<AddFeedDialogState> = _addDialogState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Implicit Launch Refresh: implicitly executed on cold start/app launch
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.initializeAndSeedIfNeeded()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun manualRefresh() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try {
                repository.refreshAllFeeds()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun openAddDialog() {
        _addDialogState.value = AddFeedDialogState(isOpen = true)
    }

    fun closeAddDialog() {
        searchJob?.cancel()
        _addDialogState.value = AddFeedDialogState(isOpen = false)
    }

    fun onSearchQueryChange(query: String) {
        _addDialogState.value = _addDialogState.value.copy(
            searchQuery = query,
            error = null
        )

        searchJob?.cancel()
        if (query.trim().isBlank()) {
            _addDialogState.value = _addDialogState.value.copy(
                isSearching = false,
                searchResults = emptyList()
            )
            return
        }

        // Debounce search by 400ms
        searchJob = viewModelScope.launch {
            delay(400.milliseconds)
            executeSearch(query)
        }
    }

    fun executeSearch(query: String) {
        viewModelScope.launch {
            _addDialogState.value = _addDialogState.value.copy(isSearching = true, error = null)
            val result = repository.searchPodcasts(query)
            if (result.isSuccess) {
                _addDialogState.value = _addDialogState.value.copy(
                    isSearching = false,
                    searchResults = result.getOrDefault(emptyList())
                )
            } else {
                _addDialogState.value = _addDialogState.value.copy(
                    isSearching = false,
                    error = "Could not find podcasts for \"$query\". You can also paste an RSS link."
                )
            }
        }
    }

    fun subscribeToFeed(url: String) {
        viewModelScope.launch {
            _addDialogState.value = _addDialogState.value.copy(
                subscribingFeedUrl = url,
                error = null
            )
            val result = repository.syncFeedUrl(url, isInitialSeed = false)
            if (result.isSuccess) {
                _addDialogState.value = AddFeedDialogState(isOpen = false)
            } else {
                _addDialogState.value = _addDialogState.value.copy(
                    subscribingFeedUrl = null,
                    error = result.exceptionOrNull()?.localizedMessage ?: "Failed to subscribe to podcast. Please check connection."
                )
            }
        }
    }

    fun onPodcastClicked(podcastId: String) {
        viewModelScope.launch {
            repository.markPodcastAsRead(podcastId)
        }
    }

    class Factory(private val repository: PodcastRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PodcastGridViewModel(repository) as T
        }
    }
}
