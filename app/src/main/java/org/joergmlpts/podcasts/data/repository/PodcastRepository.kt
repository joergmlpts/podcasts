package org.joergmlpts.podcasts.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import org.joergmlpts.podcasts.data.api.PodcastSearchApi
import org.joergmlpts.podcasts.data.api.PodcastSearchResult
import org.joergmlpts.podcasts.data.local.EpisodeDao
import org.joergmlpts.podcasts.data.local.EpisodeEntity
import org.joergmlpts.podcasts.data.local.PodcastDao
import org.joergmlpts.podcasts.data.local.PodcastEntity
import org.joergmlpts.podcasts.data.parser.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

const val maxUpdatesInParallel = 6 // do not update more than this many podcasts in parallel

class PodcastRepository(
    private val context: Context,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val searchApi = PodcastSearchApi(client)

    val allPodcasts: Flow<List<PodcastEntity>> = podcastDao.getAllPodcasts()

    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>> {
        return episodeDao.getEpisodesForPodcast(podcastId)
    }

    fun getPodcastByIdFlow(podcastId: String): Flow<PodcastEntity?> {
        return podcastDao.getPodcastByIdFlow(podcastId)
    }

    suspend fun searchPodcasts(query: String): Result<List<PodcastSearchResult>> {
        return searchApi.searchPodcasts(query)
    }

    suspend fun initializeAndSeedIfNeeded() = withContext(Dispatchers.IO) {
        cleanupDuplicatePodcasts()
        refreshAllFeeds()
    }

    private suspend fun cleanupDuplicatePodcasts() {
        val podcasts = podcastDao.getAllPodcastsList()
        val seenFeedUrls = mutableSetOf<String>()
        for ((id, _, _, _, feedUrl) in podcasts) {
            if (seenFeedUrls.contains(feedUrl)) {
                podcastDao.deletePodcast(id)
            } else {
                seenFeedUrls.add(feedUrl)
            }
        }
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager) ?: return true
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun refreshAllFeeds() = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            Log.i("PodcastRepo", "Network unavailable; skipping feed refresh on launch.")
            return@withContext
        }
        val podcastsInDb = podcastDao.getAllPodcastsList()
        val semaphore = Semaphore(permits = maxUpdatesInParallel)
        coroutineScope {
            podcastsInDb.map { (_, _, _, _, feedUrl) ->
                async {
                    semaphore.withPermit {
                        try {
                            syncFeedUrl(feedUrl, isInitialSeed = false)
                        } catch (e: Exception) {
                            Log.e("PodcastRepo", "Refresh error on $feedUrl", e)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun syncFeedUrl(feedUrl: String, isInitialSeed: Boolean = false): Result<PodcastEntity> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(UnknownHostException("Network unavailable"))
        }

        val request = try {
            Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "Podcasts/1.0 (Android Native Audio)")
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .build()
        } catch (e: Exception) {
            Log.e("PodcastRepo", "Invalid feed URL: $feedUrl", e)
            return@withContext Result.failure(e)
        }

        val bodyString = try {
            fetchFeedBodyWithRetry(request)
        } catch (e: java.io.IOException) {
            if ((!isNetworkAvailable()) || (e is UnknownHostException)) {
                Log.d("PodcastRepo", "Feed fetch deferred (offline/DNS unresolved) for $feedUrl: ${e.message}")
            } else {
                Log.w("PodcastRepo", "Network error fetching feed $feedUrl: ${e.localizedMessage}")
            }
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            Log.e("PodcastRepo", "Failed to fetch feed $feedUrl: ${e.localizedMessage}", e)
            return@withContext Result.failure(e)
        }

        val (podcast, episodes) = try {
            RssParser.parseRss(feedUrl, bodyString)
        } catch (e: Exception) {
            Log.e("PodcastRepo", "Failed to parse feed XML for: $feedUrl", e)
            return@withContext Result.failure(e)
        }

        try {
            val existingPodcast = podcastDao.getPodcastByIdOrFeedUrl(podcast.id, feedUrl)
            val targetPodcastId = existingPodcast?.id ?: podcast.id

            val existingEpisodeIds = episodeDao.getEpisodeIdsForPodcast(targetPodcastId).toSet()

            var hasNewEpisodes = existingPodcast?.hasNewEpisodes ?: false
            val newEpisodes = episodes.filter { !existingEpisodeIds.contains(it.id) }

            if ((!isInitialSeed) && newEpisodes.isNotEmpty() && (existingPodcast != null)) {
                hasNewEpisodes = true
            }

            val podcastToSave = podcast.copy(
                id = targetPodcastId,
                hasNewEpisodes = hasNewEpisodes,
                lastRefreshedAt = System.currentTimeMillis(),
                episodeCount = episodes.size
            )

            val existingEpisodesMap = episodeDao.getEpisodesForPodcastList(targetPodcastId).associateBy { it.id }

            val episodesToSave = episodes.map { ep ->
                val targetEpId = ep.id
                val existingEp = existingEpisodesMap[targetEpId]

                val (isDownloaded, localPath, progress) = if (existingEp != null && existingEp.isDownloaded) {
                    if (!existingEp.localFilePath.isNullOrBlank() && File(existingEp.localFilePath).exists()) {
                        Triple(true, existingEp.localFilePath, existingEp.downloadProgress)
                    } else {
                        Triple(false, null, 0)
                    }
                } else {
                    Triple(false, existingEp?.localFilePath, existingEp?.downloadProgress ?: 0)
                }

                val positionMs = existingEp?.playbackPositionMs ?: 0L
                val isPlayed = existingEp?.isPlayed ?: false

                ep.copy(
                    podcastId = targetPodcastId,
                    isDownloaded = isDownloaded,
                    localFilePath = localPath,
                    downloadProgress = progress,
                    playbackPositionMs = positionMs,
                    isPlayed = isPlayed
                )
            }

            podcastDao.insertPodcast(podcastToSave)
            episodeDao.upsertEpisodes(episodesToSave)

            Result.success(podcastToSave)
        } catch (e: Exception) {
            Log.e("PodcastRepo", "Database error saving feed $feedUrl", e)
            Result.failure(e)
        }
    }

    suspend fun refreshPodcast(podcastId: String): Result<PodcastEntity> = withContext(Dispatchers.IO) {
        val podcast = podcastDao.getPodcastById(podcastId)
            ?: return@withContext Result.failure(IllegalArgumentException("Podcast not found: $podcastId"))
        val result = syncFeedUrl(podcast.feedUrl, isInitialSeed = false)
        if (result.isSuccess) {
            markPodcastAsRead(podcastId)
            Result.success(result.getOrThrow().copy(hasNewEpisodes = false))
        } else {
            result
        }
    }

    suspend fun markPodcastAsRead(podcastId: String) = withContext(Dispatchers.IO) {
        podcastDao.markPodcastAsRead(podcastId)
    }

    suspend fun deletePodcast(podcastId: String) = withContext(Dispatchers.IO) {
        val podcastDir = File(context.filesDir, "podcasts/$podcastId")
        if (podcastDir.exists()) {
            podcastDir.deleteRecursively()
        }
        podcastDao.deletePodcast(podcastId)
    }

    suspend fun downloadEpisode(episode: EpisodeEntity, onProgress: (Int) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            val podcastDir = File(context.filesDir, "podcasts/${episode.podcastId}")
            if (!podcastDir.exists()) {
                podcastDir.mkdirs()
            }
            val targetFile = File(podcastDir, "${episode.id}.mp3")

            val request = Request.Builder().url(episode.audioUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed download ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Null body"))
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            val localPath = targetFile.absolutePath
            episodeDao.updateDownloadStatus(episode.id, true, localPath, 100)
            Result.success(localPath)
        } catch (e: Exception) {
            Log.e("PodcastRepo", "Download failed for ${episode.title}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteLocalDownload(episode: EpisodeEntity) = withContext(Dispatchers.IO) {
        episode.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        episodeDao.updateDownloadStatus(episode.id, false, null, 0)
    }

    private suspend fun fetchFeedBodyWithRetry(request: Request): String {
        return try {
            executeRequest(request)
        } catch (_: UnknownHostException) {
            // DNS may be warming up on cold start; retry once after 500ms
            delay(500.milliseconds)
            executeRequest(request)
        }
    }

    private fun executeRequest(request: Request): String {
        val response = client.newCall(request).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                throw java.io.IOException("HTTP error ${resp.code}")
            }
            resp.body?.string() ?: throw java.io.IOException("Empty feed body")
        }
    }
}
