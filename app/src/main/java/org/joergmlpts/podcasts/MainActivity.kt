package org.joergmlpts.podcasts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.joergmlpts.podcasts.ui.components.PlaybackSpeedSheet
import org.joergmlpts.podcasts.ui.screens.episode.EpisodeScreen
import org.joergmlpts.podcasts.ui.screens.episode.EpisodeViewModel
import org.joergmlpts.podcasts.ui.screens.grid.PodcastGridScreen
import org.joergmlpts.podcasts.ui.screens.grid.PodcastGridViewModel
import org.joergmlpts.podcasts.ui.theme.PodcastsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PodcastsApp
        val playerManager = app.playerManager

        setContent {
            PodcastsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PodcastsNavigation(
                        app = app,
                        onPlayEpisode = { episode, podcast ->
                            playerManager.playEpisode(episode, podcast)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PodcastsNavigation(
    app: PodcastsApp,
    onPlayEpisode: (org.joergmlpts.podcasts.data.local.EpisodeEntity, org.joergmlpts.podcasts.data.local.PodcastEntity) -> Unit
) {
    val navController = rememberNavController()
    val playerState by app.playerManager.uiState.collectAsStateWithLifecycle()
    var showSpeedSheet by remember { mutableStateOf(value = false) }

    NavHost(
        navController = navController,
        startDestination = "grid"
    ) {
        composable("grid") {
            val gridViewModel: PodcastGridViewModel = viewModel(
                factory = PodcastGridViewModel.Factory(app.repository)
            )

            PodcastGridScreen(
                viewModel = gridViewModel,
                playerState = playerState,
                onTogglePlayPause = { app.playerManager.togglePlayPause() },
                onRewind15s = { app.playerManager.rewind15s() },
                onForward30s = { app.playerManager.forward30s() },
                onOpenSpeedSheet = { showSpeedSheet = true },
                onStopPlayback = { app.playerManager.stopPlayback() },
                onPodcastClick = { podcastId ->
                    navController.navigate("episode/$podcastId")
                }
            )
        }

        composable(
            route = "episode/{podcastId}",
            arguments = listOf(navArgument("podcastId") { type = NavType.StringType })
        ) { backStackEntry ->
            val podcastId = backStackEntry.arguments?.getString("podcastId") ?: ""
            val episodeViewModel: EpisodeViewModel = viewModel(
                factory = EpisodeViewModel.Factory(podcastId, app.repository)
            )

            EpisodeScreen(
                viewModel = episodeViewModel,
                playerState = playerState,
                onTogglePlayPause = { app.playerManager.togglePlayPause() },
                onRewind15s = { app.playerManager.rewind15s() },
                onForward30s = { app.playerManager.forward30s() },
                onOpenSpeedSheet = { showSpeedSheet = true },
                onStopPlayback = { app.playerManager.stopPlayback() },
                onPlayEpisode = onPlayEpisode,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }

    if (showSpeedSheet) {
        PlaybackSpeedSheet(
            currentSpeed = playerState.playbackSpeed,
            speedOptions = app.playerManager.speedOptions,
            onSpeedSelected = { newSpeed ->
                app.playerManager.setSpeed(newSpeed)
            },
            onDismiss = { showSpeedSheet = false }
        )
    }
}
