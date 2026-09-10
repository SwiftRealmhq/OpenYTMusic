package com.rootleo.velqi.ui.screens.settings

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.rootleo.velqi.LocalPlayerAwareWindowInsets
import com.rootleo.velqi.R
import com.rootleo.velqi.constants.DiscordTokenKey
import com.rootleo.velqi.ui.component.IconButton
import com.rootleo.velqi.ui.utils.backToMain
import com.rootleo.velqi.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "VelqiDiscord"
private const val GRAB_FAIL = "__VELQI_FAIL__"

/**
 * Gancho moderno: captura el token leyendo localStorage de la app web de
 * Discord (donde guarda la sesion) y, como respaldo, interceptando el header
 * Authorization de fetch/XHR y el token de la respuesta de /auth/login.
 * Independiente del bundle webpack, que Discord cambia seguido.
 */
private const val HOOK_JS = """
(function(){
  // SONDA: prueba de vida + estado de localStorage (sin valores sensibles)
  try {
    var ks = [];
    for (var kk = 0; kk < localStorage.length; kk++) ks.push(String(localStorage.key(kk)));
    Android.debug('HOOK_ALIVE ls=' + localStorage.length + ' keys=' + ks.join(','));
  } catch(e){ try { Android.debug('HOOK_ERR ' + e); } catch(e2){} }
  var RE = /^[\\w-]{20,}\\.[\\w-]{6,}\\.[\\w-]{20,}$/;
  function report(t){
    try {
      if (typeof t === 'string' && RE.test(t)) { Android.onRetrieveToken(t); return true; }
    } catch(e){}
    return false;
  }
  // 0) localStorage: Discord guarda el token de sesion ahi (clave "token" o similar)
  try {
    var direct = localStorage.getItem('token') || sessionStorage.getItem('token');
    if (report(direct)) return;
    for (var i = 0; i < localStorage.length; i++) {
      try {
        var k = localStorage.key(i);
        var v = localStorage.getItem(k);
        if (v && report(v)) return;
        if (v) {
          try {
            var j = JSON.parse(v);
            if (j && typeof j === 'object') {
              for (var key in j) {
                try { if (typeof j[key] === 'string' && report(j[key])) return; } catch(e){}
              }
            }
          } catch(e){}
        }
      } catch(e){}
    }
  } catch(e){}
  // 1) fetch: capturar header Authorization y token de respuestas /auth/
  try {
    var of = window.fetch;
    if (typeof of === 'function' && !window.__velqiHooked) {
      window.fetch = function(url, opts){
        var p;
        try {
          p = of.apply(this, arguments);
          var u = String(url);
          try {
            if (opts && opts.headers) {
              var h = opts.headers;
              var t = (h && h.get) ? h.get('authorization') : (h.Authorization || h.authorization);
              report(t);
            }
          } catch(e){}
          if (u.indexOf('/auth/') !== -1 || u.indexOf('/auth2/') !== -1) {
            p.then(function(r){ try { r.clone().text().then(function(txt){ try { var j = JSON.parse(txt); if (j && typeof j.token === 'string') report(j.token); } catch(e){} }); } catch(e){} });
          }
        } catch(e){}
        return p;
      };
    }
  } catch(e){}
  // 2) XHR: capturar header Authorization
  try {
    var osh = XMLHttpRequest.prototype.setRequestHeader;
    if (!window.__velqiHooked) {
      XMLHttpRequest.prototype.setRequestHeader = function(k,v){
        try { if (String(k).toLowerCase() === 'authorization') report(v); } catch(e){}
        return osh.apply(this, arguments);
      };
    }
  } catch(e){}
  window.__velqiHooked = true;
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordLoginScreen(
    navController: NavController,
) {
    val scope = rememberCoroutineScope()
    var discordToken by rememberPreference(DiscordTokenKey, "")

    // bandera compartida entre el cliente JS y el bucle de reintentos
    var tokenGrabbed = false
    var webView: WebView? = null

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        webView: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        // Vigilar el header Authorization en TODO el trafico de red
                        try {
                            val headers = request.requestHeaders
                            val auth = headers?.get("Authorization") ?: headers?.get("authorization")
                            if (auth != null && !tokenGrabbed) {
                                Log.i(TAG, "AUTH HEADER visto en ${request.url.host}${request.url.path ?: ""}")
                                val re = Regex("^[\\w-]{20,}\\.[\\w-]{6,}\\.[\\w-]{20,}$")
                                if (re.matches(auth)) {
                                    Log.i(TAG, "TOKEN OBTENIDO via red (len=${auth.length})")
                                    tokenGrabbed = true
                                    webView.post {
                                        scope.launch(Dispatchers.Main) {
                                            discordToken = auth
                                            navController.navigateUp()
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "shouldInterceptRequest: ${e.message}")
                        }
                        return null
                    }

                    override fun shouldOverrideUrlLoading(
                        webView: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url
                        // Dejar cargar la pagina completa; solo marcar cuando llegamos a /app
                        if (url.host == "discord.com" && url.path?.startsWith("/app") == true) {
                            Log.i(TAG, "Llegamos a /app: $url")
                        }
                        return false
                    }

                    override fun onPageStarted(webView: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(webView, url, favicon)
                        // Inyectar el gancho lo antes posible en cada pagina
                        if (!tokenGrabbed) {
                            webView.evaluateJavascript(HOOK_JS, null)
                        }
                    }

                    override fun onPageFinished(webView: WebView, url: String?) {
                        super.onPageFinished(webView, url)
                        val u = url ?: return
                        if (u.contains("discord.com/app") && !tokenGrabbed) {
                            Log.i(TAG, "Pagina /app cargada, manteniendo el gancho activo...")
                            scope.launch {
                                repeat(12) { attempt ->
                                    if (tokenGrabbed) return@launch
                                    delay(if (attempt == 0) 300L else 800L)
                                    Log.i(TAG, "Reintento ${attempt + 1} del gancho")
                                    webView.evaluateJavascript(HOOK_JS, null)
                                }
                                if (!tokenGrabbed) {
                                    Log.w(TAG, "No se capturo el token: revisa si Discord cambio el flujo de auth")
                                }
                            }
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                }
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()

                WebStorage.getInstance().deleteAllData()
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun debug(msg: String) {
                        Log.i(TAG, "JS: $msg")
                    }

                    @JavascriptInterface
                    fun onRetrieveToken(token: String) {
                        if (token.isBlank() || token == GRAB_FAIL) {
                            Log.w(TAG, "El grabador no encontro el token (intentara de nuevo)")
                            return
                        }
                        tokenGrabbed = true
                        Log.i(TAG, "TOKEN OBTENIDO (len=${token.length})")
                        scope.launch(Dispatchers.Main) {
                            discordToken = token
                            navController.navigateUp()
                        }
                    }
                }, "Android")

                webView = this
                Log.i(TAG, "Abriendo discord.com/login")
                loadUrl("https://discord.com/login")
            }
        }
    )

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