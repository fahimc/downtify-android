package com.downtify.android.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_tracks", primaryKeys = ["serverId", "trackId"])
data class OfflineTrack(
    val trackId: String, val serverId: String, val serverUrl: String,
    val title: String, val artist: String = "", val album: String = "",
    val artwork: String = "", val duration: Long = 0,
    val localFilePath: String, val downloadStatus: String = "downloaded",
    val downloadedAt: Long = System.currentTimeMillis(), val playbackPosition: Long = 0
)

@Dao interface OfflineTrackDao {
    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAt DESC") fun observeAll(): Flow<List<OfflineTrack>>
    @Query("SELECT * FROM offline_tracks WHERE serverId=:server AND trackId=:id LIMIT 1") suspend fun find(server: String, id: String): OfflineTrack?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(track: OfflineTrack)
    @Query("DELETE FROM offline_tracks WHERE serverId=:server AND trackId=:id") suspend fun remove(server: String, id: String)
    @Query("UPDATE offline_tracks SET playbackPosition=:position WHERE serverId=:server AND trackId=:id") suspend fun position(server: String, id: String, position: Long)
    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAt DESC") suspend fun all(): List<OfflineTrack>
}

@Database(entities = [OfflineTrack::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): OfflineTrackDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: android.content.Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "downtify.db").build().also { instance = it }
        }
    }
}
