package org.joergmlpts.podcasts.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["podcastId"]), Index(value = ["pubDate"])]
)
data class EpisodeEntity(
    @PrimaryKey val id: String, // guid or hash of audioUrl
    val podcastId: String,
    val title: String,
    val description: String,
    val pubDate: Long, // Epoch timestamp for sorting descending
    val audioUrl: String,
    val duration: String,
    val durationSec: Long = 0L,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val downloadProgress: Int = 0,
    val playbackPositionMs: Long = 0L,
    val isPlayed: Boolean = false
)
