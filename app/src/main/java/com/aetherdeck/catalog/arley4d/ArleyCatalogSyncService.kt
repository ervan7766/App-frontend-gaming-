package com.aetherdeck.catalog.arley4d

import com.aetherdeck.catalog.jsonl.StreamingJsonlCatalogParser
import com.aetherdeck.core.database.AetherDao
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.core.models.BackgroundTaskState
import com.aetherdeck.core.models.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ArleyCatalogSyncService(
    private val dao: AetherDao,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val RAW_ANDROID_ROMS_BASE = "https://raw.githubusercontent.com/Arley4d/AndroidRoms/main"
    }

    private val _syncTask = MutableStateFlow<BackgroundTaskState?>(null)
    val syncTask: StateFlow<BackgroundTaskState?> = _syncTask.asStateFlow()

    suspend fun syncSelectedSystems(systems: List<SystemEntity>): Result<Int> = withContext(Dispatchers.IO) {
        val enabledSystems = systems.filter { it.catalogSyncEnabled && it.catalogFile.isNotBlank() }
        if (enabledSystems.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No catalog systems selected for sync."))
        }

        var totalEntries = 0
        val taskId = "catalog_sync_${System.currentTimeMillis()}"

        try {
            for ((index, sys) in enabledSystems.withIndex()) {
                val url = "$RAW_ANDROID_ROMS_BASE/${sys.catalogFile}"
                _syncTask.value = BackgroundTaskState(
                    id = taskId,
                    type = TaskType.CATALOG_SYNC,
                    title = "CATALOG SYNC (${sys.shortName})",
                    currentItem = sys.catalogFile,
                    processed = index + 1,
                    total = enabledSystems.size
                )

                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        AetherLogger.warn(LogCategory.CATALOG, "HTTP ${response.code} fetching $url")
                        return@use
                    }
                    val bodyStream = response.body?.byteStream() ?: return@use
                    dao.clearCatalogForSystem(sys.id)
                    val inserted = StreamingJsonlCatalogParser.parseStreamInBatches(
                        inputStream = bodyStream,
                        defaultSystemId = sys.id,
                        batchSize = 250
                    ) { batch, countSoFar ->
                        dao.insertCatalogEntriesBatch(batch)
                        _syncTask.value = _syncTask.value?.copy(
                            currentItem = "${sys.shortName}: $countSoFar entries"
                        )
                    }
                    totalEntries += inserted
                }
            }

            _syncTask.value = _syncTask.value?.copy(
                isCompleted = true,
                currentItem = "Synced $totalEntries catalog entries"
            )
            AetherLogger.info(LogCategory.CATALOG, "Catalog sync finished: $totalEntries total entries")
            Result.success(totalEntries)
        } catch (e: Exception) {
            _syncTask.value = _syncTask.value?.copy(
                isCompleted = true,
                error = e.message ?: "Network error"
            )
            AetherLogger.error(LogCategory.CATALOG, "Catalog sync failed", e)
            Result.failure(e)
        }
    }

    fun dismissSyncTask() {
        _syncTask.value = null
    }
}
