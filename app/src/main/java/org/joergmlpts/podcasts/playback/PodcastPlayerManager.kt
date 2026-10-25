package org.joergmlpts.podcasts.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.joergmlpts.podcasts.data.local.EpisodeEntity
import org.joergmlpts.podcasts.data.local.PodcastEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.milliseconds

data class PlayerUiState(
    val currentEpisode: EpisodeEntity? = null,
    val currentPodcast: PodcastEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f
)

class PodcastPlayerManager(private val context: Context) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PodcastPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    mediaController = controllerFuture?.get()
                    setupPlayerListener()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _uiState.value = _uiState.value.copy(
                    isBuffering = isBuffering,
                    totalDurationMs = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                )
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _uiState.value = _uiState.value.copy(playbackSpeed = playbackParameters.speed)
            }
        })
    }

    @OptIn(UnstableApi::class)
    fun playEpisode(episode: EpisodeEntity, podcast: PodcastEntity) {
        val controller = mediaController ?: return

        // Offline Priority Engine:
        // Check if localFilePath exists and is readable on the file system
        val mediaUri = if (episode.isDownloaded && !episode.localFilePath.isNullOrBlank()) {
            val localFile = File(episode.localFilePath)
            if (localFile.exists() && localFile.canRead()) {
                Uri.fromFile(localFile)
            } else {
                episode.audioUrl.toUri()
            }
        } else {
            episode.audioUrl.toUri()
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcast.title)
            .setArtworkUri(podcast.imageUrl.toUri())
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()

        _uiState.value = _uiState.value.copy(
            currentEpisode = episode,
            currentPodcast = podcast,
            currentPositionMs = episode.playbackPositionMs
        )

        controller.setMediaItem(mediaItem)
        if (episode.playbackPositionMs > 0) {
            controller.seekTo(episode.playbackPositionMs)
        }
        controller.playbackParameters = PlaybackParameters(_uiState.value.playbackSpeed)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
    }

    fun rewind15s() {
        val current = mediaController?.currentPosition ?: 0L
        val target = (current - 15_000L).coerceAtLeast(0L)
        seekTo(target)
    }

    fun forward30s() {
        val current = mediaController?.currentPosition ?: 0L
        val duration = mediaController?.duration ?: 0L
        val target = if (duration > 0) (current + 30_000L).coerceAtMost(duration) else current + 30_000L
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        mediaController?.playbackParameters = PlaybackParameters(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun stopPlayback() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        stopProgressTracker()
        _uiState.value = PlayerUiState()
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { c ->
                    val pos = c.currentPosition
                    val dur = c.duration
                    _uiState.value = _uiState.value.copy(
                        currentPositionMs = pos,
                        totalDurationMs = dur.coerceAtLeast(0L)
                    )
                }
                delay(500.milliseconds)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
    }

    fun release() {
        progressJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
