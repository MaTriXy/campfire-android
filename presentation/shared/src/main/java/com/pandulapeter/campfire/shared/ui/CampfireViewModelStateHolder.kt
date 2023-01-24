package com.pandulapeter.campfire.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class CampfireViewModelStateHolder(
    private val viewModel: CampfireViewModel,
    private val coroutineScope: CoroutineScope,
    val uiMode: State<UserPreferences.UiMode?>,
    val userPreferences: State<UserPreferences?>,
    val selectedNavigationDestination: State<CampfireViewModel.NavigationDestination?>,
    val navigationDestinations: State<List<CampfireViewModel.NavigationDestinationWrapper>>,
    val isRefreshing: State<Boolean>,
    val query: State<String>,
    val databases: State<List<Database>>,
    val songs: State<List<Song>>
) {
    fun onQueryChanged(query: String) = viewModel.onQueryChanged(query)

    fun onSongClicked(song: Song) = viewModel.onSongClicked(song)

    fun onForceRefreshTriggered() = coroutineScope.launch { viewModel.onForceRefreshTriggered() }

    fun onDeleteLocalDataPressed() = coroutineScope.launch { viewModel.onDeleteLocalDataPressed() }

    fun onDatabaseEnabledChanged(databases: List<Database>, database: Database, isEnabled: Boolean) = coroutineScope.launch {
        viewModel.onDatabaseEnabledChanged(
            databases = databases,
            database = database,
            isEnabled = isEnabled
        )
    }

    fun onDatabaseSelectedChanged(userPreferences: UserPreferences, database: Database, isSelected: Boolean) = coroutineScope.launch {
        viewModel.onDatabaseSelectedChanged(
            userPreferences = userPreferences,
            database = database,
            isSelected = isSelected
        )
    }

    fun onShouldShowExplicitSongsChanged(userPreferences: UserPreferences, shouldShowExplicitSongs: Boolean) = coroutineScope.launch {
        viewModel.onShouldShowExplicitSongsChanged(
            userPreferences = userPreferences,
            shouldShowExplicitSongs = shouldShowExplicitSongs
        )
    }

    fun onShouldShowSongsWithoutChordsChanged(userPreferences: UserPreferences, shouldShowSongsWithoutChords: Boolean) = coroutineScope.launch {
        viewModel.onShouldShowSongsWithoutChordsChanged(
            userPreferences = userPreferences,
            shouldShowSongsWithoutChords = shouldShowSongsWithoutChords
        )
    }

    companion object {

        @Composable
        fun fromViewModel(viewModel: CampfireViewModel) = CampfireViewModelStateHolder(
            viewModel = viewModel,
            coroutineScope = rememberCoroutineScope(),
            uiMode = viewModel.uiMode.collectAsState(null),
            userPreferences = viewModel.userPreferences.collectAsState(null),
            selectedNavigationDestination = viewModel.selectedNavigationDestination.collectAsState(initial = null),
            navigationDestinations = viewModel.navigationDestinations.collectAsState(initial = emptyList()),
            isRefreshing = viewModel.shouldShowLoadingIndicator.collectAsState(false),
            query = viewModel.query.collectAsState(""),
            databases = viewModel.databases.collectAsState(emptyList()),
            songs = viewModel.songs.collectAsState(emptyList())
        )
    }
}