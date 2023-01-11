package com.pandulapeter.campfire.data.source.localImpl.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.PlaylistEntity

internal fun PlaylistEntity.toModel() = Playlist(
    id = id,
    title = title,
    songIds = songIds.mapToList(),
    priority = priority
)

internal fun Playlist.toEntity() = PlaylistEntity(
    id = id,
    title = title,
    songIds = songIds.mapToString(),
    priority = priority
)