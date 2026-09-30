package com.aetherdeck.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aetherdeck.core.models.BackgroundTaskStatus
import kotlinx.coroutines.flow.Flow

data class CanonicalGameWithDetails(
    @Embedded val game: CanonicalGameEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "canonicalGameId"
    )
    val variants: List<GameVariantEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "canonicalGameId"
    )
    val media: List<GameMediaEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "canonicalGameId"
    )
    val externalIdentities: List<ExternalIdentityEntity>
)

@Dao
interface AetherDao {
    // Systems
    @Query("SELECT * FROM systems ORDER BY name ASC")
    fun observeSystems(): Flow<List<SystemEntity>>

    @Query("SELECT * FROM systems ORDER BY name ASC")
    suspend fun getSystemsList(): List<SystemEntity>

    @Query("SELECT * FROM systems WHERE id = :systemId LIMIT 1")
    suspend fun getSystemById(systemId: String): SystemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSystems(systems: List<SystemEntity>)

    @Update
    suspend fun updateSystem(system: SystemEntity)

    // Library Sources (ScanRoots)
    @Query("SELECT * FROM library_sources ORDER BY name ASC")
    fun observeLibrarySources(): Flow<List<LibrarySourceEntity>>

    @Query("SELECT * FROM library_sources ORDER BY name ASC")
    suspend fun getLibrarySourcesList(): List<LibrarySourceEntity>

    @Query("SELECT * FROM library_sources WHERE id = :sourceId LIMIT 1")
    suspend fun getLibrarySourceById(sourceId: String): LibrarySourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrarySource(source: LibrarySourceEntity)

    @Update
    suspend fun updateLibrarySource(source: LibrarySourceEntity)

    @Query("DELETE FROM library_sources WHERE id = :sourceId")
    suspend fun deleteLibrarySource(sourceId: String)

    // Canonical Games with Variants & Media
    @Transaction
    @Query("SELECT * FROM canonical_games WHERE hidden = 0 ORDER BY sortTitle ASC")
    fun observeAllVisibleGames(): Flow<List<CanonicalGameWithDetails>>

    @Transaction
    @Query("SELECT * FROM canonical_games ORDER BY sortTitle ASC")
    fun observeAllGamesIncludingHidden(): Flow<List<CanonicalGameWithDetails>>

    @Transaction
    @Query("SELECT * FROM canonical_games WHERE id = :gameId LIMIT 1")
    fun observeGameWithDetails(gameId: String): Flow<CanonicalGameWithDetails?>

    @Transaction
    @Query("SELECT * FROM canonical_games WHERE id = :gameId LIMIT 1")
    suspend fun getGameWithDetails(gameId: String): CanonicalGameWithDetails?

    @Transaction
    @Query("SELECT * FROM canonical_games WHERE hidden = 0 ORDER BY sortTitle ASC")
    suspend fun getAllVisibleGamesWithDetailsList(): List<CanonicalGameWithDetails>

    @Query("SELECT * FROM canonical_games")
    suspend fun getAllCanonicalGamesList(): List<CanonicalGameEntity>

    @Query("SELECT * FROM canonical_games WHERE systemId = :systemId")
    suspend fun getCanonicalGamesBySystem(systemId: String): List<CanonicalGameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonicalGame(game: CanonicalGameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonicalGames(games: List<CanonicalGameEntity>)

    @Update
    suspend fun updateCanonicalGame(game: CanonicalGameEntity)

    @Query("DELETE FROM canonical_games WHERE id = :gameId")
    suspend fun deleteCanonicalGame(gameId: String)

    @Query("UPDATE canonical_games SET favorite = :favorite WHERE id = :gameId")
    suspend fun setGameFavorite(gameId: String, favorite: Boolean)

    @Query("UPDATE canonical_games SET hidden = :hidden WHERE id = :gameId")
    suspend fun setGameHidden(gameId: String, hidden: Boolean)

    @Query("UPDATE canonical_games SET preferredVariantId = :variantId WHERE id = :gameId")
    suspend fun setPreferredVariant(gameId: String, variantId: String)

    @Query("UPDATE canonical_games SET emulatorOverrideId = :emulatorId, coreOverride = :coreName WHERE id = :gameId")
    suspend fun setGameEmulatorOverride(gameId: String, emulatorId: String?, coreName: String?)

    @Query("UPDATE canonical_games SET playCount = playCount + 1, lastPlayed = :timestamp, lastLaunch = :timestamp, lastPlaySessionStart = :timestamp WHERE id = :gameId")
    suspend fun recordGameLaunch(gameId: String, timestamp: Long)

    // Variants & Sources
    @Query("SELECT * FROM game_variants WHERE canonicalGameId = :canonicalGameId ORDER BY preferred DESC, discNumber ASC, originalTitle ASC")
    suspend fun getVariantsForGame(canonicalGameId: String): List<GameVariantEntity>

    @Query("SELECT * FROM game_variants")
    suspend fun getAllVariantsList(): List<GameVariantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameVariants(variants: List<GameVariantEntity>)

    @Update
    suspend fun updateGameVariant(variant: GameVariantEntity)

    @Query("DELETE FROM game_variants WHERE id = :variantId")
    suspend fun deleteGameVariant(variantId: String)

    @Query("SELECT * FROM game_sources")
    suspend fun getAllGameSources(): List<GameSourceEntity>

    @Query("SELECT * FROM game_sources WHERE librarySourceId = :librarySourceId")
    suspend fun getGameSourcesByRoot(librarySourceId: String): List<GameSourceEntity>

    @Query("SELECT * FROM game_sources WHERE id = :sourceId LIMIT 1")
    suspend fun getGameSourceById(sourceId: String): GameSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameSources(sources: List<GameSourceEntity>)

    @Query("UPDATE game_sources SET available = :available WHERE id IN (:sourceIds)")
    suspend fun markSourcesAvailability(sourceIds: List<String>, available: Boolean)

    @Query("UPDATE game_variants SET available = :available WHERE sourceId IN (:sourceIds)")
    suspend fun markVariantsAvailabilityBySource(sourceIds: List<String>, available: Boolean)

    // Media
    @Query("SELECT * FROM game_media WHERE canonicalGameId = :canonicalGameId")
    suspend fun getMediaForGame(canonicalGameId: String): List<GameMediaEntity>

    @Query("SELECT * FROM game_media")
    suspend fun getAllMediaList(): List<GameMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameMedia(media: List<GameMediaEntity>)

    @Query("DELETE FROM game_media WHERE canonicalGameId = :canonicalGameId AND isCustomUserArt = 0")
    suspend fun deleteNonCustomMediaForGame(canonicalGameId: String)

    // External Identities
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalIdentities(identities: List<ExternalIdentityEntity>)

    @Query("SELECT * FROM external_identities WHERE canonicalGameId = :canonicalGameId")
    suspend fun getExternalIdentitiesForGame(canonicalGameId: String): List<ExternalIdentityEntity>

    // Catalog Entries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogEntriesBatch(entries: List<CatalogEntryEntity>)

    @Query("SELECT * FROM catalog_entries WHERE systemId = :systemId")
    suspend fun getCatalogEntriesForSystem(systemId: String): List<CatalogEntryEntity>

    @Query("SELECT * FROM catalog_entries WHERE md5 = :md5 LIMIT 1")
    suspend fun findCatalogByMd5(md5: String): CatalogEntryEntity?

    @Query("SELECT * FROM catalog_entries WHERE systemId = :systemId AND normalizedTitle = :normalizedTitle LIMIT 1")
    suspend fun findCatalogBySystemAndTitle(systemId: String, normalizedTitle: String): CatalogEntryEntity?

    @Query("SELECT COUNT(*) FROM catalog_entries")
    fun observeCatalogCount(): Flow<Int>

    @Query("DELETE FROM catalog_entries WHERE systemId = :systemId")
    suspend fun clearCatalogForSystem(systemId: String)

    // Collections
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    suspend fun getAllCollectionsList(): List<CollectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: String)

    @Query("SELECT * FROM collection_game_cross_ref")
    fun observeCollectionCrossRefs(): Flow<List<CollectionGameCrossRef>>

    @Query("SELECT * FROM collection_game_cross_ref")
    suspend fun getAllCollectionCrossRefs(): List<CollectionGameCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGameToCollection(ref: CollectionGameCrossRef)

    @Query("DELETE FROM collection_game_cross_ref WHERE collectionId = :collectionId AND canonicalGameId = :canonicalGameId")
    suspend fun removeGameFromCollection(collectionId: String, canonicalGameId: String)

    // Match Reviews
    @Query("SELECT * FROM match_reviews WHERE resolved = 0")
    fun observePendingMatchReviews(): Flow<List<MatchReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchReviews(reviews: List<MatchReviewEntity>)

    @Query("UPDATE match_reviews SET resolved = 1 WHERE id = :reviewId")
    suspend fun markMatchReviewResolved(reviewId: String)

    // Persistent Background Tasks (Task Center)
    @Query("SELECT * FROM background_tasks ORDER BY updatedAt DESC LIMIT 30")
    fun observeBackgroundTasks(): Flow<List<BackgroundTaskEntity>>

    @Query("SELECT * FROM background_tasks ORDER BY updatedAt DESC")
    suspend fun getAllBackgroundTasks(): List<BackgroundTaskEntity>

    @Query("SELECT * FROM background_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getBackgroundTaskById(taskId: String): BackgroundTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackgroundTask(task: BackgroundTaskEntity)

    @Query("UPDATE background_tasks SET status = :status, updatedAt = :updatedAt, finishedAt = :finishedAt, errorMessage = :error WHERE id = :taskId")
    suspend fun updateBackgroundTaskStatus(
        taskId: String,
        status: BackgroundTaskStatus,
        updatedAt: Long = System.currentTimeMillis(),
        finishedAt: Long? = null,
        error: String? = null
    )

    @Query("DELETE FROM background_tasks WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun clearFinishedBackgroundTasks()
}

@Database(
    entities = [
        SystemEntity::class,
        LibrarySourceEntity::class,
        CanonicalGameEntity::class,
        GameVariantEntity::class,
        GameSourceEntity::class,
        GameMediaEntity::class,
        ExternalIdentityEntity::class,
        CatalogEntryEntity::class,
        CollectionEntity::class,
        CollectionGameCrossRef::class,
        MatchReviewEntity::class,
        BackgroundTaskEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AetherDatabase : RoomDatabase() {
    abstract fun dao(): AetherDao

    companion object {
        @Volatile
        private var INSTANCE: AetherDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `background_tasks` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `totalItems` INTEGER NOT NULL,
                        `processedItems` INTEGER NOT NULL,
                        `successfulItems` INTEGER NOT NULL,
                        `failedItems` INTEGER NOT NULL,
                        `currentItemId` TEXT NOT NULL,
                        `currentItemTitle` TEXT NOT NULL,
                        `currentPhase` TEXT NOT NULL,
                        `progressPercent` INTEGER,
                        `startedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_background_tasks_type` ON `background_tasks` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_background_tasks_status` ON `background_tasks` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_background_tasks_updatedAt` ON `background_tasks` (`updatedAt`)")
            }
        }

        fun getInstance(context: Context): AetherDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AetherDatabase::class.java,
                    "aetherdeck.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
