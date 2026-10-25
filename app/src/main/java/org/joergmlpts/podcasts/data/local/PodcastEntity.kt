package org.joergmlpts.podcasts.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: String, // SHA-256 or hash of feedUrl
    val title: String,
    val author: String,
    val description: String,
    val feedUrl: String,
    val imageUrl: String,
    val hasNewEpisodes: Boolean = false, // Controls the animation of the podcast icon
    val lastRefreshedAt: Long = System.currentTimeMillis(),
    val episodeCount: Int = 0,
)
