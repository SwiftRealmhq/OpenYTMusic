package com.openytmusic.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.openytmusic.app.innertube.YouTube
import com.openytmusic.app.innertube.utils.parseCookieString
import com.openytmusic.app.LocalPlayerAwareWindowInsets
import com.openytmusic.app.R
import com.openytmusic.app.constants.AccountChannelHandleKey
import com.openytmusic.app.constants.AccountEmailKey
import com.openytmusic.app.constants.AccountNameKey
import com.openytmusic.app.constants.AppLanguageKey
import com.openytmusic.app.constants.ContentCountryKey
import com.openytmusic.app.constants.ContentLanguageKey
import com.openytmusic.app.constants.CountryCodeToName
import com.openytmusic.app.constants.EnableKugouKey
import com.openytmusic.app.constants.EnableLrcLibKey
import com.openytmusic.app.constants.HideExplicitKey
import com.openytmusic.app.constants.InnerTubeCookieKey
import com.openytmusic.app.constants.VisitorDataKey
import com.openytmusic.app.constants.LanguageCodeToName
import com.openytmusic.app.constants.ProxyEnabledKey
import com.openytmusic.app.constants.ProxyTypeKey
import com.openytmusic.app.constants.ProxyUrlKey
import com.openytmusic.app.constants.SYSTEM_DEFAULT
import com.openytmusic.app.ui.component.EditTextPreference
import com.openytmusic.app.ui.component.IconButton
import com.openytmusic.app.ui.component.ListPreference
import com.openytmusic.app.ui.component.PreferenceEntry
import com.openytmusic.app.ui.component.PreferenceGroupTitle
import com.openytmusic.app.ui.component.SwitchPreference
import com.openytmusic.app.ui.utils.backToMain
import com.openytmusic.app.utils.contentLanguageToUiLanguageTag
import com.openytmusic.app.utils.dataStore
import com.openytmusic.app.utils.findActivity
import com.openytmusic.app.utils.rememberEnumPreference
import com.openytmusic.app.utils.rememberPreference
import java.net.Proxy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableLrcLib, onEnableLrcLibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")


    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        ListPreference(
            title = { Text(stringResource(R.string.language)) },
            icon = { Icon(painterResource(R.drawable.language), null) },
            selectedValue = contentLanguage,
            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
            valueText = {
                LanguageCodeToName.getOrElse(it) {
                    stringResource(R.string.system_default)
                }
            },
            onValueSelected = { tag ->
                onContentLanguageChange(tag)
                // El mismo selector tambien cambia el idioma de la interfaz cuando
                // hay traduccion disponible, y recrea la actividad al instante.
                contentLanguageToUiLanguageTag(tag)?.let { uiTag ->
                    scope.launch {
                        context.dataStore.edit { settings ->
                            settings[AppLanguageKey] = uiTag
                        }
                        context.findActivity()?.recreate()
                    }
                }
            }
        )
        ListPreference(
            title = { Text(stringResource(R.string.content_country)) },
            icon = { Icon(painterResource(R.drawable.location_on), null) },
            selectedValue = contentCountry,
            values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
            valueText = {
                CountryCodeToName.getOrElse(it) {
                    stringResource(R.string.system_default)
                }
            },
            onValueSelected = onContentCountryChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.hide_explicit)) },
            icon = { Icon(painterResource(R.drawable.explicit), null) },
            checked = hideExplicit,
            onCheckedChange = onHideExplicitChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_lrclib)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = enableLrcLib,
            onCheckedChange = onEnableLrcLibChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_kugou)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = enableKugou,
            onCheckedChange = onEnableKugouChange
        )

        PreferenceGroupTitle(
            title = "PROXY"
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_proxy)) },
            icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
            checked = proxyEnabled,
            onCheckedChange = onProxyEnabledChange
        )

        AnimatedVisibility(proxyEnabled) {
            Column {
                ListPreference(
                    title = { Text(stringResource(R.string.proxy_type)) },
                    selectedValue = proxyType,
                    values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                    valueText = { it.name },
                    onValueSelected = onProxyTypeChange
                )
                EditTextPreference(
                    title = { Text(stringResource(R.string.proxy_url)) },
                    value = proxyUrl,
                    onValueChange = onProxyUrlChange
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.content)) },
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
