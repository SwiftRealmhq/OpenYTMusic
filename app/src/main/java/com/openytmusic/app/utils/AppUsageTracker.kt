package com.openytmusic.app.utils

import android.os.SystemClock

/**
 * Medicion compartida del tiempo con la app en primer plano.
 * MainActivity llama a [onAppForeground]/[onAppBackground] y acumula
 * deltas periodicamente; StatsViewModel usa [currentSessionMillis]
 * para mostrar el contador en tiempo real sin duplicar.
 */
object AppUsageTracker {
    @Volatile
    private var sessionStart = 0L

    @Volatile
    private var lastMeasure = 0L

    fun onAppForeground() {
        sessionStart = SystemClock.elapsedRealtime()
        lastMeasure = sessionStart
    }

    fun onAppBackground() {
        sessionStart = 0L
        lastMeasure = 0L
    }

    /** Delta transcurrido desde la ultima medicion (para persistir). */
    fun consumeDelta(): Long {
        if (sessionStart == 0L) return 0L
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastMeasure
        lastMeasure = now
        return delta
    }

    /** Milis de la sesion actual (para el contador en vivo). */
    fun currentSessionMillis(): Long =
        if (sessionStart > 0L) SystemClock.elapsedRealtime() - sessionStart else 0L
}

// ==== Uso de la app por dia (para el desglose diario) ====
// Formato: "2026-09-09=12345;2026-09-10=678"

fun addDayUsage(existing: String?, day: String, millis: Long): String {
    val map = parseDayUsage(existing).toMutableMap()
    map[day] = (map[day] ?: 0L) + millis
    return map.entries.joinToString(";") { "${it.key}=${it.value}" }
}

fun parseDayUsage(raw: String?): Map<String, Long> =
    raw?.split(";").orEmpty()
        .mapNotNull { part ->
            val pieces = part.split("=")
            if (pieces.size == 2) pieces[0] to (pieces[1].toLongOrNull() ?: 0L) else null
        }
        .toMap()