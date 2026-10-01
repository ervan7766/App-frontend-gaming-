package com.aetherdeck.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aetherdeck.catalog.arley4d.ArleyCatalogSyncService
import com.aetherdeck.collections.smart.SmartCollectionDefinition
import com.aetherdeck.collections.smart.SmartCollectionEngine
import com.aetherdeck.core.database.AetherDatabase
import com.aetherdeck.core.database.CanonicalGameEntity
import com.aetherdeck.core.database.CanonicalGameWithDetails
import com.aetherdeck.core.database.CollectionEntity
import com.aetherdeck.core.database.CollectionGameCrossRef
import com.aetherdeck.core.database.GameMediaEntity
import com.aetherdeck.core.database.GameSourceEntity
import com.aetherdeck.core.database.GameVariantEntity
import com.aetherdeck.core.database.LibrarySourceEntity
import com.aetherdeck.core.database.MatchReviewEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.core.models.ArtworkFilter
import com.aetherdeck.core.models.AutoCategory
import com.aetherdeck.core.models.BackgroundTaskState
import com.aetherdeck.core.models.ContextualError
import com.aetherdeck.core.models.LibraryFilterState
import com.aetherdeck.core.models.MediaType
import com.aetherdeck.core.models.PlayedFilter
import com.aetherdeck.core.models.PreLaunchCheckResult
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.core.models.SortOption
import com.aetherdeck.core.models.TaskType
import com.aetherdeck.core.settings.AetherSettingsRepository
import com.aetherdeck.core.settings.AetherSettingsState
import com.aetherdeck.core.utils.BackupPreview
import com.aetherdeck.core.utils.BackupRestoreManager
import com.aetherdeck.emulators.config.EmulatorConfigManager
import com.aetherdeck.emulators.detection.DetectedEmulatorStatus
import com.aetherdeck.emulators.detection.EmulatorDetector
import com.aetherdeck.emulators.detection.InstalledAndroidApp
import com.aetherdeck.input.gamepad.GamepadInputController
import com.aetherdeck.launchers.retroarch.RetroArchAdapter
import com.aetherdeck.launchers.retroarch.RetroArchTestDiagnostic
import com.aetherdeck.launchers.router.Arley4dLauncher
import com.aetherdeck.launchers.router.GameLaunchRouter
import com.aetherdeck.library.grouping.GameGroupingEngine
import com.aetherdeck.library.normalizer.TitleNormalizer
import com.aetherdeck.library.scanner.SafLibraryScanner
import com.aetherdeck.media.cache.MediaCacheManager
import com.aetherdeck.media.cache.StorageBreakdown
import com.aetherdeck.media.resolver.ArleyMediaResolver
import com.aetherdeck.metadata.scraper.ModularScraperEngine
import com.aetherdeck.metadata.scraper.ScrapeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AetherDeckViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext: Context = application.applicationContext
    val database = AetherDatabase.getInstance(appContext)
    val dao = database.dao()
    val settingsRepo = AetherSettingsRepository(appContext)
    val configManager = EmulatorConfigManager(appContext)
    val emulatorDetector = EmulatorDetector(appContext, configManager)
    val retroArchAdapter = RetroArchAdapter(appContext, emulatorDetector)
    val arley4dLauncher = Arley4dLauncher(appContext, emulatorDetector)
    val launchRouter = GameLaunchRouter(appContext, configManager, emulatorDetector, retroArchAdapter, arley4dLauncher)
    val arleyMediaResolver = ArleyMediaResolver(appContext)
    val scanner = SafLibraryScanner(appContext, dao, arleyMediaResolver)
    val scraperEngine = ModularScraperEngine(dao, arleyMediaResolver)
    val catalogSyncService = ArleyCatalogSyncService(dao)
    val mediaCacheManager = MediaCacheManager(appContext)
    val backupRestoreManager = BackupRestoreManager(appContext, dao)
    val gamepadController = GamepadInputController()

    val settings: StateFlow<AetherSettingsState> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AetherSettingsState())

    val systems: StateFlow<List<SystemEntity>> = dao.observeSystems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val librarySources: StateFlow<List<LibrarySourceEntity>> = dao.observeLibrarySources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVisibleGames: StateFlow<List<CanonicalGameWithDetails>> = dao.observeAllVisibleGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collections: StateFlow<List<CollectionEntity>> = dao.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collectionCrossRefs: StateFlow<List<CollectionGameCrossRef>> = dao.observeCollectionCrossRefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingMatchReviews: StateFlow<List<MatchReviewEntity>> = dao.observePendingMatchReviews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val catalogCount: StateFlow<Int> = dao.observeCatalogCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _filterState = MutableStateFlow(LibraryFilterState())
    val filterState: StateFlow<LibraryFilterState> = _filterState.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.TITLE)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedCategory = MutableStateFlow(AutoCategory.ALL_GAMES)
    val selectedCategory: StateFlow<AutoCategory> = _selectedCategory.asStateFlow()

    private val _detectedEmulators = MutableStateFlow<List<DetectedEmulatorStatus>>(emptyList())
    val detectedEmulators: StateFlow<List<DetectedEmulatorStatus>> = _detectedEmulators.asStateFlow()

    private val _installedLauncherApps = MutableStateFlow<List<InstalledAndroidApp>>(emptyList())
    val installedLauncherApps: StateFlow<List<InstalledAndroidApp>> = _installedLauncherApps.asStateFlow()

    private val _scanTask = MutableStateFlow<BackgroundTaskState?>(null)
    val scanTask: StateFlow<BackgroundTaskState?> = _scanTask.asStateFlow()

    private val _contextualError = MutableStateFlow<ContextualError?>(null)
    val contextualError: StateFlow<ContextualError?> = _contextualError.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _retroArchDiagnostic = MutableStateFlow<RetroArchTestDiagnostic?>(null)
    val retroArchDiagnostic: StateFlow<RetroArchTestDiagnostic?> = _retroArchDiagnostic.asStateFlow()

    private val _storageBreakdown = MutableStateFlow<StorageBreakdown?>(null)
    val storageBreakdown: StateFlow<StorageBreakdown?> = _storageBreakdown.asStateFlow()

    private val _pendingBackupPreview = MutableStateFlow<BackupPreview?>(null)
    val pendingBackupPreview: StateFlow<BackupPreview?> = _pendingBackupPreview.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _sourcesMap = MutableStateFlow<Map<String, GameSourceEntity>>(emptyMap())
    val sourcesMap: StateFlow<Map<String, GameSourceEntity>> = _sourcesMap.asStateFlow()

    val filteredAndSortedGames: StateFlow<List<CanonicalGameWithDetails>> = combine(
        allVisibleGames,
        _filterState,
        _sortOption,
        _selectedCategory,
        _sourcesMap
    ) { games, filter, sort, category, srcMap ->
        applyFiltersAndSorting(games, filter, sort, category, srcMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            seedSystemsIfNeeded()
            refreshEmulatorsAndControllers()
            refreshSourcesMap()
            refreshNetworkStatus()
            refreshStorageStats()
            val currentSettings = settingsRepo.settingsFlow.first()
            gamepadController.loadBindingsFromJson(currentSettings.buttonMappingsJson)
            if (currentSettings.autoRescanOnStartup) {
                rescanAllEnabledSources()
            }
        }
    }

    private suspend fun seedSystemsIfNeeded() = withContext(Dispatchers.IO) {
        val existing = dao.getSystemsList()
        val merged = configManager.toSystemEntities(existing)
        dao.insertSystems(merged)
    }

    fun refreshEmulatorsAndControllers() {
        viewModelScope.launch(Dispatchers.IO) {
            _detectedEmulators.value = emulatorDetector.detectAllEmulators()
            gamepadController.refreshConnectedControllers()
            val favSet = settings.value.favoriteAndroidPackagesCsv
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            _installedLauncherApps.value = emulatorDetector.queryLauncherApps(favSet)
        }
    }

    fun refreshNetworkStatus() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            _isOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            _isOnline.value = true
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageBreakdown.value = mediaCacheManager.getStorageBreakdown()
        }
    }

    private suspend fun refreshSourcesMap() = withContext(Dispatchers.IO) {
        val list = dao.getAllGameSources()
        _sourcesMap.value = list.associateBy { it.id }
    }

    fun updateFilter(transform: (LibraryFilterState) -> LibraryFilterState) {
        _filterState.value = transform(_filterState.value)
    }

    fun resetFilters() {
        _filterState.value = LibraryFilterState()
        _selectedCategory.value = AutoCategory.ALL_GAMES
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun selectAutoCategory(category: AutoCategory) {
        _selectedCategory.value = category
    }

    fun dismissContextualError() {
        _contextualError.value = null
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    /**
     * Adds a SAF folder (or JSONL file) as a persistent LibrarySource and immediately scans it.
     */
    fun addLibrarySourceFromSafUri(uri: Uri, providerType: ProviderType, customName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(uri, flags)
                }

                val doc = DocumentFile.fromTreeUri(appContext, uri) ?: DocumentFile.fromSingleUri(appContext, uri)
                val folderName = customName?.takeIf { it.isNotBlank() }
                    ?: doc?.name
                    ?: uri.lastPathSegment?.substringAfterLast(':')
                    ?: when (providerType) {
                        ProviderType.LOCAL_ROM -> "Local ROMs"
                        ProviderType.ARLEY4D_LOCAL_FAKE_ROM -> "Arley4d Fake ROMs"
                        ProviderType.EMULATION_STATION -> "EmulationStation Library"
                        ProviderType.ARLEY4D_CATALOG -> "Arley4d Catalog"
                        ProviderType.GENERIC_JSONL -> "JSONL Catalog"
                    }

                val sourceId = GameGroupingEngine.stableId("lib_${uri}_$providerType")
                val entity = LibrarySourceEntity(
                    id = sourceId,
                    name = folderName,
                    uriString = uri.toString(),
                    providerType = providerType,
                    enabled = true,
                    lastScannedAt = 0L
                )
                dao.insertLibrarySource(entity)
                scanSingleSource(entity)
            } catch (e: Exception) {
                AetherLogger.error(LogCategory.SCAN, "Failed adding SAF library source", e)
                _contextualError.value = ContextualError(
                    title = "Could Not Add Folder",
                    whatHappened = "AetherDeck could not persist access to the selected folder.",
                    whyItHappened = e.message ?: "Storage Access Framework permission error.",
                    howToFix = "Select a folder using the system folder picker and grant access."
                )
            }
        }
    }

    fun scanSingleSource(source: LibrarySourceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val taskId = "scan_${source.id}"
            _scanTask.value = BackgroundTaskState(
                id = taskId,
                type = TaskType.SCAN,
                title = "SCANNING ${source.name.uppercase()}",
                currentItem = "Preparing scan...",
                processed = 0,
                total = 100
            )
            val currentSettings = settings.value
            val result = scanner.scanLibrarySource(
                librarySource = source,
                regionPriority = currentSettings.regionPriorityList,
                languagePriority = currentSettings.languagePriorityList
            ) { fileName, current, total ->
                _scanTask.value = _scanTask.value?.copy(
                    currentItem = fileName,
                    processed = current,
                    total = total.coerceAtLeast(1)
                )
            }
            refreshSourcesMap()
            settingsRepo.recordScanTimestamp()
            refreshStorageStats()

            result.onSuccess { summary ->
                _scanTask.value = _scanTask.value?.copy(
                    isCompleted = true,
                    currentItem = "Found ${summary.canonicalGamesCount} games (${summary.variantsCount} versions)"
                )
                _toastMessage.value = "Library scan completed: ${summary.canonicalGamesCount} games ready."
            }.onFailure { err ->
                _scanTask.value = _scanTask.value?.copy(
                    isCompleted = true,
                    error = err.message
                )
                _contextualError.value = ContextualError(
                    title = "Scan Failed for ${source.name}",
                    whatHappened = err.message ?: "Could not scan folder.",
                    whyItHappened = "Folder permission may have been revoked or storage disconnected.",
                    howToFix = "Re-add the folder in Library Sources to refresh SAF permissions.",
                    settingsRoute = "providers"
                )
            }
        }
    }

    fun rescanAllEnabledSources() {
        viewModelScope.launch(Dispatchers.IO) {
            val enabled = dao.getLibrarySourcesList().filter { it.enabled }
            for (src in enabled) {
                scanSingleSource(src)
            }
        }
    }

    fun toggleLibrarySourceEnabled(source: LibrarySourceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = source.copy(enabled = !source.enabled)
            dao.updateLibrarySource(updated)
            val rootSources = dao.getGameSourcesByRoot(source.id).map { it.id }
            if (rootSources.isNotEmpty()) {
                dao.markSourcesAvailability(rootSources, updated.enabled)
                dao.markVariantsAvailabilityBySource(rootSources, updated.enabled)
            }
        }
    }

    fun renameLibrarySource(source: LibrarySourceEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateLibrarySource(source.copy(name = newName.trim()))
        }
    }

    fun removeLibrarySource(source: LibrarySourceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val rootSources = dao.getGameSourcesByRoot(source.id).map { it.id }
            if (rootSources.isNotEmpty()) {
                dao.markSourcesAvailability(rootSources, false)
                dao.markVariantsAvailabilityBySource(rootSources, false)
            }
            dao.deleteLibrarySource(source.id)
            refreshSourcesMap()
        }
    }

    fun assignSaveOrStateFolder(source: LibrarySourceEntity, uri: Uri, isSaveFolder: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val updated = if (isSaveFolder) {
                source.copy(saveFolderUri = uri.toString())
            } else {
                source.copy(stateFolderUri = uri.toString())
            }
            dao.updateLibrarySource(updated)
            _toastMessage.value = if (isSaveFolder) "Save folder linked via SAF" else "State folder linked via SAF"
        }
    }

    // Game Actions: Play, Favorite, Preferred Variant, Merge/Split, Edit Metadata, Custom Art
    fun launchGame(gameId: String, specificVariantId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val details = dao.getGameWithDetails(gameId) ?: return@launch
            val game = details.game
            val variant = when {
                specificVariantId != null -> details.variants.firstOrNull { it.id == specificVariantId }
                game.preferredVariantId != null -> details.variants.firstOrNull { it.id == game.preferredVariantId }
                else -> details.variants.firstOrNull { it.preferred } ?: details.variants.firstOrNull()
            }

            if (variant == null) {
                _contextualError.value = ContextualError(
                    title = "No Game Version Available",
                    whatHappened = "No playable variant was found for '${game.displayTitle}'.",
                    whyItHappened = "The underlying ROM file may have been removed.",
                    howToFix = "Rescan your library folder or check Versions.",
                    settingsRoute = "providers"
                )
                return@launch
            }

            val source = dao.getGameSourceById(variant.sourceId)
            val system = dao.getSystemById(game.systemId)
            val preCheck: PreLaunchCheckResult = launchRouter.performPreLaunchCheck(
                game = game,
                variant = variant,
                source = source,
                system = system,
                preferredRetroArchPackage = settings.value.selectedRetroArchPackage.ifBlank { null }
            )

            if (!preCheck.canLaunch || source == null) {
                _contextualError.value = preCheck.error
                return@launch
            }

            val launchResult = kotlinx.coroutines.withContext(Dispatchers.Main) {
                launchRouter.executeLaunch(preCheck, source)
            }
            launchResult.onSuccess {
                dao.recordGameLaunch(game.id, System.currentTimeMillis())
            }.onFailure { err ->
                _contextualError.value = ContextualError(
                    title = "Launch Failed",
                    whatHappened = "Could not start ${preCheck.resolvedPackage ?: "emulator"} for '${game.displayTitle}'.",
                    whyItHappened = err.message ?: "Android rejected the launch Intent.",
                    howToFix = "Verify your emulator and core configuration in Emulator Settings.",
                    settingsRoute = "emulators"
                )
            }
        }
    }

    fun toggleFavorite(gameId: String, currentFavorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setGameFavorite(gameId, !currentFavorite)
        }
    }

    fun toggleHidden(gameId: String, currentHidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setGameHidden(gameId, !currentHidden)
            _toastMessage.value = if (!currentHidden) "Game hidden from library" else "Game restored to library"
        }
    }

    fun setPreferredVariant(gameId: String, variantId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setPreferredVariant(gameId, variantId)
            val variants = dao.getVariantsForGame(gameId)
            val updated = variants.map { it.copy(preferred = (it.id == variantId)) }
            dao.insertGameVariants(updated)
            _toastMessage.value = "Preferred version updated"
        }
    }

    fun saveUserEditedMetadata(
        game: CanonicalGameEntity,
        title: String,
        description: String,
        releaseYear: Int?,
        developer: String,
        publisher: String,
        genre: String,
        players: String,
        franchise: String,
        emulatorOverrideId: String?,
        coreOverride: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanTitle = title.trim().ifEmpty { game.displayTitle }
            val updated = game.copy(
                displayTitle = cleanTitle,
                sortTitle = TitleNormalizer.createSortTitle(cleanTitle),
                description = description.trim(),
                releaseYear = releaseYear,
                developer = developer.trim(),
                publisher = publisher.trim(),
                genre = genre.trim(),
                players = players.trim().ifEmpty { "1" },
                franchise = franchise.trim(),
                userEditedMetadata = true,
                metadataSourceRank = 1,
                emulatorOverrideId = emulatorOverrideId?.takeIf { it.isNotBlank() },
                coreOverride = coreOverride?.takeIf { it.isNotBlank() }
            )
            dao.updateCanonicalGame(updated)
            _toastMessage.value = "Metadata saved (Protected from automatic overwrite)"
        }
    }

    fun setCustomArtwork(gameId: String, mediaType: MediaType, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val mediaEntity = GameMediaEntity(
                id = GameGroupingEngine.stableId("med_${gameId}_${mediaType.name}"),
                canonicalGameId = gameId,
                mediaType = mediaType,
                localOrRemoteUri = uri.toString(),
                providerName = "UserCustomArt",
                isCustomUserArt = true
            )
            dao.insertGameMedia(listOf(mediaEntity))
            _toastMessage.value = "Custom ${mediaType.name.lowercase()} artwork applied"
        }
    }

    /**
     * Manual Merge: moves all variants from [secondaryGameId] into [primaryGameId] and deletes [secondaryGameId].
     */
    fun mergeCanonicalGames(primaryGameId: String, secondaryGameId: String, reviewId: String? = null) {
        if (primaryGameId == secondaryGameId) return
        viewModelScope.launch(Dispatchers.IO) {
            val secondaryVariants = dao.getVariantsForGame(secondaryGameId)
            val reassigned = secondaryVariants.map {
                it.copy(canonicalGameId = primaryGameId, preferred = false)
            }
            dao.insertGameVariants(reassigned)
            dao.deleteCanonicalGame(secondaryGameId)
            reviewId?.let { dao.markMatchReviewResolved(it) }
            _toastMessage.value = "Merged variants into single game"
        }
    }

    /**
     * Manual Split: extracts [variant] out of its current CanonicalGame into its own independent CanonicalGame.
     */
    fun splitVariantIntoSeparateGame(variant: GameVariantEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = TitleNormalizer.parse(variant.originalTitle)
            val newGameId = GameGroupingEngine.stableId("split_${variant.id}_${System.currentTimeMillis()}")
            val newCanonical = CanonicalGameEntity(
                id = newGameId,
                displayTitle = variant.originalTitle,
                normalizedTitle = parsed.normalizedTitle,
                sortTitle = parsed.sortTitle,
                systemId = variant.systemId,
                preferredVariantId = variant.id
            )
            dao.insertCanonicalGame(newCanonical)
            dao.updateGameVariant(variant.copy(canonicalGameId = newGameId, preferred = true))
            _toastMessage.value = "Split '${variant.originalTitle}' into its own library entry"
        }
    }

    // Collections (Manual & Smart)
    fun createManualCollection(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val col = CollectionEntity(
                id = GameGroupingEngine.stableId("col_${name}_${System.currentTimeMillis()}"),
                name = name.trim(),
                description = description.trim(),
                isSmart = false
            )
            dao.insertCollection(col)
            _toastMessage.value = "Created collection '${col.name}'"
        }
    }

    fun createSmartCollection(name: String, description: String, definition: SmartCollectionDefinition) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val col = CollectionEntity(
                id = GameGroupingEngine.stableId("smart_${name}_${System.currentTimeMillis()}"),
                name = name.trim(),
                description = description.trim(),
                isSmart = true,
                smartRulesJson = SmartCollectionEngine.encodeDefinition(definition)
            )
            dao.insertCollection(col)
            _toastMessage.value = "Created smart collection '${col.name}'"
        }
    }

    fun addGameToManualCollection(collectionId: String, gameId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.addGameToCollection(CollectionGameCrossRef(collectionId, gameId))
            _toastMessage.value = "Added to collection"
        }
    }

    fun removeGameFromManualCollection(collectionId: String, gameId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.removeGameFromCollection(collectionId, gameId)
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteCollection(collectionId)
        }
    }

    // System & Emulator Configuration
    fun updateSystemEmulatorAndCore(system: SystemEntity, emulatorId: String, coreName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateSystem(
                system.copy(
                    selectedEmulatorId = emulatorId,
                    selectedCore = coreName
                )
            )
            _toastMessage.value = "Updated ${system.shortName} emulator configuration"
        }
    }

    fun toggleSystemCatalogSync(system: SystemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateSystem(system.copy(catalogSyncEnabled = !system.catalogSyncEnabled))
        }
    }

    fun syncSelectedCatalogs() {
        viewModelScope.launch {
            refreshNetworkStatus()
            if (!_isOnline.value) {
                _toastMessage.value = "Cannot sync remote catalog while OFFLINE"
                return@launch
            }
            val result = catalogSyncService.syncSelectedSystems(systems.value)
            result.onSuccess { count ->
                settingsRepo.recordCatalogTimestamp()
                _toastMessage.value = "Synced $count catalog entries"
            }.onFailure { err ->
                _toastMessage.value = "Catalog sync error: ${err.message}"
            }
        }
    }

    // RetroArch TEST APP & TEST GAME
    fun testLaunchRetroArchStandalone() {
        val res = retroArchAdapter.testLaunchRetroArchApp()
        res.onSuccess { msg -> _toastMessage.value = msg }
            .onFailure { err ->
                _contextualError.value = ContextualError(
                    title = "RetroArch TEST APP Failed",
                    whatHappened = err.message ?: "Could not launch RetroArch.",
                    whyItHappened = "RetroArch is not installed on this device.",
                    howToFix = "Install RetroArch (com.retroarch.aarch64 or com.retroarch) and try again.",
                    settingsRoute = "emulators"
                )
            }
    }

    fun testLaunchRetroArchWithRom(romUri: Uri, coreName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    romUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val diag = kotlinx.coroutines.withContext(Dispatchers.Main) {
                retroArchAdapter.runTestGameLaunch(
                    preferredPackage = settings.value.selectedRetroArchPackage.ifBlank { null },
                    romUriString = romUri.toString(),
                    coreFileName = coreName.ifBlank { "snes9x_libretro_android.so" },
                    actuallyStartActivity = true
                )
            }
            _retroArchDiagnostic.value = diag
        }
    }

    // Scraping
    fun scrapeSingleGame(gameId: String, mode: ScrapeMode = ScrapeMode.MISSING_ONLY) {
        viewModelScope.launch {
            val details = dao.getGameWithDetails(gameId) ?: return@launch
            scraperEngine.scrapeGames(listOf(details), mode, settings.value)
        }
    }

    fun scrapeSystemGames(systemId: String, mode: ScrapeMode = ScrapeMode.MISSING_ONLY) {
        viewModelScope.launch {
            val targets = allVisibleGames.value.filter { it.game.systemId == systemId }
            scraperEngine.scrapeGames(targets, mode, settings.value)
        }
    }

    fun scrapeAllGames(mode: ScrapeMode) {
        viewModelScope.launch {
            scraperEngine.scrapeGames(allVisibleGames.value, mode, settings.value)
        }
    }

    // Backup, Restore & Diagnostics Export
    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            backupRestoreManager.exportBackupToUri(uri)
                .onSuccess { _toastMessage.value = it }
                .onFailure { _toastMessage.value = "Backup failed: ${it.message}" }
        }
    }

    fun previewRestoreFromUri(uri: Uri) {
        viewModelScope.launch {
            backupRestoreManager.inspectBackupUri(uri)
                .onSuccess { _pendingBackupPreview.value = it }
                .onFailure { _toastMessage.value = "Invalid backup file: ${it.message}" }
        }
    }

    fun confirmRestorePreview() {
        val preview = _pendingBackupPreview.value ?: return
        viewModelScope.launch {
            backupRestoreManager.applyRestore(preview)
                .onSuccess {
                    _pendingBackupPreview.value = null
                    refreshSourcesMap()
                    _toastMessage.value = it
                }
                .onFailure { _toastMessage.value = "Restore error: ${it.message}" }
        }
    }

    fun cancelRestorePreview() {
        _pendingBackupPreview.value = null
    }

    fun exportDiagnosticsReportToUri(uri: Uri, screenSummary: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val emuSummary = _detectedEmulators.value.map { "${it.config.name}: ${it.verificationNote}" }
                val report = backupRestoreManager.buildDiagnosticsReport(
                    appVersion = "1.0.0 (Build 1)",
                    screenSummary = screenSummary,
                    controllerDetected = gamepadController.telemetry.value.detectedControllerName,
                    installedEmulatorsSummary = emuSummary
                )
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(report.toByteArray(Charsets.UTF_8))
                }
                _toastMessage.value = "Diagnostics exported (Secrets redacted)"
            } catch (e: Exception) {
                _toastMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun pickRandomFilteredGame(): CanonicalGameWithDetails? {
        val list = filteredAndSortedGames.value
        return if (list.isNotEmpty()) list.random() else null
    }

    private fun applyFiltersAndSorting(
        games: List<CanonicalGameWithDetails>,
        filter: LibraryFilterState,
        sort: SortOption,
        category: AutoCategory,
        srcMap: Map<String, GameSourceEntity>
    ): List<CanonicalGameWithDetails> {
        val normQuery = TitleNormalizer.normalizeForMatching(filter.searchQuery)

        val filtered = games.filter { item ->
            val g = item.game
            val hasCover = item.media.any { it.mediaType == MediaType.COVER }
            val hasMissingMeta = g.description.isBlank() || g.developer.isBlank() || g.releaseYear == null

            // AutoCategory filter
            val matchesCategory = when (category) {
                AutoCategory.ALL_GAMES, AutoCategory.SYSTEMS, AutoCategory.GENRES,
                AutoCategory.YEARS, AutoCategory.DEVELOPERS, AutoCategory.PUBLISHERS,
                AutoCategory.FRANCHISES, AutoCategory.REGIONS, AutoCategory.LANGUAGES -> true
                AutoCategory.FAVORITES -> g.favorite
                AutoCategory.RECENT, AutoCategory.CONTINUE_PLAYING -> g.lastPlayed != null
                AutoCategory.RECENTLY_ADDED -> true
                AutoCategory.MOST_PLAYED -> g.playCount > 0
                AutoCategory.NEVER_PLAYED -> g.playCount == 0
                AutoCategory.MULTIPLAYER -> g.players != "1" && g.players.isNotBlank()
                AutoCategory.LOCAL -> item.variants.any { srcMap[it.sourceId]?.provider == ProviderType.LOCAL_ROM }
                AutoCategory.ARLEY4D -> item.variants.any {
                    val p = srcMap[it.sourceId]?.provider
                    p == ProviderType.ARLEY4D_LOCAL_FAKE_ROM || p == ProviderType.ARLEY4D_CATALOG
                }
                AutoCategory.UNKNOWN_SYSTEM -> g.systemId == "unknown"
                AutoCategory.MISSING_ARTWORK -> !hasCover
                AutoCategory.MISSING_METADATA -> hasMissingMeta
            }
            if (!matchesCategory) return@filter false

            if (filter.systemId != null && g.systemId != filter.systemId) return@filter false
            if (filter.genre != null && !g.genre.contains(filter.genre, ignoreCase = true)) return@filter false
            if (filter.releaseYear != null && g.releaseYear != filter.releaseYear) return@filter false
            if (filter.developer != null && !g.developer.contains(filter.developer, ignoreCase = true)) return@filter false
            if (filter.publisher != null && !g.publisher.contains(filter.publisher, ignoreCase = true)) return@filter false
            if (filter.region != null && item.variants.none { it.region.equals(filter.region, ignoreCase = true) }) return@filter false
            if (filter.language != null && item.variants.none { it.languages.contains(filter.language, ignoreCase = true) }) return@filter false
            if (filter.players != null && !g.players.contains(filter.players, ignoreCase = true)) return@filter false
            if (filter.providerType != null && item.variants.none { srcMap[it.sourceId]?.provider == filter.providerType }) return@filter false
            if (filter.favoritesOnly && !g.favorite) return@filter false

            when (filter.playedStatus) {
                PlayedFilter.ALL -> Unit
                PlayedFilter.PLAYED_ONLY -> if (g.playCount == 0) return@filter false
                PlayedFilter.NEVER_PLAYED -> if (g.playCount > 0) return@filter false
            }

            when (filter.artworkStatus) {
                ArtworkFilter.ALL -> Unit
                ArtworkFilter.HAS_ARTWORK -> if (!hasCover) return@filter false
                ArtworkFilter.MISSING_ARTWORK -> if (hasCover) return@filter false
            }

            if (filter.missingMetadataOnly && !hasMissingMeta) return@filter false

            if (normQuery.isNotEmpty()) {
                val matchesTitle = g.normalizedTitle.contains(normQuery)
                val matchesDev = TitleNormalizer.normalizeForMatching(g.developer).contains(normQuery)
                val matchesGenre = TitleNormalizer.normalizeForMatching(g.genre).contains(normQuery)
                val matchesSys = g.systemId.contains(normQuery, ignoreCase = true)
                val matchesFranchise = TitleNormalizer.normalizeForMatching(g.franchise).contains(normQuery)
                if (!matchesTitle && !matchesDev && !matchesGenre && !matchesSys && !matchesFranchise) {
                    return@filter false
                }
            }
            true
        }

        return when (sort) {
            SortOption.TITLE -> filtered.sortedBy { it.game.sortTitle }
            SortOption.DATE_ADDED -> filtered.sortedByDescending { it.game.dateAdded }
            SortOption.LAST_PLAYED -> filtered.sortedByDescending { it.game.lastPlayed ?: 0L }
            SortOption.PLAY_COUNT -> filtered.sortedByDescending { it.game.playCount }
            SortOption.RELEASE_YEAR -> filtered.sortedByDescending { it.game.releaseYear ?: 0 }
            SortOption.RATING -> filtered.sortedByDescending { it.game.rating ?: 0f }
            SortOption.SYSTEM -> filtered.sortedWith(compareBy<CanonicalGameWithDetails> { it.game.systemId }.thenBy { it.game.sortTitle })
            SortOption.RANDOM -> filtered.shuffled()
        }
    }
}
