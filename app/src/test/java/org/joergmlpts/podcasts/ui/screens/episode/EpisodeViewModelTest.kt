package org.joergmlpts.podcasts.ui.screens.episode

import org.joergmlpts.podcasts.data.local.EpisodeEntity
import org.joergmlpts.podcasts.data.local.PodcastEntity
import org.joergmlpts.podcasts.data.repository.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeViewModelTest {

    private val podcastId = "pod-123"
    private val repository: PodcastRepository = mockk(relaxed = true)
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val podcastFlow = MutableStateFlow<PodcastEntity?>(null)
    private val episodesFlow = MutableStateFlow<List<EpisodeEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getPodcastByIdFlow(podcastId) } returns podcastFlow
        coEvery { repository.getEpisodesForPodcast(podcastId) } returns episodesFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deleteDialog_openAndClose_updatesState() = runTest {
        val viewModel = EpisodeViewModel(podcastId, repository)

        assertFalse(viewModel.showDeleteDialog.value)

        viewModel.openDeleteDialog()
        assertTrue(viewModel.showDeleteDialog.value)

        viewModel.closeDeleteDialog()
        assertFalse(viewModel.showDeleteDialog.value)
    }

    @Test
    fun confirmDeletePodcast_deletesPodcastAndInvokesCallback() = runTest {
        val viewModel = EpisodeViewModel(podcastId, repository)
        viewModel.openDeleteDialog()

        var callbackCalled = false
        viewModel.confirmDeletePodcast {
            callbackCalled = true
        }
        advanceUntilIdle()

        coVerify { repository.deletePodcast(podcastId) }
        assertFalse(viewModel.showDeleteDialog.value)
        assertTrue(callbackCalled)
    }

    @Test
    fun downloadEpisode_tracksProgressAndClearsOnCompletion() = runTest {
        val episode = EpisodeEntity(
            id = "ep-1",
            podcastId = podcastId,
            title = "Ep Title",
            description = "Desc",
            pubDate = 1000L,
            audioUrl = "https://example.com/audio.mp3",
            duration = "10:00",
            durationSec = 600L,
        )

        val progressLambdaSlot = slot<(Int) -> Unit>()
        coEvery { repository.downloadEpisode(episode, capture(progressLambdaSlot)) } answers {
            progressLambdaSlot.captured.invoke(50)
            Result.success("/path/to/file.mp3")
        }

        val viewModel = EpisodeViewModel(podcastId, repository)

        viewModel.downloadEpisode(episode)
        advanceUntilIdle()

        coVerify { repository.downloadEpisode(episode, any()) }
        assertTrue(viewModel.downloadingIds.value.isEmpty())
    }

    @Test
    fun deleteDownloadedEpisode_callsRepositoryDeleteLocalDownload() = runTest {
        val episode = EpisodeEntity(
            id = "ep-1",
            podcastId = podcastId,
            title = "Ep Title",
            description = "Desc",
            pubDate = 1000L,
            audioUrl = "https://example.com/audio.mp3",
            duration = "10:00",
            durationSec = 600L,
            isDownloaded = true,
            localFilePath = "/path/to/file.mp3"
        )

        val viewModel = EpisodeViewModel(podcastId, repository)

        viewModel.deleteDownloadedEpisode(episode)
        advanceUntilIdle()

        coVerify { repository.deleteLocalDownload(episode) }
    }

    @Test
    fun refreshPodcast_callsRepositoryRefreshPodcast() = runTest {
        coEvery { repository.refreshPodcast(podcastId) } returns Result.success(mockk(relaxed = true))

        val viewModel = EpisodeViewModel(podcastId, repository)

        assertFalse(viewModel.isRefreshing.value)

        viewModel.refreshPodcast()
        advanceUntilIdle()

        coVerify { repository.refreshPodcast(podcastId) }
        assertFalse(viewModel.isRefreshing.value)
    }
}
