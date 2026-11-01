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
    val syncState: String = "SYNCED",
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
    /** Abgemeldet (deaktiviert): bleibt in der Liste, wird aber nicht mehr genutzt. */
    val deregisteredAt: String? = null,
    /**
     * Gelöscht. Im lokalen Modus wird nur archiviert statt die Zeile zu entfernen,
     * weil Fahrten und noch nicht übertragene Aufzeichnungen darauf verweisen.
     */
    val archivedAt: String? = null
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
    val syncState: String = "SYNCED",
    val specialCategory: String? = null,
    val startTimeZone: String? = null,
    val endTimeZone: String? = null,
    // Map-Matching des Servers (null = nicht gematcht), damit gematchte Fahrten
    // auch offline und direkt nach dem Start als solche erkannt werden
    val mapMatchingStatus: String? = null,
    val mapMatchingProvider: String? = null,
    val mapMatchingMatchedAt: String? = null,
    val mapMatchingRawDistanceMeters: Long? = null,
    val mapMatchingMatchedDistanceMeters: Long? = null,
    val mapMatchingMessage: String? = null
)

/**
 * Straßenroute einer gematchten Server-Fahrt, als Polyline (Präzision 1e-6) kompakt
 * gespeichert. [matchedAt] erkennt, ob der Server inzwischen neu gematcht hat.
 */
@Entity(tableName = "matched_routes")
data class MatchedRouteEntity(
    @PrimaryKey val tripId: String,
    val matchedAt: String?,
    val polyline: String,
    val pointCount: Int
)

/** Fahrt-ID, Stand und Punktzahl einer gespeicherten Straßenroute (für den Sync). */
data class MatchedRouteVersion(val tripId: String, val matchedAt: String?, val pointCount: Int)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val syncState: String = "SYNCED",
    val color: String? = null
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
    val distanceKm: Double? = null,
    val specialCategory: String? = null,
    // Zeitzone des Geräts bei Start/Ende der Aufzeichnung (Ortszeit der Fahrt)
    val startTimeZone: String? = null,
    val endTimeZone: String? = null,
    // Fahrtzweck, Kontakt und Notiz (lokal bearbeitet)
    val purpose: String? = null,
    val contact: String? = null,
    val notes: String? = null,
    // Tags der Fahrt als JSON ([{id, name, color}])
    val tagsJson: String = "[]"
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

    /** Gelöschte Fahrzeuge bleiben in der Tabelle, tauchen aber nirgends mehr auf. Inaktive ans Ende. */
    @Query("SELECT * FROM vehicles WHERE archivedAt IS NULL ORDER BY (deregisteredAt IS NOT NULL), name COLLATE NOCASE")
    suspend fun vehicles(): List<VehicleEntity>
    /** Auch archivierte – nur für die Übernahme lokaler Daten auf einen Server. */
    @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE") suspend fun allVehicles(): List<VehicleEntity>
    @Query("SELECT * FROM vehicles WHERE id=:id") suspend fun vehicle(id: String): VehicleEntity?
    /** Kein Auto-Start für gelöschte oder abgemeldete Fahrzeuge. */
    @Query("SELECT * FROM vehicles WHERE bluetoothMac=:mac COLLATE NOCASE AND archivedAt IS NULL AND deregisteredAt IS NULL LIMIT 1")
    suspend fun vehicleByBluetooth(mac: String): VehicleEntity?
    @Query("UPDATE vehicles SET name=:name, licensePlate=:licensePlate, updatedAt=:updatedAt WHERE id=:id")
    suspend fun updateVehicleDetails(id: String, name: String, licensePlate: String?, updatedAt: String)
    /** Name der Fahrten mitziehen, damit gespeicherte Fahrten nicht den alten Namen zeigen. */
    @Query("UPDATE trips SET vehicleName=:name WHERE vehicleId=:id")
    suspend fun renameVehicleInTrips(id: String, name: String)
    @Query("UPDATE vehicles SET deregisteredAt=:at, isDefault=0, updatedAt=:updatedAt WHERE id=:id")
    suspend fun setVehicleDeregistered(id: String, at: String?, updatedAt: String)
    @Query("UPDATE vehicles SET archivedAt=:at, isDefault=0, bluetoothMac=NULL, updatedAt=:updatedAt WHERE id=:id")
    suspend fun setVehicleArchived(id: String, at: String, updatedAt: String)
    @Query("DELETE FROM vehicles WHERE id=:id") suspend fun deleteVehicleRow(id: String)
    @Query("SELECT COUNT(*) FROM offline_trips WHERE vehicleId=:id AND state='RECORDING'")
    suspend fun recordingTripCount(id: String): Int
    @Query("SELECT COUNT(*) FROM offline_trips WHERE vehicleId=:id") suspend fun offlineTripCount(id: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putVehicle(value: VehicleEntity)
    @Query("UPDATE vehicles SET bluetoothMac=:mac, updatedAt=:updatedAt WHERE id=:id") suspend fun setVehicleBluetooth(id: String, mac: String?, updatedAt: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putVehicles(values: List<VehicleEntity>)
    @Query("DELETE FROM vehicles") suspend fun clearVehicles()
    /** Genau ein Standardfahrzeug: das gewählte auf true, alle anderen auf false. */
    @Query("UPDATE vehicles SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END") suspend fun setDefaultVehicle(id: String)

    @Query("SELECT * FROM trips ORDER BY startedAt DESC") suspend fun trips(): List<TripEntity>
    @Query("SELECT * FROM trips WHERE id=:id") suspend fun storedTrip(id: String): TripEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTripRecord(value: TripEntity)
    @Query("UPDATE offline_trips SET type=:type WHERE localId=:id") suspend fun setLocalTripType(id: String, type: String)
    @Query("UPDATE offline_trips SET specialCategory=:specialCategory WHERE localId=:id") suspend fun setLocalSpecialCategory(id: String, specialCategory: String?)
    @Query("UPDATE offline_trips SET purpose=:purpose, contact=:contact, notes=:notes WHERE localId=:id") suspend fun setLocalPurpose(id: String, purpose: String?, contact: String?, notes: String?)
    @Query("UPDATE offline_trips SET tagsJson=:tagsJson WHERE localId=:id") suspend fun setLocalTags(id: String, tagsJson: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTag(value: TagEntity)
    @Query("UPDATE offline_trips SET vehicleId=:newVehicleId WHERE vehicleId=:oldVehicleId") suspend fun remapOfflineTripsVehicle(oldVehicleId: String, newVehicleId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTripRecords(values: List<TripEntity>)
    @Query("DELETE FROM trips") suspend fun clearTripRecords()

    @Query("SELECT * FROM matched_routes WHERE tripId=:tripId") suspend fun matchedRoute(tripId: String): MatchedRouteEntity?
    @Query("SELECT tripId, matchedAt, pointCount FROM matched_routes") suspend fun matchedRouteVersions(): List<MatchedRouteVersion>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMatchedRoute(value: MatchedRouteEntity)
    /** Routen von Fahrten entfernen, die es nicht mehr gibt oder die nicht (mehr) gematcht sind. */
    @Query("DELETE FROM matched_routes WHERE tripId NOT IN (SELECT id FROM trips WHERE mapMatchingStatus = 'applied')")
    suspend fun pruneMatchedRoutes()
    @Query("DELETE FROM matched_routes") suspend fun clearMatchedRoutes()

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
    /** Beenden nur über gezielte Spalten: ein gleichzeitiger Sync darf seine remoteId nicht verlieren. */
    @Query("UPDATE offline_trips SET endedAt=:endedAt, endTimeZone=:endTimeZone, state='STOPPED', pendingStop=1 WHERE localId=:localId")
    suspend fun markTripStopped(localId: String, endedAt: String, endTimeZone: String?)
    @Query("UPDATE offline_trips SET remoteId=:remoteId WHERE localId=:localId") suspend fun setRemoteId(localId: String, remoteId: String)
    @Query("UPDATE offline_trips SET state='SYNCED', pendingStop=0, syncError=NULL WHERE localId=:localId") suspend fun markTripSynced(localId: String)

    @Insert suspend fun addPoints(points: List<TrackPointEntity>)
    @Query("SELECT * FROM track_points WHERE localTripId=:tripId ORDER BY id") suspend fun points(tripId: String): List<TrackPointEntity>
    @Query("SELECT * FROM track_points WHERE localTripId=:tripId AND uploaded=0 ORDER BY id LIMIT :limit") suspend fun pendingPoints(tripId: String, limit: Int = 500): List<TrackPointEntity>
    @Query("UPDATE track_points SET uploaded=1 WHERE id IN (:ids)") suspend fun markPointsUploaded(ids: List<Long>)

    // Für Backup/Wiederherstellung (ZIP)
    @Query("SELECT * FROM offline_trips ORDER BY startedAt") suspend fun allOfflineTrips(): List<OfflineTripEntity>
    @Query("SELECT * FROM track_points ORDER BY id") suspend fun allPoints(): List<TrackPointEntity>
    @Query("DELETE FROM offline_trips") suspend fun clearOfflineTrips()
    @Query("DELETE FROM track_points") suspend fun clearPoints()
    @Query("DELETE FROM cache") suspend fun clearCache()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOfflineTrips(values: List<OfflineTripEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPoints(values: List<TrackPointEntity>)

    /** Ersetzt den kompletten lokalen Datenbestand durch den Inhalt eines Backups. */
    @Transaction
    suspend fun restoreAll(
        vehicles: List<VehicleEntity>,
        trips: List<TripEntity>,
        tags: List<TagEntity>,
        offlineTrips: List<OfflineTripEntity>,
        points: List<TrackPointEntity>
    ) {
        clearPoints()
        clearOfflineTrips()
        clearMatchedRoutes()
        clearTripRecords()
        clearTags()
        clearVehicles()
        clearCache()
        putVehicles(vehicles)
        putTripRecords(trips)
        putTags(tags)
        putOfflineTrips(offlineTrips)
        putPoints(points)
    }

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
        TrackPointEntity::class,
        MatchedRouteEntity::class
    ],
    version = 11,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN deregisteredAt TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN archivedAt TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN mapMatchingStatus TEXT")
                db.execSQL("ALTER TABLE trips ADD COLUMN mapMatchingProvider TEXT")
                db.execSQL("ALTER TABLE trips ADD COLUMN mapMatchingMatchedAt TEXT")
                db.execSQL("ALTER TABLE trips ADD COLUMN mapMatchingRawDistanceMeters INTEGER")
                db.execSQL("ALTER TABLE trips ADD COLUMN mapMatchingMatchedDistanceMeters INTEGER")
                db.execSQL("ALTER TABLE trips ADD COLUMN mapMatchingMessage TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS matched_routes (" +
                        "tripId TEXT NOT NULL, matchedAt TEXT, polyline TEXT NOT NULL, " +
                        "pointCount INTEGER NOT NULL, PRIMARY KEY(tripId))"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE tags ADD COLUMN color TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN purpose TEXT")
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN contact TEXT")
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN notes TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN startTimeZone TEXT")
                db.execSQL("ALTER TABLE trips ADD COLUMN endTimeZone TEXT")
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN startTimeZone TEXT")
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN endTimeZone TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN specialCategory TEXT")
                db.execSQL("ALTER TABLE offline_trips ADD COLUMN specialCategory TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
            }
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
