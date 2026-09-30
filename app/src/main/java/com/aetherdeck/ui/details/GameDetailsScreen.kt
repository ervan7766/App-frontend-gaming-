package com.aetherdeck.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aetherdeck.core.database.CanonicalGameEntity
import com.aetherdeck.core.database.CanonicalGameWithDetails
import com.aetherdeck.core.database.CollectionEntity
import com.aetherdeck.core.database.GameSourceEntity
import com.aetherdeck.core.database.GameVariantEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.models.MediaType
import com.aetherdeck.ui.components.SystemFallbackArtwork
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDetailsScreen(
    item: CanonicalGameWithDetails,
    system: SystemEntity?,
    allSystemGames: List<CanonicalGameWithDetails>,
    sourcesMap: Map<String, GameSourceEntity>,
    collections: List<CollectionEntity>,
    onBack: () -> Unit,
    onPlayVariant: (String?) -> Unit,
    onSetPreferredVariant: (String) -> Unit,
    onSplitVariant: (GameVariantEntity) -> Unit,
    onMergeWithGame: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onScrapeThisGame: () -> Unit,
    onSaveMetadata: (
        CanonicalGameEntity,
        String,
        String,
        Int?,
        String,
        String,
        String,
        String,
        String,
        String?,
        String?
    ) -> Unit,
    onPickCustomArtwork: (MediaType) -> Unit,
    onAddToCollection: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val game = item.game
    var showEditMetadata by remember { mutableStateOf(false) }
    var showAddToCollection by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var selectedVariantInfo by remember { mutableStateOf<GameVariantEntity?>(null) }

    val coverMedia = item.media.firstOrNull { it.mediaType == MediaType.COVER }
    val bgMedia = item.media.firstOrNull { it.mediaType == MediaType.BACKGROUND }
        ?: item.media.firstOrNull { it.mediaType == MediaType.HERO }
        ?: item.media.firstOrNull { it.mediaType == MediaType.FANART }
    val logoMedia = item.media.firstOrNull { it.mediaType == MediaType.LOGO }

    val preferredVariant = item.variants.firstOrNull { it.id == game.preferredVariantId }
        ?: item.variants.firstOrNull { it.preferred }
        ?: item.variants.firstOrNull()

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("game_details_screen")
    ) {
        // Optional Hero / Background Layer with dim gradient
        if (bgMedia != null && bgMedia.localOrRemoteUri.isNotBlank()) {
            AsyncImage(
                model = bgMedia.localOrRemoteUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("details_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BACK")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onScrapeThisGame) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SCRAPE THIS GAME")
                        }
                        OutlinedButton(onClick = { showEditMetadata = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EDIT METADATA")
                        }
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Toggle Favorite",
                                tint = if (game.favorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Hero Header: Cover + Metadata + PLAY Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Cover Card
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (coverMedia != null && coverMedia.localOrRemoteUri.isNotBlank()) {
                            AsyncImage(
                                model = coverMedia.localOrRemoteUri,
                                contentDescription = game.displayTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            SystemFallbackArtwork(
                                systemId = game.systemId,
                                title = game.displayTitle,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Title, Badges, Primary Play Action, and Metadata
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (logoMedia != null && logoMedia.localOrRemoteUri.isNotBlank()) {
                            AsyncImage(
                                model = logoMedia.localOrRemoteUri,
                                contentDescription = "${game.displayTitle} Logo",
                                modifier = Modifier.height(48.dp)
                            )
                        }

                        Text(
                            text = game.displayTitle,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetadataPill(system?.name ?: game.systemId.uppercase(), highlight = true)
                            preferredVariant?.region?.takeIf { it != "Unknown" }?.let { MetadataPill("Region: $it") }
                            game.releaseYear?.let { MetadataPill("Year: $it") }
                            if (game.genre.isNotBlank()) MetadataPill(game.genre)
                            if (game.players.isNotBlank()) MetadataPill("${game.players}P")
                            game.rating?.let { MetadataPill("★ ${String.format(Locale.US, "%.1f", it)}") }
                            if (game.userEditedMetadata) MetadataPill("User Edited ✓")
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onPlayVariant(preferredVariant?.id) },
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                                modifier = Modifier.testTag("details_play_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                val verSuffix = preferredVariant?.region?.takeIf { it != "Unknown" }?.let { " ($it)" } ?: ""
                                Text("PLAY$verSuffix", style = MaterialTheme.typography.titleMedium)
                            }

                            FilledTonalButton(onClick = { showAddToCollection = true }) {
                                Icon(Icons.Filled.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("COLLECTION")
                            }

                            if (allSystemGames.size > 1) {
                                OutlinedButton(onClick = { showMergeDialog = true }) {
                                    Icon(Icons.Filled.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("MERGE GAMES")
                                }
                            }
                        }

                        Text(
                            text = game.description.ifBlank {
                                "No description available yet. Use 'SCRAPE THIS GAME' or 'EDIT METADATA' to enrich this entry."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Developer / Publisher / Franchise row
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            if (game.developer.isNotBlank()) {
                                Text("Developer: ${game.developer}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (game.publisher.isNotBlank()) {
                                Text("Publisher: ${game.publisher}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (game.franchise.isNotBlank()) {
                                Text("Franchise: ${game.franchise}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // VERSIONS SELECTOR (USA, Europe, Japan, Multi-Disc, Local vs Arley4d)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "VERSIONS (${item.variants.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "All regional versions and multi-disc entries are grouped under this canonical game.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        item.variants.forEach { variant ->
                            val src = sourcesMap[variant.sourceId]
                            val isPreferred = (game.preferredVariantId == variant.id) || variant.preferred
                            val providerBadge = when (src?.provider?.name) {
                                "ARLEY4D_LOCAL_FAKE_ROM" -> "Arley4d Fake ROM"
                                "ARLEY4D_CATALOG" -> "Arley4d Catalog"
                                "EMULATION_STATION" -> "ES-DE"
                                else -> "Local ROM"
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isPreferred) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = variant.region,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (variant.discNumber != null) {
                                                MetadataPill("Disc ${variant.discNumber}${variant.discTotal?.let { "/$it" } ?: ""}", highlight = true)
                                            }
                                            MetadataPill(variant.languages.ifBlank { "En" })
                                            MetadataPill(providerBadge)
                                            if (isPreferred) {
                                                MetadataPill("Preferred ✓", highlight = true)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = src?.sourceFileName ?: variant.originalTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { onPlayVariant(variant.id) },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("PLAY")
                                        }
                                        if (!isPreferred) {
                                            OutlinedButton(
                                                onClick = { onSetPreferredVariant(variant.id) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("SET PREFERRED")
                                            }
                                        }
                                        if (item.variants.size > 1) {
                                            IconButton(onClick = { onSplitVariant(variant) }) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.CallSplit,
                                                    contentDescription = "Split into separate game"
                                                )
                                            }
                                        }
                                        IconButton(onClick = { selectedVariantInfo = variant }) {
                                            Icon(Icons.Filled.Info, contentDescription = "Variant Information")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Custom Artwork & Game Stats Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("CUSTOM ARTWORK (SAF)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onPickCustomArtwork(MediaType.COVER) }) {
                                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CHANGE COVER")
                                }
                                OutlinedButton(onClick = { onPickCustomArtwork(MediaType.BACKGROUND) }) {
                                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CHANGE BACKGROUND")
                                }
                                OutlinedButton(onClick = { onPickCustomArtwork(MediaType.LOGO) }) {
                                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CHANGE LOGO")
                                }
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("GAME STATS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Play Count: ${game.playCount}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Last Played: ${game.lastPlayed?.let { dateFormatter.format(Date(it)) } ?: "Never played"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Date Added: ${dateFormatter.format(Date(game.dateAdded))}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text("Version Count: ${item.variants.size}", style = MaterialTheme.typography.bodyMedium)
                            val primarySrc = preferredVariant?.let { sourcesMap[it.sourceId] }
                            Text(
                                "Source: ${primarySrc?.provider?.name ?: "Local"} (${primarySrc?.extension?.uppercase() ?: "ROM"})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditMetadata) {
        EditMetadataDialog(
            game = game,
            onDismiss = { showEditMetadata = false },
            onSave = { title, desc, year, dev, pub, genre, players, franchise, emuOverride, coreOverride ->
                onSaveMetadata(game, title, desc, year, dev, pub, genre, players, franchise, emuOverride, coreOverride)
                showEditMetadata = false
            }
        )
    }

    if (showAddToCollection) {
        AlertDialog(
            onDismissRequest = { showAddToCollection = false },
            title = { Text("Add to Manual Collection") },
            text = {
                val manualCols = collections.filter { !it.isSmart }
                if (manualCols.isEmpty()) {
                    Text("No manual collections created yet. Create one in the Collections tab first.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        manualCols.forEach { col ->
                            OutlinedButton(
                                onClick = {
                                    onAddToCollection(col.id)
                                    showAddToCollection = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(col.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToCollection = false }) { Text("CLOSE") }
            }
        )
    }

    if (showMergeDialog) {
        val otherGames = allSystemGames.filter { it.game.id != game.id }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text("Merge Another ${game.systemId.uppercase()} Game Into ${game.displayTitle}") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(otherGames, key = { it.game.id }) { candidate ->
                        OutlinedButton(
                            onClick = {
                                onMergeWithGame(candidate.game.id)
                                showMergeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${candidate.game.displayTitle} (${candidate.variants.size} ver)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMergeDialog = false }) { Text("CANCEL") }
            }
        )
    }

    selectedVariantInfo?.let { v ->
        val src = sourcesMap[v.sourceId]
        AlertDialog(
            onDismissRequest = { selectedVariantInfo = null },
            title = { Text("Version Information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Original Filename: ${src?.sourceFileName ?: v.originalTitle}")
                    Text("Relative Path: ${src?.relativePath ?: "N/A"}")
                    Text("Region: ${v.region}")
                    Text("Languages: ${v.languages}")
                    if (v.revision.isNotBlank()) Text("Revision: ${v.revision}")
                    if (v.version.isNotBlank()) Text("Version: ${v.version}")
                    if (v.discNumber != null) Text("Disc: ${v.discNumber}")
                    Text("Extension: .${v.fileExtension}")
                    Text("Provider: ${src?.provider ?: "LOCAL_ROM"}")
                    Text("Content URI: ${src?.sourceUri ?: "N/A"}", style = MaterialTheme.typography.labelMedium)
                    if (!v.md5.isNullOrBlank()) Text("MD5: ${v.md5}", style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedVariantInfo = null }) { Text("CLOSE") }
            }
        )
    }
}

@Composable
private fun MetadataPill(text: String, highlight: Boolean = false) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EditMetadataDialog(
    game: CanonicalGameEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, Int?, String, String, String, String, String, String?, String?) -> Unit
) {
    var title by remember { mutableStateOf(game.displayTitle) }
    var desc by remember { mutableStateOf(game.description) }
    var yearStr by remember { mutableStateOf(game.releaseYear?.toString().orEmpty()) }
    var dev by remember { mutableStateOf(game.developer) }
    var pub by remember { mutableStateOf(game.publisher) }
    var genre by remember { mutableStateOf(game.genre) }
    var players by remember { mutableStateOf(game.players) }
    var franchise by remember { mutableStateOf(game.franchise) }
    var emuOverride by remember { mutableStateOf(game.emulatorOverrideId.orEmpty()) }
    var coreOverride by remember { mutableStateOf(game.coreOverride.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Game Metadata & Overrides") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                }
                item {
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = yearStr,
                            onValueChange = { yearStr = it },
                            label = { Text("Release Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = players,
                            onValueChange = { players = it },
                            label = { Text("Players") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dev,
                            onValueChange = { dev = it },
                            label = { Text("Developer") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pub,
                            onValueChange = { pub = it },
                            label = { Text("Publisher") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text("Genre") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = franchise,
                            onValueChange = { franchise = it },
                            label = { Text("Franchise") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    HorizontalDivider()
                    Text("Per-Game Emulator Override (Optional)", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = emuOverride,
                        onValueChange = { emuOverride = it },
                        label = { Text("Emulator ID (e.g. ppsspp, retroarch_aarch64)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = coreOverride,
                        onValueChange = { coreOverride = it },
                        label = { Text("Libretro Core (e.g. snes9x_libretro_android.so)") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    title,
                    desc,
                    yearStr.toIntOrNull(),
                    dev,
                    pub,
                    genre,
                    players,
                    franchise,
                    emuOverride.ifBlank { null },
                    coreOverride.ifBlank { null }
                )
            }) {
                Text("SAVE (LOCK MANUAL METADATA)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}
