package app.fahrtenbuch.mobile.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "cache")
data class CacheEntity(@PrimaryKey val key: String, val json: String, val updatedAt: String)

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val licensePlate: String?,
    val bluetoothMac: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val syncState: String = "SYNCED"
)

@Entity(tableName = "trips", indices = [Index("vehicleId"), Index("startedAt")])
data class TripEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val type: String,
    val status: String,
    val startedAt: String,
    val endedAt: String?,
    val startAddress: String?,
    val endAddress: String?,
    val purpose: String?,
    val contact: String?,
    val notes: String?,
    val distanceKm: Double?,
    val durationSeconds: Int?,
    val createdAt: String?,
    val updatedAt: String?,
    val vehicleName: String?,
    val tagsJson: String = "[]",
    val syncState: String = "SYNCED"
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val syncState: String = "SYNCED"
)

@Entity(tableName = "offline_trips")
data class OfflineTripEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val vehicleId: String,
    val type: String,
    val startedAt: String,
    val endedAt: String?,
    val state: String, // RECORDING, STOPPED, SYNCED
    val pendingStop: Boolean = false,
    val syncError: String? = null,
    // Auf dem Gerät ermittelt (Geocoder bzw. GPS-Punkte), vor allem für den lokalen Modus
    val startAddress: String? = null,
    val endAddress: String? = null,
    val distanceKm: Double? = null
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

    @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE") suspend fun vehicles(): List<VehicleEntity>
    @Query("SELECT * FROM vehicles WHERE id=:id") suspend fun vehicle(id: String): VehicleEntity?
    @Query("SELECT * FROM vehicles WHERE bluetoothMac=:mac COLLATE NOCASE LIMIT 1") suspend fun vehicleByBluetooth(mac: String): VehicleEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putVehicle(value: VehicleEntity)
    @Query("UPDATE vehicles SET bluetoothMac=:mac, updatedAt=:updatedAt WHERE id=:id") suspend fun setVehicleBluetooth(id: String, mac: String?, updatedAt: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putVehicles(values: List<VehicleEntity>)
    @Query("DELETE FROM vehicles") suspend fun clearVehicles()

    @Query("SELECT * FROM trips ORDER BY startedAt DESC") suspend fun trips(): List<TripEntity>
    @Query("SELECT * FROM trips WHERE id=:id") suspend fun storedTrip(id: String): TripEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTripRecord(value: TripEntity)
    @Query("UPDATE offline_trips SET type=:type WHERE localId=:id") suspend fun setLocalTripType(id: String, type: String)
    @Query("UPDATE offline_trips SET vehicleId=:newVehicleId WHERE vehicleId=:oldVehicleId") suspend fun remapOfflineTripsVehicle(oldVehicleId: String, newVehicleId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTripRecords(values: List<TripEntity>)
    @Query("DELETE FROM trips") suspend fun clearTripRecords()

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE") suspend fun tags(): List<TagEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTags(values: List<TagEntity>)
    @Query("DELETE FROM tags") suspend fun clearTags()

    @Query("SELECT * FROM offline_trips WHERE localId = :id") suspend fun trip(id: String): OfflineTripEntity?
    @Query("SELECT * FROM offline_trips WHERE state != 'SYNCED' ORDER BY startedAt") suspend fun pendingTrips(): List<OfflineTripEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTrip(value: OfflineTripEntity)
    @Query("UPDATE offline_trips SET syncError=:message WHERE localId=:localId") suspend fun setTripSyncError(localId: String, message: String?)
    @Query("SELECT * FROM offline_trips WHERE endedAt IS NOT NULL AND (startAddress IS NULL OR endAddress IS NULL OR distanceKm IS NULL)")
    suspend fun tripsMissingDetails(): List<OfflineTripEntity>
    @Query("UPDATE offline_trips SET startAddress=:startAddress, endAddress=:endAddress, distanceKm=:distanceKm WHERE localId=:localId")
    suspend fun setTripDetails(localId: String, startAddress: String?, endAddress: String?, distanceKm: Double?)
    @Query("UPDATE offline_trips SET remoteId=:remoteId WHERE localId=:localId") suspend fun setRemoteId(localId: String, remoteId: String)
    @Query("UPDATE offline_trips SET state='SYNCED', pendingStop=0, syncError=NULL WHERE localId=:localId") suspend fun markTripSynced(localId: String)

    @Insert suspend fun addPoints(points: List<TrackPointEntity>)
    @Query("SELECT * FROM track_points WHERE localTripId=:tripId ORDER BY id") suspend fun points(tripId: String): List<TrackPointEntity>
    @Query("SELECT * FROM track_points WHERE localTripId=:tripId AND uploaded=0 ORDER BY id LIMIT :limit") suspend fun pendingPoints(tripId: String, limit: Int = 500): List<TrackPointEntity>
    @Query("UPDATE track_points SET uploaded=1 WHERE id IN (:ids)") suspend fun markPointsUploaded(ids: List<Long>)

    @Transaction
    suspend fun replaceVehicles(values: List<VehicleEntity>) {
        clearVehicles()
        putVehicles(values)
    }

    @Transaction
    suspend fun replaceTrips(values: List<TripEntity>) {
        clearTripRecords()
        putTripRecords(values)
    }

    @Transaction
    suspend fun replaceTags(values: List<TagEntity>) {
        clearTags()
        putTags(values)
    }
}

@Database(
    entities = [
        CacheEntity::class,
        VehicleEntity::class,
        TripEntity::class,
        TagEntity::class,
        OfflineTripEntity::class,
        TrackPointEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun dao(): OfflineDao

    companion object {
        @Volatile private var instance: OfflineDatabase? = null

        fun get(context: Context): OfflineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                OfflineDatabase::class.java,
                "fahrtenbuch-offline.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN startAddress TEXT")
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN endAddress TEXT")
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN distanceKm REAL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN syncError TEXT")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS vehicles (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        licensePlate TEXT,
                        bluetoothMac TEXT,
                        createdAt TEXT,
                        updatedAt TEXT,
                        syncState TEXT NOT NULL DEFAULT 'SYNCED'
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trips (
                        id TEXT NOT NULL PRIMARY KEY,
                        vehicleId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        startedAt TEXT NOT NULL,
                        endedAt TEXT,
                        startAddress TEXT,
                        endAddress TEXT,
                        purpose TEXT,
                        contact TEXT,
                        notes TEXT,
                        distanceKm REAL,
                        durationSeconds INTEGER,
                        createdAt TEXT,
                        updatedAt TEXT,
                        vehicleName TEXT,
                        tagsJson TEXT NOT NULL DEFAULT '[]',
                        syncState TEXT NOT NULL DEFAULT 'SYNCED'
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trips_vehicleId ON trips(vehicleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trips_startedAt ON trips(startedAt)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        syncState TEXT NOT NULL DEFAULT 'SYNCED'
                    )
                """.trimIndent())
            }
        }
    }
}
