package com.aetherdeck.core.tasks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aetherdeck.catalog.arley4d.ArleyCatalogSyncService
import com.aetherdeck.core.database.AetherDatabase
import com.aetherdeck.core.database.BackgroundTaskEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.core.models.BackgroundTaskStatus
import com.aetherdeck.core.models.BackgroundTaskType
import com.aetherdeck.core.settings.AetherSettingsRepository
import com.aetherdeck.library.scanner.SafLibraryScanner
import com.aetherdeck.media.resolver.ArleyMediaResolver
import com.aetherdeck.metadata.scraper.ModularScraperEngine
import com.aetherdeck.metadata.scraper.ScrapeMode
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val NOTIFICATION_CHANNEL_ID = "aetherdeck_background_tasks"

private fun ensureTaskNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Tareas en segundo plano de AetherDeck",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }
}

private fun createTaskForegroundInfo(
    context: Context,
    notificationId: Int,
    title: String,
    subtitle: String,
    processed: Int,
    total: Int
): ForegroundInfo {
    ensureTaskNotificationChannel(context)
    val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(java.util.UUID.randomUUID())
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(subtitle)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(total.coerceAtLeast(1), processed.coerceAtLeast(0), total <= 0)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelIntent)
        .build()
    return ForegroundInfo(notificationId, notification)
}

/**
 * WorkManager CoroutineWorker for Library Scan / Rescan.
 * Continues running even if user switches screens, opens another app, or launches an emulator.
 */
class LibraryScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = AetherDatabase.getInstance(applicationContext).dao()
        val settingsRepo = AetherSettingsRepository(applicationContext)
        val settings = settingsRepo.settingsFlow.first()
        val scanner = SafLibraryScanner(applicationContext, dao, ArleyMediaResolver(applicationContext))

        val sourceId = inputData.getString("source_id")
        val taskId = inputData.getString("task_id") ?: "work_scan_${sourceId ?: "all"}"
        val sources = if (!sourceId.isNullOrBlank()) {
            listOfNotNull(dao.getLibrarySourceById(sourceId))
        } else {
            dao.getLibrarySourcesList().filter { it.enabled }
        }

        if (sources.isEmpty()) return Result.success()

        val startedAt = System.currentTimeMillis()
        dao.upsertBackgroundTask(
            BackgroundTaskEntity(
                id = taskId,
                type = BackgroundTaskType.SCAN,
                status = BackgroundTaskStatus.RUNNING,
                totalItems = sources.size,
                processedItems = 0,
                currentItemTitle = sources.first().name,
                currentPhase = "Escaneando carpetas...",
                startedAt = startedAt
            )
        )

        var totalScanned = 0
        for ((idx, src) in sources.withIndex()) {
            if (isStopped) {
                dao.updateBackgroundTaskStatus(taskId, BackgroundTaskStatus.CANCELLED, finishedAt = System.currentTimeMillis())
                return Result.failure()
            }

            runCatching {
                setForeground(createTaskForegroundInfo(applicationContext, 1001, "AetherDeck", "Escaneando ${src.name}", idx + 1, sources.size))
            }

            val res = scanner.scanLibrarySource(
                librarySource = src,
                regionPriority = settings.regionPriorityList,
                languagePriority = settings.languagePriorityList
            ) { fileName, current, total ->
                setProgress(workDataOf("file" to fileName, "current" to current, "total" to total))
                dao.upsertBackgroundTask(
                    BackgroundTaskEntity(
                        id = taskId,
                        type = BackgroundTaskType.SCAN,
                        status = BackgroundTaskStatus.RUNNING,
                        totalItems = total.coerceAtLeast(1),
                        processedItems = current,
                        successfulItems = current,
                        currentItemTitle = fileName,
                        currentPhase = "Analizando ${src.name}",
                        progressPercent = ((current * 100f) / total.coerceAtLeast(1)).toInt().coerceIn(0, 100),
                        startedAt = startedAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            res.onSuccess { totalScanned += it.canonicalGamesCount }
        }

        settingsRepo.recordScanTimestamp()
        val now = System.currentTimeMillis()
        dao.upsertBackgroundTask(
            BackgroundTaskEntity(
                id = taskId,
                type = BackgroundTaskType.SCAN,
                status = BackgroundTaskStatus.COMPLETED,
                totalItems = totalScanned.coerceAtLeast(1),
                processedItems = totalScanned.coerceAtLeast(1),
                successfulItems = totalScanned,
                currentItemTitle = "$totalScanned juegos listos",
                currentPhase = "Completado",
                progressPercent = 100,
                startedAt = startedAt,
                updatedAt = now,
                finishedAt = now
            )
        )
        return Result.success()
    }
}

/**
 * WorkManager CoroutineWorker for Library Scraping.
 * Continues scraping in background when the user launches RetroArch, PPSSPP, Dolphin, or Arley4d.
 */
class LibraryScrapeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = AetherDatabase.getInstance(applicationContext).dao()
        val settingsRepo = AetherSettingsRepository(applicationContext)
        val settings = settingsRepo.settingsFlow.first()
        val engine = PersistentTaskCoordinator.getSharedScraperEngine(applicationContext)

        val taskId = inputData.getString("task_id") ?: "work_scrape_library"
        val modeStr = inputData.getString("mode") ?: ScrapeMode.MISSING_ONLY.name
        val systemId = inputData.getString("system_id")
        val gameId = inputData.getString("game_id")
        val mode = runCatching { ScrapeMode.valueOf(modeStr) }.getOrDefault(ScrapeMode.MISSING_ONLY)

        val allGames = dao.getAllVisibleGamesWithDetailsList()
        val targets = when {
            !gameId.isNullOrBlank() -> allGames.filter { it.game.id == gameId }
            !systemId.isNullOrBlank() -> allGames.filter { it.game.systemId == systemId }
            else -> allGames
        }

        if (targets.isEmpty()) return Result.success()

        engine.scrapeGames(
            games = targets,
            mode = mode,
            settings = settings,
            taskId = taskId
        ) { progress ->
            runCatching {
                setForeground(
                    createTaskForegroundInfo(
                        applicationContext,
                        1002,
                        "AetherDeck · Scrapeando biblioteca",
                        "${progress.processed} / ${progress.total} · ${progress.currentGameTitle}",
                        progress.processed,
                        progress.total
                    )
                )
            }
        }
        return Result.success()
    }
}

/**
 * WorkManager CoroutineWorker for Catalog Sync.
 */
class CatalogSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = AetherDatabase.getInstance(applicationContext).dao()
        val settingsRepo = AetherSettingsRepository(applicationContext)
        val syncService = ArleyCatalogSyncService(dao)
        val systems = dao.getSystemsList()
        val taskId = inputData.getString("task_id") ?: "work_catalog_sync"
        val startedAt = System.currentTimeMillis()

        dao.upsertBackgroundTask(
            BackgroundTaskEntity(
                id = taskId,
                type = BackgroundTaskType.CATALOG_SYNC,
                status = BackgroundTaskStatus.RUNNING,
                totalItems = systems.count { it.catalogSyncEnabled }.coerceAtLeast(1),
                processedItems = 0,
                currentItemTitle = "Sincronizando catálogos...",
                currentPhase = "Conectando",
                startedAt = startedAt
            )
        )

        val res = syncService.syncSelectedSystems(systems)
        val now = System.currentTimeMillis()
        return res.fold(
            onSuccess = { count ->
                settingsRepo.recordCatalogTimestamp(now)
                dao.upsertBackgroundTask(
                    BackgroundTaskEntity(
                        id = taskId,
                        type = BackgroundTaskType.CATALOG_SYNC,
                        status = BackgroundTaskStatus.COMPLETED,
                        totalItems = count.coerceAtLeast(1),
                        processedItems = count.coerceAtLeast(1),
                        successfulItems = count,
                        currentItemTitle = "$count entradas sincronizadas",
                        currentPhase = "Completado",
                        progressPercent = 100,
                        startedAt = startedAt,
                        updatedAt = now,
                        finishedAt = now
                    )
                )
                Result.success()
            },
            onFailure = { err ->
                dao.upsertBackgroundTask(
                    BackgroundTaskEntity(
                        id = taskId,
                        type = BackgroundTaskType.CATALOG_SYNC,
                        status = BackgroundTaskStatus.FAILED,
                        currentItemTitle = "Error de sincronización",
                        currentPhase = "Error",
                        startedAt = startedAt,
                        updatedAt = now,
                        finishedAt = now,
                        errorMessage = err.message
                    )
                )
                Result.failure()
            }
        )
    }
}

/**
 * Application-scoped coordinator that owns background tasks outside of any Activity/Composable lifecycle.
 * Uses WorkManager + application-scoped CoroutineScope + Room persistence so tasks NEVER stop when:
 * - User navigates between screens
 * - Device rotates
 * - User launches RetroArch, PPSSPP, Dolphin, Arley4d, or YouTube
 */
object PersistentTaskCoordinator {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var sharedScraperEngine: ModularScraperEngine? = null

    fun getSharedScraperEngine(context: Context): ModularScraperEngine {
        return sharedScraperEngine ?: synchronized(this) {
            val db = AetherDatabase.getInstance(context.applicationContext)
            ModularScraperEngine(db.dao(), ArleyMediaResolver(context.applicationContext)).also {
                sharedScraperEngine = it
            }
        }
    }

    fun enqueueLibraryScan(context: Context, sourceId: String? = null) {
        val appCtx = context.applicationContext
        val taskId = if (sourceId != null) "scan_$sourceId" else "scan_all"
        val workReq = OneTimeWorkRequestBuilder<LibraryScanWorker>()
            .setInputData(workDataOf("source_id" to sourceId, "task_id" to taskId))
            .addTag("aetherdeck_scan")
            .build()

        runCatching {
            WorkManager.getInstance(appCtx).enqueueUniqueWork(
                "unique_$taskId",
                ExistingWorkPolicy.KEEP,
                workReq
            )
        }

        // Also run on application-scoped supervisor coroutine so execution is immediate even in environments where WorkManager defers
        appScope.launch {
            val db = AetherDatabase.getInstance(appCtx)
            val dao = db.dao()
            val settingsRepo = AetherSettingsRepository(appCtx)
            val settings = settingsRepo.settingsFlow.first()
            val scanner = SafLibraryScanner(appCtx, dao, ArleyMediaResolver(appCtx))
            val sources = if (sourceId != null) {
                listOfNotNull(dao.getLibrarySourceById(sourceId))
            } else {
                dao.getLibrarySourcesList().filter { it.enabled }
            }
            if (sources.isEmpty()) return@launch
            val startedAt = System.currentTimeMillis()
            var totalGames = 0
            for (src in sources) {
                dao.upsertBackgroundTask(
                    BackgroundTaskEntity(
                        id = taskId,
                        type = BackgroundTaskType.SCAN,
                        status = BackgroundTaskStatus.RUNNING,
                        totalItems = 100,
                        processedItems = 5,
                        currentItemTitle = src.name,
                        currentPhase = "Escaneando ${src.name}...",
                        startedAt = startedAt
                    )
                )
                val res = scanner.scanLibrarySource(
                    librarySource = src,
                    regionPriority = settings.regionPriorityList,
                    languagePriority = settings.languagePriorityList
                ) { fileName, current, total ->
                    dao.upsertBackgroundTask(
                        BackgroundTaskEntity(
                            id = taskId,
                            type = BackgroundTaskType.SCAN,
                            status = BackgroundTaskStatus.RUNNING,
                            totalItems = total.coerceAtLeast(1),
                            processedItems = current,
                            successfulItems = current,
                            currentItemTitle = fileName,
                            currentPhase = "Escaneando ${src.name}",
                            progressPercent = ((current * 100f) / total.coerceAtLeast(1)).toInt().coerceIn(0, 100),
                            startedAt = startedAt,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                res.onSuccess { totalGames = it.canonicalGamesCount }
            }
            settingsRepo.recordScanTimestamp()
            val now = System.currentTimeMillis()
            dao.upsertBackgroundTask(
                BackgroundTaskEntity(
                    id = taskId,
                    type = BackgroundTaskType.SCAN,
                    status = BackgroundTaskStatus.COMPLETED,
                    totalItems = totalGames.coerceAtLeast(1),
                    processedItems = totalGames.coerceAtLeast(1),
                    successfulItems = totalGames,
                    currentItemTitle = "$totalGames juegos encontrados",
                    currentPhase = "Completado",
                    progressPercent = 100,
                    startedAt = startedAt,
                    updatedAt = now,
                    finishedAt = now
                )
            )
        }
    }

    fun enqueueLibraryScrape(
        context: Context,
        mode: ScrapeMode,
        systemId: String? = null,
        gameId: String? = null
    ) {
        val appCtx = context.applicationContext
        val taskId = when {
            gameId != null -> "scrape_game_$gameId"
            systemId != null -> "scrape_sys_$systemId"
            else -> "scrape_library"
        }

        val workReq = OneTimeWorkRequestBuilder<LibraryScrapeWorker>()
            .setInputData(
                workDataOf(
                    "task_id" to taskId,
                    "mode" to mode.name,
                    "system_id" to systemId,
                    "game_id" to gameId
                )
            )
            .addTag("aetherdeck_scrape")
            .build()

        runCatching {
            WorkManager.getInstance(appCtx).enqueueUniqueWork(
                "unique_$taskId",
                ExistingWorkPolicy.KEEP,
                workReq
            )
        }

        appScope.launch {
            val db = AetherDatabase.getInstance(appCtx)
            val dao = db.dao()
            val settings = AetherSettingsRepository(appCtx).settingsFlow.first()
            val allGames = dao.getAllVisibleGamesWithDetailsList()
            val targets = when {
                gameId != null -> allGames.filter { it.game.id == gameId }
                systemId != null -> allGames.filter { it.game.systemId == systemId }
                else -> allGames
            }
            getSharedScraperEngine(appCtx).scrapeGames(
                games = targets,
                mode = mode,
                settings = settings,
                taskId = taskId
            )
        }
    }

    fun enqueueCatalogSync(context: Context) {
        val appCtx = context.applicationContext
        val taskId = "catalog_sync"
        val workReq = OneTimeWorkRequestBuilder<CatalogSyncWorker>()
            .setInputData(workDataOf("task_id" to taskId))
            .addTag("aetherdeck_catalog")
            .build()

        runCatching {
            WorkManager.getInstance(appCtx).enqueueUniqueWork(
                "unique_catalog_sync",
                ExistingWorkPolicy.KEEP,
                workReq
            )
        }

        appScope.launch {
            val dao = AetherDatabase.getInstance(appCtx).dao()
            val settingsRepo = AetherSettingsRepository(appCtx)
            val syncService = ArleyCatalogSyncService(dao)
            val systems = dao.getSystemsList()
            val startedAt = System.currentTimeMillis()
            dao.upsertBackgroundTask(
                BackgroundTaskEntity(
                    id = taskId,
                    type = BackgroundTaskType.CATALOG_SYNC,
                    status = BackgroundTaskStatus.RUNNING,
                    totalItems = systems.count { it.catalogSyncEnabled }.coerceAtLeast(1),
                    processedItems = 0,
                    currentItemTitle = "Sincronizando catálogos JSONL...",
                    currentPhase = "Descargando",
                    startedAt = startedAt
                )
            )
            val res = syncService.syncSelectedSystems(systems)
            val now = System.currentTimeMillis()
            res.onSuccess { count ->
                settingsRepo.recordCatalogTimestamp(now)
                dao.upsertBackgroundTask(
                    BackgroundTaskEntity(
                        id = taskId,
                        type = BackgroundTaskType.CATALOG_SYNC,
                        status = BackgroundTaskStatus.COMPLETED,
                        totalItems = count.coerceAtLeast(1),
                        processedItems = count.coerceAtLeast(1),
                        successfulItems = count,
                        currentItemTitle = "$count entradas sincronizadas",
                        currentPhase = "Completado",
                        progressPercent = 100,
                        startedAt = startedAt,
                        updatedAt = now,
                        finishedAt = now
                    )
                )
            }.onFailure { err ->
                dao.upsertBackgroundTask(
                    BackgroundTaskEntity(
                        id = taskId,
                        type = BackgroundTaskType.CATALOG_SYNC,
                        status = BackgroundTaskStatus.FAILED,
                        currentItemTitle = "Error de catálogo",
                        currentPhase = "Error",
                        startedAt = startedAt,
                        updatedAt = now,
                        finishedAt = now,
                        errorMessage = err.message
                    )
                )
            }
        }
    }

    /**
     * Reconciles Room BackgroundTaskEntity records after process death so stale RUNNING tasks
     * that are no longer active in memory or WorkManager are accurately updated.
     */
    suspend fun reconcileTasksOnStartup(context: Context) {
        try {
            val dao = AetherDatabase.getInstance(context.applicationContext).dao()
            val tasks = dao.getAllBackgroundTasks()
            val activeScraper = getSharedScraperEngine(context).liveScrapeProgress.value
            val now = System.currentTimeMillis()

            for (task in tasks) {
                if (task.status == BackgroundTaskStatus.RUNNING || task.status == BackgroundTaskStatus.QUEUED) {
                    val isStale = (now - task.updatedAt) > 120_000L && activeScraper == null
                    if (isStale) {
                        val wmInfos = runCatching {
                            WorkManager.getInstance(context.applicationContext)
                                .getWorkInfosForUniqueWork("unique_${task.id}")
                                .get()
                        }.getOrNull().orEmpty()

                        val stillRunningInWm = wmInfos.any {
                            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                        }
                        if (!stillRunningInWm) {
                            dao.updateBackgroundTaskStatus(
                                taskId = task.id,
                                status = BackgroundTaskStatus.FAILED,
                                finishedAt = now,
                                error = "Proceso interrumpido por el sistema Android"
                            )
                            AetherLogger.info(LogCategory.APP, "Reconciled stale task ${task.id} after process death")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AetherLogger.warn(LogCategory.APP, "Task reconciliation check skipped", e.message)
        }
    }
}
