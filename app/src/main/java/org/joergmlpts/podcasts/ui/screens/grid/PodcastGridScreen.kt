package org.joergmlpts.podcasts.ui.screens.grid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.joergmlpts.podcasts.playback.PlayerUiState
import org.joergmlpts.podcasts.ui.components.AddPodcastDialog
import org.joergmlpts.podcasts.ui.components.MiniPlayer
import org.joergmlpts.podcasts.ui.components.PodcastCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastGridScreen(
    viewModel: PodcastGridViewModel,
    playerState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onRewind15s: () -> Unit,
    onForward30s: () -> Unit,
    onOpenSpeedSheet: () -> Unit,
    onStopPlayback: () -> Unit,
    onPodcastClick: (String) -> Unit,
) {
    val podcasts by viewModel.podcasts.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val addDialogState by viewModel.addDialogState.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Podcasts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    // Manual Refresh Button with rotating indicator
                    IconButton(
                        onClick = { viewModel.manualRefresh() },
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Feeds",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = if (isRefreshing) Modifier.rotate(rotationAngle) else Modifier
                        )
                    }

                    // Add Feed Button
                    IconButton(onClick = { viewModel.openAddDialog() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Podcast RSS",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (playerState.currentEpisode != null) {
                Box(modifier = Modifier.padding(12.dp)) {
                    MiniPlayer(
                        playerState = playerState,
                        onTogglePlayPause = onTogglePlayPause,
                        onRewind15s = onRewind15s,
                        onForward30s = onForward30s,
                        onOpenSpeedSheet = onOpenSpeedSheet,
                        onStopPlayback = onStopPlayback,
                        onClickPlayer = {
                            playerState.currentPodcast?.let { onPodcastClick(it.id) }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (podcasts.isEmpty()) {
                if (isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Podcasts Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the + button above to subscribe to any podcast RSS feed.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(podcasts, key = { it.id }) { podcast ->
                        PodcastCard(
                            podcast = podcast,
                            onClick = {
                                viewModel.onPodcastClicked(podcast.id)
                                onPodcastClick(podcast.id)
                            }
                        )
                    }
                }
            }

            // Discover & Add Podcast BottomSheet / Dialog
            if (addDialogState.isOpen) {
                AddPodcastDialog(
                    searchQuery = addDialogState.searchQuery,
                    isSearching = addDialogState.isSearching,
                    searchResults = addDialogState.searchResults,
                    subscribingFeedUrl = addDialogState.subscribingFeedUrl,
                    errorMessage = addDialogState.error,
                    onQueryChange = { query -> viewModel.onSearchQueryChange(query) },
                    onSearchSubmit = { query -> viewModel.executeSearch(query) },
                    onSubscribe = { url -> viewModel.subscribeToFeed(url) },
                    onDismiss = { viewModel.closeAddDialog() }
                )
            }
        }
    }
}
