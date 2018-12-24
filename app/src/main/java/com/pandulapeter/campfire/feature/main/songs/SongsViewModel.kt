package com.pandulapeter.campfire.feature.main.songs

import android.content.Context
import androidx.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.main.shared.baseSongList.OldBaseSongListViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.HeaderItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.normalize
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.onTextChanged
import com.pandulapeter.campfire.util.removePrefixes
import com.pandulapeter.campfire.util.swap

class SongsViewModel(
    context: Context,
    songRepository: SongRepository,
    songDetailRepository: SongDetailRepository,
    preferenceDatabase: PreferenceDatabase,
    playlistRepository: PlaylistRepository,
    analyticsManager: AnalyticsManager,
    val toolbarTextInputView: ToolbarTextInputView, //TODO: Move to Fragment.
    private val updateSearchToggleDrawable: (Boolean) -> Unit, //TODO: Replace with LiveData
    private val onDataLoaded: (languages: List<Language>) -> Unit, //TODO: Replace with LiveData
    private val openSecondaryNavigationDrawer: () -> Unit, //TODO: Replace with LiveData
    private val setFastScrollEnabled: (Boolean) -> Unit //TODO: Replace with LiveData
) : OldBaseSongListViewModel(context, songRepository, songDetailRepository, preferenceDatabase, playlistRepository, analyticsManager) {

    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_SONGS
    override val buttonIcon = R.drawable.ic_filter_and_sort_24dp
    private val popularString = context.getString(R.string.popular_tag)
    private val newString = context.getString(R.string.new_tag)
    override val placeholderText = R.string.songs_placeholder
    val shouldShowEraseButton = ObservableBoolean().apply {
        onPropertyChanged {
            isSwipeRefreshEnabled.set(!it)
        }
    }
    val shouldEnableEraseButton = ObservableBoolean()
    var query = ""
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(true)
                trackSearchEvent()
                shouldEnableEraseButton.set(query.isNotEmpty())
            }
        }
    var shouldShowDownloadedOnly = preferenceDatabase.shouldShowDownloadedOnly
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowDownloadedOnly = value
                updateAdapterItems()
            }
        }
    var shouldShowExplicit = preferenceDatabase.shouldShowExplicit
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowExplicit = value
                updateAdapterItems()
            }
        }
    var sortingMode = SortingMode.fromIntValue(preferenceDatabase.songsSortingMode)
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.songsSortingMode = value.intValue
                updateAdapterItems(true)
            }
        }
    var disabledLanguageFilters = preferenceDatabase.disabledLanguageFilters
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.disabledLanguageFilters = value
                updateAdapterItems(true)
            }
        }
    var languages = mutableListOf<Language>()
    var shouldSearchInTitles = preferenceDatabase.shouldSearchInTitles
        set(value) {
            field = value
            updateAdapterItems(true)
            trackSearchEvent()
        }
    var shouldSearchInArtists = preferenceDatabase.shouldSearchInArtists
        set(value) {
            field = value
            updateAdapterItems(true)
            trackSearchEvent()
        }

    init {
        toolbarTextInputView.apply {
            textInput.onTextChanged { if (isTextInputVisible) query = it }
        }
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_SONGS
        adapter.itemTitleCallback = {
            when (it) {
                is HeaderItemViewModel -> it.title.normalize().removePrefixes()[0].toString()
                is CollectionItemViewModel -> ""
                is SongItemViewModel -> when (sortingMode) {
                    SongsViewModel.SortingMode.TITLE -> it.song.getNormalizedTitle().removePrefixes()[0].toString()
                    SongsViewModel.SortingMode.ARTIST -> it.song.getNormalizedArtist().removePrefixes()[0].toString()
                    SongsViewModel.SortingMode.POPULARITY -> ""
                }
                else -> ""
            }
        }
    }

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        super.onSongRepositoryDataUpdated(data)
        if (data.isNotEmpty()) {
            languages.swap(songRepository.languages)
            onDataLoaded(languages)
        }
    }

    override fun onListUpdated(items: List<ItemViewModel>) {
        super.onListUpdated(items)
        if (songs.toList().isNotEmpty()) {
            buttonText.value = if (toolbarTextInputView.isTextInputVisible) 0 else R.string.filters
            setFastScrollEnabled(sortingMode != SortingMode.POPULARITY)
        }
    }

    override fun onActionButtonClicked() = openSecondaryNavigationDrawer()

    override fun Sequence<Song>.createViewModels() = filterByQuery()
        .filterDownloaded()
        .filterByLanguage()
        .filterExplicit()
        .sort()
        .map { SongItemViewModel(context, songDetailRepository, playlistRepository, it) }
        .toMutableList<ItemViewModel>()
        .apply {
            val headerIndices = mutableListOf<Int>()
            val songsOnly = filterIsInstance<SongItemViewModel>().map { it.song }
            songsOnly.forEachIndexed { index, song ->
                if (when (sortingMode) {
                        SortingMode.TITLE -> {
                            val thisTitleFirstCharacter = song.getNormalizedTitle().removePrefixes()[0]
                            index == 0 || (thisTitleFirstCharacter != songsOnly[index - 1].getNormalizedTitle().removePrefixes()[0] && !thisTitleFirstCharacter.isDigit())
                        }
                        SortingMode.ARTIST -> index == 0 || song.artist != songsOnly[index - 1].artist
                        SortingMode.POPULARITY -> songsOnly[0].isNew && (index == 0 || songsOnly[index].isNew != songsOnly[index - 1].isNew)
                    }
                ) {
                    headerIndices.add(index)
                }
            }
            (headerIndices.size - 1 downTo 0).forEach { position ->
                val index = headerIndices[position]
                add(
                    index, HeaderItemViewModel(
                        when (sortingMode) {
                            SortingMode.TITLE -> songsOnly[index].getNormalizedTitle().removePrefixes()[0].let { if (it.isDigit()) "0 - 9" else it.toString() }
                            SortingMode.ARTIST -> songsOnly[index].artist
                            SortingMode.POPULARITY -> if (!songsOnly[0].isNew) "" else if (songsOnly[index].isNew) newString else popularString
                        }
                    )
                )
            }
        }

    fun restoreToolbarButtons() {
        if (languages.isNotEmpty()) {
            onDataLoaded(languages)
        }
        setFastScrollEnabled(sortingMode != SortingMode.POPULARITY)
    }

    fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                val shouldScrollToTop = !query.isEmpty()
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    textInput.setText("")
                }
                updateSearchToggleDrawable(toolbarTextInputView.isTextInputVisible)
                if (shouldScrollToTop) {
                    updateAdapterItems(!isTextInputVisible)
                }
                buttonText.value = if (toolbarTextInputView.isTextInputVisible) 0 else R.string.filters
            }
            shouldShowEraseButton.set(isTextInputVisible)
        }
    }

    private fun Sequence<Song>.filterByQuery() = if (toolbarTextInputView.isTextInputVisible && query.isNotEmpty()) {
        query.trim().normalize().let { query ->
            filter {
                (it.getNormalizedTitle().contains(query, true) && shouldSearchInTitles) || (it.getNormalizedArtist().contains(query, true) && shouldSearchInArtists)
            }
        }
    } else this

    private fun Sequence<Song>.filterDownloaded() = if (shouldShowDownloadedOnly) filter { songDetailRepository.isSongDownloaded(it.id) } else this

    private fun Sequence<Song>.filterByLanguage() = filter { !disabledLanguageFilters.contains(it.language ?: Language.Unknown.id) }

    private fun Sequence<Song>.filterExplicit() = if (!shouldShowExplicit) filter { it.isExplicit != true } else this

    private fun Sequence<Song>.sort() = when (sortingMode) {
        SortingMode.TITLE -> sortedBy { it.getNormalizedArtist().removePrefixes().toString() }.sortedBy { it.getNormalizedTitle().removePrefixes().toString() }
        SortingMode.ARTIST -> sortedBy { it.getNormalizedTitle().removePrefixes() }.sortedBy { it.getNormalizedArtist().removePrefixes() }
        SortingMode.POPULARITY -> sortedBy { it.getNormalizedArtist().removePrefixes() }
            .sortedBy { it.getNormalizedTitle().removePrefixes() }
            .sortedByDescending { it.popularity }.sortedByDescending { it.isNew }
    }

    private fun trackSearchEvent() {
        if (query.isNotEmpty()) {
            analyticsManager.onSongsSearchQueryChanged(query, shouldSearchInArtists, shouldSearchInTitles)
        }
    }

    enum class SortingMode(val intValue: Int) {
        TITLE(0),
        ARTIST(1),
        POPULARITY(2);

        companion object {
            fun fromIntValue(value: Int) = SortingMode.values().find { it.intValue == value } ?: TITLE
        }
    }
}