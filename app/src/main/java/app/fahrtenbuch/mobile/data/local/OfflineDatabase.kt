package app.fahrtenbuch.mobile.data.local

import android.content.Context
import androidx.room.*

@Entity(tableName = "cache")
data class CacheEntity(@PrimaryKey val key: String, val json: String, val updatedAt: String)

@Entity(tableName = "offline_trips")
data class OfflineTripEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val vehicleId: String,
    val type: String,
    val startedAt: String,
    val endedAt: String?,
    val state: String, // RECORDING, STOPPED, SYNCED
    val pendingStop: Boolean = false
)

@Entity(tableName = "track_points", indices = [Index("localTripId")])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localTripId: String,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val accuracy: Double?,
    val speed: Double?,
    val bearing: Double?,
    val recordedAt: String,
    val uploaded: Boolean = false
)

@Dao
interface OfflineDao {
    @Query("SELECT * FROM cache WHERE `key` = :key") suspend fun cache(key: String): CacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCache(value: CacheEntity)

    @Query("SELECT * FROM offline_trips WHERE localId = :id") suspend fun trip(id: String): OfflineTripEntity?
    @Query("SELECT * FROM offline_trips WHERE state != 'SYNCED' ORDER BY startedAt") suspend fun pendingTrips(): List<OfflineTripEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTrip(value: OfflineTripEntity)
    @Query("UPDATE offline_trips SET remoteId=:remoteId WHERE localId=:localId") suspend fun setRemoteId(localId: String, remoteId: String)
    @Query("UPDATE offline_trips SET state='SYNCED', pendingStop=0 WHERE localId=:localId") suspend fun markTripSynced(localId: String)

    @Insert suspend fun addPoints(points: List<TrackPointEntity>)
    @Query("SELECT * FROM track_points WHERE localTripId=:tripId AND uploaded=0 ORDER BY id LIMIT :limit") suspend fun pendingPoints(tripId: String, limit: Int = 500): List<TrackPointEntity>
    @Query("UPDATE track_points SET uploaded=1 WHERE id IN (:ids)") suspend fun markPointsUploaded(ids: List<Long>)
}

@Database(entities = [CacheEntity::class, OfflineTripEntity::class, TrackPointEntity::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun dao(): OfflineDao
    companion object {
        @Volatile private var instance: OfflineDatabase? = null
        fun get(context: Context): OfflineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OfflineDatabase::class.java, "fahrtenbuch-offline.db").build().also { instance = it }
        }
    }
}
