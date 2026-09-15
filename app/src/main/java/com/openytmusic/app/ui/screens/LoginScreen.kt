package com.openytmusic.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.openytmusic.app.innertube.YouTube
import com.openytmusic.app.LocalPlayerAwareWindowInsets
import com.openytmusic.app.R
import com.openytmusic.app.constants.AccountChannelHandleKey
import com.openytmusic.app.constants.AccountEmailKey
import com.openytmusic.app.constants.AccountNameKey
import com.openytmusic.app.constants.InnerTubeCookieKey
import com.openytmusic.app.constants.VisitorDataKey
import com.openytmusic.app.ui.component.IconButton
import com.openytmusic.app.ui.utils.backToMain
import com.openytmusic.app.utils.rememberPreference
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Velqi: login manual. El WebView muestra Google/YT Music para que el usuario
 * entre - y CAMBIE de cuenta si quiere (Google no expone las secundarias en el
 * flujo automatico). NADA se captura solo: el usuario decide cuando tocar
 * "Usar esta cuenta", que se activa cuando hay sesion real (SAPISID).
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    var visitorData by rememberPreference(VisitorDataKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")

    var webView: WebView? = null
    // Habilitado solo cuando la cookie actual trae sesion real
    var hasSession by remember { mutableStateOf(false) }
    // Velqi: en Waydroid el renderizador del WebView a veces no arranca al
    // primer intento (sandbox start timeout); reintentamos la carga sola.
    var loadRetries by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            // Solo monitoreamos: hay sesion real en esta URL?
                            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com")
                            hasSession = cookie?.contains("SAPISID") == true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com")
                            hasSession = cookie?.contains("SAPISID") == true
                        }

                        override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                            // Reintento automatico (max 2) para el arranque lento
                            // del renderizador en contenedores (Waydroid)
                            if (failingUrl == url?.toString() || loadRetries < 2) {
                                if (loadRetries < 2) {
                                    loadRetries++
                                    view.postDelayed({ view.reload() }, 3000L)
                                }
                            }
                        }

                        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            // El renderizador murio: reconstruir el WebView es
                            // responsabilidad del sistema; evitamos el crash de la app.
                            return true
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onRetrieveVisitorData(newVisitorData: String?) {
                            if (newVisitorData != null) {
                                visitorData = newVisitorData
                            }
                        }
                    }, "Android")
                    webView = this
                    loadUrl("https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F")
                }
            },
            // Sin destroy() el WebView quedaba referenciado por el factory y el estado
            // de Compose: retenia el Context de la Activity y dejaba vivo el proceso de
            // render con JS y el bridge inyectados toda la vida del proceso.
            onRelease = { view ->
                view.stopLoading()
                view.removeJavascriptInterface("Android")
                view.webViewClient = WebViewClient()
                view.destroy()
            }
        )

        // Velqi: captura MANUAL de la sesion - el usuario decide cuando
        Button(
            onClick = {
                val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com")
                if (cookie?.contains("SAPISID") == true) {
                    innerTubeCookie = cookie
                    // El nombre de la cuenta se trae en segundo plano con reintentos
                    GlobalScope.launch(Dispatchers.Main) {
                        repeat(6) {
                            YouTube.accountInfo().onSuccess {
                                accountName = it.name
                                accountEmail = it.email.orEmpty()
                                accountChannelHandle = it.channelHandle.orEmpty()
                            }.onSuccess { return@launch }
                            delay(1500)
                        }
                    }
                    navController.navigateUp()
                }
            },
            enabled = hasSession,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom))
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.use_this_account))
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.login)) },
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

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
