package com.openytmusic.app.utils

/**
 * Cambios que se muestran UNA vez por version nueva (al actualizar o al
 * instalar por primera vez). El texto va en primera persona porque lo firma el
 * desarrollador: es la nota que le deja al usuario, no un texto corporativo.
 *
 * Para la proxima version: agregar una entrada nueva al principio de [entries]
 * con su propio [Changelog.version]. Se muestra la primera entrada cuya version
 * coincida con la instalada, asi que no hay que tocar nada mas.
 */
data class Changelog(
    val version: String,
    val highlights: List<String>,
)

object ChangelogData {
    val entries = listOf(
        Changelog(
            version = "0.6.2",
            highlights = listOf(
                "Escuchar música con alguien más: creas una sala, le pasas el código a quien quieras y los dos " +
                    "escuchan lo mismo al mismo tiempo. Si uno pausa, cambia de canción o se adelanta, al otro le " +
                    "pasa igual, y trae un mini chat para opinar de lo que suena. Está en la barra de abajo, " +
                    "en el icono de la personita.",
                "Dejé la sala estable de verdad: al cambiar de canción ya no se reinicia sola ni entra en " +
                    "bucle, y nada suena hasta que los dos la tienen cargada; ahí arranca sola, a la vez, sin " +
                    "delays. Arreglé que a veces aparecieran oyentes de más y que la música se pausara sola " +
                    "(era un fantasma en la sala y el aviso de pausa que la app mandaba cuando el stream se " +
                    "quedaba sin buffer). El buscador de la sala ahora trae predicciones, como la búsqueda " +
                    "principal, y al entrar se para lo que estuvieras escuchando para que los dos oigan lo " +
                    "mismo. También puedes ponerle tu nombre y el chat quedó más amplio.",
                "Arreglé las portadas del reproductor: algunas se veían borrosas porque la app " +
                    "las pedía en baja calidad. Ahora se piden en alta resolución y todas se ven nítidas.",
                "Arreglé el Rich Presence de Discord: ya no se queda colgado mostrando una canción " +
                    "vieja cuando cierras la app, y ahora aparece con nuestra propia identidad en lugar " +
                    "de la de otro cliente.",
                "Agregué un registro para controlar las IPs y el flujo de las canciones: veo cuánta " +
                    "gente usa la app, qué busca y qué suena, para cortar el abuso. Es anónimo " +
                    "(no se manda tu IP ni tu cuenta) y lo puedes apagar en Privacidad.",
                "Arreglé que \"Acerca de\" mostrara una versión vieja de la app."
            )
        ),
    )

    fun forVersion(version: String): Changelog? =
        entries.firstOrNull { it.version == version }
}
