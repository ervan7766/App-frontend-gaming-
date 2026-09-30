package com.aetherdeck.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aetherdeck.core.models.CardSizePreset
import com.aetherdeck.core.models.DensityPreset
import com.aetherdeck.core.models.GameSelectAction
import com.aetherdeck.core.models.HomeWidgetConfig
import com.aetherdeck.core.models.HomeWidgetType
import com.aetherdeck.core.models.InterfaceScale
import com.aetherdeck.core.models.LastLaunchContext
import com.aetherdeck.core.models.LibraryViewMode
import com.aetherdeck.core.models.ThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.aetherDataStore: DataStore<Preferences> by preferencesDataStore(name = "aetherdeck_settings")

data class AetherSettingsState(
    val onboardingCompleted: Boolean = false,
    // General
    val startupPage: String = "HOME",
    val language: String = "Español",
    val autoRescanOnStartup: Boolean = false,
    val defaultLibraryView: LibraryViewMode = LibraryViewMode.GRID,
    val gameSelectAction: GameSelectAction = GameSelectAction.ABRIR_DETALLES,
    val confirmBeforeExit: Boolean = true,
    val keepScreenAwakeOnLaunch: Boolean = true,
    val immersiveMode: Boolean = false,
    val selectedRetroArchPackage: String = "",
    // Appearance & Themes
    val themePreset: ThemePreset = ThemePreset.AETHER_DARK,
    val customAccentHex: String = "#00E5FF",
    val customBackgroundHex: String = "#0A0E17",
    val customSurfaceHex: String = "#121826",
    val backgroundStrength: Float = 0.85f,
    val blurRadiusDp: Int = 12,
    val cardTransparency: Float = 0.95f,
    val cornerRadiusDp: Int = 10,
    val cardBorderWidthDp: Float = 1.5f,
    val focusGlowIntensity: Float = 0.5f,
    val animationIntensity: Float = 1.0f,
    val artworkSaturation: Float = 1.0f,
    val backgroundDim: Float = 0.65f,
    // Scale & Accessibility
    val interfaceScale: InterfaceScale = InterfaceScale.SCALE_100,
    val densityPreset: DensityPreset = DensityPreset.NORMAL,
    val cardSizePreset: CardSizePreset = CardSizePreset.MEDIUM,
    val textScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val screenReaderLabels: Boolean = true,
    val largerFocusBorders: Boolean = false,
    val colorBlindSafeFocus: Boolean = false,
    // Library
    val regionPriorityCsv: String = "Europe,USA,World,Spain,Japan",
    val languagePriorityCsv: String = "Es,En,Ja,Fr,De",
    val autoGroupVariants: Boolean = true,
    val groupMultiDisc: Boolean = true,
    val hideDuplicates: Boolean = true,
    val showVariantsBadge: Boolean = false,
    val cleanTitles: Boolean = true,
    val unknownSystemBehavior: String = "Mover a Sistema Desconocido",
    // Input / Controller
    val deadzone: Float = 0.18f,
    val stickSensitivity: Float = 1.0f,
    val repeatDelayMs: Int = 320,
    val repeatSpeedMs: Int = 85,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val buttonMappingsJson: String = "",
    // Scraper & Metadata
    val scraperProviderPriorityCsv: String = "ScreenScraper,TheGamesDB,SteamGridDB,RetroAchievements",
    val screenScraperUser: String = "",
    val screenScraperPass: String = "",
    val screenScraperApiKey: String = "",
    val steamGridDbApiKey: String = "",
    val theGamesDbApiKey: String = "",
    val retroAchievementsUser: String = "",
    val retroAchievementsApiKey: String = "",
    val preferredMediaTypesCsv: String = "COVER,LOGO,HERO,BACKGROUND,SCREENSHOT",
    val imageQuality: String = "Alta (Original)",
    val videosEnabled: Boolean = false,
    val autoScrapeNewGames: Boolean = false,
    val wifiOnlyScraping: Boolean = true,
    // Media & Cache
    val maxCacheSizeMb: Int = 1024,
    val mediaStorageLocation: String = "Cache interna de la aplicación",
    val preCacheCovers: Boolean = true,
    val preCacheHeroes: Boolean = false,
    val videosAutoPreview: Boolean = false,
    val muteVideoPreview: Boolean = true,
    // Developer & Diagnostics
    val developerModeEnabled: Boolean = false,
    val lastScanTimestamp: Long = 0L,
    val lastCatalogUpdateTimestamp: Long = 0L,
    // Custom Home Layout
    val homeWidgetsJson: String = "",
    // Android Apps Launcher management
    val favoriteAndroidPackagesCsv: String = "",
    val homeAndroidPackagesCsv: String = "",
    val hiddenAndroidPackagesCsv: String = "",
    // Persistent Navigation & Return Target
    val lastLaunchContextJson: String = ""
) {
    val isEnglish: Boolean
        get() = language.equals("English", ignoreCase = true)

    val regionPriorityList: List<String>
        get() = regionPriorityCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val languagePriorityList: List<String>
        get() = languagePriorityCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val favoriteAppsSet: Set<String>
        get() = favoriteAndroidPackagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val homeAppsSet: Set<String>
        get() = homeAndroidPackagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val hiddenAppsSet: Set<String>
        get() = hiddenAndroidPackagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val lastLaunchContext: LastLaunchContext?
        get() {
            if (lastLaunchContextJson.isBlank()) return null
            return try {
                Json.decodeFromString<LastLaunchContext>(lastLaunchContextJson)
            } catch (_: Exception) {
                null
            }
        }

    val homeWidgets: List<HomeWidgetConfig>
        get() {
            if (homeWidgetsJson.isNotBlank()) {
                return try {
                    Json.decodeFromString<List<HomeWidgetConfig>>(homeWidgetsJson)
                        .sortedBy { it.order }
                } catch (_: Exception) {
                    defaultHomeWidgets()
                }
            }
            return defaultHomeWidgets()
        }

    companion object {
        fun defaultHomeWidgets(): List<HomeWidgetConfig> = listOf(
            HomeWidgetConfig(HomeWidgetType.CONTINUE_PLAYING, enabled = true, order = 0, largeSize = true),
            HomeWidgetConfig(HomeWidgetType.RECENTLY_PLAYED, enabled = true, order = 1),
            HomeWidgetConfig(HomeWidgetType.FAVORITES, enabled = true, order = 2),
            HomeWidgetConfig(HomeWidgetType.COLLECTIONS, enabled = true, order = 3),
            HomeWidgetConfig(HomeWidgetType.SYSTEMS, enabled = true, order = 4),
            HomeWidgetConfig(HomeWidgetType.RECENTLY_ADDED, enabled = true, order = 5),
            HomeWidgetConfig(HomeWidgetType.MOST_PLAYED, enabled = true, order = 6),
            HomeWidgetConfig(HomeWidgetType.UNFINISHED_GAMES, enabled = true, order = 7),
            HomeWidgetConfig(HomeWidgetType.RANDOM_GAME, enabled = true, order = 8)
        )
    }
}

class AetherSettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val STARTUP_PAGE = stringPreferencesKey("startup_page")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_RESCAN = booleanPreferencesKey("auto_rescan")
        val DEFAULT_VIEW = stringPreferencesKey("default_view")
        val GAME_SELECT_ACTION = stringPreferencesKey("game_select_action")
        val CONFIRM_EXIT = booleanPreferencesKey("confirm_exit")
        val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
        val IMMERSIVE_MODE = booleanPreferencesKey("immersive_mode")
        val SELECTED_RETROARCH_PKG = stringPreferencesKey("selected_retroarch_pkg")

        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val CUSTOM_ACCENT = stringPreferencesKey("custom_accent")
        val CUSTOM_BG = stringPreferencesKey("custom_bg")
        val CUSTOM_SURFACE = stringPreferencesKey("custom_surface")
        val BG_STRENGTH = floatPreferencesKey("bg_strength")
        val BLUR_RADIUS = intPreferencesKey("blur_radius")
        val CARD_TRANSPARENCY = floatPreferencesKey("card_transparency")
        val CORNER_RADIUS = intPreferencesKey("corner_radius")
        val CARD_BORDER_WIDTH = floatPreferencesKey("card_border_width")
        val FOCUS_GLOW = floatPreferencesKey("focus_glow")
        val ANIMATION_INTENSITY = floatPreferencesKey("animation_intensity")
        val ARTWORK_SATURATION = floatPreferencesKey("artwork_saturation")
        val BG_DIM = floatPreferencesKey("bg_dim")

        val INTERFACE_SCALE = stringPreferencesKey("interface_scale")
        val DENSITY_PRESET = stringPreferencesKey("density_preset")
        val CARD_SIZE_PRESET = stringPreferencesKey("card_size_preset")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val SCREEN_READER = booleanPreferencesKey("screen_reader")
        val LARGER_BORDERS = booleanPreferencesKey("larger_borders")
        val COLOR_BLIND_FOCUS = booleanPreferencesKey("color_blind_focus")

        val REGION_PRIORITY = stringPreferencesKey("region_priority")
        val LANGUAGE_PRIORITY = stringPreferencesKey("language_priority")
        val AUTO_GROUP = booleanPreferencesKey("auto_group")
        val GROUP_MULTI_DISC = booleanPreferencesKey("group_multi_disc")
        val HIDE_DUPLICATES = booleanPreferencesKey("hide_duplicates")
        val SHOW_VARIANTS = booleanPreferencesKey("show_variants")
        val CLEAN_TITLES = booleanPreferencesKey("clean_titles")
        val UNKNOWN_SYSTEM_BEHAVIOR = stringPreferencesKey("unknown_system_behavior")

        val DEADZONE = floatPreferencesKey("deadzone")
        val STICK_SENSITIVITY = floatPreferencesKey("stick_sensitivity")
        val REPEAT_DELAY = intPreferencesKey("repeat_delay")
        val REPEAT_SPEED = intPreferencesKey("repeat_speed")
        val SWAP_AB = booleanPreferencesKey("swap_ab")
        val SWAP_XY = booleanPreferencesKey("swap_xy")
        val VIBRATION = booleanPreferencesKey("vibration")
        val BUTTON_MAPPINGS = stringPreferencesKey("button_mappings")

        val SCRAPER_PRIORITY = stringPreferencesKey("scraper_priority")
        val SS_USER = stringPreferencesKey("ss_user")
        val SS_PASS = stringPreferencesKey("ss_pass")
        val SS_API_KEY = stringPreferencesKey("ss_api_key")
        val SGDB_API_KEY = stringPreferencesKey("sgdb_api_key")
        val TGDB_API_KEY = stringPreferencesKey("tgdb_api_key")
        val RA_USER = stringPreferencesKey("ra_user")
        val RA_API_KEY = stringPreferencesKey("ra_api_key")
        val PREFERRED_MEDIA_TYPES = stringPreferencesKey("preferred_media_types")
        val IMAGE_QUALITY = stringPreferencesKey("image_quality")
        val VIDEOS_ENABLED = booleanPreferencesKey("videos_enabled")
        val AUTO_SCRAPE = booleanPreferencesKey("auto_scrape")
        val WIFI_ONLY_SCRAPE = booleanPreferencesKey("wifi_only_scrape")

        val MAX_CACHE_MB = intPreferencesKey("max_cache_mb")
        val MEDIA_STORAGE_LOC = stringPreferencesKey("media_storage_loc")
        val PRE_CACHE_COVERS = booleanPreferencesKey("pre_cache_covers")
        val PRE_CACHE_HEROES = booleanPreferencesKey("pre_cache_heroes")
        val VIDEOS_AUTO_PREVIEW = booleanPreferencesKey("videos_auto_preview")
        val MUTE_PREVIEW = booleanPreferencesKey("mute_preview")

        val DEV_MODE = booleanPreferencesKey("dev_mode")
        val LAST_SCAN_TS = longPreferencesKey("last_scan_ts")
        val LAST_CATALOG_TS = longPreferencesKey("last_catalog_ts")
        val HOME_WIDGETS = stringPreferencesKey("home_widgets")
        val FAVORITE_APPS = stringPreferencesKey("favorite_apps")
        val HOME_APPS = stringPreferencesKey("home_apps")
        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val LAST_LAUNCH_CONTEXT = stringPreferencesKey("last_launch_context")
    }

    val settingsFlow: Flow<AetherSettingsState> = context.aetherDataStore.data.map { prefs ->
        AetherSettingsState(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            startupPage = prefs[Keys.STARTUP_PAGE] ?: "HOME",
            language = prefs[Keys.LANGUAGE] ?: "Español",
            autoRescanOnStartup = prefs[Keys.AUTO_RESCAN] ?: false,
            defaultLibraryView = prefs[Keys.DEFAULT_VIEW]?.let { runCatching { LibraryViewMode.valueOf(it) }.getOrNull() } ?: LibraryViewMode.GRID,
            gameSelectAction = prefs[Keys.GAME_SELECT_ACTION]?.let { runCatching { GameSelectAction.valueOf(it) }.getOrNull() } ?: GameSelectAction.ABRIR_DETALLES,
            confirmBeforeExit = prefs[Keys.CONFIRM_EXIT] ?: true,
            keepScreenAwakeOnLaunch = prefs[Keys.KEEP_AWAKE] ?: true,
            immersiveMode = prefs[Keys.IMMERSIVE_MODE] ?: false,
            selectedRetroArchPackage = prefs[Keys.SELECTED_RETROARCH_PKG] ?: "",
            themePreset = prefs[Keys.THEME_PRESET]?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() } ?: ThemePreset.AETHER_DARK,
            customAccentHex = prefs[Keys.CUSTOM_ACCENT] ?: "#00E5FF",
            customBackgroundHex = prefs[Keys.CUSTOM_BG] ?: "#0A0E17",
            customSurfaceHex = prefs[Keys.CUSTOM_SURFACE] ?: "#121826",
            backgroundStrength = prefs[Keys.BG_STRENGTH] ?: 0.85f,
            blurRadiusDp = prefs[Keys.BLUR_RADIUS] ?: 12,
            cardTransparency = prefs[Keys.CARD_TRANSPARENCY] ?: 0.95f,
            cornerRadiusDp = prefs[Keys.CORNER_RADIUS] ?: 10,
            cardBorderWidthDp = prefs[Keys.CARD_BORDER_WIDTH] ?: 1.5f,
            focusGlowIntensity = prefs[Keys.FOCUS_GLOW] ?: 0.5f,
            animationIntensity = prefs[Keys.ANIMATION_INTENSITY] ?: 1.0f,
            artworkSaturation = prefs[Keys.ARTWORK_SATURATION] ?: 1.0f,
            backgroundDim = prefs[Keys.BG_DIM] ?: 0.65f,
            interfaceScale = prefs[Keys.INTERFACE_SCALE]?.let { runCatching { InterfaceScale.valueOf(it) }.getOrNull() } ?: InterfaceScale.SCALE_100,
            densityPreset = prefs[Keys.DENSITY_PRESET]?.let { runCatching { DensityPreset.valueOf(it) }.getOrNull() } ?: DensityPreset.NORMAL,
            cardSizePreset = prefs[Keys.CARD_SIZE_PRESET]?.let { runCatching { CardSizePreset.valueOf(it) }.getOrNull() } ?: CardSizePreset.MEDIUM,
            textScale = prefs[Keys.TEXT_SCALE] ?: 1.0f,
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            screenReaderLabels = prefs[Keys.SCREEN_READER] ?: true,
            largerFocusBorders = prefs[Keys.LARGER_BORDERS] ?: false,
            colorBlindSafeFocus = prefs[Keys.COLOR_BLIND_FOCUS] ?: false,
            regionPriorityCsv = prefs[Keys.REGION_PRIORITY] ?: "Europe,USA,World,Spain,Japan",
            languagePriorityCsv = prefs[Keys.LANGUAGE_PRIORITY] ?: "Es,En,Ja,Fr,De",
            autoGroupVariants = prefs[Keys.AUTO_GROUP] ?: true,
            groupMultiDisc = prefs[Keys.GROUP_MULTI_DISC] ?: true,
            hideDuplicates = prefs[Keys.HIDE_DUPLICATES] ?: true,
            showVariantsBadge = prefs[Keys.SHOW_VARIANTS] ?: false,
            cleanTitles = prefs[Keys.CLEAN_TITLES] ?: true,
            unknownSystemBehavior = prefs[Keys.UNKNOWN_SYSTEM_BEHAVIOR] ?: "Mover a Sistema Desconocido",
            deadzone = prefs[Keys.DEADZONE] ?: 0.18f,
            stickSensitivity = prefs[Keys.STICK_SENSITIVITY] ?: 1.0f,
            repeatDelayMs = prefs[Keys.REPEAT_DELAY] ?: 320,
            repeatSpeedMs = prefs[Keys.REPEAT_SPEED] ?: 85,
            swapAB = prefs[Keys.SWAP_AB] ?: false,
            swapXY = prefs[Keys.SWAP_XY] ?: false,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            buttonMappingsJson = prefs[Keys.BUTTON_MAPPINGS] ?: "",
            scraperProviderPriorityCsv = prefs[Keys.SCRAPER_PRIORITY] ?: "ScreenScraper,TheGamesDB,SteamGridDB,RetroAchievements",
            screenScraperUser = prefs[Keys.SS_USER] ?: "",
            screenScraperPass = prefs[Keys.SS_PASS] ?: "",
            screenScraperApiKey = prefs[Keys.SS_API_KEY] ?: "",
            steamGridDbApiKey = prefs[Keys.SGDB_API_KEY] ?: "",
            theGamesDbApiKey = prefs[Keys.TGDB_API_KEY] ?: "",
            retroAchievementsUser = prefs[Keys.RA_USER] ?: "",
            retroAchievementsApiKey = prefs[Keys.RA_API_KEY] ?: "",
            preferredMediaTypesCsv = prefs[Keys.PREFERRED_MEDIA_TYPES] ?: "COVER,LOGO,HERO,BACKGROUND,SCREENSHOT",
            imageQuality = prefs[Keys.IMAGE_QUALITY] ?: "Alta (Original)",
            videosEnabled = prefs[Keys.VIDEOS_ENABLED] ?: false,
            autoScrapeNewGames = prefs[Keys.AUTO_SCRAPE] ?: false,
            wifiOnlyScraping = prefs[Keys.WIFI_ONLY_SCRAPE] ?: true,
            maxCacheSizeMb = prefs[Keys.MAX_CACHE_MB] ?: 1024,
            mediaStorageLocation = prefs[Keys.MEDIA_STORAGE_LOC] ?: "Cache interna de la aplicación",
            preCacheCovers = prefs[Keys.PRE_CACHE_COVERS] ?: true,
            preCacheHeroes = prefs[Keys.PRE_CACHE_HEROES] ?: false,
            videosAutoPreview = prefs[Keys.VIDEOS_AUTO_PREVIEW] ?: false,
            muteVideoPreview = prefs[Keys.MUTE_PREVIEW] ?: true,
            developerModeEnabled = prefs[Keys.DEV_MODE] ?: false,
            lastScanTimestamp = prefs[Keys.LAST_SCAN_TS] ?: 0L,
            lastCatalogUpdateTimestamp = prefs[Keys.LAST_CATALOG_TS] ?: 0L,
            homeWidgetsJson = prefs[Keys.HOME_WIDGETS] ?: "",
            favoriteAndroidPackagesCsv = prefs[Keys.FAVORITE_APPS] ?: "",
            homeAndroidPackagesCsv = prefs[Keys.HOME_APPS] ?: "",
            hiddenAndroidPackagesCsv = prefs[Keys.HIDDEN_APPS] ?: "",
            lastLaunchContextJson = prefs[Keys.LAST_LAUNCH_CONTEXT] ?: ""
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.aetherDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun updateLanguage(lang: String) {
        context.aetherDataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun updateGameSelectAction(action: GameSelectAction) {
        context.aetherDataStore.edit { it[Keys.GAME_SELECT_ACTION] = action.name }
    }

    suspend fun updateSelectedRetroArchPackage(pkg: String) {
        context.aetherDataStore.edit { it[Keys.SELECTED_RETROARCH_PKG] = pkg }
    }

    suspend fun saveLastLaunchContext(ctx: LastLaunchContext?) {
        val encoded = if (ctx != null) json.encodeToString(ctx) else ""
        context.aetherDataStore.edit { it[Keys.LAST_LAUNCH_CONTEXT] = encoded }
    }

    suspend fun updateTheme(preset: ThemePreset) {
        context.aetherDataStore.edit { it[Keys.THEME_PRESET] = preset.name }
    }

    suspend fun updateCustomThemeColors(accentHex: String, bgHex: String, surfaceHex: String) {
        context.aetherDataStore.edit {
            it[Keys.CUSTOM_ACCENT] = accentHex
            it[Keys.CUSTOM_BG] = bgHex
            it[Keys.CUSTOM_SURFACE] = surfaceHex
        }
    }

    suspend fun updateThemeAppearance(
        cornerRadiusDp: Int? = null,
        focusGlow: Float? = null,
        backgroundDim: Float? = null,
        artworkSaturation: Float? = null,
        cardBorderWidth: Float? = null,
        blurRadiusDp: Int? = null,
        backgroundStrength: Float? = null,
        animationIntensity: Float? = null
    ) {
        context.aetherDataStore.edit { prefs ->
            cornerRadiusDp?.let { prefs[Keys.CORNER_RADIUS] = it }
            focusGlow?.let { prefs[Keys.FOCUS_GLOW] = it }
            backgroundDim?.let { prefs[Keys.BG_DIM] = it }
            artworkSaturation?.let { prefs[Keys.ARTWORK_SATURATION] = it }
            cardBorderWidth?.let { prefs[Keys.CARD_BORDER_WIDTH] = it }
            blurRadiusDp?.let { prefs[Keys.BLUR_RADIUS] = it }
            backgroundStrength?.let { prefs[Keys.BG_STRENGTH] = it }
            animationIntensity?.let { prefs[Keys.ANIMATION_INTENSITY] = it }
        }
    }

    suspend fun updateInterfaceScale(scale: InterfaceScale) {
        context.aetherDataStore.edit { it[Keys.INTERFACE_SCALE] = scale.name }
    }

    suspend fun updateDensityPreset(density: DensityPreset) {
        context.aetherDataStore.edit { it[Keys.DENSITY_PRESET] = density.name }
    }

    suspend fun updateCardSizePreset(cardSize: CardSizePreset) {
        context.aetherDataStore.edit { it[Keys.CARD_SIZE_PRESET] = cardSize.name }
    }

    suspend fun updateTextScale(scale: Float) {
        context.aetherDataStore.edit { it[Keys.TEXT_SCALE] = scale.coerceIn(0.8f, 1.5f) }
    }

    suspend fun updateLibraryViewMode(mode: LibraryViewMode) {
        context.aetherDataStore.edit { it[Keys.DEFAULT_VIEW] = mode.name }
    }

    suspend fun updateAccessibility(
        highContrast: Boolean? = null,
        reducedMotion: Boolean? = null,
        largerBorders: Boolean? = null,
        colorBlindFocus: Boolean? = null,
        immersiveMode: Boolean? = null
    ) {
        context.aetherDataStore.edit { prefs ->
            highContrast?.let { prefs[Keys.HIGH_CONTRAST] = it }
            reducedMotion?.let { prefs[Keys.REDUCED_MOTION] = it }
            largerBorders?.let { prefs[Keys.LARGER_BORDERS] = it }
            colorBlindFocus?.let { prefs[Keys.COLOR_BLIND_FOCUS] = it }
            immersiveMode?.let { prefs[Keys.IMMERSIVE_MODE] = it }
        }
    }

    suspend fun updateControllerSettings(
        deadzone: Float? = null,
        stickSensitivity: Float? = null,
        repeatDelayMs: Int? = null,
        repeatSpeedMs: Int? = null,
        swapAB: Boolean? = null,
        swapXY: Boolean? = null,
        vibrationEnabled: Boolean? = null,
        buttonMappingsJson: String? = null
    ) {
        context.aetherDataStore.edit { prefs ->
            deadzone?.let { prefs[Keys.DEADZONE] = it }
            stickSensitivity?.let { prefs[Keys.STICK_SENSITIVITY] = it }
            repeatDelayMs?.let { prefs[Keys.REPEAT_DELAY] = it }
            repeatSpeedMs?.let { prefs[Keys.REPEAT_SPEED] = it }
            swapAB?.let { prefs[Keys.SWAP_AB] = it }
            swapXY?.let { prefs[Keys.SWAP_XY] = it }
            vibrationEnabled?.let { prefs[Keys.VIBRATION] = it }
            buttonMappingsJson?.let { prefs[Keys.BUTTON_MAPPINGS] = it }
        }
    }

    suspend fun updateLibraryPreferences(
        regionPriorityCsv: String? = null,
        languagePriorityCsv: String? = null,
        autoGroup: Boolean? = null,
        groupMultiDisc: Boolean? = null,
        hideDuplicates: Boolean? = null,
        showVariantsBadge: Boolean? = null
    ) {
        context.aetherDataStore.edit { prefs ->
            regionPriorityCsv?.let { prefs[Keys.REGION_PRIORITY] = it }
            languagePriorityCsv?.let { prefs[Keys.LANGUAGE_PRIORITY] = it }
            autoGroup?.let { prefs[Keys.AUTO_GROUP] = it }
            groupMultiDisc?.let { prefs[Keys.GROUP_MULTI_DISC] = it }
            hideDuplicates?.let { prefs[Keys.HIDE_DUPLICATES] = it }
            showVariantsBadge?.let { prefs[Keys.SHOW_VARIANTS] = it }
        }
    }

    suspend fun updateScraperCredentials(
        ssUser: String? = null,
        ssPass: String? = null,
        ssApiKey: String? = null,
        sgdbApiKey: String? = null,
        tgdbApiKey: String? = null,
        raUser: String? = null,
        raApiKey: String? = null,
        autoScrape: Boolean? = null,
        wifiOnly: Boolean? = null
    ) {
        context.aetherDataStore.edit { prefs ->
            ssUser?.let { prefs[Keys.SS_USER] = it }
            ssPass?.let { prefs[Keys.SS_PASS] = it }
            ssApiKey?.let { prefs[Keys.SS_API_KEY] = it }
            sgdbApiKey?.let { prefs[Keys.SGDB_API_KEY] = it }
            tgdbApiKey?.let { prefs[Keys.TGDB_API_KEY] = it }
            raUser?.let { prefs[Keys.RA_USER] = it }
            raApiKey?.let { prefs[Keys.RA_API_KEY] = it }
            autoScrape?.let { prefs[Keys.AUTO_SCRAPE] = it }
            wifiOnly?.let { prefs[Keys.WIFI_ONLY_SCRAPE] = it }
        }
    }

    suspend fun updateDeveloperMode(enabled: Boolean) {
        context.aetherDataStore.edit { it[Keys.DEV_MODE] = enabled }
    }

    suspend fun recordScanTimestamp(timestamp: Long = System.currentTimeMillis()) {
        context.aetherDataStore.edit { it[Keys.LAST_SCAN_TS] = timestamp }
    }

    suspend fun recordCatalogTimestamp(timestamp: Long = System.currentTimeMillis()) {
        context.aetherDataStore.edit { it[Keys.LAST_CATALOG_TS] = timestamp }
    }

    suspend fun updateHomeWidgets(widgets: List<HomeWidgetConfig>) {
        val encoded = json.encodeToString(widgets)
        context.aetherDataStore.edit { it[Keys.HOME_WIDGETS] = encoded }
    }

    suspend fun toggleFavoriteAndroidApp(packageName: String) {
        togglePackageInSet(Keys.FAVORITE_APPS, packageName)
    }

    suspend fun toggleHomeAndroidApp(packageName: String) {
        togglePackageInSet(Keys.HOME_APPS, packageName)
    }

    suspend fun toggleHiddenAndroidApp(packageName: String) {
        togglePackageInSet(Keys.HIDDEN_APPS, packageName)
    }

    private suspend fun togglePackageInSet(key: Preferences.Key<String>, packageName: String) {
        context.aetherDataStore.edit { prefs ->
            val current = (prefs[key] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            prefs[key] = current.joinToString(",")
        }
    }
}
