package com.aetherdeck.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aetherdeck.core.database.CanonicalGameWithDetails
import com.aetherdeck.core.database.LibrarySourceEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.models.CardSizePreset
import com.aetherdeck.core.models.DensityPreset
import com.aetherdeck.core.models.InterfaceScale
import com.aetherdeck.core.models.LibraryViewMode
import com.aetherdeck.core.models.ThemePreset
import com.aetherdeck.core.settings.AetherSettingsState
import com.aetherdeck.core.utils.BackupPreview
import com.aetherdeck.emulators.detection.DetectedEmulatorStatus
import com.aetherdeck.input.gamepad.ControllerAction
import com.aetherdeck.input.gamepad.LiveControllerTelemetry
import com.aetherdeck.media.cache.MediaCacheManager
import com.aetherdeck.media.cache.StorageBreakdown
import com.aetherdeck.metadata.scraper.ScrapeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsSection(val title: String) {
    GENERAL("GENERAL"),
    APPEARANCE("APPEARANCE"),
    LIBRARY("LIBRARY"),
    INPUT("INPUT / CONTROLLER"),
    METADATA_SCRAPING("METADATA & SCRAPING"),
    MEDIA_STORAGE("MEDIA & STORAGE"),
    BACKUP("BACKUP & SAVES"),
    ACCESSIBILITY("ACCESSIBILITY"),
    HELP("HELP CENTER"),
    INFORMATION("INFORMATION"),
    ABOUT("ABOUT"),
    DEVELOPER("DEVELOPER DIAGNOSTICS")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MasterSettingsScreen(
    settings: AetherSettingsState,
    controllerTelemetry: LiveControllerTelemetry,
    remappingTarget: ControllerAction?,
    storageBreakdown: StorageBreakdown?,
    pendingBackupPreview: BackupPreview?,
    games: List<CanonicalGameWithDetails>,
    systems: List<SystemEntity>,
    librarySources: List<LibrarySourceEntity>,
    detectedEmulators: List<DetectedEmulatorStatus>,
    initialSection: SettingsSection = SettingsSection.GENERAL,
    onSelectTheme: (ThemePreset) -> Unit,
    onUpdateCustomThemeColors: (String, String, String) -> Unit,
    onUpdateThemeAppearance: (Int?, Float?, Float?, Float?, Float?, Int?) -> Unit,
    onSelectScale: (InterfaceScale) -> Unit,
    onSelectDensity: (DensityPreset) -> Unit,
    onSelectCardSize: (CardSizePreset) -> Unit,
    onUpdateTextScale: (Float) -> Unit,
    onSelectLibraryView: (LibraryViewMode) -> Unit,
    onUpdateLibraryPrefs: (String?, String?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onUpdateControllerSettings: (Float?, Float?, Int?, Int?, Boolean?, Boolean?, Boolean?) -> Unit,
    onStartRemapAction: (ControllerAction?) -> Unit,
    onResetControllerBindings: () -> Unit,
    onUpdateScraperKeys: (String?, String?, String?, String?, String?, Boolean?, Boolean?) -> Unit,
    onTriggerScrapeAll: (ScrapeMode) -> Unit,
    onClearMediaCache: () -> Unit,
    onRefreshStorage: () -> Unit,
    onRequestExportBackup: () -> Unit,
    onRequestImportBackup: () -> Unit,
    onConfirmRestorePreview: () -> Unit,
    onCancelRestorePreview: () -> Unit,
    onUpdateAccessibility: (Boolean?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onToggleDeveloperMode: (Boolean) -> Unit,
    onRequestExportDiagnostics: () -> Unit,
    onRerunOnboarding: () -> Unit
) {
    var activeSection by remember(initialSection) { mutableStateOf(initialSection) }
    var showConfirmReplaceScrape by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        // Left Section List
        LazyColumn(
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(SettingsSection.entries) { section ->
                if (section == SettingsSection.DEVELOPER && !settings.developerModeEnabled) {
                    // Hidden unless enabled in General/About or Developer toggle
                    return@items
                }
                val selected = activeSection == section
                Surface(
                    onClick = { activeSection = section },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Right Detail Pane
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = activeSection.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            when (activeSection) {
                SettingsSection.GENERAL -> {
                    item {
                        SettingsCard {
                            Text("Default Library View Mode", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LibraryViewMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.defaultLibraryView == mode,
                                        onClick = { onSelectLibraryView(mode) },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Immersive Fullscreen Mode")
                                Switch(
                                    checked = settings.immersiveMode,
                                    onCheckedChange = { onUpdateAccessibility(null, null, null, null, it) }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Enable Developer Diagnostics Section")
                                Switch(
                                    checked = settings.developerModeEnabled,
                                    onCheckedChange = onToggleDeveloperMode
                                )
                            }
                            OutlinedButton(onClick = onRerunOnboarding) {
                                Text("RE-RUN FIRST-RUN SETUP WIZARD")
                            }
                        }
                    }
                }

                SettingsSection.APPEARANCE -> {
                    item {
                        SettingsCard {
                            Text("Built-In Themes & Custom Theme", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemePreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = settings.themePreset == preset,
                                        onClick = { onSelectTheme(preset) },
                                        label = { Text(preset.displayName) }
                                    )
                                }
                            }
                            Text(
                                text = settings.themePreset.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (settings.themePreset == ThemePreset.CUSTOM) {
                                var accent by remember(settings.customAccentHex) { mutableStateOf(settings.customAccentHex) }
                                var bg by remember(settings.customBackgroundHex) { mutableStateOf(settings.customBackgroundHex) }
                                var surf by remember(settings.customSurfaceHex) { mutableStateOf(settings.customSurfaceHex) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = accent,
                                        onValueChange = { accent = it; onUpdateCustomThemeColors(accent, bg, surf) },
                                        label = { Text("Accent Hex") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = bg,
                                        onValueChange = { bg = it; onUpdateCustomThemeColors(accent, bg, surf) },
                                        label = { Text("Background Hex") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = surf,
                                        onValueChange = { surf = it; onUpdateCustomThemeColors(accent, bg, surf) },
                                        label = { Text("Surface Hex") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            HorizontalDivider()
                            Text("Interface Scale (75% – 140%)", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InterfaceScale.entries.forEach { scale ->
                                    FilterChip(
                                        selected = settings.interfaceScale == scale,
                                        onClick = { onSelectScale(scale) },
                                        label = { Text(scale.label) }
                                    )
                                }
                            }

                            Text("Layout Density", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DensityPreset.entries.forEach { density ->
                                    FilterChip(
                                        selected = settings.densityPreset == density,
                                        onClick = { onSelectDensity(density) },
                                        label = { Text(density.label) }
                                    )
                                }
                            }

                            Text("Card Size", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CardSizePreset.entries.forEach { cs ->
                                    FilterChip(
                                        selected = settings.cardSizePreset == cs,
                                        onClick = { onSelectCardSize(cs) },
                                        label = { Text("${cs.label} (~${cs.approxColumnsLandscape} cols)") }
                                    )
                                }
                            }

                            Text("Corner Radius: ${settings.cornerRadiusDp}dp")
                            Slider(
                                value = settings.cornerRadiusDp.toFloat(),
                                onValueChange = { onUpdateThemeAppearance(it.toInt(), null, null, null, null, null) },
                                valueRange = 4f..24f
                            )

                            Text("Artwork Saturation: ${String.format(Locale.US, "%.2f", settings.artworkSaturation)}")
                            Slider(
                                value = settings.artworkSaturation,
                                onValueChange = { onUpdateThemeAppearance(null, null, null, it, null, null) },
                                valueRange = 0.2f..1.4f
                            )
                        }
                    }
                }

                SettingsSection.LIBRARY -> {
                    item {
                        var regPriority by remember(settings.regionPriorityCsv) { mutableStateOf(settings.regionPriorityCsv) }
                        var langPriority by remember(settings.languagePriorityCsv) { mutableStateOf(settings.languagePriorityCsv) }

                        SettingsCard {
                            Text("Preferred Region & Language Order", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Used to automatically select the preferred version when a game has USA, Europe, and Japan ROMs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = regPriority,
                                onValueChange = {
                                    regPriority = it
                                    onUpdateLibraryPrefs(it, null, null, null, null, null)
                                },
                                label = { Text("Region Priority (comma-separated, e.g. Europe,USA,World,Japan)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = langPriority,
                                onValueChange = {
                                    langPriority = it
                                    onUpdateLibraryPrefs(null, it, null, null, null, null)
                                },
                                label = { Text("Language Priority (e.g. Es,En,Ja)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Show Versions Count Badge on Game Cards")
                                Switch(
                                    checked = settings.showVariantsBadge,
                                    onCheckedChange = { onUpdateLibraryPrefs(null, null, null, null, null, it) }
                                )
                            }
                        }
                    }
                }

                SettingsSection.INPUT -> {
                    item {
                        ControllerSettingsPanel(
                            settings = settings,
                            telemetry = controllerTelemetry,
                            remappingTarget = remappingTarget,
                            onUpdateControllerSettings = onUpdateControllerSettings,
                            onStartRemapAction = onStartRemapAction,
                            onResetBindings = onResetControllerBindings
                        )
                    }
                }

                SettingsSection.METADATA_SCRAPING -> {
                    item {
                        var ssUser by remember(settings.screenScraperUser) { mutableStateOf(settings.screenScraperUser) }
                        var ssKey by remember(settings.screenScraperApiKey) { mutableStateOf(settings.screenScraperApiKey) }
                        var sgdbKey by remember(settings.steamGridDbApiKey) { mutableStateOf(settings.steamGridDbApiKey) }
                        var tgdbKey by remember(settings.theGamesDbApiKey) { mutableStateOf(settings.theGamesDbApiKey) }
                        var raKey by remember(settings.retroAchievementsApiKey) { mutableStateOf(settings.retroAchievementsApiKey) }

                        SettingsCard {
                            Text("Batch Scraper Actions", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onTriggerScrapeAll(ScrapeMode.MISSING_ONLY) }) {
                                    Text("SCRAPE MISSING")
                                }
                                FilledTonalButton(onClick = { onTriggerScrapeAll(ScrapeMode.METADATA_ONLY) }) {
                                    Text("METADATA ONLY")
                                }
                                FilledTonalButton(onClick = { onTriggerScrapeAll(ScrapeMode.MEDIA_ONLY) }) {
                                    Text("MEDIA ONLY")
                                }
                                OutlinedButton(onClick = { showConfirmReplaceScrape = true }) {
                                    Text("REPLACE ALL (CONFIRM)")
                                }
                            }
                            HorizontalDivider()
                            Text("Metadata Priority Order (Strict):", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "1. User Manual Edits (Never overwritten automatically) → 2. Exact Catalog → 3. ES-DE gamelist.xml → 4. RetroAchievements → 5. Remote Scrapers → 6. Filename",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider()
                            Text("Scraper API Credentials (Stored Locally)", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = sgdbKey,
                                onValueChange = { sgdbKey = it; onUpdateScraperKeys(null, null, sgdbKey, null, null, null, null) },
                                label = { Text("SteamGridDB API Key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tgdbKey,
                                onValueChange = { tgdbKey = it; onUpdateScraperKeys(null, null, null, tgdbKey, null, null, null) },
                                label = { Text("TheGamesDB API Key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = ssUser,
                                onValueChange = { ssUser = it; onUpdateScraperKeys(ssUser, null, null, null, null, null, null) },
                                label = { Text("ScreenScraper Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = ssKey,
                                onValueChange = { ssKey = it; onUpdateScraperKeys(null, ssKey, null, null, null, null, null) },
                                label = { Text("ScreenScraper Dev/API Key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = raKey,
                                onValueChange = { raKey = it; onUpdateScraperKeys(null, null, null, null, raKey, null, null) },
                                label = { Text("RetroAchievements Web API Key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                SettingsSection.MEDIA_STORAGE -> {
                    item {
                        SettingsCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Storage Breakdown", style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = onRefreshStorage) {
                                    Text("REFRESH")
                                }
                            }
                            if (storageBreakdown != null) {
                                Text("DATABASE: ${MediaCacheManager.formatBytes(storageBreakdown.databaseBytes)}")
                                Text("MEDIA CACHE: ${MediaCacheManager.formatBytes(storageBreakdown.mediaCacheBytes)}")
                                Text("BACKUPS: ${MediaCacheManager.formatBytes(storageBreakdown.backupsBytes)}")
                                Text("CATALOG CACHE: ${MediaCacheManager.formatBytes(storageBreakdown.catalogCacheBytes)}")
                                Text("USER MEDIA: ${MediaCacheManager.formatBytes(storageBreakdown.userMediaBytes)}")
                                Text(
                                    "TOTAL: ${MediaCacheManager.formatBytes(storageBreakdown.totalBytes)}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onClearMediaCache) {
                                    Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("CLEAR MEDIA CACHE")
                                }
                            }
                        }
                    }
                }

                SettingsSection.BACKUP -> {
                    item {
                        SettingsCard {
                            Text("Database, Collections & Settings Backup (ZIP)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Export a full ZIP archive of your AetherDeck database, collections, custom artwork references, emulator mappings, and scan roots.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onRequestExportBackup) {
                                    Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EXPORT BACKUP ZIP")
                                }
                                FilledTonalButton(onClick = onRequestImportBackup) {
                                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("RESTORE BACKUP (WITH PREVIEW)")
                                }
                            }
                        }
                    }
                }

                SettingsSection.ACCESSIBILITY -> {
                    item {
                        SettingsCard {
                            Text("Text Scale: ${String.format(Locale.US, "%.0f%%", settings.textScale * 100)}")
                            Slider(
                                value = settings.textScale,
                                onValueChange = onUpdateTextScale,
                                valueRange = 0.85f..1.40f
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("High Contrast Surfaces & Outlines")
                                Switch(
                                    checked = settings.highContrast,
                                    onCheckedChange = { onUpdateAccessibility(it, null, null, null, null) }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Reduced Motion (Disable Scale Transitions)")
                                Switch(
                                    checked = settings.reducedMotion,
                                    onCheckedChange = { onUpdateAccessibility(null, it, null, null, null) }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Larger Focus Borders (Gamepad Visibility)")
                                Switch(
                                    checked = settings.largerFocusBorders,
                                    onCheckedChange = { onUpdateAccessibility(null, null, it, null, null) }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Color-Blind-Safe Amber Focus Indicator")
                                Switch(
                                    checked = settings.colorBlindSafeFocus,
                                    onCheckedChange = { onUpdateAccessibility(null, null, null, it, null) }
                                )
                            }
                        }
                    }
                }

                SettingsSection.HELP -> {
                    item {
                        HelpCenterPanel()
                    }
                }

                SettingsSection.INFORMATION -> {
                    item {
                        val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
                        val totalVariants = games.sumOf { it.variants.size }
                        val retroInstalled = detectedEmulators.any { it.config.launchType == "RETROARCH" && it.isInstalled }
                        val arleyInstalled = detectedEmulators.any { it.config.id == "arley4d" && it.isInstalled }

                        SettingsCard {
                            Text("System & Library Information", style = MaterialTheme.typography.titleLarge)
                            InfoRow("AetherDeck Version", "1.0.0 (Build 1)")
                            InfoRow("Database Version", "Room Schema v1")
                            InfoRow("Android SDK", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                            InfoRow("Detected Device", "${Build.MANUFACTURER} ${Build.MODEL}")
                            InfoRow("Detected Controller", controllerTelemetry.detectedControllerName)
                            InfoRow("Configured Libraries", "${librarySources.size}")
                            InfoRow("Canonical Games", "${games.size}")
                            InfoRow("Total Game Variants", "$totalVariants")
                            InfoRow("Configured Systems", "${systems.size}")
                            InfoRow("Media Cache Size", MediaCacheManager.formatBytes(storageBreakdown?.mediaCacheBytes ?: 0L))
                            InfoRow(
                                "Last Library Scan",
                                if (settings.lastScanTimestamp > 0) dateFormatter.format(Date(settings.lastScanTimestamp)) else "Never"
                            )
                            InfoRow(
                                "Last Catalog Update",
                                if (settings.lastCatalogUpdateTimestamp > 0) dateFormatter.format(Date(settings.lastCatalogUpdateTimestamp)) else "Never"
                            )
                            InfoRow("RetroArch Status", if (retroInstalled) "Installed ✓" else "Not Installed")
                            InfoRow("Arley4d Bypass Status", if (arleyInstalled) "Installed ✓" else "Not Installed")
                        }
                    }
                }

                SettingsSection.ABOUT -> {
                    item {
                        SettingsCard {
                            Text("About AetherDeck", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "AetherDeck is an independent gaming library frontend, organizer, and launcher for Android handhelds, phones, and controllers.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            HorizontalDivider()
                            Text(
                                "• AetherDeck is a frontend.\n" +
                                    "• It does NOT contain games or ROMs.\n" +
                                    "• It does NOT contain copyrighted BIOS files.\n" +
                                    "• It does NOT bundle third-party emulators unless explicitly distributed with proper open-source rights.\n" +
                                    "• Built with Kotlin, Jetpack Compose, Room, DataStore, OkHttp, Coil, and Android Storage Access Framework.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SettingsSection.DEVELOPER -> {
                    item {
                        SettingsCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Developer Diagnostics & Logs", style = MaterialTheme.typography.titleLarge)
                                Button(onClick = onRequestExportDiagnostics) {
                                    Text("EXPORT DIAGNOSTICS (.TXT)")
                                }
                            }
                            Text(
                                "API keys, passwords, and tokens are automatically redacted from exported logs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            InfoRow("Last Launch Event", AetherLogger.lastLaunchSummary.value)
                            InfoRow("Last Scraper Request", AetherLogger.lastScraperRequest.value)
                            InfoRow("Last Provider Error", AetherLogger.lastProviderError.value)
                            HorizontalDivider()
                            Text(
                                text = AetherLogger.formatLogsForExport().ifBlank { "No diagnostic log events recorded yet." },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirmReplaceScrape) {
        AlertDialog(
            onDismissRequest = { showConfirmReplaceScrape = false },
            title = { Text("Confirm Replace Metadata & Artwork") },
            text = {
                Text("REPLACE mode will overwrite existing scraped metadata and non-custom artwork across your library. Are you sure you want to continue?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmReplaceScrape = false
                        onTriggerScrapeAll(ScrapeMode.REPLACE)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("CONFIRM REPLACE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmReplaceScrape = false }) { Text("CANCEL") }
            }
        )
    }

    pendingBackupPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = onCancelRestorePreview,
            title = { Text("Restore Backup Preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Device: ${preview.deviceModel}")
                    Text("Canonical Games: ${preview.canonicalGamesCount}")
                    Text("Game Variants: ${preview.variantsCount}")
                    Text("Collections: ${preview.collectionsCount}")
                    Text("Scan Roots: ${preview.scanRootsCount}")
                }
            },
            confirmButton = {
                Button(onClick = onConfirmRestorePreview) {
                    Text("RESTORE NOW")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelRestorePreview) { Text("CANCEL") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerSettingsPanel(
    settings: AetherSettingsState,
    telemetry: LiveControllerTelemetry,
    remappingTarget: ControllerAction?,
    onUpdateControllerSettings: (Float?, Float?, Int?, Int?, Boolean?, Boolean?, Boolean?) -> Unit,
    onStartRemapAction: (ControllerAction?) -> Unit,
    onResetBindings: () -> Unit
) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Gamepad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Detected Controller", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(telemetry.detectedControllerName, style = MaterialTheme.typography.titleLarge)
            }
        }

        HorizontalDivider()

        // Live INPUT TESTER
        Text("LIVE INPUT TESTER", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            text = "Left Stick: (${String.format(Locale.US, "%.2f", telemetry.leftStickX)}, ${String.format(Locale.US, "%.2f", telemetry.leftStickY)})  •  " +
                "Right Stick: (${String.format(Locale.US, "%.2f", telemetry.rightStickX)}, ${String.format(Locale.US, "%.2f", telemetry.rightStickY)})  •  " +
                "L2/R2: (${String.format(Locale.US, "%.2f", telemetry.l2Trigger)}, ${String.format(Locale.US, "%.2f", telemetry.r2Trigger)})",
            style = MaterialTheme.typography.labelMedium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TesterBadge("DPAD-U", telemetry.dpadUp)
            TesterBadge("DPAD-D", telemetry.dpadDown)
            TesterBadge("DPAD-L", telemetry.dpadLeft)
            TesterBadge("DPAD-R", telemetry.dpadRight)
            TesterBadge("A", telemetry.buttonA)
            TesterBadge("B", telemetry.buttonB)
            TesterBadge("X", telemetry.buttonX)
            TesterBadge("Y", telemetry.buttonY)
            TesterBadge("L1", telemetry.buttonL1)
            TesterBadge("R1", telemetry.buttonR1)
            TesterBadge("L2", telemetry.buttonL2)
            TesterBadge("R2", telemetry.buttonR2)
            TesterBadge("START", telemetry.buttonStart)
            TesterBadge("SELECT", telemetry.buttonSelect)
        }
        Text("Last Key Event: ${telemetry.lastKeyName}", style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider()

        // Analog & Repeat Tuning
        Text("Deadzone: ${String.format(Locale.US, "%.2f", settings.deadzone)}")
        Slider(
            value = settings.deadzone,
            onValueChange = { onUpdateControllerSettings(it, null, null, null, null, null, null) },
            valueRange = 0.05f..0.45f
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Swap A / B (Nintendo / Xbox Confirm Layout)")
            Switch(
                checked = settings.swapAB,
                onCheckedChange = { onUpdateControllerSettings(null, null, null, null, it, null, null) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Swap X / Y")
            Switch(
                checked = settings.swapXY,
                onCheckedChange = { onUpdateControllerSettings(null, null, null, null, null, it, null) }
            )
        }

        HorizontalDivider()

        // Visual BUTTON REMAPPER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BUTTON REMAPPER", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = onResetBindings) {
                Text("RESET DEFAULTS")
            }
        }
        if (remappingTarget != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Press any gamepad button to assign: ${remappingTarget.label}")
                    TextButton(onClick = { onStartRemapAction(null) }) { Text("CANCEL") }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControllerAction.entries.forEach { action ->
                OutlinedButton(onClick = { onStartRemapAction(action) }) {
                    Text("${action.label} (${action.defaultButtonHint})")
                }
            }
        }
    }
}

@Composable
private fun TesterBadge(label: String, active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun HelpCenterPanel() {
    val topics = remember {
        listOf(
            "Getting Started" to "1. Go to Sources -> Tap LOCAL ROMS -> Select your root /ROMs folder.\n2. AetherDeck scans all console subfolders (PS1, SNES, GBA, PSP, etc.) automatically.\n3. Regional duplicates (USA/Europe/Japan) are grouped into one card.",
            "Adding ROMs & Multi-Disc" to "Put console ROMs inside named subfolders (e.g. ROMs/PS1, ROMs/SNES). Multi-disc games (Disc 1, Disc 2) are automatically grouped under one entry with a version selector.",
            "Adding Fake ROMs & Arley4d Setup" to "Tap ARLEY4D FAKE ROMS in Sources and select your Fake ROMs folder. When you press PLAY on a Fake ROM, AetherDeck sends an explicit ACTION_VIEW Intent with read permissions to com.arley4d.bypassarley4d.MainActivity.",
            "RetroArch & Core Setup" to "Install RetroArch (64-bit recommended). Open RetroArch -> Online Updater -> Core Downloader and download the cores for your systems (e.g. snes9x_libretro_android.so, mgba_libretro_android.so, pcsx_rearmed_libretro_android.so). Use TEST APP and TEST GAME in the Emulators tab to verify before playing.",
            "Troubleshooting: RetroArch Black Screen" to "A black screen in RetroArch usually means either (1) the Libretro core (.so) has not been downloaded inside RetroArch yet, (2) a PS1/PS2/Dreamcast game requires BIOS files in RetroArch/system, or (3) SAF folder permissions expired. AetherDeck runs a Pre-Launch Check to catch missing packages and permissions upfront.",
            "Troubleshooting: Folder Permission Lost" to "If Android revokes SAF permissions after a system update or SD card remount, open Sources and re-select your ROMs folder to restore persistent read permissions."
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        topics.forEach { (title, body) ->
            SettingsCard {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
