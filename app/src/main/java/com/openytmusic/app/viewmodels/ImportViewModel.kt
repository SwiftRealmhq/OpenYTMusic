package com.openytmusic.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openytmusic.app.db.MusicDatabase
import com.openytmusic.app.db.entities.PlaylistEntity
import com.openytmusic.app.db.entities.PlaylistSongMap
import com.openytmusic.app.innertube.YouTube
import com.openytmusic.app.innertube.models.PlaylistItem
import com.openytmusic.app.innertube.utils.completed
import com.openytmusic.app.models.toMediaMetadata
import com.openytmusic.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Playlist de la cuenta lista para elegir en la pantalla de importacion. */
data class ImportablePlaylist(
    val id: String,
    val title: String,
    val songCountText: String?,
    val thumbnail: String,
)

sealed interface ImportState {
    data object Idle : ImportState
    data class Importing(val current: Int, val total: Int, val playlistTitle: String) : ImportState
    data class Done(
        val importedPlaylists: Int,
        val importedSongs: Int,
        val failedPlaylists: Int,
    ) : ImportState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    companion object {
        // ID fijo de la playlist "Me gusta" de YouTube Music
        const val LIKED_ID = "LL"
    }

    val loggedIn = MutableStateFlow(false)
    val playlists = MutableStateFlow<List<ImportablePlaylist>>(emptyList())
    val loading = MutableStateFlow(true)
    val loadError = MutableStateFlow(false)
    val selected = MutableStateFlow<Set<String>>(emptySet())
    val includeLiked = MutableStateFlow(false)
    val importState = MutableStateFlow<ImportState>(ImportState.Idle)

    private var importJob: Job? = null

    init {
        loggedIn.value = YouTube.cookie != null
        if (loggedIn.value) {
            loadPlaylists()
        } else {
            loading.value = false
        }
        // Recargar automaticamente al volver del login - si la sesion aparece (o
        // cambia), la pantalla se refresca sola sin salir y reentrar. Se observa el
        // StateFlow de sesion en vez de sondearla cada 700 ms durante toda la vida
        // del ViewModel.
        viewModelScope.launch {
            YouTube.signedIn.collect { session ->
                if (session == loggedIn.value) return@collect
                loggedIn.value = session
                if (session) {
                    selected.value = emptySet()
                    includeLiked.value = false
                    loadPlaylists()
                } else {
                    playlists.value = emptyList()
                    selected.value = emptySet()
                    includeLiked.value = false
                }
            }
        }
    }

    fun loadPlaylists() {
        loading.value = true
        loadError.value = false
        viewModelScope.launch(Dispatchers.IO) {
            // Fuente 1: playlists PROPIAS de la cuenta
            val library = YouTube.libraryPlaylists()
            // Fuente 2: colecciones automaticas (Liked Music, Episodes for Later)
            val liked = YouTube.likedPlaylists()
            if (library.isFailure && liked.isFailure) {
                reportException(library.exceptionOrNull() ?: liked.exceptionOrNull()!!)
            }
            val merged = (library.getOrNull().orEmpty() + liked.getOrNull().orEmpty())
                .distinctBy { it.id }
            playlists.value = merged.map {
                ImportablePlaylist(
                    id = it.id,
                    title = it.title,
                    songCountText = it.songCountText,
                    thumbnail = it.thumbnail
                )
            }
            loadError.value = merged.isEmpty()
            loading.value = false
        }
    }

    fun toggle(playlistId: String) {
        selected.value = selected.value.toMutableSet().apply {
            if (!add(playlistId)) remove(playlistId)
        }
    }

    fun toggleLiked() {
        includeLiked.value = !includeLiked.value
    }

    fun selectAll() {
        selected.value = playlists.value.map { it.id }.toSet()
        includeLiked.value = true
    }

    val selectionCount: Int
        get() = selected.value.size + if (includeLiked.value) 1 else 0

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        importState.value = ImportState.Idle
    }

    fun resetDone() {
        importState.value = ImportState.Idle
    }

    fun startImport() {
        if (importState.value is ImportState.Importing) return
        val targets = playlists.value.filter { it.id in selected.value }
        val withLiked = if (includeLiked.value) {
            // El titulo real llega con la pagina; mientras, uno vacio
            listOf(ImportablePlaylist(LIKED_ID, "", null, "")) + targets
        } else targets
        if (withLiked.isEmpty()) return

        importJob = viewModelScope.launch(Dispatchers.IO) {
            var importedPlaylists = 0
            var importedSongs = 0
            var failed = 0
            val total = withLiked.size

            withLiked.forEachIndexed { index, target ->
                importState.value = ImportState.Importing(index + 1, total, target.title)
                val page = withContext(Dispatchers.IO) {
                    YouTube.playlist(target.id).completed().getOrNull()
                }
                if (page == null || page.songs.isEmpty()) {
                    failed++
                } else {
                    val title = page.playlist.title.ifBlank { target.title }
                    // Dedup: si esta playlist de YT ya fue importada, se actualiza en lugar de duplicar
                    val existing = database.playlistByBrowseId(target.id)
                    val entity: PlaylistEntity = if (existing != null) {
                        PlaylistEntity(id = existing.id, name = title, browseId = target.id)
                    } else {
                        PlaylistEntity(name = title, browseId = target.id)
                    }
                    database.transaction {
                        insert(entity)
                        page.songs.map { song -> song.toMediaMetadata() }
                            .onEach(::insert)
                            .mapIndexed { position, mediaMetadata ->
                                PlaylistSongMap(
                                    songId = mediaMetadata.id,
                                    playlistId = entity.id,
                                    position = position
                                )
                            }
                            .forEach(::insert)
                    }
                    importedPlaylists++
                    importedSongs += page.songs.size
                }
                delay(300) // respiro entre playlists para no irritar el rate limit
            }
            importState.value = ImportState.Done(importedPlaylists, importedSongs, failed)
            importJob = null
        }
    }
}
