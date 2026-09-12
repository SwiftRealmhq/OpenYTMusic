package com.zemer.cipher.potoken

import android.webkit.CookieManager
import com.zemer.cipher.CipherDeobfuscator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Genera los PoToken de BotGuard (WebView invisible).
 *
 * Regla de oro de este archivo: **nunca bloquear la reproduccion**.
 *
 * El arranque del BotGuard (crear el WebView, bajar el challenge, correr el programa, pedir el
 * integrityToken y construir el minter) tarda ~2-5 s en frio. Antes eso se pagaba dentro del hilo
 * de carga de ExoPlayer, asi que un muro anti-bot se veia como una cancion "colgada" varios
 * segundos antes de que existiera fallback. Ahora:
 *
 *  - [getWebClientPoToken] es `suspend` de verdad: no hay `runBlocking` en ninguna ruta.
 *  - En frio se espera solo [POTOKEN_WARM_UP_BUDGET_MS] y, si no llega, se devuelve `null` de
 *    inmediato; el calentamiento SIGUE en segundo plano ([warmUpJob]) para la proxima cancion.
 *  - En caliente el minteo es sub-segundo, asi que se paga sin costo perceptible.
 *
 * Nada de aqui puede lanzar hacia el reproductor: todo error transitorio se traduce en `null`.
 */
class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    // poToken bound to the session (visitorData), minted once and reused across videos.
    private var webPoTokenSessionPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    /**
     * Calentamiento en curso. Vive en su propio scope a proposito: si la reproduccion deja de
     * esperarlo (presupuesto agotado) el job NO se cancela — sigue creando el WebView y minteando
     * el token de sesion, para que la proxima resolucion lo encuentre caliente.
     */
    @Volatile
    private var warmUpJob: Deferred<Unit>? = null

    /** Sesion para la que corre [warmUpJob]: un visitorData distinto exige un calentamiento nuevo. */
    @Volatile
    private var warmUpSessionId: String? = null
    private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private companion object {
        /**
         * Presupuesto que la reproduccion esta dispuesta a esperar por un BotGuard FRIO. No cubre
         * un arranque completo en un dispositivo lento, y no pasa nada: al agotarse seguimos
         * calentando en background y la cadena de clientes continua sin PoToken.
         */
        const val POTOKEN_WARM_UP_BUDGET_MS = 3_000L

        /**
         * Con el WebView ya inicializado el minteo es sub-segundo; este limite solo protege contra
         * un renderer trabado antes de que la cadena caiga a los clientes moviles.
         */
        const val POTOKEN_MINT_TIMEOUT_MS = 2_000L
    }

    /**
     * Devuelve los dos tokens (sesion -> player request, video -> `pot=` de la URL) o `null` si no
     * estan listos todavia. Nunca bloquea mas de [POTOKEN_WARM_UP_BUDGET_MS], nunca lanza y nunca
     * cancela un calentamiento en curso.
     */
    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) {
            Timber.tag(TAG).d("WebView not available: supported=$webViewSupported, badImpl=$webViewBadImpl")
            return null
        }
        if (sessionId.isBlank()) {
            // Un token de sesion ligado a un visitorData vacio no valida: el request manda otro
            // visitorData. Antes de gastar el WebView, se omite.
            Timber.tag(TAG).w("visitorData vacio; se omite el PoToken")
            return null
        }

        return try {
            awaitWarmUp(sessionId)
            if (!isWarm(sessionId)) {
                Timber.tag(TAG).w("BotGuard todavia frio; sigue calentando en segundo plano, sin PoToken por ahora")
                return null
            }
            withTimeoutOrNull(POTOKEN_MINT_TIMEOUT_MS) {
                mintTokens(videoId, sessionId)
            } ?: run {
                Timber.tag(TAG).w("minteo de poToken supero ${POTOKEN_MINT_TIMEOUT_MS}ms; se continua sin PoToken")
                null
            }
        } catch (e: BadWebViewException) {
            Timber.tag(TAG).e(e, "Could not obtain poToken because WebView is broken")
            webViewBadImpl = true
            null
        } catch (e: Exception) {
            // Transitorio (renderer muerto, JS remoto de Google, hipo de red): jamas se propaga al
            // reproductor. Un [BadWebViewException] ya se filtro arriba.
            Timber.tag(TAG).e(e, "poToken generation failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Estado en caliente: WebView vivo, no expirado, y con el token de sesion ya minteado. */
    private suspend fun isWarm(sessionId: String): Boolean = webPoTokenGenLock.withLock {
        val generator = webPoTokenGenerator
        generator != null &&
            !generator.isExpired &&
            !generator.isDead &&
            webPoTokenSessionPot != null &&
            webPoTokenSessionId == sessionId
    }

    /**
     * Espera acotada al calentamiento. Usa `join()` a proposito: cancelar [join] NO cancela el job,
     * asi que si el presupuesto se agota la inicializacion sigue viva para la proxima cancion.
     */
    private suspend fun awaitWarmUp(sessionId: String) {
        val job = ensureWarmUp(sessionId)
        withTimeoutOrNull(POTOKEN_WARM_UP_BUDGET_MS) { job.join() }
    }

    /** Arranca (o reutiliza) el calentamiento en segundo plano para [sessionId]. */
    private fun ensureWarmUp(sessionId: String): Deferred<Unit> {
        // Solo se reutiliza un calentamiento de la MISMA sesion: el token esta ligado al
        // visitorData que lo minteo, asi que tras un login hay que arrancar uno nuevo.
        warmUpJob?.takeIf { it.isActive && warmUpSessionId == sessionId }?.let { return it }
        synchronized(this) {
            warmUpJob?.takeIf { it.isActive && warmUpSessionId == sessionId }?.let { return it }
            return warmUpScope.async {
                try {
                    webPoTokenGenLock.withLock {
                        if (needsCreation(sessionId)) {
                            createNewGenerator(sessionId)
                        }
                    }
                } catch (t: Throwable) {
                    // Un fallo del warm-up no es fatal: la proxima cancion lo reintenta.
                    Timber.tag(TAG).e(t, "warm-up del PoToken fallo; se reintentara en la proxima cancion")
                }
            }.also {
                warmUpJob = it
                warmUpSessionId = sessionId
            }
        }
    }

    /** Debe leerse/escribirse bajo [webPoTokenGenLock]. */
    private fun needsCreation(sessionId: String): Boolean {
        val generator = webPoTokenGenerator
        return generator == null ||
            generator.isExpired ||
            // Renderer muerto (OOM kill) — recrear en vez de dejar que el primer mint falle.
            generator.isDead ||
            webPoTokenSessionId != sessionId
    }

    /**
     * Crea el WebView y mintea el token de sesion. Debe llamarse bajo [webPoTokenGenLock].
     *
     * El estado comprometido se limpia ANTES de los pasos que pueden fallar: si la creacion o el
     * minteo lanzan, [needsCreation] de la proxima llamada debe dar `true` en vez de emparejar el
     * sessionId ya actualizado con un sessionPot nulo/viejo.
     */
    private suspend fun createNewGenerator(sessionId: String) {
        withContext(Dispatchers.Main) {
            webPoTokenGenerator?.close()
        }
        webPoTokenGenerator = null
        webPoTokenSessionPot = null
        webPoTokenSessionId = null

        val newGenerator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)

        // El token de sesion (ligado a visitorData) se genera exactamente una vez, antes de
        // cualquier token por-video. Se reutiliza entre canciones y viaja en el /player request.
        val newSessionPot = try {
            newGenerator.generatePoToken(sessionId)
        } catch (t: Throwable) {
            // No filtrar el WebView recien creado (cubre tambien la cancelacion por timeout).
            runCatching { newGenerator.close() }
            throw t
        }

        webPoTokenGenerator = newGenerator
        webPoTokenSessionPot = newSessionPot
        webPoTokenSessionId = sessionId
        Timber.tag(TAG).d("Session poToken generated for sessionId=${sessionId.take(20)}...")
    }

    /**
     * Mintea el par de tokens con un generador ya caliente.
     *
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator], to be used in
     * case the current [webPoTokenGenerator] threw an error last time [PoTokenWebView.generatePoToken]
     * was called.
     */
    private suspend fun mintTokens(videoId: String, sessionId: String, forceRecreate: Boolean = false): PoTokenResult {
        val (poTokenGenerator, sessionPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate = forceRecreate || needsCreation(sessionId)
                if (shouldRecreate) {
                    Timber.tag(TAG).d("Creating new PoTokenWebView (forceRecreate=$forceRecreate)")
                    createNewGenerator(sessionId)
                }
                Triple(webPoTokenGenerator!!, webPoTokenSessionPot!!, shouldRecreate)
            }

        val videoPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                // El generador se acaba de recrear (y puede que ya sea el segundo intento), asi
                // que no hay mucho mas que hacer.
                throw throwable
            }
            // Reintento recreando el WebView: pasa, por ejemplo, si la app estuvo en segundo plano
            // y el contenido del WebView se perdio.
            Timber.tag(TAG).e(throwable, "Failed to obtain poToken, retrying")
            return mintTokens(videoId = videoId, sessionId = sessionId, forceRecreate = true)
        }

        Timber.tag(TAG).d("poToken generated successfully: session=${sessionPot.take(20)}..., video=${videoPot.take(20)}...")

        // Binding verificado contra el CDN real (tests/pot-probe.mjs): googlevideo solo sirve el
        // primer 1 MiB de un stream si el pot= de la URL NO esta ligado al VIDEO ID — un pot ligado
        // a visitorData da 403 en cada conexion pasado ese margen (el bug del corte al hacer seek).
        // Por eso el token por-video es el streamingDataPoToken (va en la URL) y el de sesion es el
        // playerRequestPoToken (va en el /player, que si acepta el binding de sesion).
        return PoTokenResult(
            playerRequestPoToken = sessionPot,
            streamingDataPoToken = videoPot,
        )
    }
}
