package com.openytmusic.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.openytmusic.app.BuildConfig
import com.openytmusic.app.rpc.rpc.VelqiRPC
import com.openytmusic.app.LocalPlayerAwareWindowInsets
import com.openytmusic.app.LocalPlayerConnection
import com.openytmusic.app.R
import com.openytmusic.app.constants.DiscordInfoDismissedKey
import com.openytmusic.app.constants.DiscordNameKey
import com.openytmusic.app.constants.DiscordTokenKey
import com.openytmusic.app.constants.DiscordUsernameKey
import com.openytmusic.app.constants.ClearRpcOnExitKey
import com.openytmusic.app.constants.EnableDiscordRPCKey
import com.openytmusic.app.db.entities.Song
import com.openytmusic.app.ui.component.IconButton
import com.openytmusic.app.ui.component.PreferenceEntry
import com.openytmusic.app.ui.component.PreferenceGroupTitle
import com.openytmusic.app.ui.component.SwitchPreference
import com.openytmusic.app.ui.utils.backToMain
import com.openytmusic.app.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val song by playerConnection.currentSong.collectAsState(null)

    val coroutineScope = rememberCoroutineScope()

    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    var infoDismissed by rememberPreference(DiscordInfoDismissedKey, false)

    LaunchedEffect(discordToken) {
        val token = discordToken
        if (token.isEmpty()) {
            return@LaunchedEffect
        }
        coroutineScope.launch(Dispatchers.IO) {
            VelqiRPC.getUserInfo(token).onSuccess {
                discordUsername = it.username
                discordName = it.name
            }
        }
    }

    val (discordRPC, onDiscordRPCChange) = rememberPreference(key = EnableDiscordRPCKey, defaultValue = true)
    val (clearRpcOnExit, onClearRpcOnExitChange) = rememberPreference(key = ClearRpcOnExitKey, defaultValue = true)

    val isLoggedIn = remember(discordToken) {
        discordToken != ""
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        PreferenceGroupTitle(
            title = stringResource(R.string.account)
        )

        PreferenceEntry(
            title = {
                Text(
                    text = if (isLoggedIn) discordName else stringResource(R.string.not_logged_in),
                    modifier = Modifier.alpha(if (isLoggedIn) 1f else 0.5f)
                )
            },
            description = if (discordUsername.isNotEmpty()) {
                "@$discordUsername"
            } else null,
            icon = { Icon(painterResource(R.drawable.discord), null) },
            trailingContent = {
                if (isLoggedIn) {
                    OutlinedButton(onClick = {
                        discordName = ""
                        discordToken = ""
                        discordUsername = ""
                    }) {
                        Text(stringResource(R.string.action_logout))
                    }
                } else {
                    OutlinedButton(onClick = {
                        navController.navigate("settings/discord/login")
                    }) {
                        Text(stringResource(R.string.action_login))
                    }
                }
            }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.options)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_discord_rpc)) },
            checked = discordRPC,
            onCheckedChange = onDiscordRPCChange,
            isEnabled = isLoggedIn
        )

        // Al cerrar la app: borrar la tarjeta de Discord. Sin esto, si el cierre no
        // limpia la presencia, la tarjeta se queda "zombie" en el perfil mostrando
        // una cancion que ya no suena.
        SwitchPreference(
            title = { Text(stringResource(R.string.clear_discord_rpc_on_exit)) },
            description = stringResource(R.string.clear_discord_rpc_on_exit_desc),
            checked = clearRpcOnExit,
            onCheckedChange = onClearRpcOnExitChange,
            isEnabled = isLoggedIn && discordRPC
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.preview)
        )

        RichPresence(song)
    }

    TopAppBar(
        title = { Text(stringResource(R.string.discord_integration)) },
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
fun RichPresence(
    song: Song?,
) {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Listening to OpenYTMusic",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier.size(108.dp)
                ) {
                    AsyncImage(
                        model = song?.song?.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .align(Alignment.TopStart)
                            .run {
                                if (song == null) {
                                    border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))
                                } else this
                            }
                    )

                    song?.artists?.firstOrNull()?.thumbnailUrl?.let {
                        Box(
                            modifier = Modifier
                                .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                                .padding(2.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = song?.song?.title ?: "Song Title",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = song?.artists?.joinToString { it.name } ?: "Artist",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    song?.album?.title?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                enabled = song != null,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=${song?.id}"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Listen on YouTube Music")
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SITE_URL))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Visit OpenYTMusic")
            }
        }
    }
}
