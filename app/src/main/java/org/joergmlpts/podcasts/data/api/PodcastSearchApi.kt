package org.joergmlpts.podcasts.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class PodcastSearchResult(
    val title: String,
    val author: String,
    val feedUrl: String,
    val imageUrl: String,
    val genre: String,
    val trackCount: Int,
)

class PodcastSearchApi(private val client: OkHttpClient) {

    suspend fun searchPodcasts(query: String, limit: Int = 20): Result<List<PodcastSearchResult>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&media=podcast&entity=podcast&limit=$limit"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Podcasts/1.0 (Android Native Audio)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Search failed with HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.success(emptyList())
            val json = JSONObject(body)
            val resultsArray = json.optJSONArray("results") ?: return@withContext Result.success(emptyList())

            val list = mutableListOf<PodcastSearchResult>()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                val feedUrl = item.optString("feedUrl", "").trim()
                val trackName = item.optString("trackName", "").trim()
                if (feedUrl.isBlank() || trackName.isBlank()) continue

                val artwork = item.optString("artworkUrl600")
                    .ifBlank { item.optString("artworkUrl100") }
                val artist = item.optString("artistName", "Unknown Artist")
                val genre = item.optString("primaryGenreName", "Podcast")
                val trackCount = item.optInt("trackCount", 0)

                list.add(
                    PodcastSearchResult(
                        title = trackName,
                        author = artist,
                        feedUrl = feedUrl,
                        imageUrl = artwork,
                        genre = genre,
                        trackCount = trackCount,
                    )
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
