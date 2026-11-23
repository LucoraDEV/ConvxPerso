/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens

import androidx.compose.foundation.background

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import com.convx.music.db.entities.Playlist
import com.convx.music.db.entities.PlaylistEntity
import com.convx.music.ui.component.PlaylistGridItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.convx.music.ui.ios.compat.AppTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import com.convx.music.LocalDatabase
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.ui.component.HomeImageBackground
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.component.NavigationTitle
import com.convx.music.viewmodels.NewReleaseViewModel
import com.convx.music.ui.component.rememberAppBackgroundColor
import com.convx.music.ui.theme.AppPage
import com.convx.music.ui.theme.LocalPageMetrics
import com.convx.music.ui.theme.LocalMetadataColor
import com.convx.music.viewmodels.HomeViewModel


/** Maximum number of recommendation sections included in the home feed. */
internal const val MaxSimilarSections = 4

/** Maximum number of browse sections included in the home feed. */
internal const val MaxHomePageSections = 6

/** Keep local grids to two rows; deeper browsing is available in Library. */
internal const val LocalShelfRows = 2

/** Playlist and folder rows on Home. The rest are one tap away in Library. */
internal const val LocalListPreview = 6

sealed class HomeSection(val id: String, val baseWeight: Int) {
    /** The "star of the day" card. Always first, never shuffled. */
    data object Hero : HomeSection("hero", 110)
    data object SpeedDial : HomeSection("speed_dial", 100)
    data object QuickPicks : HomeSection("quick_picks", 90)
    data object DailyDiscover : HomeSection("daily_discover", 80)
    data object ListenAgain : HomeSection("keep_listening", 50)
    data object TopSongs : HomeSection("top_songs", 55)
    data object AccountPlaylists : HomeSection("account_playlists", 40)
    data object ForgottenFavorites : HomeSection("forgotten_favorites", 30)
    data object FromTheCommunity : HomeSection("from_the_community", 20)
    data class SimilarRecommendation(val index: Int) : HomeSection("similar_recommendation_$index", 10)
    data class HomePageSection(val index: Int) : HomeSection("home_page_section_$index", 10)
    data object MoodAndGenres : HomeSection("mood_and_genres", 5)
}

/** Picks the Home for the current mode before either view model exists, so local mode never builds the online feed. */
@Composable
fun HomeRoute(navController: NavController, snackbarHostState: SnackbarHostState) {
    if (com.convx.music.LocalLocalOnlyMode.current) {
        LocalHomeScreen(navController)
    } else {
        HomeScreen(navController)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current.get()
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isPlaying = playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata = playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val home by viewModel.uiState.collectAsStateWithLifecycle()
    val playlists by remember(database) { database.playlistsByUpdatedDateAsc() }
        .collectAsStateWithLifecycle(emptyList())
    val recentEvents by remember(database) { database.recentEvents(30) }
        .collectAsStateWithLifecycle(emptyList())
    val recentSongs = remember(recentEvents) { recentEvents.map { it.song }.distinctBy { it.id }.take(10) }
    val releasesViewModel: NewReleaseViewModel = hiltViewModel()
    val releases by releasesViewModel.newReleaseAlbums.collectAsStateWithLifecycle()
    val newReleaseTitle = stringResource(R.string.new_release_albums)
    val listState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState()
    val refresh = {
        viewModel.refresh()
        releasesViewModel.refresh()
    }

    val entry by navController.currentBackStackEntryAsState()
    val scrollToTop = entry?.savedStateHandle?.getStateFlow("scrollToTop", false)
        ?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(scrollToTop) {
        if (scrollToTop == true) {
            listState.animateScrollToItem(0)
            entry?.savedStateHandle?.set("scrollToTop", false)
        }
    }
    NetworkReload(onReload = refresh)

    val itemHandlers = rememberHomeItemHandlers(
        navController, playerConnection, menuState, haptic, scope, mediaMetadata, isPlaying,
    )
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val sideInset = insets.calculateStartPadding(direction)
    val deps = HomeSectionDeps(
        context = LocalContext.current,
        randomItem = viewModel::getRandomItem,
        navController = navController,
        playerConnection = playerConnection,
        menuState = menuState,
        haptic = haptic,
        scope = scope,
        sideInset = sideInset,
        localGridItem = itemHandlers.localGridItem,
        onLocalItemClick = itemHandlers.onLocalItemClick,
        isScrolling = { listState.isScrollInProgress },
    )

    PullToRefreshBox(
        state = refreshState,
        isRefreshing = home.isRefreshing,
        onRefresh = refresh,
    ) {
        Box(Modifier.fillMaxSize().background(rememberAppBackgroundColor(AppTheme.colorScheme.surface))) {
            HomeImageBackground(contentLoaded = true)
            CompositionLocalProvider(
                LocalPageMetrics provides AppPage.Metrics,
                LocalMetadataColor provides AppTheme.colorScheme.onSurfaceVariant,
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(AppPage.ItemSpacing),
                    contentPadding = PaddingValues(
                        start = sideInset,
                        top = maxOf(insets.calculateTopPadding(), if (sideInset > 0.dp) 96.dp else 116.dp),
                        end = insets.calculateRightPadding(direction),
                        bottom = insets.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    item(key = "home_library_title") {
                        NavigationTitle(title = stringResource(R.string.filter_library))
                    }
                    item(key = "home_library_playlists") {
                        val likedTitle = stringResource(R.string.liked)
                        val liked = remember(likedTitle) {
                            Playlist(
                                playlist = PlaylistEntity(id = "liked", name = likedTitle),
                                songCount = 0,
                                songThumbnails = emptyList(),
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            item("liked") {
                                PlaylistGridItem(
                                    playlist = liked,
                                    modifier = Modifier.width(142.dp).clickable {
                                        navController.navigate("auto_playlist/liked")
                                    },
                                    autoPlaylist = true,
                                    sharedArtworkId = "liked",
                                    badges = {},
                                    fillMaxWidth = true,
                                    showIconOnly = true,
                                )
                            }
                            items(playlists.asReversed(), key = { "playlist_" + it.id }) { playlist ->
                                PlaylistGridItem(
                                    playlist = playlist,
                                    modifier = Modifier.width(142.dp).clickable {
                                        navController.navigate("local_playlist/" + playlist.id)
                                    },
                                    badges = {},
                                    fillMaxWidth = true,
                                )
                            }
                        }
                    }
                    if (recentSongs.isNotEmpty()) {
                        item(key = "recently_played_title") {
                            NavigationTitle(title = stringResource(R.string.home_recently_played))
                        }
                        item(key = "recently_played") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                items(recentSongs, key = { it.id }) { song ->
                                    HomeShelfCardBody(
                                        thumbnailUrl = song.song.thumbnailUrl,
                                        title = song.song.title,
                                        subtitle = "",
                                        artworkId = song.album?.id,
                                        modifier = Modifier.clickable { itemHandlers.onLocalItemClick(song) },
                                    )
                                }
                            }
                        }
                    }
                    topSongsSection(deps, home.topSongs, mediaMetadata, isPlaying)
                    figmaRail(deps, mediaMetadata, isPlaying, "new_albums", newReleaseTitle, "new_release", releases.take(12))
                    home.similarRecommendations.orEmpty().take(MaxSimilarSections).forEachIndexed { index, _ ->
                        similarRecommendationsSection(HomeSection.SimilarRecommendation(index), deps, home.similarRecommendations)
                    }
                }
            }
        }
    }
}
