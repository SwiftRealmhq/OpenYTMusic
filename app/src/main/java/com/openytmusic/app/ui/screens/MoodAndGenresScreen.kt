package com.openytmusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.openytmusic.app.LocalPlayerAwareWindowInsets
import com.openytmusic.app.R
import com.openytmusic.app.ui.component.IconButton
import com.openytmusic.app.ui.component.NavigationTitle
import com.openytmusic.app.ui.component.shimmer.ListItemPlaceHolder
import com.openytmusic.app.ui.component.shimmer.ShimmerHost
import com.openytmusic.app.ui.utils.backToMain
import com.openytmusic.app.viewmodels.MoodAndGenresViewModel

// Paleta de colores para las tarjetas de generos (rotada por indice)
private val genreColors = listOf(
    Color(0xFFE91E63), // rosa
    Color(0xFFFF9800), // naranja
    Color(0xFF2196F3), // azul
    Color(0xFF4CAF50), // verde
    Color(0xFF9C27B0), // morado
    Color(0xFF00BCD4), // cian
    Color(0xFFF44336), // rojo
    Color(0xFF3F51B5), // indigo
    Color(0xFFFFC107), // ambar
    Color(0xFF009688), // teal
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodAndGenresScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val moodAndGenresList by viewModel.moodAndGenres.collectAsState()

    // Cuadricula de 2 columnas: las tarjetas de colores bajan en filas de 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) {
        if (moodAndGenresList == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShimmerHost {
                    repeat(8) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }

        var colorOffset = 0
        moodAndGenresList?.forEach { moodAndGenres ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                NavigationTitle(
                    title = moodAndGenres.title
                )
            }

            // Tarjetas de colores en filas de 2
            moodAndGenres.items.forEachIndexed { index, it ->
                val color = genreColors[(colorOffset + index) % genreColors.size]
                item {
                    GenreColorCard(
                        title = it.title,
                        color = color,
                        onClick = {
                            navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            colorOffset += moodAndGenres.items.size
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.mood_and_genres)) },
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
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun GenreColorCard(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}