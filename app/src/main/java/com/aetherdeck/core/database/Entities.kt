package com.aetherdeck.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aetherdeck.core.models.BackgroundTaskStatus
import com.aetherdeck.core.models.BackgroundTaskType
import com.aetherdeck.core.models.MediaType
import com.aetherdeck.core.models.ProviderType
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "systems",
    indices = [Index(value = ["id"], unique = true)]
)
data class SystemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    val manufacturer: String,
    val releaseYear: Int,
    val folderAliasesCsv: String,
    val extensionsCsv: String,
    val defaultEmulatorId: String,
    val alternativeEmulatorIdsCsv: String,
    val defaultCore: String,
    val alternativeCoresCsv: String = "",
    val catalogFile: String = "",
    val aspectRatio: Float = 0.75f,
    val selectedEmulatorId: String? = null,
    val selectedCore: String? = null,
    val catalogSyncEnabled: Boolean = false
)

@Serializable
@Entity(
    tableName = "library_sources",
    indices = [Index(value = ["uriString"], unique = true)]
)
data class LibrarySourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uriString: String,
    val providerType: ProviderType,
    val enabled: Boolean = true,
    val lastScannedAt: Long = 0L,
    val gameCount: Int = 0,
    val saveFolderUri: String? = null,
    val stateFolderUri: String? = null
)

@Serializable
@Entity(
    tableName = "canonical_games",
    indices = [
        Index(value = ["systemId"]),
        Index(value = ["normalizedTitle"]),
        Index(value = ["favorite"]),
        Index(value = ["lastPlayed"]),
        Index(value = ["systemId", "normalizedTitle"])
    ]
)
data class CanonicalGameEntity(
    @PrimaryKey val id: String,
    val displayTitle: String,
    val normalizedTitle: String,
    val sortTitle: String,
    val systemId: String,
    val description: String = "",
    val developer: String = "",
    val publisher: String = "",
    val releaseYear: Int? = null,
    val genre: String = "",
    val players: String = "1",
    val rating: Float? = null,
    val franchise: String = "",
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long? = null,
    val lastLaunch: Long? = null,
    val lastPlaySessionStart: Long? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val preferredVariantId: String? = null,
    val userEditedMetadata: Boolean = false,
    val metadataSourceRank: Int = 6,
    val emulatorOverrideId: String? = null,
    val coreOverride: String? = null,
    val multiDiscGroupId: String? = null
)

@Serializable
@Entity(
    tableName = "game_variants",
    indices = [
        Index(value = ["canonicalGameId"]),
        Index(value = ["systemId"]),
        Index(value = ["normalizedTitle"]),
        Index(value = ["region"]),
        Index(value = ["sourceId"])
    ]
)
data class GameVariantEntity(
    @PrimaryKey val id: String,
    val canonicalGameId: String,
    val originalTitle: String,
    val normalizedTitle: String,
    val systemId: String,
    val region: String = "Unknown",
    val languages: String = "En",
    val revision: String = "",
    val version: String = "",
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val fileExtension: String = "",
    val md5: String? = null,
    val sha1: String? = null,
    val crc32: String? = null,
    val sourceId: String,
    val preferred: Boolean = false,
    val available: Boolean = true,
    val emulatorOverrideId: String? = null,
    val coreOverride: String? = null
)

@Serializable
@Entity(
    tableName = "game_sources",
    indices = [
        Index(value = ["librarySourceId"]),
        Index(value = ["provider"]),
        Index(value = ["sourceUri"], unique = true)
    ]
)
data class GameSourceEntity(
    @PrimaryKey val id: String,
    val librarySourceId: String,
    val sourceUri: String,
    val sourceFileName: String,
    val relativePath: String,
    val systemId: String,
    val extension: String,
    val provider: ProviderType,
    val fileSize: Long = 0L,
    val lastModified: Long = 0L,
    val available: Boolean = true
)

@Serializable
@Entity(
    tableName = "game_media",
    indices = [
        Index(value = ["canonicalGameId"]),
        Index(value = ["canonicalGameId", "mediaType"])
    ]
)
data class GameMediaEntity(
    @PrimaryKey val id: String,
    val canonicalGameId: String,
    val mediaType: MediaType,
    val localOrRemoteUri: String,
    val providerName: String,
    val isCustomUserArt: Boolean = false,
    val cachedFilePath: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "external_identities",
    indices = [
        Index(value = ["canonicalGameId"]),
        Index(value = ["catalogIdentity"])
    ]
)
data class ExternalIdentityEntity(
    @PrimaryKey val id: String,
    val canonicalGameId: String,
    val providerName: String,
    val catalogIdentity: String,
    val externalUrl: String? = null,
    val md5: String? = null
)

@Serializable
@Entity(
    tableName = "catalog_entries",
    indices = [
        Index(value = ["systemId"]),
        Index(value = ["normalizedTitle"]),
        Index(value = ["md5"]),
        Index(value = ["catalogIdentity"], unique = true)
    ]
)
data class CatalogEntryEntity(
    @PrimaryKey val catalogIdentity: String,
    val systemId: String,
    val filePath: String,
    val sourceTitle: String,
    val normalizedTitle: String,
    val extension: String,
    val externalReferenceUrl: String? = null,
    val metaName: String? = null,
    val metaDesc: String? = null,
    val metaImage: String? = null,
    val metaThumbnail: String? = null,
    val metaMarquee: String? = null,
    val metaVideo: String? = null,
    val metaFanart: String? = null,
    val metaBoxback: String? = null,
    val metaManual: String? = null,
    val metaRating: Float? = null,
    val metaReleaseYear: Int? = null,
    val metaDeveloper: String? = null,
    val metaPublisher: String? = null,
    val metaGenre: String? = null,
    val metaPlayers: String? = null,
    val metaRegion: String? = null,
    val metaMd5: String? = null,
    val metaLang: String? = null,
    val metaFamily: String? = null,
    val md5: String? = null
)

@Serializable
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val isSmart: Boolean = false,
    val smartRulesJson: String? = null,
    val artworkStyle: String = "AUTO_COLLAGE",
    val customArtworkUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "collection_game_cross_ref",
    primaryKeys = ["collectionId", "canonicalGameId"],
    indices = [
        Index(value = ["collectionId"]),
        Index(value = ["canonicalGameId"])
    ]
)
data class CollectionGameCrossRef(
    val collectionId: String,
    val canonicalGameId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "match_reviews",
    indices = [Index(value = ["systemId"])]
)
data class MatchReviewEntity(
    @PrimaryKey val id: String,
    val systemId: String,
    val candidateGameIdA: String,
    val candidateTitleA: String,
    val candidateGameIdB: String,
    val candidateTitleB: String,
    val similarityScore: Float,
    val reason: String,
    val resolved: Boolean = false
)

@Serializable
@Entity(
    tableName = "background_tasks",
    indices = [
        Index(value = ["type"]),
        Index(value = ["status"]),
        Index(value = ["updatedAt"])
    ]
)
data class BackgroundTaskEntity(
    @PrimaryKey val id: String,
    val type: BackgroundTaskType,
    val status: BackgroundTaskStatus,
    val totalItems: Int = 0,
    val processedItems: Int = 0,
    val successfulItems: Int = 0,
    val failedItems: Int = 0,
    val currentItemId: String = "",
    val currentItemTitle: String = "",
    val currentPhase: String = "",
    val progressPercent: Int? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val errorMessage: String? = null
)
