package com.openytmusic.app.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import com.openytmusic.app.BuildConfig

/**
 * Version REALMENTE instalada, leida del PackageManager.
 *
 * Por que no usar BuildConfig directamente: sus campos son constantes de Java y
 * Kotlin las INCRUSTA en el bytecode al compilar. Si Gradle reutiliza clases ya
 * compiladas (compilacion incremental), una version vieja se queda congelada
 * dentro del APK. Paso de verdad en 0.6.2: el manifest decia 0.6.2 pero "Acerca
 * de" seguia mostrando "v0.6.1 (32)" porque el literal ya estaba inlinado en el
 * build anterior, y ni reinstalar ni reiniciar el telefono lo cambiaba.
 *
 * PackageManager lee el manifest del APK instalado en cada llamada, asi que
 * siempre dice la verdad. BuildConfig queda solo como ultimo recurso.
 */
object AppVersion {
    private fun info(context: Context): PackageInfo? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()

    fun name(context: Context): String =
        info(context)?.versionName ?: BuildConfig.VERSION_NAME

    fun code(context: Context): Long {
        val packageInfo = info(context) ?: return BuildConfig.VERSION_CODE.toLong()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    /** "v0.6.2 (33)" — para mostrar en pantalla. */
    fun label(context: Context): String = "v${name(context)} (${code(context)})"
}
