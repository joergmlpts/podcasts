package org.joergmlpts.podcasts.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastSearchApiTest {

    @Test
    fun searchPodcasts_blankQuery_returnsEmptyListImmediately() = runBlocking {
        val client = OkHttpClient()
        val api = PodcastSearchApi(client)

        val result = api.searchPodcasts("   ")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun searchPodcasts_successfulResponse_parsesResultsCorrectly() = runBlocking {
        val jsonResponse = """
            {
              "resultCount": 2,
              "results": [
                {
                  "trackName": "Android Developers Backstage",
                  "artistName": "Android Team",
                  "feedUrl": "https://feeds.feedburner.com/AndroidDevelopersBackstage",
                  "artworkUrl600": "https://example.com/art600.jpg",
                  "primaryGenreName": "Technology",
                  "trackCount": 150
                },
                {
                  "trackName": "Kotlin Bytes",
                  "artistName": "JetBrains",
                  "feedUrl": "https://example.com/kotlin.xml",
                  "artworkUrl100": "https://example.com/art100.jpg",
                  "primaryGenreName": "Programming",
                  "trackCount": 25
                }
              ]
            }
        """.trimIndent()

        val mockInterceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(jsonResponse.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val api = PodcastSearchApi(client)

        val result = api.searchPodcasts("Android")

        assertTrue(result.isSuccess)
        val list = result.getOrNull()!!
        assertEquals(2, list.size)

        val first = list[0]
        assertEquals("Android Developers Backstage", first.title)
        assertEquals("Android Team", first.author)
        assertEquals("https://feeds.feedburner.com/AndroidDevelopersBackstage", first.feedUrl)
        assertEquals("https://example.com/art600.jpg", first.imageUrl)
        assertEquals("Technology", first.genre)
        assertEquals(150, first.trackCount)

        val second = list[1]
        assertEquals("Kotlin Bytes", second.title)
        assertEquals("JetBrains", second.author)
        assertEquals("https://example.com/art100.jpg", second.imageUrl)
    }

    @Test
    fun searchPodcasts_httpError_returnsFailure() = runBlocking {
        val mockInterceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Server Error")
                .body("Internal Server Error".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val api = PodcastSearchApi(client)

        val result = api.searchPodcasts("Tech")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }
}
