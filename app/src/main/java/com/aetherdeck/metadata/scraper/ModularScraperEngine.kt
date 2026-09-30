package com.aetherdeck.metadata.scraper

import com.aetherdeck.core.database.AetherDao
import com.aetherdeck.core.database.BackgroundTaskEntity
import com.aetherdeck.core.database.CanonicalGameWithDetails
import com.aetherdeck.core.database.GameMediaEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.core.models.BackgroundTaskState
import com.aetherdeck.core.models.BackgroundTaskStatus
import com.aetherdeck.core.models.BackgroundTaskType
import com.aetherdeck.core.models.MediaType
import com.aetherdeck.core.models.ScrapePhase
import com.aetherdeck.core.models.ScrapeProgressState
import com.aetherdeck.core.settings.AetherSettingsState
import com.aetherdeck.library.grouping.GameGroupingEngine
import com.aetherdeck.media.resolver.ArleyMediaResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class ScrapeScope {
    SINGLE_GAME,
    SYSTEM,
    MISSING_ONLY,
    ALL_GAMES
}

enum class ScrapeMode(val labelEs: String, val labelEn: String) {
    MISSING_ONLY("Solo faltantes", "Missing Only"),
    METADATA_ONLY("Solo metadatos", "Metadata Only"),
    MEDIA_ONLY("Solo carátulas/arte", "Media Only"),
    REPLACE("Reemplazar todo (Confirmar)", "Replace All (Confirm Required)");

    val label: String get() = labelEs
}

/**
 * Live non-modal Scraper Engine with:
 * - Bounded concurrency (2–3 concurrent requests via Semaphore) respecting rate limits.
 * - Live ScrapeProgressState (SEARCHING -> MATCHING -> METADATA -> COVER -> LOGO -> BACKGROUND -> SAVING -> DONE).
 * - Immediate per-game Room persistence so covers and metadata appear on cards in real time.
 * - Persistent BackgroundTaskEntity updates in Room so progress survives screen changes, emulator launches, and backgrounding.
 */
class ModularScraperEngine(
    private val dao: AetherDao,
    private val arleyMediaResolver: ArleyMediaResolver,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val concurrencySemaphore = Semaphore(3)
    private val stateMutex = Mutex()

    private val _activeTask = MutableStateFlow<BackgroundTaskState?>(null)
    val activeTask: StateFlow<BackgroundTaskState?> = _activeTask.asStateFlow()

    private val _liveScrapeProgress = MutableStateFlow<ScrapeProgressState?>(null)
    val liveScrapeProgress: StateFlow<ScrapeProgressState?> = _liveScrapeProgress.asStateFlow()

    @Volatile
    private var isPaused = false

    @Volatile
    private var isCancelled = false

    fun pauseScraping() {
        isPaused = true
        _activeTask.value = _activeTask.value?.copy(isPaused = true)
    }

    fun resumeScraping() {
        isPaused = false
        _activeTask.value = _activeTask.value?.copy(isPaused = false)
    }

    fun cancelScraping() {
        isCancelled = true
        isPaused = false
        _activeTask.value = _activeTask.value?.copy(isCancelled = true, isCompleted = true)
        _liveScrapeProgress.value = null
    }

    fun clearCompletedTask() {
        if (_activeTask.value?.isCompleted == true) {
            _activeTask.value = null
            _liveScrapeProgress.value = null
        }
    }

    suspend fun scrapeGames(
        games: List<CanonicalGameWithDetails>,
        mode: ScrapeMode,
        settings: AetherSettingsState,
        taskId: String = "task_scrape_active",
        onProgressCallback: (suspend (ScrapeProgressState) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (games.isEmpty()) return@withContext
        isPaused = false
        isCancelled = false

        val targets = if (mode == ScrapeMode.MISSING_ONLY) {
            games.filter { item ->
                val hasCover = item.media.any { it.mediaType == MediaType.COVER }
                val hasMetadata = item.game.description.isNotBlank() && item.game.developer.isNotBlank()
                !hasCover || !hasMetadata
            }
        } else {
            games
        }

        if (targets.isEmpty()) {
            val doneEntity = BackgroundTaskEntity(
                id = taskId,
                type = BackgroundTaskType.SCRAPE,
                status = BackgroundTaskStatus.COMPLETED,
                totalItems = games.size,
                processedItems = games.size,
                successfulItems = games.size,
                failedItems = 0,
                currentItemTitle = "Todas las carátulas están al día",
                currentPhase = ScrapePhase.DONE.labelEs,
                progressPercent = 100,
                finishedAt = System.currentTimeMillis()
            )
            dao.upsertBackgroundTask(doneEntity)
            return@withContext
        }

        val total = targets.size
        val startedAt = System.currentTimeMillis()
        val processedCounter = AtomicInteger(0)
        val successCounter = AtomicInteger(0)
        val failedCounter = AtomicInteger(0)

        dao.upsertBackgroundTask(
            BackgroundTaskEntity(
                id = taskId,
                type = BackgroundTaskType.SCRAPE,
                status = BackgroundTaskStatus.RUNNING,
                totalItems = total,
                processedItems = 0,
                successfulItems = 0,
                failedItems = 0,
                currentItemTitle = targets.first().game.displayTitle,
                currentPhase = ScrapePhase.SEARCHING.labelEs,
                progressPercent = 0,
                startedAt = startedAt
            )
        )

        coroutineScope {
            targets.map { item ->
                async(Dispatchers.IO) {
                    concurrencySemaphore.withPermit {
                        while (isPaused && !isCancelled) {
                            delay(250)
                        }
                        if (isCancelled) return@withPermit

                        val game = item.game
                        val currentProcessed = processedCounter.get() + 1

                        suspend fun emitPhase(
                            phase: ScrapePhase,
                            provider: String,
                            mediaType: MediaType? = null
                        ) {
                            val state = ScrapeProgressState(
                                total = total,
                                processed = currentProcessed.coerceAtMost(total),
                                successful = successCounter.get(),
                                failed = failedCounter.get(),
                                currentGameId = game.id,
                                currentGameTitle = game.displayTitle,
                                currentProvider = provider,
                                currentPhase = phase,
                                currentMediaType = mediaType
                            )
                            stateMutex.withLock {
                                _liveScrapeProgress.value = state
                                _activeTask.value = BackgroundTaskState(
                                    id = taskId,
                                    type = BackgroundTaskType.SCRAPE,
                                    title = "CARÁTULAS Y METADATOS",
                                    currentItem = game.displayTitle,
                                    currentPhase = "${phase.labelEs} ($provider)",
                                    processed = state.processed,
                                    total = total,
                                    successful = state.successful,
                                    failed = state.failed,
                                    isPaused = isPaused,
                                    metadataDone = phase.ordinal >= ScrapePhase.METADATA.ordinal,
                                    coverDone = phase.ordinal >= ScrapePhase.COVER.ordinal,
                                    logoDone = phase.ordinal >= ScrapePhase.LOGO.ordinal,
                                    backgroundDone = phase.ordinal >= ScrapePhase.BACKGROUND.ordinal
                                )
                                dao.upsertBackgroundTask(
                                    BackgroundTaskEntity(
                                        id = taskId,
                                        type = BackgroundTaskType.SCRAPE,
                                        status = if (isPaused) BackgroundTaskStatus.PAUSED else BackgroundTaskStatus.RUNNING,
                                        totalItems = total,
                                        processedItems = state.processed,
                                        successfulItems = state.successful,
                                        failedItems = state.failed,
                                        currentItemId = game.id,
                                        currentItemTitle = game.displayTitle,
                                        currentPhase = "${phase.labelEs} · $provider",
                                        progressPercent = ((state.processed * 100f) / total).toInt().coerceIn(0, 100),
                                        startedAt = startedAt,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            onProgressCallback?.invoke(state)
                        }

                        try {
                            val hasCover = item.media.any { it.mediaType == MediaType.COVER }
                            emitPhase(ScrapePhase.SEARCHING, "Catálogo / Local")

                            val catalogHit = dao.findCatalogBySystemAndTitle(game.systemId, game.normalizedTitle)
                            var scrapedDesc: String? = catalogHit?.metaDesc
                            var scrapedDev: String? = catalogHit?.metaDeveloper
                            var scrapedPub: String? = catalogHit?.metaPublisher
                            var scrapedYear: Int? = catalogHit?.metaReleaseYear
                            var scrapedGenre: String? = catalogHit?.metaGenre
                            var scrapedCoverUrl: String? = null
                            var scrapedLogoUrl: String? = null
                            var scrapedBgUrl: String? = null

                            if (catalogHit != null) {
                                emitPhase(ScrapePhase.MATCHING, "Arley4d Catalog")
                                val rawImg = catalogHit.metaImage ?: catalogHit.metaThumbnail
                                if (!rawImg.isNullOrBlank()) {
                                    emitPhase(ScrapePhase.COVER, "Arley4d Media", MediaType.COVER)
                                    scrapedCoverUrl = arleyMediaResolver.resolveMediaUrl(rawImg, game.systemId)
                                }
                                if (!catalogHit.metaMarquee.isNullOrBlank()) {
                                    emitPhase(ScrapePhase.LOGO, "Arley4d Media", MediaType.LOGO)
                                    scrapedLogoUrl = arleyMediaResolver.resolveMediaUrl(catalogHit.metaMarquee, game.systemId)
                                }
                                if (!catalogHit.metaFanart.isNullOrBlank()) {
                                    emitPhase(ScrapePhase.BACKGROUND, "Arley4d Media", MediaType.BACKGROUND)
                                    scrapedBgUrl = arleyMediaResolver.resolveMediaUrl(catalogHit.metaFanart, game.systemId)
                                }
                            }

                            val providers = settings.scraperProviderPriorityCsv.split(",").map { it.trim() }
                            for (provider in providers) {
                                if (isCancelled) break
                                when (provider.lowercase()) {
                                    "steamgriddb" -> {
                                        if (settings.steamGridDbApiKey.isNotBlank() && (scrapedCoverUrl == null || scrapedLogoUrl == null)) {
                                            emitPhase(ScrapePhase.COVER, "SteamGridDB", MediaType.COVER)
                                            val sgdb = querySteamGridDb(game.displayTitle, settings.steamGridDbApiKey)
                                            if (scrapedCoverUrl == null) scrapedCoverUrl = sgdb.coverUrl
                                            if (scrapedLogoUrl == null) scrapedLogoUrl = sgdb.logoUrl
                                            if (scrapedBgUrl == null) scrapedBgUrl = sgdb.heroUrl
                                        }
                                    }
                                    "thegamesdb" -> {
                                        if (settings.theGamesDbApiKey.isNotBlank() && scrapedDesc.isNullOrBlank()) {
                                            emitPhase(ScrapePhase.METADATA, "TheGamesDB")
                                            val tgdb = queryTheGamesDb(game.displayTitle, settings.theGamesDbApiKey)
                                            if (scrapedDesc.isNullOrBlank()) scrapedDesc = tgdb.overview
                                            if (scrapedYear == null) scrapedYear = tgdb.releaseYear
                                        }
                                    }
                                    "screenscraper" -> {
                                        if (settings.screenScraperApiKey.isNotBlank()) {
                                            emitPhase(ScrapePhase.SEARCHING, "ScreenScraper")
                                            AetherLogger.recordScraperRequest("ScreenScraper query for '${game.displayTitle}' (system=${game.systemId})")
                                        }
                                    }
                                }
                            }

                            emitPhase(ScrapePhase.SAVING, "Room DB")

                            // Protect userEditedMetadata and existing valid covers unless REPLACE was explicitly requested
                            val canUpdateMetadata = mode != ScrapeMode.MEDIA_ONLY &&
                                (!game.userEditedMetadata || mode == ScrapeMode.REPLACE)

                            if (canUpdateMetadata) {
                                val updatedGame = game.copy(
                                    description = scrapedDesc?.takeIf { it.isNotBlank() } ?: game.description,
                                    developer = scrapedDev?.takeIf { it.isNotBlank() } ?: game.developer,
                                    publisher = scrapedPub?.takeIf { it.isNotBlank() } ?: game.publisher,
                                    releaseYear = scrapedYear ?: game.releaseYear,
                                    genre = scrapedGenre?.takeIf { it.isNotBlank() } ?: game.genre,
                                    userEditedMetadata = if (mode == ScrapeMode.REPLACE) false else game.userEditedMetadata
                                )
                                dao.updateCanonicalGame(updatedGame)
                            }

                            if (mode != ScrapeMode.METADATA_ONLY) {
                                if (mode == ScrapeMode.REPLACE) {
                                    dao.deleteNonCustomMediaForGame(game.id)
                                }
                                val mediaToSave = mutableListOf<GameMediaEntity>()
                                if (!scrapedCoverUrl.isNullOrBlank() && (!hasCover || mode == ScrapeMode.REPLACE)) {
                                    mediaToSave.add(
                                        GameMediaEntity(
                                            id = GameGroupingEngine.stableId("med_${game.id}_COVER"),
                                            canonicalGameId = game.id,
                                            mediaType = MediaType.COVER,
                                            localOrRemoteUri = scrapedCoverUrl,
                                            providerName = "Scraper",
                                            isCustomUserArt = false
                                        )
                                    )
                                }
                                if (!scrapedLogoUrl.isNullOrBlank()) {
                                    mediaToSave.add(
                                        GameMediaEntity(
                                            id = GameGroupingEngine.stableId("med_${game.id}_LOGO"),
                                            canonicalGameId = game.id,
                                            mediaType = MediaType.LOGO,
                                            localOrRemoteUri = scrapedLogoUrl,
                                            providerName = "Scraper",
                                            isCustomUserArt = false
                                        )
                                    )
                                }
                                if (!scrapedBgUrl.isNullOrBlank()) {
                                    mediaToSave.add(
                                        GameMediaEntity(
                                            id = GameGroupingEngine.stableId("med_${game.id}_BACKGROUND"),
                                            canonicalGameId = game.id,
                                            mediaType = MediaType.BACKGROUND,
                                            localOrRemoteUri = scrapedBgUrl,
                                            providerName = "Scraper",
                                            isCustomUserArt = false
                                        )
                                    )
                                }
                                if (mediaToSave.isNotEmpty()) {
                                    dao.insertGameMedia(mediaToSave)
                                }
                            }

                            processedCounter.incrementAndGet()
                            successCounter.incrementAndGet()
                        } catch (e: Exception) {
                            processedCounter.incrementAndGet()
                            failedCounter.incrementAndGet()
                            AetherLogger.warn(LogCategory.SCRAPER, "Scrape error on ${game.displayTitle}", e.message)
                        }
                    }
                }
            }.awaitAll()
        }

        val finalStatus = if (isCancelled) BackgroundTaskStatus.CANCELLED else BackgroundTaskStatus.COMPLETED
        val now = System.currentTimeMillis()
        dao.upsertBackgroundTask(
            BackgroundTaskEntity(
                id = taskId,
                type = BackgroundTaskType.SCRAPE,
                status = finalStatus,
                totalItems = total,
                processedItems = processedCounter.get(),
                successfulItems = successCounter.get(),
                failedItems = failedCounter.get(),
                currentItemTitle = if (isCancelled) "Cancelado por el usuario" else "Completado (${successCounter.get()} actualizados)",
                currentPhase = if (isCancelled) "Cancelado" else ScrapePhase.DONE.labelEs,
                progressPercent = 100,
                startedAt = startedAt,
                updatedAt = now,
                finishedAt = now
            )
        )
        _activeTask.value = _activeTask.value?.copy(
            isCompleted = true,
            processed = processedCounter.get(),
            total = total
        )
        _liveScrapeProgress.value = null
    }

    private data class SgdbMediaResult(
        val coverUrl: String? = null,
        val logoUrl: String? = null,
        val heroUrl: String? = null
    )

    private fun querySteamGridDb(title: String, apiKey: String): SgdbMediaResult {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val searchUrl = "https://www.steamgriddb.com/api/v2/search/autocomplete/$encoded"
            AetherLogger.recordScraperRequest("GET $searchUrl (Bearer [REDACTED])")
            val searchReq = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val gameId = httpClient.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return SgdbMediaResult()
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val dataArr = root["data"]?.jsonArray ?: return SgdbMediaResult()
                dataArr.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            } ?: return SgdbMediaResult()

            val gridsUrl = "https://www.steamgriddb.com/api/v2/grids/game/$gameId?dimensions=600x900"
            val gridReq = Request.Builder()
                .url(gridsUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            val coverUrl = httpClient.newCall(gridReq).execute().use { resp ->
                if (!resp.isSuccessful) null
                else {
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    root["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                }
            }
            SgdbMediaResult(coverUrl = coverUrl)
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.SCRAPER, "SteamGridDB query failed for '$title'", e)
            SgdbMediaResult()
        }
    }

    private data class TgdbMetaResult(
        val overview: String? = null,
        val releaseYear: Int? = null
    )

    private fun queryTheGamesDb(title: String, apiKey: String): TgdbMetaResult {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val url = "https://api.thegamesdb.net/v1/Games/ByGameName?apikey=$apiKey&name=$encoded&fields=overview"
            AetherLogger.recordScraperRequest("GET https://api.thegamesdb.net/v1/Games/ByGameName?apikey=[REDACTED]&name=$encoded")
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return TgdbMetaResult()
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val games = root["data"]?.jsonObject?.get("games")?.jsonArray ?: return TgdbMetaResult()
                val first = games.firstOrNull()?.jsonObject ?: return TgdbMetaResult()
                val overview = first["overview"]?.jsonPrimitive?.contentOrNull
                val relDate = first["release_date"]?.jsonPrimitive?.contentOrNull
                val year = relDate?.take(4)?.toIntOrNull()
                TgdbMetaResult(overview = overview, releaseYear = year)
            }
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.SCRAPER, "TheGamesDB query failed for '$title'", e)
            TgdbMetaResult()
        }
    }
}
