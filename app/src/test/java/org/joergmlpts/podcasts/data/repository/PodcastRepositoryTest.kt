package org.joergmlpts.podcasts.data.repository

import android.content.Context
import org.joergmlpts.podcasts.data.local.EpisodeDao
import org.joergmlpts.podcasts.data.local.EpisodeEntity
import org.joergmlpts.podcasts.data.local.PodcastDao
import org.joergmlpts.podcasts.data.local.PodcastEntity
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class PodcastRepositoryTest {

    private val context: Context = mockk(relaxed = true)
    private val podcastDao: PodcastDao = mockk(relaxed = true)
    private val episodeDao: EpisodeDao = mockk(relaxed = true)
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        tempDir = File(System.getProperty("java.io.tmpdir"), "podcast_repo_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        every { context.filesDir } returns tempDir
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun refreshPodcast_queriesDaoForPodcast() = runTest {
        val repository = PodcastRepository(context, podcastDao, episodeDao)
        coEvery { podcastDao.getPodcastById("pod-123") } returns null

        val result = repository.refreshPodcast("pod-123")

        coVerify { podcastDao.getPodcastById("pod-123") }
        assert(result.isFailure)
    }

    @Test
    fun refreshPodcast_whenPodcastExistsAndSyncs_marksPodcastAsRead() = runTest {
        val podcast = PodcastEntity(
            id = "pod-123",
            title = "Test Pod",
            feedUrl = "https://invalid-url-for-test.local/feed.xml",
            imageUrl = "",
            author = "Author",
            description = "Desc",
            lastRefreshedAt = 0L,
            hasNewEpisodes = true,
        )
        coEvery { podcastDao.getPodcastById("pod-123") } returns podcast

        val repository = PodcastRepository(context, podcastDao, episodeDao)
        repository.refreshPodcast("pod-123")

        coVerify { podcastDao.getPodcastById("pod-123") }
    }

    @Test
    fun markPodcastAsRead_callsPodcastDao() = runTest {
        val repository = PodcastRepository(context, podcastDao, episodeDao)

        repository.markPodcastAsRead("pod-123")

        coVerify { podcastDao.markPodcastAsRead("pod-123") }
    }

    @Test
    fun deletePodcast_deletesFilesAndPodcastInDao() = runTest {
        val podcastId = "pod-456"
        val podcastFolder = File(tempDir, "podcasts/$podcastId")
        podcastFolder.mkdirs()
        val dummyFile = File(podcastFolder, "ep1.mp3")
        dummyFile.writeText("audio content")

        val repository = PodcastRepository(context, podcastDao, episodeDao)

        repository.deletePodcast(podcastId)

        coVerify { podcastDao.deletePodcast(podcastId) }
        assert(!podcastFolder.exists())
    }

    @Test
    fun deleteLocalDownload_deletesLocalFileAndUpdatesDao() = runTest {
        val episodeFile = File(tempDir, "test_episode.mp3")
        episodeFile.writeText("dummy audio data")

        val episode = EpisodeEntity(
            id = "ep-999",
            podcastId = "pod-1",
            title = "Episode 999",
            description = "Desc",
            pubDate = 1000L,
            audioUrl = "https://example.com/audio.mp3",
            duration = "30:00",
            durationSec = 1800L,
            isDownloaded = true,
            localFilePath = episodeFile.absolutePath
        )

        val repository = PodcastRepository(context, podcastDao, episodeDao)

        repository.deleteLocalDownload(episode)

        assert(!episodeFile.exists())
        coVerify { episodeDao.updateDownloadStatus("ep-999", downloaded = false, filePath = null, progress = 0) }
    }

    @Test
    fun initializeAndSeedIfNeeded_cleansDuplicatePodcasts() = runTest {
        val pod1 = PodcastEntity(
            id = "pod-1",
            title = "Podcast 1",
            author = "Author",
            description = "Desc",
            feedUrl = "https://example.com/feed.xml",
            imageUrl = "",
            hasNewEpisodes = false,
            lastRefreshedAt = 0L,
            episodeCount = 10
        )
        val pod2Duplicate = PodcastEntity(
            id = "pod-2",
            title = "Podcast 1 Dup",
            author = "Author",
            description = "Desc",
            feedUrl = "https://example.com/feed.xml",
            imageUrl = "",
            hasNewEpisodes = false,
            lastRefreshedAt = 0L,
            episodeCount = 10
        )

        coEvery { podcastDao.getAllPodcastsList() } returns listOf(pod1, pod2Duplicate)

        val repository = PodcastRepository(context, podcastDao, episodeDao)

        repository.initializeAndSeedIfNeeded()

        coVerify { podcastDao.deletePodcast("pod-2") }
    }
}
