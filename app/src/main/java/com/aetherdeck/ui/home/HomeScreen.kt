package com.aetherdeck.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aetherdeck.core.database.CanonicalGameWithDetails
import com.aetherdeck.core.database.CollectionEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.models.HomeWidgetConfig
import com.aetherdeck.core.models.HomeWidgetType
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.core.settings.AetherSettingsState
import com.aetherdeck.ui.components.GameCoverCard
import com.example.R

@Composable
fun HomeScreen(
    games: List<CanonicalGameWithDetails>,
    systems: List<SystemEntity>,
    collections: List<CollectionEntity>,
    settings: AetherSettingsState,
    onOpenGameDetails: (String) -> Unit,
    onQuickMenuGame: (CanonicalGameWithDetails) -> Unit,
    onToggleFavorite: (CanonicalGameWithDetails) -> Unit,
    onPlayGame: (String) -> Unit,
    onSelectSystem: (String) -> Unit,
    onOpenCollections: () -> Unit,
    onRequestAddFolder: (ProviderType) -> Unit,
    onRescanAll: () -> Unit,
    onUpdateHomeWidgets: (List<HomeWidgetConfig>) -> Unit
) {
    var showCustomizeWidgetsDialog by remember { mutableStateOf(false) }

    // Genuine empty state if 0 games are in the library (NEVER insert fake/mock games)
    if (games.isEmpty()) {
        EmptyLibraryStateView(
            onAddLocalRoms = { onRequestAddFolder(ProviderType.LOCAL_ROM) },
            onAddFakeRoms = { onRequestAddFolder(ProviderType.ARLEY4D_LOCAL_FAKE_ROM) },
            onAddEsDe = { onRequestAddFolder(ProviderType.EMULATION_STATION) },
            onRescan = onRescanAll
        )
        return
    }

    val activeWidgets = remember(settings.homeWidgets) {
        settings.homeWidgets.filter { it.enabled }.sortedBy { it.order }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AETHERDECK HOME",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${games.size} Canonical Games • ${systems.count { sys -> games.any { it.game.systemId == sys.id } }} Active Systems",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCustomizeWidgetsDialog = true }) {
                        Icon(Icons.Filled.DashboardCustomize, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CUSTOMIZE HOME")
                    }
                    FilledTonalButton(onClick = { games.randomOrNull()?.let { onOpenGameDetails(it.game.id) } }) {
                        Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.action_surprise_me))
                    }
                }
            }
        }

        items(activeWidgets, key = { it.type.id }) { widget ->
            when (widget.type) {
                HomeWidgetType.CONTINUE_PLAYING -> {
                    val recent = games.filter { it.game.lastPlayed != null }
                        .sortedByDescending { it.game.lastPlayed }
                        .take(8)
                    if (recent.isNotEmpty()) {
                        HomeGameRowSection(
                            title = "CONTINUE PLAYING",
                            games = recent,
                            cardWidthDp = if (widget.largeSize) 185 else 150,
                            showVariantsBadge = settings.showVariantsBadge,
                            onOpenGameDetails = onOpenGameDetails,
                            onQuickMenuGame = onQuickMenuGame,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                HomeWidgetType.RECENTLY_PLAYED -> {
                    val recent = games.filter { it.game.playCount > 0 }
                        .sortedByDescending { it.game.lastPlayed ?: 0L }
                        .take(10)
                    if (recent.isNotEmpty()) {
                        HomeGameRowSection(
                            title = "RECENTLY PLAYED",
                            games = recent,
                            cardWidthDp = 150,
                            showVariantsBadge = settings.showVariantsBadge,
                            onOpenGameDetails = onOpenGameDetails,
                            onQuickMenuGame = onQuickMenuGame,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                HomeWidgetType.FAVORITES -> {
                    val favs = games.filter { it.game.favorite }.take(12)
                    if (favs.isNotEmpty()) {
                        HomeGameRowSection(
                            title = "FAVORITES",
                            games = favs,
                            cardWidthDp = 155,
                            showVariantsBadge = settings.showVariantsBadge,
                            onOpenGameDetails = onOpenGameDetails,
                            onQuickMenuGame = onQuickMenuGame,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                HomeWidgetType.RECENTLY_ADDED -> {
                    val added = games.sortedByDescending { it.game.dateAdded }.take(12)
                    if (added.isNotEmpty()) {
                        HomeGameRowSection(
                            title = "RECENTLY ADDED",
                            games = added,
                            cardWidthDp = 150,
                            showVariantsBadge = settings.showVariantsBadge,
                            onOpenGameDetails = onOpenGameDetails,
                            onQuickMenuGame = onQuickMenuGame,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                HomeWidgetType.MOST_PLAYED -> {
                    val most = games.filter { it.game.playCount > 0 }
                        .sortedByDescending { it.game.playCount }
                        .take(10)
                    if (most.isNotEmpty()) {
                        HomeGameRowSection(
                            title = "MOST PLAYED",
                            games = most,
                            cardWidthDp = 150,
                            showVariantsBadge = settings.showVariantsBadge,
                            onOpenGameDetails = onOpenGameDetails,
                            onQuickMenuGame = onQuickMenuGame,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                HomeWidgetType.UNFINISHED_GAMES -> {
                    val unfinished = games.filter { it.game.playCount == 0 }.take(10)
                    if (unfinished.isNotEmpty()) {
                        HomeGameRowSection(
                            title = "UNFINISHED / BACKLOG",
                            games = unfinished,
                            cardWidthDp = 145,
                            showVariantsBadge = settings.showVariantsBadge,
                            onOpenGameDetails = onOpenGameDetails,
                            onQuickMenuGame = onQuickMenuGame,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                HomeWidgetType.SYSTEMS -> {
                    val populatedSystems = systems.mapNotNull { sys ->
                        val count = games.count { it.game.systemId == sys.id }
                        if (count > 0) sys to count else null
                    }
                    if (populatedSystems.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "SYSTEMS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(populatedSystems, key = { it.first.id }) { (sys, count) ->
                                    Card(
                                        onClick = { onSelectSystem(sys.id) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                                        ) {
                                            Text(
                                                text = sys.shortName,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = sys.name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "$count Games",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HomeWidgetType.COLLECTIONS -> {
                    if (collections.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "COLLECTIONS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(collections, key = { it.id }) { col ->
                                    Card(
                                        onClick = onOpenCollections,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(col.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = if (col.isSmart) "Smart Collection" else "Manual Collection",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HomeWidgetType.RANDOM_GAME -> {
                    val spotlight = remember(games.size) { games.randomOrNull() }
                    if (spotlight != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "RANDOM SPOTLIGHT",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = spotlight.game.displayTitle,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "${spotlight.game.systemId.uppercase()} • ${spotlight.variants.size} Version(s)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onOpenGameDetails(spotlight.game.id) }) {
                                        Text("DETAILS")
                                    }
                                    Button(onClick = { onPlayGame(spotlight.game.id) }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("PLAY")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomizeWidgetsDialog) {
        CustomizeHomeWidgetsDialog(
            widgets = settings.homeWidgets,
            onDismiss = { showCustomizeWidgetsDialog = false },
            onSave = { updated ->
                onUpdateHomeWidgets(updated)
                showCustomizeWidgetsDialog = false
            }
        )
    }
}

@Composable
private fun HomeGameRowSection(
    title: String,
    games: List<CanonicalGameWithDetails>,
    cardWidthDp: Int,
    showVariantsBadge: Boolean,
    onOpenGameDetails: (String) -> Unit,
    onQuickMenuGame: (CanonicalGameWithDetails) -> Unit,
    onToggleFavorite: (CanonicalGameWithDetails) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(games, key = { it.game.id }) { item ->
                GameCoverCard(
                    item = item,
                    showVariantsBadge = showVariantsBadge,
                    onClick = { onOpenGameDetails(item.game.id) },
                    onLongClick = { onQuickMenuGame(item) },
                    onFavoriteToggle = { onToggleFavorite(item) },
                    modifier = Modifier.width(cardWidthDp.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyLibraryStateView(
    onAddLocalRoms: () -> Unit,
    onAddFakeRoms: () -> Unit,
    onAddEsDe: () -> Unit,
    onRescan: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("empty_library_state"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.width(560.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.VideogameAsset,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(id = R.string.empty_library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.empty_library_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAddLocalRoms,
                        modifier = Modifier.testTag("empty_add_local_roms_btn")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LOCAL ROMS")
                    }
                    FilledTonalButton(
                        onClick = onAddFakeRoms,
                        modifier = Modifier.testTag("empty_add_fake_roms_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ARLEY4D FAKE ROMS")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onAddEsDe) {
                        Text("EMULATIONSTATION")
                    }
                    TextButton(onClick = onRescan) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESCAN")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizeHomeWidgetsDialog(
    widgets: List<HomeWidgetConfig>,
    onDismiss: () -> Unit,
    onSave: (List<HomeWidgetConfig>) -> Unit
) {
    var workingList by remember { mutableStateOf(widgets.sortedBy { it.order }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Home Widgets") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(workingList.size) { idx ->
                    val item = workingList[idx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.type.title, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val mutable = workingList.toMutableList()
                                    val prev = mutable[idx - 1]
                                    mutable[idx - 1] = item.copy(order = idx - 1)
                                    mutable[idx] = prev.copy(order = idx)
                                    workingList = mutable
                                }
                            },
                            enabled = idx > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up")
                        }
                        IconButton(
                            onClick = {
                                if (idx < workingList.lastIndex) {
                                    val mutable = workingList.toMutableList()
                                    val next = mutable[idx + 1]
                                    mutable[idx + 1] = item.copy(order = idx + 1)
                                    mutable[idx] = next.copy(order = idx)
                                    workingList = mutable
                                }
                            },
                            enabled = idx < workingList.lastIndex
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down")
                        }
                        Switch(
                            checked = item.enabled,
                            onCheckedChange = { checked ->
                                val mutable = workingList.toMutableList()
                                mutable[idx] = item.copy(enabled = checked)
                                workingList = mutable
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(workingList) }) {
                Text("SAVE LAYOUT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}
