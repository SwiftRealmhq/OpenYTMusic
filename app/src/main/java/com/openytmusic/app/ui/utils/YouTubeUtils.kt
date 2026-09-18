package com.openytmusic.app.ui.utils

/**
 * Hosts de portadas que Google redimensiona al vuelo segun el parametro de la
 * URL. Todas las portadas de album/cancion de Innertube (WEB_REMIX) llegan por
 * aca: yt3.googleusercontent.com es la mayoria, lh3 es el resto, y ggpht es la
 * forma vieja (=sNNN).
 */
private val GOOGLE_CONTENT_HOST = Regex("^https?://(?:lh\\d|yt\\d|yt3)\\.googleusercontent\\.com/")
private val GGPHHT_HOST = Regex("^https?://yt\\d+\\.ggpht\\.com/")

// Formas del parametro de tamano que usa Google:
//   =w544-h544-l90-rj                        cuadrada normal
//   =w544-c-h544-k-c0x00ffffff-no-l90-rj     con recorte explicito (-c-)
//   =s120-c-k-c0x00ffffff-no-rj              forma vieja (=sNNN)
// El flag -c- hay que conservarlo: sin el, Google devuelve una imagen que no es
// cuadrada (comprobado: 1200x1200 con -c-, pero solo 630x628 sin el).
private val SIZE_W_H = Regex("=w(\\d+)(-c)?-h(\\d+)")
private val SIZE_W = Regex("=w(\\d+)")
private val SIZE_S = Regex("=s(\\d+)")

/**
 * Pide la portada en el tamano indicado reescribiendo el parametro de la URL.
 *
 * Google sirve LA MISMA imagen en cualquier tamano hasta su resolucion nativa:
 * el mismo hash responde 544x544, 1200x1200 y hasta 1400x1400. Por eso pedir
 * 1200 nunca inventa pixeles: como maximo devuelve el original.
 *
 * Ojo con el historial: la version anterior solo miraba lh3.googleusercontent.com
 * y ademas exigia que la URL completa encajara con el regex (matchEntire). Pero
 * las portadas que entrega el API son casi todas de yt3.googleusercontent.com y
 * muchas vienen con el flag -c- en medio (=w544-c-h544), asi que el resize no se
 * aplicaba y cada cancion quedaba con el tamano que el API hubiera dejado en la
 * lista: unas en w544 (borrosas al estirarlas a la pantalla) y otras mas grandes
 * (nitidas). De ahi que en el reproductor unas se vieran bien y otras borrosas.
 *
 * Solo se reescribe el parametro de tamano: los flags que van despues
 * (-c, -k, -l90-rj, ...) y la query firmada (?sqp=..., ?rs=...) se conservan tal
 * cual, que es lo que hace valida la peticion.
 */
fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this
    if (!GOOGLE_CONTENT_HOST.containsMatchIn(this) && !GGPHHT_HOST.containsMatchIn(this)) return this

    // Tamano actual, para conservar la proporcion cuando solo nos dan un lado.
    val sizeW = SIZE_W_H.find(this)
    val sizeS = SIZE_S.find(this)
    val sizeWOnly = SIZE_W.find(this)
    val currentW = sizeW?.groupValues?.get(1)?.toIntOrNull()
        ?: sizeS?.groupValues?.get(1)?.toIntOrNull()
        ?: sizeWOnly?.groupValues?.get(1)?.toIntOrNull()
    val currentH = sizeW?.groupValues?.get(3)?.toIntOrNull() ?: currentW

    var w = width
    var h = height
    if (w != null && h == null) h = if (currentW != null && currentH != null) (w.toLong() * currentH / currentW).toInt() else w
    if (w == null && h != null) w = if (currentW != null && currentH != null) (h.toLong() * currentW / currentH).toInt() else h
    if (w == null || h == null) return this

    // Forma w-h (la habitual en las portadas cuadradas), conservando -c- si estaba.
    if (sizeW != null) {
        val crop = sizeW.groupValues[2]
        return SIZE_W_H.replace(this, "=w$w$crop-h$h")
    }
    // Forma =sNNN (ggpht y algunas yt3).
    if (sizeS != null) return SIZE_S.replace(this, "=s${maxOf(w, h)}")
    // Forma =wNNN sin alto explicito.
    if (sizeWOnly != null) return SIZE_W.replace(this, "=w$w")

    // Sin parametro de tamano conocido: no se toca (mejor no romper la URL).
    return this
}

/**
 * Portada cuadrada en calidad alta para las vistas grandes: el reproductor, el
 * visor a pantalla completa y la notificacion. 1200 cubre una pantalla 1080p+
 * con densidad 3x sin que se note el escalado.
 */
fun String?.highResThumbnail(): String? = this?.resize(1200, 1200)
