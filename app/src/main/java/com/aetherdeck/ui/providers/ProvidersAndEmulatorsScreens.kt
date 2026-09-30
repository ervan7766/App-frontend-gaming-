package com.aetherdeck.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.aetherdeck.core.database.LibrarySourceEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.emulators.detection.DetectedEmulatorStatus
import com.aetherdeck.launchers.retroarch.RetroArchTestDiagnostic

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProvidersAndSourcesScreen(
    librarySources: List<LibrarySourceEntity>,
    systems: List<SystemEntity>,
    catalogCount: Int,
    onRequestAddFolder: (ProviderType) -> Unit,
    onToggleSourceEnabled: (LibrarySourceEntity) -> Unit,
    onRescanSource: (LibrarySourceEntity) -> Unit,
    onRenameSource: (LibrarySourceEntity, String) -> Unit,
    onRemoveSource: (LibrarySourceEntity) -> Unit,
    onPickSaveFolder: (LibrarySourceEntity) -> Unit,
    onPickStateFolder: (LibrarySourceEntity) -> Unit,
    onToggleSystemCatalogSync: (SystemEntity) -> Unit,
    onSyncSelectedCatalogs: () -> Unit
) {
    var renamingSource by remember { mutableStateOf<LibrarySourceEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("providers_screen"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Simple ADD LIBRARY section as specified
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ADD LIBRARY",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Select a root folder once. AetherDeck recursively scans all console subfolders automatically via Storage Access Framework.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onRequestAddFolder(ProviderType.LOCAL_ROM) },
                            modifier = Modifier.testTag("btn_add_local_roms")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LOCAL ROMS")
                        }
                        FilledTonalButton(
                            onClick = { onRequestAddFolder(ProviderType.ARLEY4D_LOCAL_FAKE_ROM) },
                            modifier = Modifier.testTag("btn_add_arley4d_fake_roms")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ARLEY4D FAKE ROMS")
                        }
                        OutlinedButton(onClick = { onRequestAddFolder(ProviderType.EMULATION_STATION) }) {
                            Text("EMULATIONSTATION")
                        }
                        OutlinedButton(onClick = onSyncSelectedCatalogs) {
                            Text("ARLEY4D CATALOG")
                        }
                        OutlinedButton(onClick = { onRequestAddFolder(ProviderType.GENERIC_JSONL) }) {
                            Text("GENERIC JSON / JSONL")
                        }
                    }
                }
            }
        }

        // Multiple Libraries (ScanRoots) management: Enable, Disable, Rescan, Rename, Remove, Saves & States
        item {
            Text(
                text = "CONFIGURED LIBRARY FOLDERS (${librarySources.size})",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (librarySources.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "No library folders added yet. Tap LOCAL ROMS or ARLEY4D FAKE ROMS above to select a folder.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(librarySources, key = { it.id }) { src ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = src.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${src.providerType} • ${src.gameCount} files • ${src.uriString}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = src.enabled,
                                onCheckedChange = { onToggleSourceEnabled(src) }
                            )
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRescanSource(src) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RESCAN")
                            }
                            OutlinedButton(onClick = { renamingSource = src }) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RENAME")
                            }
                            OutlinedButton(onClick = { onPickSaveFolder(src) }) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (src.saveFolderUri != null) "SAVE FOLDER ✓" else "SET SAVE FOLDER")
                            }
                            OutlinedButton(onClick = { onPickStateFolder(src) }) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (src.stateFolderUri != null) "STATE FOLDER ✓" else "SET STATE FOLDER")
                            }
                            IconButton(onClick = { onRemoveSource(src) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove Library Source",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // CATALOG SYSTEMS Selection (Arley4d / AndroidRoms JSONL)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CATALOG SYSTEMS (Arley4d / AndroidRoms JSONL)",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Cached Catalog Entries: $catalogCount • Select which systems to sync for automatic metadata & media matching.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onSyncSelectedCatalogs) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SYNC SELECTED")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        systems.filter { it.catalogFile.isNotBlank() }.forEach { sys ->
                            FilterChip(
                                selected = sys.catalogSyncEnabled,
                                onClick = { onToggleSystemCatalogSync(sys) },
                                label = { Text("${sys.shortName} (${sys.catalogFile})") }
                            )
                        }
                    }
                }
            }
        }
    }

    renamingSource?.let { source ->
        var newName by remember(source.id) { mutableStateOf(source.name) }
        AlertDialog(
            onDismissRequest = { renamingSource = null },
            title = { Text("Rename Library Source") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Display Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameSource(source, newName)
                    renamingSource = null
                }) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingSource = null }) { Text("CANCEL") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmulatorsAndRetroArchScreen(
    detectedEmulators: List<DetectedEmulatorStatus>,
    systems: List<SystemEntity>,
    retroArchDiagnostic: RetroArchTestDiagnostic?,
    onRefreshDetection: () -> Unit,
    onTestLaunchRetroArchApp: () -> Unit,
    onRequestTestLaunchGameRom: (String) -> Unit,
    onUpdateSystemEmulator: (SystemEntity, String, String) -> Unit
) {
    var testCoreName by remember { mutableStateOf("snes9x_libretro_android.so") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("emulators_screen"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // RetroArch & Arley4d Dedicated Verification + TEST APP / TEST GAME
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "RETROARCH & LAUNCH DIAGNOSTICS",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Verify package, RetroActivityFuture, Libretro core (.so), and ROM read access before playing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onRefreshDetection) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RE-SCAN PACKAGES")
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onTestLaunchRetroArchApp,
                            modifier = Modifier.testTag("btn_test_retroarch_app")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TEST APP (OPEN RETROARCH)")
                        }
                        FilledTonalButton(
                            onClick = { onRequestTestLaunchGameRom(testCoreName) },
                            modifier = Modifier.testTag("btn_test_retroarch_game")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TEST GAME (PICK TEST ROM)")
                        }
                    }

                    OutlinedTextField(
                        value = testCoreName,
                        onValueChange = { testCoreName = it },
                        label = { Text("Core for TEST GAME (e.g. snes9x_libretro_android.so, mgba_libretro_android.so)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (retroArchDiagnostic != null) {
                        HorizontalDivider()
                        Text("TEST GAME RESULT:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            DiagCheckChip("Package", retroArchDiagnostic.packageCheck)
                            DiagCheckChip("Activity", retroArchDiagnostic.activityCheck)
                            DiagCheckChip("ROM", retroArchDiagnostic.romCheck)
                            DiagCheckChip("Core (${retroArchDiagnostic.coreStatus.label})", retroArchDiagnostic.coreCheck)
                            DiagCheckChip("Config", retroArchDiagnostic.configCheck)
                            DiagCheckChip("Launch", retroArchDiagnostic.launchSucceeded)
                        }
                        Text(
                            text = retroArchDiagnostic.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Detected Emulators Status List
        item {
            Text("DETECTED EMULATORS & INTEGRATIONS", style = MaterialTheme.typography.titleLarge)
        }

        items(detectedEmulators, key = { it.config.id }) { emu ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(emu.config.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Packages: ${emu.config.packages.joinToString(", ")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Activity: ${emu.resolvedActivity ?: emu.config.activities.firstOrNull().orEmpty()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = when {
                            emu.isInstalled && emu.config.verified -> MaterialTheme.colorScheme.primaryContainer
                            !emu.config.verified -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = emu.verificationNote,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Per-System Default & Alternative Emulator + Core Configuration
        item {
            Text("PER-SYSTEM EMULATOR & LIBRETRO CORE MAPPINGS", style = MaterialTheme.typography.titleLarge)
        }

        items(systems, key = { "map_${it.id}" }) { sys ->
            val currentEmu = sys.selectedEmulatorId ?: sys.defaultEmulatorId
            val currentCore = sys.selectedCore ?: sys.defaultCore
            val allCandidateEmus = (listOf(sys.defaultEmulatorId) + sys.alternativeEmulatorIdsCsv.split(","))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val allCandidateCores = (listOf(sys.defaultCore) + sys.alternativeCoresCsv.split(","))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${sys.name} (${sys.shortName})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Default Emulator:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allCandidateEmus.forEach { emuId ->
                            FilterChip(
                                selected = currentEmu == emuId,
                                onClick = { onUpdateSystemEmulator(sys, emuId, currentCore) },
                                label = { Text(emuId) }
                            )
                        }
                    }
                    if (allCandidateCores.isNotEmpty()) {
                        Text("Libretro Core (.so):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allCandidateCores.forEach { coreName ->
                                FilterChip(
                                    selected = currentCore == coreName,
                                    onClick = { onUpdateSystemEmulator(sys, currentEmu, coreName) },
                                    label = { Text(coreName) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagCheckChip(label: String, passed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label ${if (passed) "✓" else "✗"}",
            style = MaterialTheme.typography.labelLarge
        )
    }
}
