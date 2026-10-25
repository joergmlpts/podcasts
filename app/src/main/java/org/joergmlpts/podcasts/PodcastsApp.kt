package org.joergmlpts.podcasts

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.joergmlpts.podcasts.data.local.PodcastDatabase
import org.joergmlpts.podcasts.data.repository.PodcastRepository
import org.joergmlpts.podcasts.playback.PodcastPlayerManager

class PodcastsApp : Application() {

    lateinit var database: PodcastDatabase
        private set

    lateinit var repository: PodcastRepository
        private set

    lateinit var playerManager: PodcastPlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        database = PodcastDatabase.getDatabase(this)
        repository = PodcastRepository(
            context = this,
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
        )
        playerManager = PodcastPlayerManager(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Podcast Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Controls for podcast audio playback"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    override fun onTerminate() {
        super.onTerminate()
        playerManager.release()
    }

    companion object {
        const val CHANNEL_ID = "podcast_playback_channel"
    }
}
