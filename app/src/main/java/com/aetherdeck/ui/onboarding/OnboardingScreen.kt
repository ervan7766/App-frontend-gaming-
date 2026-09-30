package com.aetherdeck.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aetherdeck.core.database.LibrarySourceEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.models.CardSizePreset
import com.aetherdeck.core.models.InterfaceScale
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.core.models.ThemePreset
import com.aetherdeck.core.settings.AetherSettingsState
import com.aetherdeck.emulators.detection.DetectedEmulatorStatus
import com.aetherdeck.input.gamepad.LiveControllerTelemetry

private val ONBOARDING_STEPS = listOf(
    "1. Welcome",
    "2. Interface",
    "3. Controller",
    "4. Add Games",
    "5. Detect Emulators",
    "6. Metadata Services",
    "7. Scan",
    "8. Optional Scrape",
    "9. Finish"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingWizardScreen(
    settings: AetherSettingsState,
    controllerTelemetry: LiveControllerTelemetry,
    librarySources: List<LibrarySourceEntity>,
    detectedEmulators: List<DetectedEmulatorStatus>,
    systems: List<SystemEntity>,
    canonicalGameCount: Int,
    onSelectTheme: (ThemePreset) -> Unit,
    onSelectScale: (InterfaceScale) -> Unit,
    onSelectCardSize: (CardSizePreset) -> Unit,
    onToggleSwapAB: (Boolean) -> Unit,
    onRequestAddFolder: (ProviderType) -> Unit,
    onRefreshEmulators: () -> Unit,
    onSaveScraperKeys: (String, String) -> Unit,
    onRescanAll: () -> Unit,
    onSyncCatalogs: () -> Unit,
    onScrapeMissing: () -> Unit,
    onCompleteOnboarding: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var sgdbKey by remember(settings.steamGridDbApiKey) { mutableStateOf(settings.steamGridDbApiKey) }
    var tgdbKey by remember(settings.theGamesDbApiKey) { mutableStateOf(settings.theGamesDbApiKey) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Top Header & Skip All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WELCOME TO AETHERDECK",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Get your library ready in a few steps. (${ONBOARDING_STEPS[currentStep]})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onCompleteOnboarding,
                    modifier = Modifier.testTag("onboarding_skip_all_button")
                ) {
                    Text("SKIP SETUP")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / ONBOARDING_STEPS.size.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Step Content
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (currentStep) {
                        0 -> {
                            Icon(
                                Icons.Filled.RocketLaunch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Console-Grade Android Emulation Frontend",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "AetherDeck organizes your local ROMs, Fake ROMs, multi-disc sets, and regional variants automatically—without manual per-game setup.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider()
                            Text(
                                text = "• Select ONE root folder and scan all console subfolders automatically\n" +
                                    "• Groups USA / Europe / Japan versions under a single game entry\n" +
                                    "• Dedicated RetroArch, Standalone Emulator, and Arley4d Bypass integration\n" +
                                    "• 100% navigable via Razer Kishi V2 / Gamepad AND Touch",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        1 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Interface & Theme", style = MaterialTheme.typography.titleLarge)
                            }
                            Text("Theme Preset", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemePreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = settings.themePreset == preset,
                                        onClick = { onSelectTheme(preset) },
                                        label = { Text(preset.displayName) }
                                    )
                                }
                            }
                            Text("Interface Scale", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InterfaceScale.entries.forEach { scale ->
                                    FilterChip(
                                        selected = settings.interfaceScale == scale,
                                        onClick = { onSelectScale(scale) },
                                        label = { Text(scale.label) }
                                    )
                                }
                            }
                            Text("Card Size", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CardSizePreset.entries.forEach { size ->
                                    FilterChip(
                                        selected = settings.cardSizePreset == size,
                                        onClick = { onSelectCardSize(size) },
                                        label = { Text(size.label) }
                                    )
                                }
                            }
                        }

                        2 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Gamepad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Controller & Touch Input", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = "Detected: ${controllerTelemetry.detectedControllerName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Last Button Pressed: ${controllerTelemetry.lastKeyName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Swap Confirm / Back (A ↔ B)", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = settings.swapAB,
                                    onCheckedChange = onToggleSwapAB
                                )
                            }
                        }

                        3 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ADD LIBRARY", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = "Choose one or more game sources. Selecting a single root ROMs folder scans all system subfolders automatically.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(onClick = { onRequestAddFolder(ProviderType.LOCAL_ROM) }) {
                                    Text("LOCAL ROMS")
                                }
                                FilledTonalButton(onClick = { onRequestAddFolder(ProviderType.ARLEY4D_LOCAL_FAKE_ROM) }) {
                                    Text("ARLEY4D FAKE ROMS")
                                }
                                OutlinedButton(onClick = { onRequestAddFolder(ProviderType.EMULATION_STATION) }) {
                                    Text("EMULATIONSTATION")
                                }
                                OutlinedButton(onClick = onSyncCatalogs) {
                                    Text("ARLEY4D CATALOG")
                                }
                                OutlinedButton(onClick = { onRequestAddFolder(ProviderType.GENERIC_JSONL) }) {
                                    Text("GENERIC JSON / JSONL")
                                }
                            }
                            if (librarySources.isNotEmpty()) {
                                HorizontalDivider()
                                Text("Configured Folders (${librarySources.size}):", style = MaterialTheme.typography.labelLarge)
                                librarySources.forEach { src ->
                                    Text("• ${src.name} [${src.providerType}] (${src.gameCount} files)")
                                }
                            }
                        }

                        4 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Detect Emulators", style = MaterialTheme.typography.titleLarge)
                                }
                                OutlinedButton(onClick = onRefreshEmulators) {
                                    Text("RE-DETECT")
                                }
                            }
                            detectedEmulators.forEach { emu ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(emu.config.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = emu.verificationNote,
                                        color = if (emu.isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        5 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Metadata & Artwork Services (Optional)", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = "AetherDeck resolves metadata from local filenames, ES-DE gamelist.xml, and Arley4d Catalogs out of the box. You can optionally enter API keys for SteamGridDB or TheGamesDB.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = sgdbKey,
                                onValueChange = {
                                    sgdbKey = it
                                    onSaveScraperKeys(sgdbKey, tgdbKey)
                                },
                                label = { Text("SteamGridDB API Key (Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tgdbKey,
                                onValueChange = {
                                    tgdbKey = it
                                    onSaveScraperKeys(sgdbKey, tgdbKey)
                                },
                                label = { Text("TheGamesDB API Key (Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        6 -> {
                            Text("Automatic Library Scan", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Configured folders: ${librarySources.size} • Discovered canonical games: $canonicalGameCount",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onRescanAll) {
                                    Text("SCAN LIBRARIES NOW")
                                }
                                OutlinedButton(onClick = onSyncCatalogs) {
                                    Text("SYNC SELECTED CATALOGS (${systems.count { it.catalogSyncEnabled }})")
                                }
                            }
                        }

                        7 -> {
                            Text("Optional Artwork & Metadata Scrape", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Fetch missing covers, logos, and descriptions for your $canonicalGameCount discovered games. You can also run or schedule this anytime in the Scraper screen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onScrapeMissing,
                                enabled = canonicalGameCount > 0
                            ) {
                                Text("SCRAPE MISSING ARTWORK ($canonicalGameCount GAMES)")
                            }
                        }

                        8 -> {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("AetherDeck is Ready", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = "You can re-run this wizard, add more ROM folders, customize themes, or test emulators anytime from Settings.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onCompleteOnboarding,
                                modifier = Modifier.testTag("onboarding_finish_button")
                            ) {
                                Text("ENTER AETHERDECK")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { if (currentStep > 0) currentStep-- },
                    enabled = currentStep > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BACK")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentStep < ONBOARDING_STEPS.lastIndex) {
                        TextButton(onClick = { currentStep++ }) {
                            Text("SKIP STEP")
                        }
                        Button(
                            onClick = { currentStep++ },
                            modifier = Modifier.testTag("onboarding_next_button")
                        ) {
                            Text("NEXT")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    } else {
                        Button(
                            onClick = onCompleteOnboarding,
                            modifier = Modifier.testTag("onboarding_done_bottom_button")
                        ) {
                            Text("FINISH")
                        }
                    }
                }
            }
        }
    }
}
