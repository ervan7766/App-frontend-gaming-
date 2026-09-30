package com.aetherdeck.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aetherdeck.core.database.CanonicalGameWithDetails
import com.aetherdeck.core.models.BackgroundTaskState
import com.aetherdeck.core.models.ContextualError
import com.aetherdeck.core.models.MediaType
import com.aetherdeck.ui.theme.LocalAetherTokens
import com.example.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCoverCard(
    item: CanonicalGameWithDetails,
    showVariantsBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.72f
) {
    val tokens = LocalAetherTokens.current
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused && !tokens.reducedMotion) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "card_focus_scale"
    )

    val game = item.game
    val coverMedia = item.media.firstOrNull { it.mediaType == MediaType.COVER }
        ?: item.media.firstOrNull { it.mediaType == MediaType.BOX_ART }
        ?: item.media.firstOrNull { it.mediaType == MediaType.THUMBNAIL }

    val preferredVariant = item.variants.firstOrNull { it.id == game.preferredVariantId }
        ?: item.variants.firstOrNull { it.preferred }
        ?: item.variants.firstOrNull()

    val borderColor = if (isFocused) {
        tokens.focusBorderColor
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }

    val borderWidth = if (isFocused) {
        tokens.cardBorderWidth + 1.dp
    } else {
        1.dp
    }

    Card(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(borderWidth, borderColor, RoundedCornerShape(tokens.cornerRadius))
            .clip(RoundedCornerShape(tokens.cornerRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("game_card_${game.id}"),
        shape = RoundedCornerShape(tokens.cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 6.dp else 2.dp
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (coverMedia != null && coverMedia.localOrRemoteUri.isNotBlank()) {
                    val saturationMatrix = remember(tokens.artworkSaturation) {
                        ColorMatrix().apply { setToSaturation(tokens.artworkSaturation) }
                    }
                    AsyncImage(
                        model = coverMedia.localOrRemoteUri,
                        contentDescription = game.displayTitle,
                        contentScale = ContentScale.Crop,
                        colorFilter = if (tokens.artworkSaturation != 1.0f) {
                            ColorFilter.colorMatrix(saturationMatrix)
                        } else null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Honest system-generated fallback artwork ("NO ARTWORK")
                    SystemFallbackArtwork(
                        systemId = game.systemId,
                        title = game.displayTitle,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top badges row: System Pill + Variants count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = game.systemId.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    if (showVariantsBadge && item.variants.size > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${item.variants.size} VER",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Bottom-right Favorite touch button (48dp minimum touch target)
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .minimumInteractiveComponentSize()
                        .testTag("fav_btn_${game.id}")
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (game.favorite) "Remove Favorite" else "Add Favorite",
                                tint = if (game.favorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = game.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val regionText = preferredVariant?.region?.takeIf { it != "Unknown" } ?: game.releaseYear?.toString() ?: "ROM"
                    Text(
                        text = regionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (game.multiDiscGroupId != null) {
                        Text(
                            text = "MULTI-DISC",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SystemFallbackArtwork(
    systemId: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val initials = remember(title) {
        title.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
    }
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.no_artwork),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = systemId.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameListRow(
    item: CanonicalGameWithDetails,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val game = item.game
    val preferredVariant = item.variants.firstOrNull { it.id == game.preferredVariantId }
        ?: item.variants.firstOrNull()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("game_row_${game.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(width = 64.dp, height = 44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = game.systemId.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metaParts = buildList {
                    preferredVariant?.region?.takeIf { it != "Unknown" }?.let { add(it) }
                    if (item.variants.size > 1) add("${item.variants.size} Versions")
                    game.releaseYear?.let { add(it.toString()) }
                    if (game.developer.isNotBlank()) add(game.developer)
                }
                Text(
                    text = metaParts.joinToString(" • ").ifEmpty { "Local Game" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (game.favorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            FilledTonalButton(
                onClick = onPlayClick,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.testTag("play_row_btn_${game.id}")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PLAY")
            }
        }
    }
}

@Composable
fun ContextualErrorDialog(
    error: ContextualError,
    onDismiss: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(text = error.title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = "WHAT HAPPENED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = error.whatHappened, style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text(
                        text = "WHY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = error.whyItHappened, style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text(
                        text = "HOW TO FIX",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = error.howToFix, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error.settingsRoute != null) {
                    OutlinedButton(onClick = {
                        val route = error.settingsRoute
                        onDismiss()
                        onOpenSettings(route)
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("OPEN SETTINGS")
                    }
                }
                if (error.canRetry) {
                    Button(onClick = {
                        onDismiss()
                        onRetry()
                    }) {
                        Text("TEST AGAIN")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}

@Composable
fun QuickMenuDialog(
    item: CanonicalGameWithDetails,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenDetails: () -> Unit,
    onScrapeGame: () -> Unit,
    onAddToCollection: () -> Unit,
    onToggleHidden: () -> Unit
) {
    val game = item.game
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = game.displayTitle, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${game.systemId.uppercase()} • ${item.variants.size} Version(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                QuickMenuActionRow(Icons.Filled.PlayArrow, "Play (${item.variants.firstOrNull { it.preferred }?.region ?: "Default"})") {
                    onDismiss(); onPlay()
                }
                QuickMenuActionRow(
                    if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (game.favorite) "Remove from Favorites" else "Add to Favorites"
                ) {
                    onDismiss(); onToggleFavorite()
                }
                QuickMenuActionRow(Icons.Filled.Layers, "Versions (${item.variants.size}) & Emulator Override") {
                    onDismiss(); onOpenDetails()
                }
                QuickMenuActionRow(Icons.Filled.Refresh, "Scrape This Game") {
                    onDismiss(); onScrapeGame()
                }
                QuickMenuActionRow(Icons.Filled.CollectionsBookmark, "Add to Collection") {
                    onDismiss(); onAddToCollection()
                }
                QuickMenuActionRow(Icons.Filled.Edit, "Edit Metadata & Custom Art") {
                    onDismiss(); onOpenDetails()
                }
                QuickMenuActionRow(Icons.Filled.VisibilityOff, if (game.hidden) "Unhide Game" else "Hide Game") {
                    onDismiss(); onToggleHidden()
                }
                QuickMenuActionRow(Icons.Filled.Info, "Game Information") {
                    onDismiss(); onOpenDetails()
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

@Composable
private fun QuickMenuActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun TaskProgressBanner(
    task: BackgroundTaskState,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${task.title}  ${task.processed} / ${task.total}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!task.isCompleted) {
                        TextButton(onClick = onPauseResume) {
                            Icon(
                                imageVector = if (task.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (task.isPaused) "CONTINUE" else "PAUSE")
                        }
                        TextButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("CANCEL")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("DISMISS")
                        }
                    }
                }
            }
            Text(
                text = task.currentItem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task.type == com.aetherdeck.core.models.TaskType.SCRAPE) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (task.metadataDone) "Metadata ✓" else "Metadata ...",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (task.metadataDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (task.coverDone) "Cover ✓" else "Cover ...",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (task.coverDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (task.logoDone) "Logo ✓" else "Logo ...",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (task.logoDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (task.backgroundDone) "Background ✓" else "Background ...",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (task.backgroundDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            val progress = if (task.total > 0) (task.processed.toFloat() / task.total.toFloat()).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ControllerBottomHudBar(
    controllerConnected: Boolean,
    swapAB: Boolean,
    swapXY: Boolean,
    onQuickSearchClick: () -> Unit,
    onAddLibraryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HudPromptChip(button = if (swapAB) "B" else "A", action = "Select")
                    HudPromptChip(button = if (swapAB) "A" else "B", action = "Back")
                    HudPromptChip(button = if (swapXY) "Y" else "X", action = "Context")
                    HudPromptChip(button = if (swapXY) "X" else "Y", action = "Favorite")
                    HudPromptChip(button = "L1/R1", action = "Tab")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onQuickSearchClick,
                        modifier = Modifier.testTag("hud_search_button")
                    ) {
                        Text("[SELECT] Search", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = onAddLibraryClick,
                        modifier = Modifier.testTag("hud_add_library_button")
                    ) {
                        Text("+ ADD GAMES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun HudPromptChip(button: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = button,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = action,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
