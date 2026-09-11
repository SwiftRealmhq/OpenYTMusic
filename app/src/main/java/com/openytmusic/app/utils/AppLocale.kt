package com.openytmusic.app.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.openytmusic.app.constants.AppLanguageKey
import com.openytmusic.app.constants.SYSTEM_DEFAULT
import java.util.Locale

/**
 * Idiomas con traduccion incluida en la app (directorios res/values-*), expresados
 * como etiquetas BCP-47. El sufijo del directorio puede diferir de la etiqueta
 * (ej: values-in -> id, values-iw -> he).
 */
private val APP_LANGUAGE_TAGS = listOf(
    "ar", "be", "bg", "bn", "bs", "ca", "cs", "de", "el", "en", "es", "es-US",
    "et", "fa", "fi", "fr", "hi", "hr", "hu", "id", "it", "he", "ja", "ko",
    "ml", "nb-NO", "nl", "pa", "pl", "pt", "pt-BR", "ro", "ru", "sk", "sl",
    "sr-Latn", "ta", "te", "tr", "uk", "vi", "zh-CN", "zh-TW",
)

/** Idiomas traducidos, ordenados por su nombre nativo. */
val appLanguageOptions: List<String> by lazy {
    APP_LANGUAGE_TAGS.sortedBy { tag ->
        val locale = Locale.forLanguageTag(tag)
        locale.getDisplayName(locale)
    }
}

/** Nombre nativo de un idioma (ej: "es" -> "español"). */
fun appLanguageDisplayName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
}

// Etiquetas de idioma de contenido (LanguageCodeToName) que no coinciden con
// la etiqueta BCP-47 de la traduccion de la interfaz correspondiente.
private val CONTENT_TO_UI_OVERRIDES = mapOf(
    "es-419" to "es-US",
    "en-GB" to "en",
    "fr-CA" to "fr",
    "pt-PT" to "pt",
    "no" to "nb-NO",
    "zh-HK" to "zh-TW",
    "sr" to "sr-Latn",
    "iw" to "he",
)

/**
 * Traduce una etiqueta de idioma de contenido a la etiqueta de interfaz mas
 * parecida que tenga traduccion. Devuelve SYSTEM_DEFAULT si se pide "system"
 * y null si no hay traduccion de interfaz para ese idioma.
 */
fun contentLanguageToUiLanguageTag(tag: String): String? {
    if (tag == SYSTEM_DEFAULT) return SYSTEM_DEFAULT
    CONTENT_TO_UI_OVERRIDES[tag]?.let { mapped ->
        return mapped.takeIf { it in appLanguageOptions }
    }
    if (tag in appLanguageOptions) return tag
    return Locale.forLanguageTag(tag).language.takeIf { it in appLanguageOptions }
}

/**
 * Devuelve un contexto cuya configuracion de idioma es la elegida en los ajustes
 * de la app. Si no hay seleccion ("system"), devuelve el mismo contexto.
 */
fun Context.applyAppLocale(): Context {
    val languageTag = dataStore[AppLanguageKey] ?: SYSTEM_DEFAULT
    if (languageTag == SYSTEM_DEFAULT) return this
    val locale = Locale.forLanguageTag(languageTag)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        config.setLocales(LocaleList(locale))
    }
    return createConfigurationContext(config)
}

/** Localiza la Activity que envuelve este contexto (si la hay). */
fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
