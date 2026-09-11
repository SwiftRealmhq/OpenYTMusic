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
        // Velqi: recargar automaticamente al volver del login - si la sesion
        // aparece (o cambia), la pantalla se refresca sola sin salir y reentrar.
        viewModelScope.launch(Dispatchers.IO) {
            var lastSession: Boolean? = loggedIn.value
            while (true) {
                delay(700)
                val session = YouTube.cookie?.contains("SAPISID") == true
                if (session != lastSession) {
                    lastSession = session
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
    }

    fun loadPlaylists() {
        loading.value = true
        loadError.value = false
        viewModelScope.launch(Dispatchers.IO) {
            // Diagnostico: con que cuenta estamos entrando
            YouTube.accountInfo().onSuccess {
                android.util.Log.d("VELQIDBG", "import: cuenta=${it.name} email=${it.email} handle=${it.channelHandle}")
            }.onFailure {
                android.util.Log.w("VELQIDBG", "import: accountInfo fallo", it)
            }
            // Fuente 1: playlists PROPIAS de la cuenta (aqui esta "yoyoyoy")
            val library = YouTube.libraryPlaylists()
            library.onSuccess {
                android.util.Log.d("VELQIDBG", "import: libraryPlaylists ok, count=${it.size} titles=${it.map { p -> p.title }}")
            }.onFailure {
                android.util.Log.w("VELQIDBG", "import: libraryPlaylists fallo", it)
            }
            // Fuente 2: colecciones automaticas (Liked Music, Episodes for Later)
            val liked = YouTube.likedPlaylists()
            liked.onFailure {
                android.util.Log.w("VELQIDBG", "import: likedPlaylists fallo", it)
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
