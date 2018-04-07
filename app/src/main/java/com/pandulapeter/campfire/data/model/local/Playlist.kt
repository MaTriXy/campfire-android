package com.pandulapeter.campfire.data.model.local

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = Playlist.TABLE_NAME)
data class Playlist(
    @PrimaryKey() @ColumnInfo(name = ID) val id: String = FAVORITES_ID,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = ORDER) val order: Int = 0,
    @ColumnInfo(name = "songIds") val songIds: List<String> = listOf()
) {
    companion object {
        const val FAVORITES_ID = "favorites"
        const val TABLE_NAME = "playlists"
        const val ID = "id"
        const val ORDER = "order"
    }
}