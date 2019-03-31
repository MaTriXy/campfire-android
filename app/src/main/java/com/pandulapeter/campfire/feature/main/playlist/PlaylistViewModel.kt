package com.pandulapeter.campfire.feature.main.playlist

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.RecyclerAdapter
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.mutableLiveDataOf
import java.util.Collections

class PlaylistViewModel(
    private val playlistId: String,
    context: Context,
    val appShortcutManager: AppShortcutManager,
    songRepository: SongRepository,
    songDetailRepository: SongDetailRepository,
    preferenceDatabase: PreferenceDatabase,
    playlistRepository: PlaylistRepository,
    analyticsManager: AnalyticsManager,
    interactionBlocker: InteractionBlocker
) : BaseSongListViewModel(context, songRepository, songDetailRepository, preferenceDatabase, playlistRepository, analyticsManager, interactionBlocker) {

    val changeEventRange = MutableLiveData<Pair<Pair<Int, Int>, RecyclerAdapter.Payload>?>()
    val moveEvent = MutableLiveData<Pair<Int, Int>?>()
    var songToDeleteId: String? = null
        private set
    val playlist = MutableLiveData<Playlist?>()
    val songCount = mutableLiveDataOf(-1)
    val isInEditMode = mutableLiveDataOf(false) {
        if (items.value.orEmpty().size > 1) {
            changeEventRange.value = (0 to items.value.orEmpty().size) to RecyclerAdapter.Payload.EditModeChanged(it)
            updateAdapterItems()
        }
    }
    val shouldOpenSongs = MutableLiveData<Boolean?>()
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_PLAYLIST
    override val placeholderText = R.string.playlist_placeholder
    override val buttonIcon = R.drawable.ic_songs

    init {
        buttonText.value = R.string.go_to_songs
        preferenceDatabase.lastScreen = playlistId
    }

    override fun onActionButtonClicked() {
        shouldOpenSongs.value = true
    }

    override fun Sequence<Song>.createViewModels(): List<ItemViewModel> {
        val list = (playlist.value?.songIds ?: listOf<String>())
            .mapNotNull { songId -> find { it.id == songId } }
            .filter { it.id != songToDeleteId }
            .toList()
        return list.map {
            SongItemViewModel(
                context = context,
                songDetailRepository = songDetailRepository,
                playlistRepository = playlistRepository,
                song = it,
                shouldShowPlaylistButton = false,
                shouldShowDragHandle = isInEditMode.value == true && list.size > 1
            )
        }
    }

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        super.onPlaylistsUpdated(playlists)
        playlists.findLast { it.id == playlistId }.let {
            playlist.value = it
        }
    }

    override fun onListUpdated(items: List<ItemViewModel>) {
        super.onListUpdated(items)
        songCount.value = items.size
    }

    fun swapSongsInPlaylist(originalPosition: Int, targetPosition: Int) {
        items.value.orEmpty().let { items ->
            if (originalPosition < targetPosition) {
                for (i in originalPosition until targetPosition) {
                    Collections.swap(items, i, i + 1)
                }
            } else {
                for (i in originalPosition downTo targetPosition + 1) {
                    Collections.swap(items, i, i - 1)
                }
            }
            playlist.value?.let {
                moveEvent.value = originalPosition to targetPosition
                val newList = items.filterIsInstance<SongItemViewModel>().map { songItemViewModel -> songItemViewModel.song.id }.toMutableList()
                it.songIds = newList
                playlistRepository.updatePlaylistSongIds(it.id, newList)
            }
        }
    }

    fun hasSongToDelete() = songToDeleteId != null

    fun deleteSongTemporarily(songId: String) {
        analyticsManager.onSongPlaylistStateChanged(songId, playlistRepository.getPlaylistCountForSong(songId) - 1, AnalyticsManager.PARAM_VALUE_SWIPE_TO_DISMISS, false)
        songToDeleteId = songId
        updateAdapterItems()
    }

    fun cancelDeleteSong() {
        songToDeleteId?.let {
            analyticsManager.onSongPlaylistStateChanged(it, playlistRepository.getPlaylistCountForSong(it), AnalyticsManager.PARAM_VALUE_CANCEL_SWIPE_TO_DISMISS, false)
        }
        songToDeleteId = null
        updateAdapterItems()
    }

    fun deleteSongPermanently() {
        songToDeleteId?.let {
            playlist.value?.let { playlist ->
                val newList = items.value.orEmpty().filterIsInstance<SongItemViewModel>().map { songItemViewModel -> songItemViewModel.song.id }.toMutableList()
                playlist.songIds = newList
                playlistRepository.updatePlaylistSongIds(playlist.id, newList)
            }
            songToDeleteId = null
        }
    }
}