package org.joergmlpts.podcasts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    // Crucial: Ordered by pubDate DESC (newest first)
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY pubDate DESC")
    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId")
    suspend fun getEpisodesForPodcastList(podcastId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getEpisodeById(episodeId: String): EpisodeEntity?

    @Query("SELECT id FROM episodes WHERE podcastId = :podcastId")
    suspend fun getEpisodeIdsForPodcast(podcastId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Query("UPDATE episodes SET isDownloaded = :downloaded, localFilePath = :filePath, downloadProgress = :progress WHERE id = :episodeId")
    suspend fun updateDownloadStatus(episodeId: String, downloaded: Boolean, filePath: String?, progress: Int = if (downloaded) 100 else 0)

    @Query("UPDATE episodes SET playbackPositionMs = :position, isPlayed = :isPlayed WHERE id = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: String, position: Long, isPlayed: Boolean)

    @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesByPodcast(podcastId: String)
}
