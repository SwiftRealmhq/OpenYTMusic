package com.openytmusic.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openytmusic.app.innertube.YouTube
import com.openytmusic.app.constants.AppUsageByDayKey
import com.openytmusic.app.constants.AppUsageTimeKey
import com.openytmusic.app.constants.StatPeriod
import com.openytmusic.app.db.MusicDatabase
import com.openytmusic.app.utils.AppUsageTracker
import com.openytmusic.app.utils.dataStore
import com.openytmusic.app.utils.parseDayUsage
import com.openytmusic.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    val database: MusicDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val statPeriod = MutableStateFlow(StatPeriod.`1_WEEK`)

    val mostPlayedSongs = statPeriod.flatMapLatest { period ->
        database.mostPlayedSongs(period.toTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedArtists = statPeriod.flatMapLatest { period ->
        database.mostPlayedArtists(period.toTimeMillis()).map { artists ->
            artists.filter { it.artist.isYouTubeArtist }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    val mostPlayedAlbums = statPeriod.flatMapLatest { period ->
        database.mostPlayedAlbums(period.toTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ===== Estadisticas reales =====

    // Tick de 1s para los contadores en tiempo real
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1000)
        }
    }

    // Tiempo total escuchado en el periodo (suma de duracion reproducida)
    val totalPlayTime = statPeriod.flatMapLatest { period ->
        database.totalPlayTime(period.toTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    // Numero total de reproducciones en el periodo
    val totalPlayCount = statPeriod.flatMapLatest { period ->
        database.totalPlayCount(period.toTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // Canciones con su numero de veces reproducidas en el periodo (top 10)
    val topSongsWithCount = statPeriod.flatMapLatest { period ->
        database.songPlayCounts(period.toTimeMillis(), limit = 10)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Cancion mas escuchada del periodo (con su numero de veces)
    val topSongWithCount = topSongsWithCount
        .map { it.take(1) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Tiempo escuchado por dia (eventos del periodo agrupados por dia)
    val playTimeByDay = statPeriod.flatMapLatest { period ->
        database.eventsSince(period.toTimeMillis()).map { events ->
            events.groupBy { it.timestamp.toLocalDate() }
                .mapValues { (_, list) -> list.sumOf { it.playTime } }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // Tiempo total con la app abierta, en tiempo real:
    // persistido (DataStore) + milis de la sesion actual, refrescado cada 1s
    val appUsageTime = combine(
        context.dataStore.data.map { it[AppUsageTimeKey] ?: 0L },
        ticker
    ) { persisted, _ -> persisted + AppUsageTracker.currentSessionMillis() }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    // Uso de la app por dia (desglose diario)
    val appUsageByDay = context.dataStore.data
        .map { parseDayUsage(it[AppUsageByDayKey]) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        // fetch missing artist metadata
        viewModelScope.launch {
            mostPlayedArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(it.lastUpdateTime, LocalDateTime.now()) > Duration.ofDays(10)
                    }
                    .forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
        // fetch missing album metadata
        viewModelScope.launch {
            mostPlayedAlbums.collect { albums ->
                albums.filter {
                    it.album.songCount == 0
                }.forEach { album ->
                    YouTube.album(album.id).onSuccess { albumPage ->
                        database.query {
                            update(album.album, albumPage)
                        }
                    }.onFailure {
                        reportException(it)
                        if (it.message?.contains("NOT_FOUND") == true) {
                            database.query {
                                delete(album.album)
                            }
                        }
                    }
                }
            }
        }
    }
}