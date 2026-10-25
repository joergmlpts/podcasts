package org.joergmlpts.podcasts.ui.screens.grid

import org.joergmlpts.podcasts.data.api.PodcastSearchResult
import org.joergmlpts.podcasts.data.local.PodcastEntity
import org.joergmlpts.podcasts.data.repository.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastGridViewModelTest {

    private val repository: PodcastRepository = mockk(relaxed = true)
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val allPodcastsFlow = MutableStateFlow<List<PodcastEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.allPodcasts } returns allPodcastsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openAddDialog_setsIsOpenTrue() = runTest {
        val viewModel = PodcastGridViewModel(repository)

        viewModel.openAddDialog()

        assertTrue(viewModel.addDialogState.value.isOpen)
    }

    @Test
    fun closeAddDialog_resetsState() = runTest {
        val viewModel = PodcastGridViewModel(repository)
        viewModel.openAddDialog()

        viewModel.closeAddDialog()

        assertFalse(viewModel.addDialogState.value.isOpen)
    }

    @Test
    fun onSearchQueryChange_blankQuery_clearsResultsAndSearchingFlag() = runTest {
        val viewModel = PodcastGridViewModel(repository)
        viewModel.openAddDialog()

        viewModel.onSearchQueryChange("    ")

        val state = viewModel.addDialogState.value
        assertEquals("    ", state.searchQuery)
        assertFalse(state.isSearching)
        assertTrue(state.searchResults.isEmpty())
    }

    @Test
    fun onSearchQueryChange_validQuery_debouncesAndExecutesSearch() = runTest {
        val searchResults = listOf(
            PodcastSearchResult("Title 1", "Author 1", "https://feed1.com", "", "Tech", 10),
        )
        coEvery { repository.searchPodcasts("Android") } returns Result.success(searchResults)

        val viewModel = PodcastGridViewModel(repository)
        viewModel.onSearchQueryChange("Android")

        // Before debounce delay (400ms), search hasn't executed
        advanceTimeBy(200)
        coVerify(exactly = 0) { repository.searchPodcasts(any()) }

        // After debounce delay
        advanceTimeBy(250)
        advanceUntilIdle()

        val state = viewModel.addDialogState.value
        assertFalse(state.isSearching)
        assertEquals(1, state.searchResults.size)
        assertEquals("Title 1", state.searchResults[0].title)
    }

    @Test
    fun subscribeToFeed_success_closesDialog() = runTest {
        val mockPodcast = PodcastEntity(
            id = "pod-1",
            title = "Test Pod",
            author = "Test Author",
            description = "Desc",
            feedUrl = "https://example.com/feed.xml",
            imageUrl = "",
            hasNewEpisodes = false,
            lastRefreshedAt = 0L,
            episodeCount = 5
        )
        coEvery { repository.syncFeedUrl("https://example.com/feed.xml", isInitialSeed = false) } returns Result.success(mockPodcast)

        val viewModel = PodcastGridViewModel(repository)
        viewModel.openAddDialog()

        viewModel.subscribeToFeed("https://example.com/feed.xml")
        advanceUntilIdle()

        assertFalse(viewModel.addDialogState.value.isOpen)
    }

    @Test
    fun subscribeToFeed_failure_setsErrorMessage() = runTest {
        coEvery { repository.syncFeedUrl("https://invalid.com/feed.xml", isInitialSeed = false) } returns Result.failure(Exception("Connection timeout"))

        val viewModel = PodcastGridViewModel(repository)
        viewModel.openAddDialog()

        viewModel.subscribeToFeed("https://invalid.com/feed.xml")
        advanceUntilIdle()

        val state = viewModel.addDialogState.value
        assertTrue(state.isOpen)
        assertNull(state.subscribingFeedUrl)
        assertEquals("Connection timeout", state.error)
    }

    @Test
    fun onPodcastClicked_callsRepositoryMarkAsRead() = runTest {
        val viewModel = PodcastGridViewModel(repository)

        viewModel.onPodcastClicked("pod-123")
        advanceUntilIdle()

        coVerify { repository.markPodcastAsRead("pod-123") }
    }

    @Test
    fun manualRefresh_callsRepositoryRefreshAllFeeds() = runTest {
        val viewModel = PodcastGridViewModel(repository)
        advanceUntilIdle() // let init launch finish

        viewModel.manualRefresh()
        advanceUntilIdle()

        coVerify { repository.refreshAllFeeds() }
    }
}
