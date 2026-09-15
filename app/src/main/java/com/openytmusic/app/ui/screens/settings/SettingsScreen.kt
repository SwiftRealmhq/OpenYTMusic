package com.openytmusic.app.ui.screens.settings

import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.openytmusic.app.BuildConfig
import com.openytmusic.app.LocalPlayerAwareWindowInsets
import com.openytmusic.app.R
import com.openytmusic.app.constants.AccountChannelHandleKey
import com.openytmusic.app.constants.AccountEmailKey
import com.openytmusic.app.constants.AccountNameKey
import com.openytmusic.app.constants.InnerTubeCookieKey
import com.openytmusic.app.constants.VisitorDataKey
import com.openytmusic.app.innertube.YouTube
import com.openytmusic.app.innertube.utils.parseCookieString
import com.openytmusic.app.ui.component.IconButton
import com.openytmusic.app.ui.component.PreferenceEntry
import com.openytmusic.app.ui.utils.backToMain
import com.openytmusic.app.utils.Updater
import androidx.datastore.preferences.core.edit
import com.openytmusic.app.utils.dataStore
import com.openytmusic.app.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        PreferenceEntry(
            title = { Text(stringResource(R.string.appearance)) },
            icon = { Icon(painterResource(R.drawable.palette), null) },
            onClick = { navController.navigate("settings/appearance") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.content)) },
            icon = { Icon(painterResource(R.drawable.language), null) },
            onClick = { navController.navigate("settings/content") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.player_and_audio)) },
            icon = { Icon(painterResource(R.drawable.play), null) },
            onClick = { navController.navigate("settings/player") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.storage)) },
            icon = { Icon(painterResource(R.drawable.storage), null) },
            onClick = { navController.navigate("settings/storage") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.privacy)) },
            icon = { Icon(painterResource(R.drawable.security), null) },
            onClick = { navController.navigate("settings/privacy") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.discord_integration)) },
            icon = { Icon(painterResource(R.drawable.discord), null) },
            onClick = { navController.navigate("settings/discord") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.backup_restore)) },
            icon = { Icon(painterResource(R.drawable.restore), null) },
            onClick = { navController.navigate("settings/backup_restore") }
        )        // Velqi: seccion UNICA de YouTube Music - cuenta activa, cerrar sesion
        // e importar, todo aqui dentro.
        val accountName by rememberPreference(AccountNameKey, "")
        val accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
        val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
        val isLoggedIn = "SAPISID" in parseCookieString(innerTubeCookie)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                // Encabezado: abre la pantalla de importacion
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("settings/import") }
                ) {
                    // Loguito de la app (luna roja)
                    Image(
                        painter = painterResource(R.drawable.openytmusic_logo),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.import_from_ym_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!isLoggedIn) {
                    // El login no es un extra: es la unica solucion de fondo al muro anti-bot y la
                    // puerta a tus playlists de YouTube Music. Por eso va como accion principal,
                    // con sus dos beneficios a la vista, en vez de escondido tras un "Acceder".
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.login_benefits_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.login_benefits_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { navController.navigate("login") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.bot_wall_login))
                    }
                } else {
                    // Acciones de cuenta: cambiar (login) y cerrar sesion
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { navController.navigate("login") }) {
                            Text(
                                text = stringResource(R.string.switch_account),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                context.dataStore.edit { settings ->
                                    settings.remove(InnerTubeCookieKey)
                                    settings.remove(VisitorDataKey)
                                    settings.remove(AccountNameKey)
                                    settings.remove(AccountEmailKey)
                                    settings.remove(AccountChannelHandleKey)
                                }
                                YouTube.cookie = null
                                YouTube.visitorData = YouTube.DEFAULT_VISITOR_DATA
                                // Borrar la sesion de verdad: sin esto las cookies de
                                // accounts.google.com/music.youtube.com seguian en disco
                                // (app_webview) y el siguiente login entraba solo con la
                                // cuenta anterior, con la cookie aun "viva" en el WebView.
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                WebStorage.getInstance().deleteAllData()
                            }
                        }) {
                            Text(
                                text = stringResource(R.string.logout),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        PreferenceEntry(
            title = { Text(stringResource(R.string.about)) },
            icon = { Icon(painterResource(R.drawable.info), null) },
            onClick = { navController.navigate("settings/about") }
        )
        if (Updater.isNewerVersion(latestVersionName, BuildConfig.VERSION_NAME)) {
            PreferenceEntry(
                title = {
                    Text(
                        text = stringResource(R.string.new_version_available),
                    )
                },
                description = latestVersionName,
                icon = {
                    BadgedBox(
                        badge = { Badge() }
                    ) {
                        Icon(painterResource(R.drawable.update), null)
                    }
                },
                onClick = {
                    uriHandler.openUri(BuildConfig.SITE_URL)
                }
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
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
