package com.aetherdeck.core.models

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderType {
    LOCAL_ROM,
    ARLEY4D_LOCAL_FAKE_ROM,
    EMULATION_STATION,
    ARLEY4D_CATALOG,
    GENERIC_JSONL
}

@Serializable
enum class MediaType {
    COVER,
    BOX_ART,
    LOGO,
    MARQUEE,
    HERO,
    BACKGROUND,
    FANART,
    SCREENSHOT,
    THUMBNAIL,
    VIDEO,
    BOX_BACK,
    MANUAL
}

@Serializable
enum class MetadataSourcePriority(val rank: Int) {
    USER_EDITED(1),
    EXACT_CATALOG(2),
    ES_DE(3),
    RETRO_ACHIEVEMENTS(4),
    SCRAPING(5),
    FILENAME(6)
}

@Serializable
enum class ThemePreset(val displayName: String, val descriptionEs: String, val descriptionEn: String) {
    AETHER_DARK("Aether Dark", "Oscuro neutro con acento cian sutil", "Dark neutral obsidian with cyan subtle accent"),
    OLED_BLACK("OLED Black", "Negro puro de alto contraste para pantallas OLED", "True zero-luminance black with high contrast"),
    GRAPHITE("Graphite", "Gris grafito minimalista y sobrio", "Minimal dark grey industrial console aesthetic"),
    AURORA("Aurora", "Tonos atmosféricos sutiles", "Subtle atmospheric colored accents"),
    CLASSIC_CONSOLE("Classic Console", "Superficies planas y tarjetas enfocadas", "Flat surfaces with high-clarity focused cards"),
    LIGHT("Light", "Tema claro opcional para uso diurno", "Clean high-contrast daytime theme"),
    CUSTOM("Custom", "Colores, bordes y enfoque personalizados", "User-configured colors, borders, and focus");

    val description: String get() = descriptionEs
}

@Serializable
enum class InterfaceScale(val percentage: Int, val factor: Float, val label: String) {
    SCALE_75(75, 0.75f, "75%"),
    SCALE_85(85, 0.85f, "85%"),
    SCALE_100(100, 1.00f, "100%"),
    SCALE_115(115, 1.15f, "115%"),
    SCALE_125(125, 1.25f, "125%"),
    SCALE_140(140, 1.40f, "140%")
}

@Serializable
enum class DensityPreset(val labelEs: String, val labelEn: String, val spacingMultiplier: Float) {
    COMPACT("Compacta", "Compact", 0.75f),
    NORMAL("Normal", "Normal", 1.0f),
    LARGE("Amplia", "Large", 1.25f);

    val label: String get() = labelEs
}

@Serializable
enum class CardSizePreset(
    val labelEs: String,
    val labelEn: String,
    val minColumnWidthDp: Int,
    val minColumnWidthPortraitDp: Int,
    val approxColumnsLandscape: Int
) {
    SMALL("Pequeña", "Small", 112, 92, 7),
    MEDIUM("Mediana", "Medium", 140, 106, 6),
    LARGE("Grande", "Large", 172, 128, 5),
    XL("XL", "XL", 210, 156, 4);

    val label: String get() = labelEs
}

@Serializable
enum class LibraryViewMode(val labelEs: String, val labelEn: String) {
    GRID("Cuadrícula", "Grid"),
    LIST("Lista", "List"),
    COMPACT_GRID("Compacta", "Compact Grid"),
    CAROUSEL("Carrusel", "Carousel"),
    COVER_FLOW("Portadas", "Cover Flow");

    val label: String get() = labelEs
}

@Serializable
enum class SortOption(val labelEs: String, val labelEn: String) {
    TITLE("Título", "Title"),
    DATE_ADDED("Fecha añadido", "Date Added"),
    LAST_PLAYED("Última vez jugado", "Last Played"),
    PLAY_COUNT("Más jugados", "Play Count"),
    RELEASE_YEAR("Año", "Release Year"),
    RATING("Valoración", "Rating"),
    SYSTEM("Sistema", "System"),
    RANDOM("Aleatorio", "Random");

    val label: String get() = labelEs
}

@Serializable
enum class GameSelectAction(val labelEs: String, val labelEn: String) {
    ABRIR_DETALLES("Abrir detalles", "Open Details"),
    JUGAR_DIRECTAMENTE("Jugar directamente", "Play Directly")
}

@Serializable
enum class AutoCategory(val id: String, val title: String) {
    ALL_GAMES("all", "TODOS"),
    FAVORITES("favorites", "FAVORITOS"),
    RECENT("recent", "RECIENTES"),
    CONTINUE_PLAYING("continue", "CONTINUAR"),
    RECENTLY_ADDED("added", "AÑADIDOS"),
    MOST_PLAYED("most_played", "MÁS JUGADOS"),
    NEVER_PLAYED("never_played", "SIN JUGAR"),
    SYSTEMS("systems", "SISTEMAS"),
    GENRES("genres", "GÉNEROS"),
    YEARS("years", "AÑOS"),
    DEVELOPERS("developers", "DESARROLLADORES"),
    PUBLISHERS("publishers", "EDITORES"),
    FRANCHISES("franchises", "FRANQUICIAS"),
    REGIONS("regions", "REGIONES"),
    LANGUAGES("languages", "IDIOMAS"),
    MULTIPLAYER("multiplayer", "MULTIJUGADOR"),
    LOCAL("local", "LOCAL"),
    ARLEY4D("arley4d", "ARLEY4D"),
    UNKNOWN_SYSTEM("unknown_system", "SISTEMA DESCONOCIDO"),
    MISSING_ARTWORK("missing_artwork", "SIN CARÁTULA"),
    MISSING_METADATA("missing_metadata", "SIN METADATOS")
}

@Serializable
data class LibraryFilterState(
    val systemId: String? = null,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val region: String? = null,
    val language: String? = null,
    val players: String? = null,
    val providerType: ProviderType? = null,
    val favoritesOnly: Boolean = false,
    val playedStatus: PlayedFilter = PlayedFilter.ALL,
    val artworkStatus: ArtworkFilter = ArtworkFilter.ALL,
    val missingMetadataOnly: Boolean = false,
    val searchQuery: String = ""
) {
    val hasActiveFilters: Boolean
        get() = systemId != null || genre != null || releaseYear != null ||
            developer != null || publisher != null || region != null ||
            language != null || players != null || providerType != null ||
            favoritesOnly || playedStatus != PlayedFilter.ALL ||
            artworkStatus != ArtworkFilter.ALL || missingMetadataOnly
}

@Serializable
enum class PlayedFilter(val labelEs: String) {
    ALL("Todos"),
    PLAYED_ONLY("Jugados"),
    NEVER_PLAYED("Sin jugar")
}

@Serializable
enum class ArtworkFilter(val labelEs: String) {
    ALL("Todos"),
    HAS_ARTWORK("Con carátula"),
    MISSING_ARTWORK("Sin carátula")
}

@Serializable
enum class HomeWidgetType(val id: String, val title: String) {
    CONTINUE_PLAYING("continue_playing", "Continuar jugando"),
    RECENTLY_PLAYED("recently_played", "Recientes"),
    FAVORITES("favorites", "Favoritos"),
    RECENTLY_ADDED("recently_added", "Añadidos recientemente"),
    SYSTEMS("systems", "Sistemas"),
    COLLECTIONS("collections", "Colecciones"),
    RANDOM_GAME("random_game", "Juego aleatorio"),
    UNFINISHED_GAMES("unfinished_games", "Pendientes"),
    MOST_PLAYED("most_played", "Más jugados")
}

@Serializable
data class HomeWidgetConfig(
    val type: HomeWidgetType,
    val enabled: Boolean = true,
    val order: Int,
    val largeSize: Boolean = false
)

@Serializable
data class ContextualError(
    val title: String,
    val whatHappened: String,
    val whyItHappened: String,
    val howToFix: String,
    val settingsRoute: String? = null,
    val canRetry: Boolean = true,
    val isMissingRetroArchCore: Boolean = false,
    val gameIdForCoreChange: String? = null
)

@Serializable
data class PreLaunchCheckResult(
    val gameAvailable: Boolean,
    val sourceReadable: Boolean,
    val emulatorInstalled: Boolean,
    val activityValid: Boolean,
    val coreConfigured: Boolean,
    val coreVerified: Boolean,
    val permissionAvailable: Boolean,
    val resolvedPackage: String?,
    val resolvedActivity: String?,
    val resolvedCore: String?,
    val resolvedUri: String?,
    val error: ContextualError? = null
) {
    val canLaunch: Boolean
        get() = gameAvailable && sourceReadable && emulatorInstalled && activityValid && coreConfigured && permissionAvailable && error == null
}

@Serializable
enum class BackgroundTaskType(val displayTitleEs: String, val displayTitleEn: String) {
    SCAN("Escaneando", "Scanning"),
    SCRAPE("Scrape", "Scraping"),
    CATALOG_SYNC("Catálogo", "Catalog Sync"),
    MEDIA("Media", "Media Download"),
    HASH("Verificación Hash", "Hashing"),
    BACKUP("Copia de seguridad", "Backup"),
    ARLEY_HANDOFF("Arley4d", "Arley4d Handoff")
}

@Serializable
enum class BackgroundTaskStatus(val labelEs: String, val labelEn: String) {
    QUEUED("En cola", "Queued"),
    RUNNING("En curso", "Running"),
    PAUSED("Pausado", "Paused"),
    COMPLETED("Completado", "Completed"),
    FAILED("Error", "Failed"),
    CANCELLED("Cancelado", "Cancelled");

    val isActive: Boolean
        get() = this == QUEUED || this == RUNNING || this == PAUSED
}

@Serializable
enum class ScrapePhase(val labelEs: String, val labelEn: String) {
    SEARCHING("Buscando coincidencia...", "Searching match..."),
    MATCHING("Verificando metadatos...", "Matching metadata..."),
    METADATA("Descargando metadatos...", "Fetching metadata..."),
    COVER("Descargando carátula...", "Downloading cover..."),
    LOGO("Descargando logo...", "Downloading logo..."),
    BACKGROUND("Descargando fondo...", "Downloading background..."),
    FANART("Descargando fanart...", "Downloading fanart..."),
    VIDEO("Verificando vídeo...", "Checking video..."),
    SAVING("Guardando metadatos...", "Saving metadata..."),
    DONE("Completado", "Completed"),
    FAILED("Error al obtener datos", "Failed")
}

@Serializable
data class ScrapeProgressState(
    val total: Int = 0,
    val processed: Int = 0,
    val successful: Int = 0,
    val failed: Int = 0,
    val currentGameId: String = "",
    val currentGameTitle: String = "",
    val currentProvider: String = "",
    val currentPhase: ScrapePhase = ScrapePhase.SEARCHING,
    val currentMediaType: MediaType? = null
)

@Serializable
data class LastLaunchContext(
    val route: String = "home",
    val gameId: String? = null,
    val variantId: String? = null,
    val systemId: String? = null,
    val collectionId: String? = null,
    val libraryQuery: String = "",
    val filters: LibraryFilterState = LibraryFilterState(),
    val sort: SortOption = SortOption.TITLE,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val focusedGameId: String? = null,
    val launchedAppPackage: String? = null,
    val appsScrollIndex: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

// Backwards-compatible alias for tests/components
typealias TaskType = BackgroundTaskType

@Serializable
data class BackgroundTaskState(
    val id: String,
    val type: BackgroundTaskType,
    val title: String,
    val currentItem: String = "",
    val currentPhase: String = "",
    val processed: Int = 0,
    val total: Int = 0,
    val successful: Int = 0,
    val failed: Int = 0,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val metadataDone: Boolean = false,
    val coverDone: Boolean = false,
    val logoDone: Boolean = false,
    val backgroundDone: Boolean = false,
    val error: String? = null
)
