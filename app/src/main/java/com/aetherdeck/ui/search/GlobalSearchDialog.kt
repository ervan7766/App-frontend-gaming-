package com.aetherdeck.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.aetherdeck.core.database.CollectionEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.emulators.detection.InstalledAndroidApp
import com.aetherdeck.library.normalizer.TitleNormalizer
import com.aetherdeck.ui.settings.SettingsSection

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchDialog(
    games: List<CanonicalGameWithDetails>,
    systems: List<SystemEntity>,
    collections: List<CollectionEntity>,
    installedApps: List<InstalledAndroidApp>,
    onDismiss: () -> Unit,
    onSelectGame: (String) -> Unit,
    onSelectSystem: (String) -> Unit,
    onSelectCollection: () -> Unit,
    onSelectSettingsSection: (SettingsSection) -> Unit,
    onLaunchAndroidApp: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normQuery = remember(query) { TitleNormalizer.normalizeForMatching(query) }

    val matchedGames = remember(normQuery, games) {
        if (normQuery.isEmpty()) games.take(6)
        else games.filter {
            it.game.normalizedTitle.contains(normQuery) ||
                TitleNormalizer.normalizeForMatching(it.game.developer).contains(normQuery) ||
                TitleNormalizer.normalizeForMatching(it.game.genre).contains(normQuery) ||
                TitleNormalizer.normalizeForMatching(it.game.franchise).contains(normQuery)
        }.take(12)
    }

    val matchedSystems = remember(normQuery, systems) {
        if (normQuery.isEmpty()) emptyList()
        else systems.filter {
            TitleNormalizer.normalizeForMatching(it.name).contains(normQuery) ||
                TitleNormalizer.normalizeForMatching(it.shortName).contains(normQuery)
        }
    }

    val matchedCollections = remember(normQuery, collections) {
        if (normQuery.isEmpty()) emptyList()
        else collections.filter {
            TitleNormalizer.normalizeForMatching(it.name).contains(normQuery)
        }
    }

    val matchedSettings = remember(normQuery) {
        if (normQuery.isEmpty()) emptyList()
        else SettingsSection.entries.filter {
            TitleNormalizer.normalizeForMatching(it.title).contains(normQuery)
        }
    }

    val matchedApps = remember(normQuery, installedApps) {
        if (normQuery.isEmpty()) installedApps.filter { it.isFavorite }.take(4)
        else installedApps.filter {
            TitleNormalizer.normalizeForMatching(it.label).contains(normQuery)
        }.take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("GLOBAL SEARCH", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search games, systems, developers, genres, settings, apps...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_search_input")
                )

                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (matchedSystems.isNotEmpty()) {
                        item {
                            Text("SYSTEMS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                matchedSystems.forEach { sys ->
                                    AssistChip(
                                        onClick = { onDismiss(); onSelectSystem(sys.id) },
                                        label = { Text("${sys.shortName} - ${sys.name}") }
                                    )
                                }
                            }
                        }
                    }

                    if (matchedCollections.isNotEmpty()) {
                        item {
                            Text("COLLECTIONS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                matchedCollections.forEach { col ->
                                    AssistChip(
                                        onClick = { onDismiss(); onSelectCollection() },
                                        leadingIcon = { Icon(Icons.Filled.CollectionsBookmark, contentDescription = null) },
                                        label = { Text(col.name) }
                                    )
                                }
                            }
                        }
                    }

                    if (matchedSettings.isNotEmpty()) {
                        item {
                            Text("SETTINGS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                matchedSettings.forEach { sec ->
                                    AssistChip(
                                        onClick = { onDismiss(); onSelectSettingsSection(sec) },
                                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                        label = { Text(sec.title) }
                                    )
                                }
                            }
                        }
                    }

                    if (matchedApps.isNotEmpty()) {
                        item {
                            Text("ANDROID APPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                matchedApps.forEach { app ->
                                    AssistChip(
                                        onClick = { onDismiss(); onLaunchAndroidApp(app.packageName) },
                                        leadingIcon = { Icon(Icons.Filled.Apps, contentDescription = null) },
                                        label = { Text(app.label) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider()
                        Text(
                            text = if (normQuery.isEmpty()) "GAMES" else "MATCHING GAMES (${matchedGames.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(matchedGames, key = { it.game.id }) { g ->
                        Surface(
                            onClick = { onDismiss(); onSelectGame(g.game.id) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.VideogameAsset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(g.game.displayTitle, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${g.game.systemId.uppercase()} • ${g.variants.size} Ver • ${g.game.developer.ifBlank { "ROM" }}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}
