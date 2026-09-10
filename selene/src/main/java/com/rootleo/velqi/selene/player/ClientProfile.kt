package com.rootleo.velqi.selene.player

/**
 * Perfiles de cliente: nos presentamos ante YouTube como la app oficial
 * correspondiente (los mismos identificadores públicos que envían los
 * clientes reales). Es nuestra propia implementación del protocolo:
 * nosotros construimos el JSON y las cabeceras.
 */
enum class ClientProfile(
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val androidSdkVersion: Int? = null,
    val osVersion: String? = null,
    val apiKey: String? = null,
    val referer: String? = null,
) {
    ANDROID(
        clientName = "ANDROID",
        clientVersion = "20.01.35",
        userAgent = "com.google.android.youtube/20.01.35 (Linux; U; Android 13) gzip",
        androidSdkVersion = 33,
        apiKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
    ),
    ANDROID_VR(
        clientName = "ANDROID_VR",
        clientVersion = "1.58.0",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.58.0 (Linux; U; Android 12) gzip",
        androidSdkVersion = 31,
        apiKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
    ),
    IOS(
        clientName = "IOS",
        clientVersion = "20.01.35",
        userAgent = "com.google.ios.youtube/20.01.35 (iPhone14,3; U; CPU iOS 17_4 like Mac OS X)",
        osVersion = "17.4.0",
        apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
    ),
    /** Cliente web de YouTube Music: usado para búsqueda, browse y sugerencias. */
    WEB_REMIX(
        clientName = "WEB_REMIX",
        clientVersion = "1.20220606.03.00",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36",
        apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
        referer = "https://music.youtube.com/",
    ),
}