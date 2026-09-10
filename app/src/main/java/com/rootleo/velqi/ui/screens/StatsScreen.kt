package com.rootleo.velqi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.rootleo.velqi.LocalPlayerAwareWindowInsets
import com.rootleo.velqi.LocalPlayerConnection
import com.rootleo.velqi.R
import com.rootleo.velqi.constants.StatPeriod
import com.rootleo.velqi.db.entities.SongWithPlayCount
import com.rootleo.velqi.extensions.togglePlayPause
import com.rootleo.velqi.models.toMediaMetadata
import com.rootleo.velqi.playback.queues.YouTubeQueue
import com.rootleo.velqi.ui.component.AlbumGridItem
import com.rootleo.velqi.ui.component.ArtistGridItem
import com.rootleo.velqi.ui.component.ChipsRow
import com.rootleo.velqi.ui.component.IconButton
import com.rootleo.velqi.ui.component.LocalMenuState
import com.rootleo.velqi.ui.component.NavigationTitle
import com.rootleo.velqi.ui.component.SongListItem
import com.rootleo.velqi.ui.menu.AlbumMenu
import com.rootleo.velqi.ui.menu.ArtistMenu
import com.rootleo.velqi.ui.menu.SongMenu
import com.rootleo.velqi.ui.utils.backToMain
import com.rootleo.velqi.viewmodels.StatsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class StatDetail {
    LISTEN_TIME, APP_TIME, PLAYS, TOP_SONG
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val statPeriod by viewModel.statPeriod.collectAsState()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsState()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsState()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsState()

    // Estadisticas reales
    val totalPlayTime by viewModel.totalPlayTime.collectAsState()
    val totalPlayCount by viewModel.totalPlayCount.collectAsState()
    val topSongWithCount by viewModel.topSongWithCount.collectAsState()
    val topSongsWithCount by viewModel.topSongsWithCount.collectAsState()
    val appUsageTime by viewModel.appUsageTime.collectAsState()
    val playTimeByDay by viewModel.playTimeByDay.collectAsState()
    val appUsageByDay by viewModel.appUsageByDay.collectAsState()

    // Detalle expandido de la tarjeta pulsada
    var expandedDetail by remember { mutableStateOf<StatDetail?>(null) }

    // Posicion actual del reproductor: el tiempo escuchado sube en vivo
    var livePosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            livePosition = playerConnection.player.currentPosition
            delay(1000)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
        modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top))
    ) {
        item(key = "summary") {
            val topSong = topSongWithCount.firstOrNull()

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                StatSummaryCard(
                    title = stringResource(R.string.stat_listen_time),
                    value = formatPlayTime(totalPlayTime + livePosition),
                    icon = R.drawable.music_note,
                    expanded = expandedDetail == StatDetail.LISTEN_TIME,
                    onClick = {
                        expandedDetail = if (expandedDetail == StatDetail.LISTEN_TIME) null else StatDetail.LISTEN_TIME
                    },
                    modifier = Modifier.weight(1f)
                )
                StatSummaryCard(
                    title = stringResource(R.string.stat_app_time),
                    value = formatPlayTime(appUsageTime),
                    icon = R.drawable.trending_up,
                    expanded = expandedDetail == StatDetail.APP_TIME,
                    onClick = {
                        expandedDetail = if (expandedDetail == StatDetail.APP_TIME) null else StatDetail.APP_TIME
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(
                visible = expandedDetail == StatDetail.LISTEN_TIME,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                DayUsageDetail(
                    byDay = playTimeByDay,
                    emptyText = stringResource(R.string.stat_no_listen_data)
                )
            }

            AnimatedVisibility(
                visible = expandedDetail == StatDetail.APP_TIME,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                DayUsageDetail(
                    byDay = appUsageByDay.mapKeys { LocalDate.parse(it.key) },
                    emptyText = stringResource(R.string.stat_no_app_data)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                StatSummaryCard(
                    title = stringResource(R.string.stat_songs_played),
                    value = pluralStringResource(R.plurals.n_song, totalPlayCount, totalPlayCount),
                    icon = R.drawable.queue_music,
                    expanded = expandedDetail == StatDetail.PLAYS,
                    onClick = {
                        expandedDetail = if (expandedDetail == StatDetail.PLAYS) null else StatDetail.PLAYS
                    },
                    modifier = Modifier.weight(1f)
                )
                StatSummaryCard(
                    title = stringResource(R.string.stat_plays),
                    value = totalPlayCount.toString(),
                    icon = R.drawable.bar_chart,
                    expanded = expandedDetail == StatDetail.TOP_SONG,
                    onClick = {
                        expandedDetail = if (expandedDetail == StatDetail.TOP_SONG) null else StatDetail.TOP_SONG
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(
                visible = expandedDetail == StatDetail.PLAYS || expandedDetail == StatDetail.TOP_SONG,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                TopSongsDetail(songs = topSongsWithCount)
            }

            if (topSong != null) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.star),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.stat_top_song).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = topSong.song.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = pluralStringResource(R.plurals.n_times, topSong.playCount, topSong.playCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        item {
            ChipsRow(
                chips = listOf(
                    StatPeriod.`1_WEEK` to pluralStringResource(R.plurals.n_week, 1, 1),
                    StatPeriod.`1_MONTH` to pluralStringResource(R.plurals.n_month, 1, 1),
                    StatPeriod.`3_MONTH` to pluralStringResource(R.plurals.n_month, 3, 3),
                    StatPeriod.`6_MONTH` to pluralStringResource(R.plurals.n_month, 6, 6),
                    StatPeriod.`1_YEAR` to pluralStringResource(R.plurals.n_year, 1, 1),
                    StatPeriod.ALL to stringResource(R.string.filter_all)
                ),
                currentValue = statPeriod,
                onValueUpdate = { viewModel.statPeriod.value = it }
            )
        }

        item(key = "mostPlayedSongs") {
            NavigationTitle(
                title = stringResource(R.string.most_played_songs),
                modifier = Modifier.animateItem()
            )
        }

        items(
            items = mostPlayedSongs,
            key = { it.id }
        ) { song ->
            SongListItem(
                song = song,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                trailingContent = {
                    IconButton(
                        onClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable {
                        if (song.id == mediaMetadata?.id) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubeQueue.radio(song.toMediaMetadata())
                            )
                        }
                    }
                    .animateItem()
            )
        }

        item(key = "mostPlayedArtists") {
            NavigationTitle(
                title = stringResource(R.string.most_played_artists),
                modifier = Modifier.animateItem()
            )

            LazyRow(
                modifier = Modifier.animateItem()
            ) {
                items(
                    items = mostPlayedArtists,
                    key = { it.id }
                ) { artist ->
                    ArtistGridItem(
                        artist = artist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    navController.navigate("artist/${artist.id}")
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        ArtistMenu(
                                            originalArtist = artist,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            )
                            .animateItem()
                    )
                }
            }
        }

        if (mostPlayedAlbums.isNotEmpty()) {
            item(key = "mostPlayedAlbums") {
                NavigationTitle(
                    title = stringResource(R.string.most_played_albums),
                    modifier = Modifier.animateItem()
                )

                LazyRow(
                    modifier = Modifier.animateItem()
                ) {
                    items(
                        items = mostPlayedAlbums,
                        key = { it.id }
                    ) { album ->
                        AlbumGridItem(
                            album = album,
                            isActive = album.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            coroutineScope = coroutineScope,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("album/${album.id}")
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            AlbumMenu(
                                                originalAlbum = album,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                )
                                .animateItem()
                        )
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.stats)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}

@Composable
private fun StatSummaryCard(
    title: String,
    value: String,
    icon: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Desglose por dia (ultimos 7 dias) con barras proporcionales. */
@Composable
private fun DayUsageDetail(
    byDay: Map<LocalDate, Long>,
    emptyText: String,
) {
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val max = maxOf(days.maxOfOrNull { byDay[it] ?: 0L } ?: 0L, 1L)

    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        days.forEach { day ->
            val value = byDay[day] ?: 0L
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp)
            ) {
                Text(
                    text = dayLabel(day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(70.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (value > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((value.toFloat() / max).coerceIn(0.04f, 1f))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = formatPlayTime(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        if (days.none { (byDay[it] ?: 0L) > 0 }) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

/** Top de canciones con su numero de veces (barras proporcionales). */
@Composable
private fun TopSongsDetail(songs: List<SongWithPlayCount>) {
    if (songs.isEmpty()) {
        Text(
            text = stringResource(R.string.stat_no_listen_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        return
    }
    val max = songs.maxOfOrNull { it.playCount } ?: 1
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        songs.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp)
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((item.playCount.toFloat() / max).coerceIn(0.05f, 1f))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = pluralStringResource(R.plurals.n_times, item.playCount, item.playCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

private fun dayLabel(day: LocalDate): String =
    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + day.dayOfMonth

private fun formatPlayTime(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}min"
        totalMinutes > 0 -> "${totalMinutes}min"
        else -> "${millis / 1000}s"
    }
}