package com.aetherdeck.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.aetherdeck.collections.smart.LogicalJoin
import com.aetherdeck.collections.smart.SmartCollectionDefinition
import com.aetherdeck.collections.smart.SmartCollectionEngine
import com.aetherdeck.collections.smart.SmartRuleCondition
import com.aetherdeck.collections.smart.SmartRuleField
import com.aetherdeck.collections.smart.SmartRuleOperator
import com.aetherdeck.core.database.CanonicalGameWithDetails
import com.aetherdeck.core.database.CollectionEntity
import com.aetherdeck.core.database.CollectionGameCrossRef
import com.aetherdeck.core.database.GameSourceEntity
import com.aetherdeck.core.database.MatchReviewEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.models.ArtworkFilter
import com.aetherdeck.core.models.AutoCategory
import com.aetherdeck.core.models.LibraryFilterState
import com.aetherdeck.core.models.LibraryViewMode
import com.aetherdeck.core.models.PlayedFilter
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.core.models.SortOption
import com.aetherdeck.core.settings.AetherSettingsState
import com.aetherdeck.ui.components.GameCoverCard
import com.aetherdeck.ui.components.GameListRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    games: List<CanonicalGameWithDetails>,
    systems: List<SystemEntity>,
    filterState: LibraryFilterState,
    sortOption: SortOption,
    selectedCategory: AutoCategory,
    settings: AetherSettingsState,
    onUpdateFilter: ((LibraryFilterState) -> LibraryFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onSelectCategory: (AutoCategory) -> Unit,
    onSelectViewMode: (LibraryViewMode) -> Unit,
    onOpenGameDetails: (String) -> Unit,
    onQuickMenuGame: (CanonicalGameWithDetails) -> Unit,
    onToggleFavorite: (CanonicalGameWithDetails) -> Unit,
    onPlayGame: (String) -> Unit,
    onSurpriseMe: () -> Unit,
    onRequestAddFolder: (ProviderType) -> Unit
) {
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // Top Toolbar: Search, Category Pills, View Mode, Filter, Sort, Surprise Me
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = filterState.searchQuery,
                    onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
                    placeholder = { Text("Search games, developers, genres, franchises...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filterState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "") } }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("library_search_input")
                )

                OutlinedButton(onClick = { showFilterDialog = true }) {
                    Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FILTERS")
                }

                Box {
                    OutlinedButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(sortOption.label.uppercase())
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label) },
                                onClick = {
                                    onSelectSort(opt)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        val modes = LibraryViewMode.entries
                        val next = modes[(modes.indexOf(settings.defaultLibraryView) + 1) % modes.size]
                        onSelectViewMode(next)
                    }
                ) {
                    val icon = when (settings.defaultLibraryView) {
                        LibraryViewMode.LIST -> Icons.Filled.ViewList
                        LibraryViewMode.CAROUSEL, LibraryViewMode.COVER_FLOW -> Icons.Filled.ViewCarousel
                        else -> Icons.Filled.GridView
                    }
                    Icon(icon, contentDescription = "Cycle View Mode (${settings.defaultLibraryView.label})")
                }

                FilledTonalButton(onClick = onSurpriseMe) {
                    Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SURPRISE ME")
                }
            }

            // Automatic Categories Bar
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AutoCategory.entries, key = { it.id }) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(cat.title) }
                    )
                }
            }
        }

        if (games.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("No matching games in this view", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onResetFilters) {
                            Text("RESET FILTERS")
                        }
                        Button(onClick = { onRequestAddFolder(ProviderType.LOCAL_ROM) }) {
                            Text("ADD ROM FOLDER")
                        }
                    }
                }
            }
        } else {
            when (settings.defaultLibraryView) {
                LibraryViewMode.LIST -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(games, key = { it.game.id }) { item ->
                            GameListRow(
                                item = item,
                                onClick = { onOpenGameDetails(item.game.id) },
                                onLongClick = { onQuickMenuGame(item) },
                                onPlayClick = { onPlayGame(item.game.id) },
                                onFavoriteToggle = { onToggleFavorite(item) }
                            )
                        }
                    }
                }

                LibraryViewMode.CAROUSEL, LibraryViewMode.COVER_FLOW -> {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 24.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(games, key = { it.game.id }) { item ->
                            GameCoverCard(
                                item = item,
                                showVariantsBadge = settings.showVariantsBadge,
                                onClick = { onOpenGameDetails(item.game.id) },
                                onLongClick = { onQuickMenuGame(item) },
                                onFavoriteToggle = { onToggleFavorite(item) },
                                onPlayClick = { onPlayGame(item.game.id) },
                                modifier = Modifier.width(230.dp)
                            )
                        }
                    }
                }

                LibraryViewMode.GRID, LibraryViewMode.COMPACT_GRID -> {
                    val minColWidth = if (settings.defaultLibraryView == LibraryViewMode.COMPACT_GRID) {
                        115.dp
                    } else {
                        settings.cardSizePreset.minColumnWidthDp.dp
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = minColWidth),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(games, key = { it.game.id }) { item ->
                            GameCoverCard(
                                item = item,
                                showVariantsBadge = settings.showVariantsBadge,
                                onClick = { onOpenGameDetails(item.game.id) },
                                onLongClick = { onQuickMenuGame(item) },
                                onFavoriteToggle = { onToggleFavorite(item) },
                                onPlayClick = { onPlayGame(item.game.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        LibraryFilterSheetDialog(
            filter = filterState,
            systems = systems,
            onUpdateFilter = onUpdateFilter,
            onReset = {
                onResetFilters()
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilterSheetDialog(
    filter: LibraryFilterState,
    systems: List<SystemEntity>,
    onUpdateFilter: ((LibraryFilterState) -> LibraryFilterState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Library Filters") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("System", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filter.systemId == null,
                            onClick = { onUpdateFilter { it.copy(systemId = null) } },
                            label = { Text("All Systems") }
                        )
                        systems.forEach { sys ->
                            FilterChip(
                                selected = filter.systemId == sys.id,
                                onClick = { onUpdateFilter { it.copy(systemId = sys.id) } },
                                label = { Text(sys.shortName) }
                            )
                        }
                    }
                }
                item {
                    Text("Region", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "All", "USA" to "USA", "Europe" to "Europe", "Japan" to "Japan", "World" to "World", "Spain" to "Spain").forEach { (reg, label) ->
                            FilterChip(
                                selected = filter.region == reg,
                                onClick = { onUpdateFilter { it.copy(region = reg) } },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                item {
                    Text("Provider", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filter.providerType == null,
                            onClick = { onUpdateFilter { it.copy(providerType = null) } },
                            label = { Text("All") }
                        )
                        ProviderType.entries.forEach { pt ->
                            FilterChip(
                                selected = filter.providerType == pt,
                                onClick = { onUpdateFilter { it.copy(providerType = pt) } },
                                label = { Text(pt.name) }
                            )
                        }
                    }
                }
                item {
                    Text("Play Status & Artwork", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filter.favoritesOnly,
                            onClick = { onUpdateFilter { it.copy(favoritesOnly = !it.favoritesOnly) } },
                            label = { Text("Favorites Only") }
                        )
                        PlayedFilter.entries.forEach { pf ->
                            FilterChip(
                                selected = filter.playedStatus == pf,
                                onClick = { onUpdateFilter { it.copy(playedStatus = pf) } },
                                label = { Text(pf.name) }
                            )
                        }
                        ArtworkFilter.entries.forEach { af ->
                            FilterChip(
                                selected = filter.artworkStatus == af,
                                onClick = { onUpdateFilter { it.copy(artworkStatus = af) } },
                                label = { Text(af.name) }
                            )
                        }
                        FilterChip(
                            selected = filter.missingMetadataOnly,
                            onClick = { onUpdateFilter { it.copy(missingMetadataOnly = !it.missingMetadataOnly) } },
                            label = { Text("Missing Metadata") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("APPLY")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("RESET ALL")
            }
        }
    )
}

@Composable
fun SystemsOverviewScreen(
    systems: List<SystemEntity>,
    games: List<CanonicalGameWithDetails>,
    onSelectSystem: (String) -> Unit,
    onScrapeSystem: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 240.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("systems_screen"),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(systems, key = { it.id }) { sys ->
            val sysGames = games.filter { it.game.systemId == sys.id }
            Card(
                onClick = { onSelectSystem(sys.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = sys.shortName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "${sysGames.size} Games",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = sys.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${sys.manufacturer} • ${sys.releaseYear}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Emulator: ${sys.selectedEmulatorId ?: sys.defaultEmulatorId}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sysGames.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AssistChip(
                                onClick = { onScrapeSystem(sys.id) },
                                label = { Text("SCRAPE SYSTEM") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionsAndMatchReviewScreen(
    collections: List<CollectionEntity>,
    crossRefs: List<CollectionGameCrossRef>,
    allGames: List<CanonicalGameWithDetails>,
    sourcesMap: Map<String, GameSourceEntity>,
    pendingReviews: List<MatchReviewEntity>,
    onCreateManualCollection: (String, String) -> Unit,
    onCreateSmartCollection: (String, String, SmartCollectionDefinition) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onMergeGames: (String, String, String?) -> Unit,
    onOpenGameDetails: (String) -> Unit
) {
    var showCreateManual by remember { mutableStateOf(false) }
    var showCreateSmart by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("collections_screen"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("COLLECTIONS & SMART LISTS", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Organize manual playlists or automatic rule-based smart collections.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showCreateManual = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("MANUAL COLLECTION")
                    }
                    FilledTonalButton(onClick = { showCreateSmart = true }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SMART COLLECTION")
                    }
                }
            }
        }

        // Pending Match Reviews (No Over-Merge Safeguard)
        if (pendingReviews.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MATCH REVIEW (${pendingReviews.size} Ambiguous Regional Candidates)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "AetherDeck did not auto-merge these titles because they lacked a verified shared external ID.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        pendingReviews.forEach { review ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${review.candidateTitleA} ↔ ${review.candidateTitleB} (${review.systemId.uppercase()})",
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        onMergeGames(review.candidateGameIdA, review.candidateGameIdB, review.id)
                                    }
                                ) {
                                    Icon(Icons.Filled.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("MERGE")
                                }
                            }
                        }
                    }
                }
            }
        }

        items(collections, key = { it.id }) { col ->
            val matchingGames = if (col.isSmart) {
                val def = SmartCollectionEngine.decodeDefinition(col.smartRulesJson)
                SmartCollectionEngine.evaluate(def, allGames, sourcesMap)
            } else {
                val ids = crossRefs.filter { it.collectionId == col.id }.map { it.canonicalGameId }.toSet()
                allGames.filter { ids.contains(it.game.id) }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(col.name, style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (col.isSmart) "SMART" else "MANUAL",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (col.description.isNotBlank()) {
                                Text(
                                    text = col.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${matchingGames.size} Games", style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { onDeleteCollection(col.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Collection")
                            }
                        }
                    }

                    if (matchingGames.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(matchingGames, key = { it.game.id }) { g ->
                                AssistChip(
                                    onClick = { onOpenGameDetails(g.game.id) },
                                    label = { Text("${g.game.displayTitle} (${g.game.systemId.uppercase()})") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateManual) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateManual = false },
            title = { Text("New Manual Collection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Collection Name (e.g. Games to finish)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreateManualCollection(name, desc)
                    showCreateManual = false
                }) {
                    Text("CREATE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateManual = false }) { Text("CANCEL") }
            }
        )
    }

    if (showCreateSmart) {
        var name by remember { mutableStateOf("90s RPG Classics") }
        var field by remember { mutableStateOf(SmartRuleField.GENRE) }
        var operator by remember { mutableStateOf(SmartRuleOperator.CONTAINS) }
        var value by remember { mutableStateOf("RPG") }
        var secondSystemValue by remember { mutableStateOf("psx,snes") }

        AlertDialog(
            onDismissRequest = { showCreateSmart = false },
            title = { Text("New Smart Collection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Smart Collection Name") },
                        singleLine = true
                    )
                    Text("Rule 1 Field:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SmartRuleField.GENRE,
                            SmartRuleField.SYSTEM,
                            SmartRuleField.YEAR,
                            SmartRuleField.DEVELOPER,
                            SmartRuleField.REGION,
                            SmartRuleField.FAVORITE
                        ).forEach { f ->
                            FilterChip(
                                selected = field == f,
                                onClick = { field = f },
                                label = { Text(f.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("${field.label} ${operator.label} value") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secondSystemValue,
                        onValueChange = { secondSystemValue = it },
                        label = { Text("AND System in (comma-separated, optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val conds = mutableListOf(SmartRuleCondition(field, operator, value))
                    if (secondSystemValue.isNotBlank()) {
                        conds.add(SmartRuleCondition(SmartRuleField.SYSTEM, SmartRuleOperator.IN, secondSystemValue))
                    }
                    onCreateSmartCollection(
                        name,
                        "Auto-rule: ${field.label} ${operator.label} $value",
                        SmartCollectionDefinition(join = LogicalJoin.AND, conditions = conds)
                    )
                    showCreateSmart = false
                }) {
                    Text("CREATE SMART COLLECTION")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSmart = false }) { Text("CANCEL") }
            }
        )
    }
}
