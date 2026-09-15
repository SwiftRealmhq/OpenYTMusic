package com.openytmusic.app

import android.app.Application
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.openytmusic.app.innertube.YouTube
import com.openytmusic.app.innertube.models.YouTubeLocale
import com.openytmusic.app.kugou.KuGou
import com.openytmusic.app.constants.ContentCountryKey
import com.openytmusic.app.constants.ContentLanguageKey
import com.openytmusic.app.constants.CountryCodeToName
import com.openytmusic.app.constants.InnerTubeCookieKey
import com.openytmusic.app.constants.LanguageCodeToName
import com.openytmusic.app.constants.MaxImageCacheSizeKey
import com.openytmusic.app.constants.ProxyEnabledKey
import com.openytmusic.app.constants.ProxyTypeKey
import com.openytmusic.app.constants.ProxyUrlKey
import com.openytmusic.app.constants.SYSTEM_DEFAULT
import com.openytmusic.app.constants.UseLoginForBrowse
import com.openytmusic.app.constants.VisitorDataKey
import com.openytmusic.app.extensions.toEnum
import com.openytmusic.app.extensions.toInetSocketAddress
import com.openytmusic.app.utils.dataStore
import com.openytmusic.app.utils.get
import com.openytmusic.app.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.Proxy
import java.util.Locale

@HiltAndroidApp
class App : Application(), ImageLoaderFactory {
    /** Scope de vida de la app: no bloquea `onCreate` ni deja corrutinas sin dueño. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // DebugTree solo en debug: en release volcaba datos del usuario (cuenta,
        // respuestas autenticadas) en logcat, legible por cualquier app.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // PoToken: BotGuard via WebView (zemer-cipher). Si el WebView esta roto,
        // el provider devuelve null y la reproduccion cae a los clientes Android.
        runCatching {
            com.zemer.cipher.ZemerCipher.initialize(applicationContext)
            val generator = com.zemer.cipher.potoken.PoTokenGenerator()
            YouTube.poTokenProvider = { videoId, visitorData ->
                val result = generator.getWebClientPoToken(videoId, visitorData ?: "")
                result?.let { it.playerRequestPoToken to it.streamingDataPoToken }
            }
        }.onFailure { Timber.w(it, "PoToken no disponible") }

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "") // replace zh-Hant-* to zh-*
        YouTube.locale = YouTubeLocale(
            gl = dataStore[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = dataStore[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (dataStore[ProxyEnabledKey] == true) {
            try {
                YouTube.proxy = Proxy(
                    dataStore[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    dataStore[ProxyUrlKey]!!.toInetSocketAddress()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to parse proxy url.", LENGTH_SHORT).show()
                reportException(e)
            }
        }

        if (dataStore[UseLoginForBrowse] == true) {
            YouTube.useLoginForBrowse = true
        }

        appScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .catch { reportException(it) }
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" } // Previously visitorData was sometimes saved as "null" due to a bug
                        ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        } ?: YouTube.DEFAULT_VISITOR_DATA
                }
        }
        appScope.launch {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .catch { reportException(it) }
                .collect { cookie ->
                    YouTube.cookie = cookie
                }
        }
    }

    override fun newImageLoader() = ImageLoader.Builder(this)
        .crossfade(true)
        .respectCacheHeaders(false)
        .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        .diskCache(
            DiskCache.Builder()
                .directory(cacheDir.resolve("coil"))
                .maxSizeBytes((dataStore[MaxImageCacheSizeKey] ?: 512) * 1024 * 1024L)
                .build()
        )
        .build()
}