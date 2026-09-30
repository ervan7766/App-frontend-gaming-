package com.aetherdeck.core.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import com.aetherdeck.core.database.AetherDao
import com.aetherdeck.core.database.CanonicalGameEntity
import com.aetherdeck.core.database.CollectionEntity
import com.aetherdeck.core.database.CollectionGameCrossRef
import com.aetherdeck.core.database.GameMediaEntity
import com.aetherdeck.core.database.GameSourceEntity
import com.aetherdeck.core.database.GameVariantEntity
import com.aetherdeck.core.database.LibrarySourceEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class AetherBackupPayload(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val systems: List<SystemEntity> = emptyList(),
    val librarySources: List<LibrarySourceEntity> = emptyList(),
    val canonicalGames: List<CanonicalGameEntity> = emptyList(),
    val variants: List<GameVariantEntity> = emptyList(),
    val sources: List<GameSourceEntity> = emptyList(),
    val media: List<GameMediaEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val collectionCrossRefs: List<CollectionGameCrossRef> = emptyList()
)

data class BackupPreview(
    val createdAt: Long,
    val deviceModel: String,
    val canonicalGamesCount: Int,
    val variantsCount: Int,
    val collectionsCount: Int,
    val scanRootsCount: Int,
    val payload: AetherBackupPayload
)

class BackupRestoreManager(
    private val context: Context,
    private val dao: AetherDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportBackupToUri(destinationUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = AetherBackupPayload(
                systems = dao.getSystemsList(),
                librarySources = dao.getLibrarySourcesList(),
                canonicalGames = dao.getAllCanonicalGamesList(),
                variants = dao.getAllVariantsList(),
                sources = dao.getAllGameSources(),
                media = dao.getAllMediaList(),
                collections = dao.getAllCollectionsList(),
                collectionCrossRefs = dao.getAllCollectionCrossRefs()
            )
            val jsonBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)

            context.contentResolver.openOutputStream(destinationUri)?.use { rawOut ->
                ZipOutputStream(BufferedOutputStream(rawOut)).use { zipOut ->
                    val entry = ZipEntry("aetherdeck_backup.json")
                    zipOut.putNextEntry(entry)
                    zipOut.write(jsonBytes)
                    zipOut.closeEntry()
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open output stream"))

            val msg = "Exported backup (${payload.canonicalGames.size} games, ${payload.collections.size} collections)"
            AetherLogger.info(LogCategory.DATABASE, msg)
            Result.success(msg)
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.DATABASE, "Backup export failed", e)
            Result.failure(e)
        }
    }

    suspend fun inspectBackupUri(sourceUri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            var jsonContent: String? = null
            context.contentResolver.openInputStream(sourceUri)?.use { rawIn ->
                ZipInputStream(BufferedInputStream(rawIn)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".json", ignoreCase = true)) {
                            val baos = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (zipIn.read(buffer).also { read = it } != -1) {
                                baos.write(buffer, 0, read)
                            }
                            jsonContent = baos.toString("UTF-8")
                            break
                        }
                        entry = zipIn.nextEntry
                    }
                }
            }

            if (jsonContent.isNullOrBlank()) {
                // Fallback if user selected a plain JSON backup
                jsonContent = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
            }

            val payload = json.decodeFromString<AetherBackupPayload>(
                jsonContent ?: return@withContext Result.failure(IllegalArgumentException("Invalid backup archive"))
            )
            Result.success(
                BackupPreview(
                    createdAt = payload.createdAt,
                    deviceModel = payload.deviceModel,
                    canonicalGamesCount = payload.canonicalGames.size,
                    variantsCount = payload.variants.size,
                    collectionsCount = payload.collections.size,
                    scanRootsCount = payload.librarySources.size,
                    payload = payload
                )
            )
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.DATABASE, "Failed reading backup preview", e)
            Result.failure(e)
        }
    }

    suspend fun applyRestore(preview: BackupPreview): Result<String> = withContext(Dispatchers.IO) {
        try {
            val p = preview.payload
            if (p.systems.isNotEmpty()) dao.insertSystems(p.systems)
            p.librarySources.forEach { dao.insertLibrarySource(it) }
            if (p.sources.isNotEmpty()) dao.insertGameSources(p.sources)
            if (p.canonicalGames.isNotEmpty()) dao.insertCanonicalGames(p.canonicalGames)
            if (p.variants.isNotEmpty()) dao.insertGameVariants(p.variants)
            if (p.media.isNotEmpty()) dao.insertGameMedia(p.media)
            p.collections.forEach { dao.insertCollection(it) }
            p.collectionCrossRefs.forEach { dao.addGameToCollection(it) }

            val msg = "Restored ${p.canonicalGames.size} games and ${p.collections.size} collections."
            AetherLogger.info(LogCategory.DATABASE, msg)
            Result.success(msg)
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.DATABASE, "Backup restore failed", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a comprehensive plain-text diagnostic report without any API keys, passwords, or tokens.
     */
    suspend fun buildDiagnosticsReport(
        appVersion: String,
        screenSummary: String,
        controllerDetected: String,
        installedEmulatorsSummary: List<String>
    ): String = withContext(Dispatchers.IO) {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val persistedPerms = context.contentResolver.persistedUriPermissions.joinToString("\n") { perm ->
            "  - ${perm.uri} (read=${perm.isReadPermission}, write=${perm.isWritePermission})"
        }.ifBlank { "  None" }

        val sources = dao.getLibrarySourcesList().joinToString("\n") { src ->
            "  - [${src.providerType}] ${src.name}: ${src.uriString} (enabled=${src.enabled}, games=${src.gameCount})"
        }.ifBlank { "  None" }

        val systems = dao.getSystemsList().joinToString("\n") { sys ->
            "  - ${sys.id} (${sys.name}): emulator=${sys.selectedEmulatorId ?: sys.defaultEmulatorId}, core=${sys.selectedCore ?: sys.defaultCore}"
        }

        buildString {
            appendLine("==================================================")
            appendLine("AETHERDECK DIAGNOSTICS REPORT")
            appendLine("Generated: ${formatter.format(Date())}")
            appendLine("==================================================")
            appendLine("AetherDeck Version: $appVersion")
            appendLine("Android Version: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Screen & Orientation: $screenSummary")
            appendLine("Controller Detected: $controllerDetected")
            appendLine("Last Launch Event: ${AetherLogger.lastLaunchSummary.value}")
            appendLine("Last Scraper Request: ${AetherLogger.lastScraperRequest.value}")
            appendLine("Last Provider Error: ${AetherLogger.lastProviderError.value}")
            appendLine()
            appendLine("--- PERSISTED SAF URI PERMISSIONS ---")
            appendLine(persistedPerms)
            appendLine()
            appendLine("--- LIBRARY SOURCES (SCAN ROOTS) ---")
            appendLine(sources)
            appendLine()
            appendLine("--- DETECTED EMULATORS ---")
            installedEmulatorsSummary.forEach { appendLine("  - $it") }
            appendLine()
            appendLine("--- SYSTEM EMULATOR & CORE MAPPINGS ---")
            appendLine(systems)
            appendLine()
            appendLine("--- RECENT LOG ENTRIES (SECRETS REDACTED) ---")
            appendLine(AetherLogger.formatLogsForExport())
        }
    }
}
