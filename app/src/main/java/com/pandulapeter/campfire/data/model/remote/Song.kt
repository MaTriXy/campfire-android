package com.pandulapeter.campfire.data.model.remote

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.util.normalize
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize

@Parcelize
@Entity(tableName = Song.TABLE_NAME)
data class Song(
    @PrimaryKey @ColumnInfo(name = ID) @SerializedName(ID) val id: String,
    @ColumnInfo(name = TITLE) @SerializedName(TITLE) val title: String = "",
    @ColumnInfo(name = ARTIST) @SerializedName(ARTIST) val artist: String = "",
    @ColumnInfo(name = LANGUAGE) @SerializedName(LANGUAGE) val language: String? = null,
    @ColumnInfo(name = VERSION) @SerializedName(VERSION) val version: Int? = 0,
    @ColumnInfo(name = POPULARITY) @SerializedName(POPULARITY) val popularity: Int? = 0,
    @ColumnInfo(name = IS_EXPLICIT) @SerializedName(IS_EXPLICIT) val isExplicit: Boolean? = false
) : Parcelable {

    companion object {
        const val TABLE_NAME = "songs"
        const val ID = "id"
        private const val TITLE = "title"
        private const val ARTIST = "artist"
        private const val LANGUAGE = "language"
        private const val VERSION = "version"
        private const val POPULARITY = "popularity"
        private const val IS_EXPLICIT = "isExplicit"
    }

    @IgnoredOnParcel
    @Ignore
    @Transient
    private var normalizedTitle: String? = null

    @IgnoredOnParcel
    @Ignore
    @Transient
    private var normalizedArtist: String? = null

    @IgnoredOnParcel
    @Ignore
    @Transient
    var isNew = false

    fun getNormalizedTitle(): String {
        if (normalizedTitle == null) {
            normalizedTitle = title.normalize()
        }
        return normalizedTitle ?: ""
    }

    fun getNormalizedArtist(): String {
        if (normalizedArtist == null) {
            normalizedArtist = artist.normalize()
        }
        return normalizedArtist ?: ""
    }
}