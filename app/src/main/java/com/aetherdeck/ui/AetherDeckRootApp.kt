package com.aetherdeck.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.aetherdeck.core.database.CanonicalGameWithDetails
import com.aetherdeck.core.database.LibrarySourceEntity
import com.aetherdeck.core.models.MediaType
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.input.gamepad.ControllerAction
import com.aetherdeck.metadata.scraper.ScrapeMode
import com.aetherdeck.ui.components.ContextualErrorDialog
import com.aetherdeck.ui.components.ControllerBottomHudBar
import com.aetherdeck.ui.components.QuickMenuDialog
import com.aetherdeck.ui.components.TaskProgressBanner
import com.aetherdeck.ui.details.GameDetailsScreen
import com.aetherdeck.ui.home.HomeScreen
import com.aetherdeck.ui.library.CollectionsAndMatchReviewScreen
import com.aetherdeck.ui.library.LibraryScreen
import com.aetherdeck.ui.library.SystemsOverviewScreen
import com.aetherdeck.ui.onboarding.OnboardingWizardScreen
import com.aetherdeck.ui.providers.EmulatorsAndRetroArchScreen
import com.aetherdeck.ui.providers.ProvidersAndSourcesScreen
import com.aetherdeck.ui.search.GlobalSearchDialog
import com.aetherdeck.ui.settings.MasterSettingsScreen
import com.aetherdeck.ui.settings.SettingsSection
import com.aetherdeck.ui.theme.AetherDeckTheme

enum class MainDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    LIBRARY("library", "Library", Icons.Filled.VideogameAsset),
    SYSTEMS("systems", "Systems", Icons.Filled.Devices),
    COLLECTIONS("collections", "Collections", Icons.Filled.CollectionsBookmark),
    SOURCES("providers", "Sources", Icons.Filled.FolderOpen),
    EMULATORS("emulators", "Emulators", Icons.Filled.Memory),
    SETTINGS("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun AetherDeckRootApp(viewModel: AetherDeckViewModel) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allGames by viewModel.allVisibleGames.collectAsStateWithLifecycle()
    val filteredGames by viewModel.filteredAndSortedGames.collectAsStateWithLifecycle()
    val systems by viewModel.systems.collectAsStateWithLifecycle()
    val librarySources by viewModel.librarySources.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val crossRefs by viewModel.collectionCrossRefs.collectAsStateWithLifecycle()
    val pendingReviews by viewModel.pendingMatchReviews.collectAsStateWithLifecycle()
    val catalogCount by viewModel.catalogCount.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val detectedEmulators by viewModel.detectedEmulators.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedLauncherApps.collectAsStateWithLifecycle()
    val sourcesMap by viewModel.sourcesMap.collectAsStateWithLifecycle()
    val contextualError by viewModel.contextualError.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val retroArchDiag by viewModel.retroArchDiagnostic.collectAsStateWithLifecycle()
    val storageBreakdown by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val pendingBackupPreview by viewModel.pendingBackupPreview.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    val scanTask by viewModel.scanTask.collectAsStateWithLifecycle()
    val scrapeTask by viewModel.scraperEngine.activeTask.collectAsStateWithLifecycle()
    val catalogTask by viewModel.catalogSyncService.syncTask.collectAsStateWithLifecycle()
    val controllerTelemetry by viewModel.gamepadController.telemetry.collectAsStateWithLifecycle()
    val remappingTarget by viewModel.gamepadController.remappingTarget.collectAsStateWithLifecycle()

    var currentDest by remember { mutableStateOf(MainDestination.HOME) }
    var activeSettingsSection by remember { mutableStateOf(SettingsSection.GENERAL) }
    var selectedGameIdForDetails by remember { mutableStateOf<String?>(null) }
    var quickMenuTarget by remember { mutableStateOf<CanonicalGameWithDetails?>(null) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var forceShowOnboarding by remember { mutableStateOf(false) }

    // SAF Launchers
    var pendingFolderProviderType by remember { mutableStateOf(ProviderType.LOCAL_ROM) }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.addLibrarySourceFromSafUri(uri, pendingFolderProviderType)
        }
    }

    var pendingSaveStateSource by remember { mutableStateOf<Pair<LibrarySourceEntity, Boolean>?>(null) }
    val saveStateFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val pair = pendingSaveStateSource
        if (uri != null && pair != null) {
            viewModel.assignSaveOrStateFolder(pair.first, uri, pair.second)
        }
        pendingSaveStateSource = null
    }

    var pendingTestGameCore by remember { mutableStateOf("snes9x_libretro_android.so") }
    val testGameRomPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.testLaunchRetroArchWithRom(uri, pendingTestGameCore)
        }
    }

    var pendingCustomArtTarget by remember { mutableStateOf<Pair<String, MediaType>?>(null) }
    val customArtworkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = pendingCustomArtTarget
        if (uri != null && target != null) {
            viewModel.setCustomArtwork(target.first, target.second, uri)
        }
        pendingCustomArtTarget = null
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.previewRestoreFromUri(uri)
        }
    }

    val exportDiagnosticsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val screenSummary = "${configuration.screenWidthDp}x${configuration.screenHeightDp}dp (${if (isLandscape) "Landscape" else "Portrait"})"
            viewModel.exportDiagnosticsReportToUri(uri, screenSummary)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // Hardware Controller Semantic Action Listener (L1/R1 section switch, SELECT search, START settings, B back, Y favorite)
    LaunchedEffect(Unit) {
        viewModel.gamepadController.actions.collect { action ->
            when (action) {
                ControllerAction.PREV_SECTION -> {
                    if (selectedGameIdForDetails == null) {
                        val entries = MainDestination.entries
                        val prevIdx = (entries.indexOf(currentDest) - 1 + entries.size) % entries.size
                        currentDest = entries[prevIdx]
                    }
                }
                ControllerAction.NEXT_SECTION -> {
                    if (selectedGameIdForDetails == null) {
                        val entries = MainDestination.entries
                        val nextIdx = (entries.indexOf(currentDest) + 1) % entries.size
                        currentDest = entries[nextIdx]
                    }
                }
                ControllerAction.QUICK_MENU -> {
                    showGlobalSearch = !showGlobalSearch
                }
                ControllerAction.MAIN_MENU -> {
                    selectedGameIdForDetails = null
                    currentDest = MainDestination.SETTINGS
                }
                ControllerAction.BACK -> {
                    when {
                        showGlobalSearch -> showGlobalSearch = false
                        quickMenuTarget != null -> quickMenuTarget = null
                        selectedGameIdForDetails != null -> selectedGameIdForDetails = null
                        currentDest != MainDestination.HOME -> currentDest = MainDestination.HOME
                    }
                }
                ControllerAction.FAVORITE -> {
                    val activeId = selectedGameIdForDetails ?: filteredGames.firstOrNull()?.game?.id
                    if (activeId != null) {
                        val gameObj = allGames.firstOrNull { it.game.id == activeId }
                        if (gameObj != null) {
                            viewModel.toggleFavorite(gameObj.game.id, gameObj.game.favorite)
                        }
                    }
                }
                ControllerAction.CONTEXT_MENU -> {
                    val activeId = selectedGameIdForDetails ?: filteredGames.firstOrNull()?.game?.id
                    if (activeId != null) {
                        quickMenuTarget = allGames.firstOrNull { it.game.id == activeId }
                    }
                }
                else -> Unit
            }
        }
    }

    BackHandler(enabled = selectedGameIdForDetails != null || currentDest != MainDestination.HOME) {
        if (selectedGameIdForDetails != null) {
            selectedGameIdForDetails = null
        } else {
            currentDest = MainDestination.HOME
        }
    }

    AetherDeckTheme(settings = settings) {
        if (!settings.onboardingCompleted || forceShowOnboarding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                OnboardingWizardScreen(
                    settings = settings,
                    controllerTelemetry = controllerTelemetry,
                    librarySources = librarySources,
                    detectedEmulators = detectedEmulators,
                    systems = systems,
                    canonicalGameCount = allGames.size,
                    onSelectTheme = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateTheme(it) } },
                    onSelectScale = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateInterfaceScale(it) } },
                    onSelectCardSize = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateCardSizePreset(it) } },
                    onToggleSwapAB = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateControllerSettings(swapAB = it) } },
                    onRequestAddFolder = { pt ->
                        pendingFolderProviderType = pt
                        folderPickerLauncher.launch(null)
                    },
                    onRefreshEmulators = { viewModel.refreshEmulatorsAndControllers() },
                    onSaveScraperKeys = { sgdb, tgdb ->
                        viewModel.viewModelScopeLaunch {
                            viewModel.settingsRepo.updateScraperCredentials(sgdbApiKey = sgdb, tgdbApiKey = tgdb)
                        }
                    },
                    onRescanAll = { viewModel.rescanAllEnabledSources() },
                    onSyncCatalogs = { viewModel.syncSelectedCatalogs() },
                    onScrapeMissing = { viewModel.scrapeAllGames(ScrapeMode.MISSING_ONLY) },
                    onCompleteOnboarding = {
                        forceShowOnboarding = false
                        viewModel.viewModelScopeLaunch { viewModel.settingsRepo.setOnboardingCompleted(true) }
                    }
                )
            }
        } else {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        if (!isLandscape && selectedGameIdForDetails == null) {
                            NavigationBar {
                                MainDestination.entries.take(5).forEach { dest ->
                                    NavigationBarItem(
                                        selected = currentDest == dest,
                                        onClick = {
                                            selectedGameIdForDetails = null
                                            currentDest = dest
                                        },
                                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                                        label = { Text(dest.label) }
                                    )
                                }
                            }
                        }
                        ControllerBottomHudBar(
                            controllerConnected = controllerTelemetry.isConnected,
                            swapAB = settings.swapAB,
                            swapXY = settings.swapXY,
                            onQuickSearchClick = { showGlobalSearch = true },
                            onAddLibraryClick = {
                                pendingFolderProviderType = ProviderType.LOCAL_ROM
                                folderPickerLauncher.launch(null)
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isLandscape && selectedGameIdForDetails == null) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            header = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "AETHER",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { showGlobalSearch = true },
                                        modifier = Modifier.testTag("nav_search_button")
                                    ) {
                                        Icon(Icons.Filled.Search, contentDescription = "Global Search")
                                    }
                                }
                            }
                        ) {
                            MainDestination.entries.forEach { dest ->
                                NavigationRailItem(
                                    selected = currentDest == dest,
                                    onClick = {
                                        selectedGameIdForDetails = null
                                        currentDest = dest
                                    },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Non-blocking Offline Banner
                        if (!isOnline) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "OFFLINE — Local library & gameplay remain 100% available",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    TextButton(onClick = { viewModel.refreshNetworkStatus() }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }

                        // Active Background Task Banner (Scan / Scrape / Catalog Sync)
                        val activeBannerTask = scrapeTask ?: scanTask?.takeIf { !it.isCompleted } ?: catalogTask
                        if (activeBannerTask != null) {
                            TaskProgressBanner(
                                task = activeBannerTask,
                                onPauseResume = {
                                    if (activeBannerTask.isPaused) viewModel.scraperEngine.resumeScraping()
                                    else viewModel.scraperEngine.pauseScraping()
                                },
                                onCancel = {
                                    viewModel.scraperEngine.cancelScraping()
                                    viewModel.catalogSyncService.dismissSyncTask()
                                },
                                onDismiss = {
                                    viewModel.scraperEngine.clearCompletedTask()
                                    viewModel.catalogSyncService.dismissSyncTask()
                                }
                            )
                        }

                        // Main Content Area
                        val detailGame = selectedGameIdForDetails?.let { id ->
                            allGames.firstOrNull { it.game.id == id }
                        }

                        if (detailGame != null) {
                            val sysEntity = systems.firstOrNull { it.id == detailGame.game.systemId }
                            val sameSystemGames = allGames.filter { it.game.systemId == detailGame.game.systemId }
                            GameDetailsScreen(
                                item = detailGame,
                                system = sysEntity,
                                allSystemGames = sameSystemGames,
                                sourcesMap = sourcesMap,
                                collections = collections,
                                onBack = { selectedGameIdForDetails = null },
                                onPlayVariant = { variantId -> viewModel.launchGame(detailGame.game.id, variantId) },
                                onSetPreferredVariant = { variantId -> viewModel.setPreferredVariant(detailGame.game.id, variantId) },
                                onSplitVariant = { variant -> viewModel.splitVariantIntoSeparateGame(variant) },
                                onMergeWithGame = { otherGameId ->
                                    viewModel.mergeCanonicalGames(detailGame.game.id, otherGameId)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(detailGame.game.id, detailGame.game.favorite) },
                                onScrapeThisGame = { viewModel.scrapeSingleGame(detailGame.game.id, ScrapeMode.MISSING_ONLY) },
                                onSaveMetadata = { g, t, d, y, dev, pub, gen, pl, fr, emu, core ->
                                    viewModel.saveUserEditedMetadata(g, t, d, y, dev, pub, gen, pl, fr, emu, core)
                                },
                                onPickCustomArtwork = { mediaType ->
                                    pendingCustomArtTarget = detailGame.game.id to mediaType
                                    customArtworkPickerLauncher.launch(arrayOf("image/*"))
                                },
                                onAddToCollection = { colId ->
                                    viewModel.addGameToManualCollection(colId, detailGame.game.id)
                                }
                            )
                        } else {
                            when (currentDest) {
                                MainDestination.HOME -> HomeScreen(
                                    games = allGames,
                                    systems = systems,
                                    collections = collections,
                                    settings = settings,
                                    onOpenGameDetails = { selectedGameIdForDetails = it },
                                    onQuickMenuGame = { quickMenuTarget = it },
                                    onToggleFavorite = { viewModel.toggleFavorite(it.game.id, it.game.favorite) },
                                    onPlayGame = { viewModel.launchGame(it) },
                                    onSelectSystem = { sysId ->
                                        viewModel.updateFilter { it.copy(systemId = sysId) }
                                        currentDest = MainDestination.LIBRARY
                                    },
                                    onOpenCollections = { currentDest = MainDestination.COLLECTIONS },
                                    onRequestAddFolder = { pt ->
                                        pendingFolderProviderType = pt
                                        folderPickerLauncher.launch(null)
                                    },
                                    onRescanAll = { viewModel.rescanAllEnabledSources() },
                                    onUpdateHomeWidgets = { widgets ->
                                        viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateHomeWidgets(widgets) }
                                    }
                                )

                                MainDestination.LIBRARY -> LibraryScreen(
                                    games = filteredGames,
                                    systems = systems,
                                    filterState = filterState,
                                    sortOption = sortOption,
                                    selectedCategory = selectedCategory,
                                    settings = settings,
                                    onUpdateFilter = viewModel::updateFilter,
                                    onResetFilters = viewModel::resetFilters,
                                    onSelectSort = viewModel::setSortOption,
                                    onSelectCategory = viewModel::selectAutoCategory,
                                    onSelectViewMode = { mode ->
                                        viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateLibraryViewMode(mode) }
                                    },
                                    onOpenGameDetails = { selectedGameIdForDetails = it },
                                    onQuickMenuGame = { quickMenuTarget = it },
                                    onToggleFavorite = { viewModel.toggleFavorite(it.game.id, it.game.favorite) },
                                    onPlayGame = { viewModel.launchGame(it) },
                                    onSurpriseMe = {
                                        viewModel.pickRandomFilteredGame()?.let { selectedGameIdForDetails = it.game.id }
                                    },
                                    onRequestAddFolder = { pt ->
                                        pendingFolderProviderType = pt
                                        folderPickerLauncher.launch(null)
                                    }
                                )

                                MainDestination.SYSTEMS -> SystemsOverviewScreen(
                                    systems = systems,
                                    games = allGames,
                                    onSelectSystem = { sysId ->
                                        viewModel.updateFilter { it.copy(systemId = sysId) }
                                        currentDest = MainDestination.LIBRARY
                                    },
                                    onScrapeSystem = { sysId -> viewModel.scrapeSystemGames(sysId) }
                                )

                                MainDestination.COLLECTIONS -> CollectionsAndMatchReviewScreen(
                                    collections = collections,
                                    crossRefs = crossRefs,
                                    allGames = allGames,
                                    sourcesMap = sourcesMap,
                                    pendingReviews = pendingReviews,
                                    onCreateManualCollection = viewModel::createManualCollection,
                                    onCreateSmartCollection = viewModel::createSmartCollection,
                                    onDeleteCollection = viewModel::deleteCollection,
                                    onMergeGames = viewModel::mergeCanonicalGames,
                                    onOpenGameDetails = { selectedGameIdForDetails = it }
                                )

                                MainDestination.SOURCES -> ProvidersAndSourcesScreen(
                                    librarySources = librarySources,
                                    systems = systems,
                                    catalogCount = catalogCount,
                                    onRequestAddFolder = { pt ->
                                        pendingFolderProviderType = pt
                                        folderPickerLauncher.launch(null)
                                    },
                                    onToggleSourceEnabled = viewModel::toggleLibrarySourceEnabled,
                                    onRescanSource = viewModel::scanSingleSource,
                                    onRenameSource = viewModel::renameLibrarySource,
                                    onRemoveSource = viewModel::removeLibrarySource,
                                    onPickSaveFolder = { src ->
                                        pendingSaveStateSource = src to true
                                        saveStateFolderPickerLauncher.launch(null)
                                    },
                                    onPickStateFolder = { src ->
                                        pendingSaveStateSource = src to false
                                        saveStateFolderPickerLauncher.launch(null)
                                    },
                                    onToggleSystemCatalogSync = viewModel::toggleSystemCatalogSync,
                                    onSyncSelectedCatalogs = viewModel::syncSelectedCatalogs
                                )

                                MainDestination.EMULATORS -> EmulatorsAndRetroArchScreen(
                                    detectedEmulators = detectedEmulators,
                                    systems = systems,
                                    retroArchDiagnostic = retroArchDiag,
                                    onRefreshDetection = viewModel::refreshEmulatorsAndControllers,
                                    onTestLaunchRetroArchApp = viewModel::testLaunchRetroArchStandalone,
                                    onRequestTestLaunchGameRom = { core ->
                                        pendingTestGameCore = core
                                        testGameRomPickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    onUpdateSystemEmulator = viewModel::updateSystemEmulatorAndCore
                                )

                                MainDestination.SETTINGS -> MasterSettingsScreen(
                                    settings = settings,
                                    controllerTelemetry = controllerTelemetry,
                                    remappingTarget = remappingTarget,
                                    storageBreakdown = storageBreakdown,
                                    pendingBackupPreview = pendingBackupPreview,
                                    games = allGames,
                                    systems = systems,
                                    librarySources = librarySources,
                                    detectedEmulators = detectedEmulators,
                                    initialSection = activeSettingsSection,
                                    onSelectTheme = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateTheme(it) } },
                                    onUpdateCustomThemeColors = { a, b, s ->
                                        viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateCustomThemeColors(a, b, s) }
                                    },
                                    onUpdateThemeAppearance = { cr, fg, bd, sat, cb, bl ->
                                        viewModel.viewModelScopeLaunch {
                                            viewModel.settingsRepo.updateThemeAppearance(cr, fg, bd, sat, cb, bl)
                                        }
                                    },
                                    onSelectScale = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateInterfaceScale(it) } },
                                    onSelectDensity = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateDensityPreset(it) } },
                                    onSelectCardSize = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateCardSizePreset(it) } },
                                    onUpdateTextScale = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateTextScale(it) } },
                                    onSelectLibraryView = { viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateLibraryViewMode(it) } },
                                    onUpdateLibraryPrefs = { r, l, ag, md, hd, sv ->
                                        viewModel.viewModelScopeLaunch {
                                            viewModel.settingsRepo.updateLibraryPreferences(r, l, ag, md, hd, sv)
                                        }
                                    },
                                    onUpdateControllerSettings = { dz, ss, rd, rs, ab, xy, vib ->
                                        viewModel.viewModelScopeLaunch {
                                            viewModel.settingsRepo.updateControllerSettings(dz, ss, rd, rs, ab, xy, vib)
                                        }
                                    },
                                    onStartRemapAction = { action -> viewModel.gamepadController.startRemapping(action) },
                                    onResetControllerBindings = {
                                        val resetJson = viewModel.gamepadController.resetBindingsToDefault()
                                        viewModel.viewModelScopeLaunch {
                                            viewModel.settingsRepo.updateControllerSettings(buttonMappingsJson = resetJson)
                                        }
                                    },
                                    onUpdateScraperKeys = { ssu, ssk, sgdb, tgdb, ra, auto, wifi ->
                                        viewModel.viewModelScopeLaunch {
                                            viewModel.settingsRepo.updateScraperCredentials(
                                                ssUser = ssu,
                                                ssApiKey = ssk,
                                                sgdbApiKey = sgdb,
                                                tgdbApiKey = tgdb,
                                                raApiKey = ra,
                                                autoScrape = auto,
                                                wifiOnly = wifi
                                            )
                                        }
                                    },
                                    onTriggerScrapeAll = { mode -> viewModel.scrapeAllGames(mode) },
                                    onClearMediaCache = {
                                        viewModel.viewModelScopeLaunch {
                                            viewModel.mediaCacheManager.clearMediaCache()
                                            viewModel.refreshStorageStats()
                                            viewModel.showToast("Media cache cleared")
                                        }
                                    },
                                    onRefreshStorage = viewModel::refreshStorageStats,
                                    onRequestExportBackup = {
                                        exportBackupLauncher.launch("aetherdeck_backup.zip")
                                    },
                                    onRequestImportBackup = {
                                        importBackupLauncher.launch(arrayOf("application/zip", "application/json", "*/*"))
                                    },
                                    onConfirmRestorePreview = viewModel::confirmRestorePreview,
                                    onCancelRestorePreview = viewModel::cancelRestorePreview,
                                    onUpdateAccessibility = { hc, rm, lb, cb, imm ->
                                        viewModel.viewModelScopeLaunch {
                                            viewModel.settingsRepo.updateAccessibility(hc, rm, lb, cb, imm)
                                        }
                                    },
                                    onToggleDeveloperMode = {
                                        viewModel.viewModelScopeLaunch { viewModel.settingsRepo.updateDeveloperMode(it) }
                                    },
                                    onRequestExportDiagnostics = {
                                        exportDiagnosticsLauncher.launch("aetherdeck_diagnostics.txt")
                                    },
                                    onRerunOnboarding = { forceShowOnboarding = true }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Menu Dialog
        quickMenuTarget?.let { item ->
            QuickMenuDialog(
                item = item,
                onDismiss = { quickMenuTarget = null },
                onPlay = { viewModel.launchGame(item.game.id) },
                onToggleFavorite = { viewModel.toggleFavorite(item.game.id, item.game.favorite) },
                onOpenDetails = { selectedGameIdForDetails = item.game.id },
                onScrapeGame = { viewModel.scrapeSingleGame(item.game.id) },
                onAddToCollection = {
                    val firstManual = collections.firstOrNull { !it.isSmart }
                    if (firstManual != null) {
                        viewModel.addGameToManualCollection(firstManual.id, item.game.id)
                    } else {
                        currentDest = MainDestination.COLLECTIONS
                    }
                },
                onToggleHidden = { viewModel.toggleHidden(item.game.id, item.game.hidden) }
            )
        }

        // Contextual Error Dialog (WHAT HAPPENED / WHY / HOW TO FIX / OPEN SETTINGS / TEST AGAIN)
        contextualError?.let { error ->
            ContextualErrorDialog(
                error = error,
                onDismiss = viewModel::dismissContextualError,
                onOpenSettings = { route ->
                    selectedGameIdForDetails = null
                    when (route) {
                        "providers" -> currentDest = MainDestination.SOURCES
                        "emulators" -> currentDest = MainDestination.EMULATORS
                        "help" -> {
                            activeSettingsSection = SettingsSection.HELP
                            currentDest = MainDestination.SETTINGS
                        }
                        else -> currentDest = MainDestination.SETTINGS
                    }
                },
                onRetry = {
                    selectedGameIdForDetails?.let { viewModel.launchGame(it) }
                }
            )
        }

        // Global Search Dialog
        if (showGlobalSearch) {
            GlobalSearchDialog(
                games = allGames,
                systems = systems,
                collections = collections,
                installedApps = installedApps,
                onDismiss = { showGlobalSearch = false },
                onSelectGame = { gameId -> selectedGameIdForDetails = gameId },
                onSelectSystem = { sysId ->
                    selectedGameIdForDetails = null
                    viewModel.updateFilter { it.copy(systemId = sysId) }
                    currentDest = MainDestination.LIBRARY
                },
                onSelectCollection = {
                    selectedGameIdForDetails = null
                    currentDest = MainDestination.COLLECTIONS
                },
                onSelectSettingsSection = { sec ->
                    selectedGameIdForDetails = null
                    activeSettingsSection = sec
                    currentDest = MainDestination.SETTINGS
                },
                onLaunchAndroidApp = { pkg ->
                    runCatching {
                        context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                }
            )
        }
    }
}

private fun AetherDeckViewModel.viewModelScopeLaunch(block: suspend () -> Unit) {
    kotlinx.coroutines.MainScope().launch {
        block()
    }
}
