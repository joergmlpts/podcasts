package org.joergmlpts.podcasts.ui.screens.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.joergmlpts.podcasts.data.local.EpisodeEntity
import org.joergmlpts.podcasts.data.local.PodcastEntity
import org.joergmlpts.podcasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EpisodeViewModel(
    private val podcastId: String,
    private val repository: PodcastRepository
) : ViewModel() {

    val podcast: StateFlow<PodcastEntity?> = repository.getPodcastByIdFlow(podcastId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val episodes: StateFlow<List<EpisodeEntity>> = repository.getEpisodesForPodcast(podcastId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _downloadingIds = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadingIds: StateFlow<Map<String, Int>> = _downloadingIds.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    fun refreshPodcast() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try {
                repository.refreshPodcast(podcastId)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun openDeleteDialog() {
        _showDeleteDialog.value = true
    }

    fun closeDeleteDialog() {
        _showDeleteDialog.value = false
    }

    fun confirmDeletePodcast(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePodcast(podcastId)
            _showDeleteDialog.value = false
            onDeleted()
        }
    }

    fun downloadEpisode(episode: EpisodeEntity) {
        viewModelScope.launch {
            _downloadingIds.value += (episode.id to 0)
            repository.downloadEpisode(episode) { progress ->
                _downloadingIds.value += (episode.id to progress)
            }
            _downloadingIds.value -= episode.id
        }
    }

    fun deleteDownloadedEpisode(episode: EpisodeEntity) {
        viewModelScope.launch {
            repository.deleteLocalDownload(episode)
        }
    }

    class Factory(
        private val podcastId: String,
        private val repository: PodcastRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EpisodeViewModel(podcastId, repository) as T
        }
    }
}
