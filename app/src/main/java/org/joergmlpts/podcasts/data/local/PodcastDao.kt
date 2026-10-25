package org.joergmlpts.podcasts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    fun getAllPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    suspend fun getAllPodcastsList(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    suspend fun getPodcastById(podcastId: String): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE id = :podcastId OR feedUrl = :feedUrl LIMIT 1")
    suspend fun getPodcastByIdOrFeedUrl(podcastId: String, feedUrl: String): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl LIMIT 1")
    suspend fun getPodcastByFeedUrl(feedUrl: String): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    fun getPodcastByIdFlow(podcastId: String): Flow<PodcastEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Update
    suspend fun updatePodcast(podcast: PodcastEntity)

    @Query("UPDATE podcasts SET hasNewEpisodes = 0 WHERE id = :podcastId")
    suspend fun markPodcastAsRead(podcastId: String)

    @Query("UPDATE podcasts SET hasNewEpisodes = 1 WHERE id = :podcastId")
    suspend fun markPodcastHasNew(podcastId: String)

    @Query("DELETE FROM podcasts WHERE id = :podcastId")
    suspend fun deletePodcast(podcastId: String)

    @Query("SELECT COUNT(*) FROM podcasts")
    suspend fun getPodcastCount(): Int
}
