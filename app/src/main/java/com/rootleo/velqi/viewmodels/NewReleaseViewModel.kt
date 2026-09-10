package com.rootleo.velqi.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootleo.velqi.innertube.YouTube
import com.rootleo.velqi.innertube.models.AlbumItem
import com.rootleo.velqi.innertube.models.filterExplicit
import com.rootleo.velqi.constants.HideExplicitKey
import com.rootleo.velqi.db.MusicDatabase
import com.rootleo.velqi.db.entities.Artist
import com.rootleo.velqi.utils.dataStore
import com.rootleo.velqi.utils.get
import com.rootleo.velqi.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewReleaseViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    private val _newReleaseAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val newReleaseAlbums = _newReleaseAlbums.asStateFlow()

    private val _featured = MutableStateFlow<List<AlbumItem>>(emptyList())
    val featured = _featured.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albums = _albums.asStateFlow()

    private val _singles = MutableStateFlow<List<AlbumItem>>(emptyList())
    val singles = _singles.asStateFlow()

    private val _eps = MutableStateFlow<List<AlbumItem>>(emptyList())
    val eps = _eps.asStateFlow()

    init {
        viewModelScope.launch {
            YouTube.newReleaseAlbums().onSuccess { albums ->
                val artists: Set<String>
                val favouriteArtists: Set<String>
                database.artistsByCreateDateAsc().first().let { list ->
                    artists = list.map(Artist::id).toHashSet()
                    favouriteArtists = list
                        .filter { it.artist.bookmarkedAt != null }
                        .map { it.id }
                        .toHashSet()
                }
                val sorted = albums
                    .sortedBy { album ->
                        if (album.artists.orEmpty().any { it.id in favouriteArtists }) 0
                        else if (album.artists.orEmpty().any { it.id in artists }) 1
                        else 2
                    }
                    .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                _newReleaseAlbums.value = sorted
                _featured.value = sorted.take(5)
                _albums.value = sorted.filter { it.isAlbumType() }
                _singles.value = sorted.filter { it.isSingleType() }
                _eps.value = sorted.filter { it.isEpType() }
            }.onFailure {
                reportException(it)
            }
        }
    }
}

private fun AlbumItem.isAlbumType(): Boolean {
    val t = type?.lowercase()
    return t == null || t.contains("lbum") // Album / Álbum
}

private fun AlbumItem.isSingleType(): Boolean = type?.lowercase()?.let {
    it.contains("ingle") || it.contains("encillo") // Single / Sencillo
} == true

private fun AlbumItem.isEpType(): Boolean = type?.lowercase()?.let {
    it == "ep" || it.startsWith("ep ") || it.contains(" ep")
} == true