package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.LanguageRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import com.pandulapeter.campfire.util.mapToLanguage
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.toggle

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(homeCallbacks: HomeFragment.HomeCallbacks?,
                       songInfoRepository: SongInfoRepository,
                       playlistRepository: PlaylistRepository,
                       userPreferenceRepository: UserPreferenceRepository,
                       private val languageRepository: LanguageRepository) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository, playlistRepository) {
    val isSearchInputVisible = ObservableBoolean(userPreferenceRepository.query.isNotEmpty())
    val query = ObservableField(userPreferenceRepository.query)
    val shouldShowViewOptions = ObservableBoolean(false)
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val shouldShowDownloadedOnly = ObservableBoolean(userPreferenceRepository.shouldShowDownloadedOnly)
    val isSortedByTitle = ObservableBoolean(userPreferenceRepository.isSortedByTitle)
    val languageFilters = ObservableField(HashMap<Language, ObservableBoolean>())
    val totalItemCount = ObservableInt()
    val filteredItemCount = ObservableInt()
    val shouldDisplaySubtitle = ObservableBoolean()

    init {
        isSearchInputVisible.onPropertyChanged { if (it) query.set("") else userPreferenceRepository.query = "" }
        query.onPropertyChanged { userPreferenceRepository.query = it }
        shouldShowDownloadedOnly.onPropertyChanged { userPreferenceRepository.shouldShowDownloadedOnly = it }
        isSortedByTitle.onPropertyChanged { userPreferenceRepository.isSortedByTitle = it }
        filteredItemCount.onPropertyChanged { shouldDisplaySubtitle.set(it != totalItemCount.get()) }
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val downloadedSongs = songInfoRepository.getDownloadedSongs()
        val downloadedSongIds = downloadedSongs.map { it.id }
        val librarySongs = songInfoRepository.getLibrarySongs()
            .filterWorkInProgress()
            .filterExplicit()
        totalItemCount.set(librarySongs.size)
        val filteredItems = librarySongs
            .filterByLanguages()
            .filterDownloaded()
            .filterByQuery()
            .sort()
            .map { songInfo ->
                SongInfoViewModel(
                    songInfo,
                    downloadedSongIds.contains(songInfo.id),
                    downloadedSongs.firstOrNull { songInfo.id == it.id }?.version ?: 0 != songInfo.version ?: 0)
            }
        filteredItemCount.set(filteredItems.size)
        return filteredItems
    }

    override fun onUpdate() {
        isLoading.set(songInfoRepository.isLoading)
        languageRepository.getLanguages().let { languages ->
            if (languages != languageFilters.get().keys.toList()) {
                languageFilters.get().clear()
                languages.forEach { language ->
                    languageFilters.get().put(
                        language,
                        ObservableBoolean(languageRepository.isLanguageFilterEnabled(language)).apply {
                            onPropertyChanged { languageRepository.setLanguageFilterEnabled(language, it) }
                        })
                }
                languageFilters.notifyChange()
            }
        }
        super.onUpdate()
    }

    fun forceRefresh() = songInfoRepository.updateDataSet { shouldShowErrorSnackbar.set(true) }

    fun showOrHideSearchInput() = isSearchInputVisible.toggle()

    fun addOrRemoveSongFromDownloads(songInfo: SongInfo) =
        if (songInfoRepository.isSongDownloaded(songInfo.id)) {
            playlistRepository.removeSongFromFavorites(songInfo.id)
            songInfoRepository.removeSongFromDownloads(songInfo.id)
        } else {
            songInfoRepository.addSongToDownloads(songInfo)
        }

    fun showViewOptions() = shouldShowViewOptions.set(true)

    fun isHeader(position: Int) = position == 0 ||
        if (isSortedByTitle.get()) {
            adapter.items[position].songInfo.title[0] != adapter.items[position - 1].songInfo.title[0]
        } else {
            adapter.items[position].songInfo.artist[0] != adapter.items[position - 1].songInfo.artist[0]
        }

    fun getHeaderTitle(position: Int) = (if (isSortedByTitle.get()) adapter.items[position].songInfo.title[0] else adapter.items[position].songInfo.artist[0]).toString()

    private fun List<SongInfo>.filterByLanguages() = filter { languageRepository.isLanguageFilterEnabled(it.language.mapToLanguage()) }

    private fun List<SongInfo>.filterDownloaded() = if (shouldShowDownloadedOnly.get()) filter { songInfoRepository.isSongDownloaded(it.id) } else this

    //TODO: Handle special characters, prioritize results that begin with the query.
    private fun List<SongInfo>.filterByQuery() = if (isSearchInputVisible.get()) {
        val query = query.get().trim()
        filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    } else this

    //TODO: Handle special characters
    private fun List<SongInfo>.sort() = sortedBy { if (isSortedByTitle.get()) it.title else it.artist }
}