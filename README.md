<div align="center">

<img src="assets/OpenYTMusicBannerOriginal.png" alt="OpenYTMusic" width="100%"/>

# OpenYTMusic

**v0.5.0** · Cliente de YouTube Music · Material Design 3

Desarrollado por: Leo

</div>

---

> ⚠️ **CONFIDENCIAL** — Repositorio **privado** de código **cerrado**. El acceso está limitado
> por invitación explícita del autor. Queda prohibida la copia, distribución, publicación, fork
> o divulgación de cualquier parte del código, total o parcial, sin autorización escrita previa.

---

## Tabla de contenido

1. [Stack técnico](#stack-técnico)
2. [Arquitectura de módulos](#arquitectura-de-módulos)
3. [Funciones](#funciones)
4. [Requisitos de build](#requisitos-de-build)
5. [Variantes de build](#variantes-de-build)
6. [Compilar paso a paso](#compilar-paso-a-paso)
7. [Firmar el release](#firmar-el-release)
8. [Instalar y probar](#instalar-y-probar)
9. [Logs y diagnóstico](#logs-y-diagnóstico)
10. [Resolución de streams y anti-bot](#resolución-de-streams-y-anti-bot)
11. [Problemas comunes](#problemas-comunes)
12. [API (YouTube Music / InnerTube)](#api-youtube-music--innertube)
13. [Testear vía código](#testear-vía-código)
14. [Guía para contribuidores](#guía-para-contribuidores)
15. [Estado](#estado)
16. [Disclaimer](#disclaimer)

## Stack técnico

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin (JVM target 17) |
| UI | Jetpack Compose + Material Design 3 |
| Reproducción | AndroidX Media3 (ExoPlayer) |
| Persistencia | Room + DataStore (Preferences) |
| Red | Ktor client + OkHttp |
| Extracción de streams | Cliente InnerTube propio (rotación de clientes, streams muxed) |
| Anti-bot | PoToken (BotGuard vía WebView) — módulo `zemer-cipher` |
| Letras | LrcLib + KuGou + YouTube transcript (con fallback en cascada) |
| RPC | Discord Rich Presence (módulo `discord-rpc`) |
| Tema | Rojo YouTube Music (`#FF0000`), acento saturado, dark puro |
| Build | Gradle 8.7 (wrapper) · AGP 8.6.0 · Kotlin 2.0.10 |
| minSdk / targetSdk / compileSdk | 24 / 35 / 35 |
| build-tools | 35.0.0 |

## Arquitectura de módulos

```
OpenYTMusic/
├── app/                      # Módulo principal (UI, navegación, reproductor, servicios)
├── innertube/                # Cliente InnerTube: parseo de respuestas de YouTube Music
├── zemer-cipher/             # Generación de PoToken (BotGuard en WebView) + firmas de streams
├── selene/                   # Extracción alternativa (browse/search/next/streams); aún no la usa `app`
├── lrclib/                   # Cliente de LrcLib (letras sincronizadas)
├── kugou/                    # Cliente de KuGou (letras)
├── discord-rpc/              # Gateway de Discord (Rich Presence)
├── material-color-utilities/ # Utilidades de color Material (tema)
└── desktop/                  # Variante de escritorio (experimental)
```

El módulo `app` depende de los módulos de extracción a través de `com.openytmusic.app.innertube`
y expone la lógica de negocio vía ViewModels (`com.openytmusic.app.viewmodels`). La capa de
reproducción vive en `com.openytmusic.app.playback` (servicio de media, colas, radios).

## Funciones

- Búsqueda, reproducción y colas de YouTube Music (canciones, álbumes, artistas, playlists)
- **Importar playlists y "Me gusta" desde la cuenta real de YouTube Music** (login con captura
  manual de sesión: el usuario decide qué cuenta conectar)
- Shuffle determinista: al activarlo, el salto aleatorio ocurre al terminar la pista o avanzar
- Temporizador de sueño: barra de tiempo libre y "detener al terminar la canción" con contador real
- Letras sincronizadas (LrcLib / KuGou / transcript) y sin sincronizar
- **PoToken (BotGuard) como rescate automático cuando YouTube responde muro anti-bot**
- Discord Rich Presence con timestamp tipo Spotify
- Descargas offline (`ExoDownloadService` + `DownloadUtil` en `app`), colas, radios y mezcla infinita
- Tema oscuro puro con acento rojo vivo y color dinámico opcional desde la portada

## Requisitos de build

| Requisito | Versión | Nota |
|---|---|---|
| JDK | **17** | Obligatorio. Se verifica con `java -version` |
| Android SDK | API 35 | `compileSdk 35` / `targetSdk 35` |
| build-tools | **35.0.0** | Necesario para `zipalign` y `apksigner` al firmar |
| Gradle | 8.7 | Wrapper incluido — no hace falta instalarlo |
| kotlin / ksp | 2.0.10 / 2.0.10-1.0.24 | Gestionados por el wrapper y `libs.versions.toml` |

Configura el SDK con `ANDROID_HOME` o con un `local.properties` en la raíz:

```properties
sdk.dir=/ruta/a/Android/Sdk
```

> `local.properties` está en `.gitignore` — nunca se sube.

## Variantes de build

El proyecto tiene dos *flavors* (`version`) × dos *tipos* (`debug` / `release`):

| Flavor | Contenido | Requiere `google-services.json` |
|---|---|---|
| `foss` | Sin Google Play Services. Build limpio y reproducible | No |
| `full` | Firebase (Analytics, Crashlytics, Config, Perf) + ML Kit | Sí |

| Tipo | `applicationId` | Optimización |
|---|---|---|
| `debug` | `com.openytmusic.app` **+ `.debug`** | Sin R8, sin recorte de recursos |
| `release` | `com.openytmusic.app` | R8 + `shrinkResources` + sin `crunchPngs` |

> Al ser `applicationId` distintos, **debug y release se pueden instalar en paralelo**. Para
> instalar release encima de debug hay que desinstalar antes (firmas distintas).

El APK es **universal** (todas las ABI en un solo archivo): el bloque `splits { abi }` está
desactivado a propósito en `app/build.gradle.kts`.

## Compilar paso a paso

Todo se ejecuta desde la raíz del proyecto (`Velqi-Kt/`).

### 1. Verificación rápida de tipos (lo más rápido, sin empaquetar)

```bash
./gradlew :app:compileFossReleaseKotlin
```

### 2. Debug (desarrollo y testeo en dispositivo)

```bash
./gradlew :app:assembleFossDebug
```

Salida: `app/build/outputs/apk/foss/debug/app-foss-debug.apk` (~25 MB).

### 3. Release optimizado (el que se distribuye)

```bash
./gradlew :app:assembleFossRelease
```

Salida: `app/build/outputs/apk/foss/release/app-foss-release.apk` (~7,7 MB, **firmado** con
`openytmusic-release.jks`).

Si la keystore **no** está en la raíz, el build continúa y deja el APK sin firmar como
`app-foss-release-unsigned.apk`; en ese caso hay que firmarlo a mano (ver
[Firmar el release](#firmar-el-release)).

R8 + recorte de recursos bajan el APK de ~25 MB a ~7,7 MB. En este entorno tarda ~20-60 s en
caliente; la primera vez (sin caché de Gradle) puede pasar de 5 minutos.

### 4. APK completo (con Firebase)

```bash
./gradlew :app:assembleFullRelease
```

Necesita `google-services.json` en `app/`. Un build cuyo nombre de tarea **no** contenga `foss`
activa automáticamente los plugins de Firebase (`isFullBuild` en `build.gradle.kts` raíz).

### 5. Limpieza

```bash
./gradlew clean          # limpia build/ de todos los módulos
./gradlew --stop         # detiene el daemon de Gradle (útil si algo queda trabado)
```

## Firmar el release

`./gradlew :app:assembleFossRelease` **firma el APK automáticamente** cuando la keystore está en
la raíz: `signingConfigs.release` se asigna a `buildTypes.release.signingConfig` en
`app/build.gradle.kts`. La keystore vive fuera del control de versiones
(`openytmusic-release.jks`, en `.gitignore`) y no se comparte.

Si la keystore **no** está presente, el build no falla: sigue adelante y entrega
`app-foss-release-unsigned.apk` sin firmar. Para ese caso (o para re-firmar a mano) usa el flujo
`zipalign` + `apksigner`:

**Credenciales** (mismas que usa `signingConfigs.release` en `app/build.gradle.kts`):

| Dato | Valor |
|---|---|
| Archivo | `openytmusic-release.jks` (raíz del proyecto) |
| Alias | `openytmusic` |
| Contraseña de store | variable `OYM_STORE_PASSWORD` (respaldo: `OpenYTMusic2026`) |
| Contraseña de clave | variable `OYM_KEY_PASSWORD` (respaldo: `OpenYTMusic2026`) |

Firma manual de respaldo con `zipalign` + `apksigner`:

```bash
BT="$ANDROID_HOME/build-tools/35.0.0"

# 1) Alinear el APK (obligatorio antes de firmar)
"$BT/zipalign" -f -p 4 \
  app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk \
  /tmp/oym-aligned.apk

# 2) Firmar
"$BT/apksigner" sign \
  --ks openytmusic-release.jks \
  --ks-key-alias openytmusic \
  --ks-pass env:OYM_STORE_PASSWORD \
  --key-pass env:OYM_KEY_PASSWORD \
  --out OpenYTMusic-0.5.0-release.apk \
  /tmp/oym-aligned.apk

# 3) Verificar la firma (imprime el certificado)
"$BT/apksigner" verify --print-certs OpenYTMusic-0.5.0-release.apk
```

Salida esperada del paso 3: `Signer #1 certificate DN: CN=OpenYTMusic, ...` y
`Signer #1 certificate SHA-256 digest: b37ab893...`.

> **Guarda la keystore y sus contraseñas.** Perderla significa que no podrás volver a
> actualizar la app firmada: Android rechaza cualquier APK firmado con otra clave.

## Instalar y probar

```bash
# Dispositivo por USB
adb install -r OpenYTMusic-0.5.0-release.apk

# Emulador / Waydroid por red
adb connect 192.168.240.112:5555
adb install -r OpenYTMusic-0.5.0-release.apk

# Desinstalar y empezar de cero (borra biblioteca local y sesión)
adb uninstall com.openytmusic.app
```

Comprobación rápida de que la app está viva y reproduciendo:

```bash
adb shell dumpsys media_session | grep -E "state=PlaybackState"
# state=3 -> reproduciendo · state=2 -> pausado · error=null -> sin errores
```

## Logs y diagnóstico

Tags útiles de `logcat`:

| Tag | Qué muestra |
|---|---|
| `KernelVelqi` | Resolución de streams: `itag`, si es muxed, **cliente ganador**, `pot=`, intento con PoToken y URL (recortada) |
| `PoTokenGenerator` / `PoTokenWebView` | Ciclo de vida del BotGuard: WebView, `botguardResponse`, minter, token generado |
| `VelqiRPC` | Discord Rich Presence (gateway, presencia enviada) |
| `Timber` (resto) | Errores de red, sesión y parseo |

```bash
# Solo lo importante, en vivo
adb logcat -c                      # limpia el buffer
adb logcat | grep -E "KernelVelqi|PoToken|VelqiRPC"

# Volcado de todo el buffer a un archivo
adb logcat -d > logcat.txt
```

Lectura del log de reproducción:

```
KernelVelqi: stream itag=18 muxed=true cliente=ANDROID pot=false intento=null url=...
```

- `cliente=` → cliente que entregó los streams (`ANDROID`, `IOS`, `ANDROID+pot`, `WEB_REMIX+pot`,
  `ANDROID_VR`, `TVHTML5+piped`).
- `pot=true/false` → si la URL **que se está reproduciendo** lleva token de atestación. En los
  muxed (`muxed=true`) siempre es `false`: el itag 18 no consulta el `pot`.
- `intento=` → resultado del intento con PoToken; `null` cuando **no fue necesario** levantarlo, y
  `sin PoToken (BotGuard frio, calentando o no disponible)` cuando no estaba listo a tiempo.

## Resolución de streams y anti-bot

La obtención de streams vive en `innertube/YouTube.kt` → `player()`. La estrategia está
**medida contra la API real**, no asumida:

| Paso | Cliente | Estrategia |
|---|---|---|
| 1 | `ANDROID` (con cookie de sesión si existe) | Camino normal. En la práctica responde `OK` siempre |
| 2 | `IOS` | Respaldo del anterior |
| 3 | `ANDROID` + **PoToken** | Solo si los anteriores devolvieron muro anti-bot (`LOGIN_REQUIRED` / *"not a bot"*). Es el cliente que **mejor** entrega streams (muxed itag 18 + adaptativos con rangos completos), así que el rescate no depende del cliente web. El WebView se levanta **únicamente aquí** y **nunca bloquea la reproducción**: si el BotGuard todavía está frío, devuelve `null` y termina de arrancar en segundo plano |
| 4 | `WEB_REMIX` + **PoToken** | Respaldo del anterior (camino histórico, medido sin sesión) |
| 5 | `ANDROID_VR` | Último recurso móvil |
| 6 | `TVHTML5` + piped | Fallback histórico para streams muxed |

Resultados medidos desde una red de datacenter (Waydroid) con y sin sesión:

| Cliente | Resultado |
|---|---|
| `ANDROID` | ✅ `OK`, 25 formatos |
| `IOS` | ✅ `OK`, 23 formatos |
| `ANDROID_VR` | ❌ `LOGIN_REQUIRED` — *"Sign in to confirm you're not a bot"* |
| `WEB_REMIX` / `WEB` | ❌ `UNPLAYABLE` — *"Video unavailable"* (desde esa red) |
| `TVHTML5` | ❌ *"YouTube is no longer supported in this application"* |
| `ANDROID` + pot | ⏳ ronda nueva: pendiente de medir en dispositivo. El `intento=` del log y la telemetría
  de muro (ver abajo) dicen si gana a `WEB_REMIX+pot` |

### Cómo funciona el PoToken

El PoToken (*Proof of Origin Token*) es la prueba de que la petición viene de un navegador
legítimo. El módulo `zemer-cipher` resuelve el desafío de BotGuard de YouTube dentro de un
**WebView invisible** y devuelve dos tokens:

- `playerRequestPoToken` → token **ligado a la sesión** (`visitorData`); viaja en el cuerpo de
  la petición a `/player` como `serviceIntegrityDimensions.poToken`.
- `streamingDataPoToken` → token **ligado al video**; se añade como `pot=` a la URL del stream.
  Sin él, googlevideo puede servir solo el primer ~1 MB (corta al hacer seek).

Reglas de diseño ya implementadas (no romper):

1. **El PoToken nunca va primero.** Si los clientes móviles responden `OK`, el WebView no se
   levanta y la reproducción no paga los ~2-5 s de su arranque.
2. **El `pot=` solo se inyecta cuando la respuesta web es la que se va a reproducir.** Añadirlo
   a URLs de clientes Android no aporta y ensucia el diagnóstico.
3. **Si falla, no rompe nada:** la cadena sigue con los clientes móviles, y todo error transitorio
   del BotGuard se traduce en `null` — nada de ahí puede lanzar hacia el reproductor.
4. **Un BotGuard frío no bloquea:** el arranque en frío se espera como máximo **3 s**. Si no llega,
   la cadena sigue sin PoToken y el WebView termina de inicializar en segundo plano para la
   siguiente canción; el reintento de `/player` (3 s tras un muro) es lo que convierte esa segunda
   vuelta en música en vez de en un error.
5. **El `pot=` solo se añade a streams adaptativos:** el muxed (itag 18) sirve el archivo completo
   con rangos ilimitados y no consulta el token.
6. **El orden del rescate se mide, no se asume.** Cada intento con PoToken queda en `intento=`
   (log) y los muros se reportan como *no-fatal* (`BotWallException`, ver abajo), así que la ruta
   ganadora se decide con datos de campo y no con intuición.

### Qué dispara el muro anti-bot y qué lo mata

El muro (`Sign in to confirm you're not a bot`) no es aleatorio: lo dispara la **reputación de la
IP** sumada a la **falta de atestación**. En orden de eficacia:

| Palanca | Quién la sufre / quién la resuelve |
|---|---|
| **Sesión iniciada** | Es lo que **mata** el muro y es la única solución de fondo. Se captura desde
  el WebView de login (`Ajustes → YouTube Music`) y es **la misma cookie** que desbloquea tus
  playlists de YouTube Music: no hay que exportar nada a mano |
| **PoToken** | Rescate invisible sin cuenta: cubre el caso de quien no quiere iniciar sesión |
| **IP** | Un usuario normal (casa, datos móviles) **casi nunca** lo topa; los que lo topan son
  VPN, proxies y redes de datacenter — justo el escenario de un emulador |

Y para no ir a ciegas: cuando el muro aparece, el resolver reporta un **no-fatal** (`reportException`
→ Crashlytics en el flavor `full`, log en `foss`) con el detalle de qué cliente se usó, si hubo
rescate y si había sesión. Así la frecuencia y la eficacia se miden en campo en vez de suponerse.

> **Autenticación por familia de cliente:** los clientes móviles (`ANDROID`, `IOS`) se autentican
> solo con la cookie. La familia **web** (`WEB`, `WEB_REMIX`) exige además
> `Authorization: SAPISIDHASH <ts>_<sha1("<ts> <SAPISID> https://music.youtube.com")>` — enviar
> la cookie **sin** esa firma equivale a ir sin sesión, que es exactamente lo que dispara el muro.
> `/player` ahora firma cuando el cliente es web.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| *"Sign in to confirm you're not a bot"* en el reproductor | Muro anti-bot de YouTube. Abre **Ajustes → YouTube Music** e inicia sesión: con cookies las peticiones van autenticadas y el muro desaparece. También se activa solo el rescate con PoToken. Revisa `cliente=` e `intento=` en `KernelVelqi` para ver qué pasó |
| La reproducción tarda ~2-5 s en empezar | El WebView del PoToken se está levantando. Debería pasar **una sola vez y solo tras un muro anti-bot**: en frío se espera 3 s como máximo y el resto del arranque sigue en segundo plano. Si se repite en cada canción, revisa el orden de clientes en `player()`; si el `visitorData` está vacío (`intento=` en `KernelVelqi`), el PoToken se omite y el muro nunca se resuelve |
| Error de KSP con rutas de paquetes viejas | Caché de KSP tras mover/renombrar paquetes: `./gradlew clean` y recompilar (el daemon también con `./gradlew --stop`) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | El APK release está firmado con otra clave que el instalado (o hay debug instalado). `adb uninstall com.openytmusic.app` y reinstalar |
| El APK release no se instala encima del debug | Son apps distintas por `applicationId` (`.debug`); desinstala el debug si quieres el mismo paquete |
| `signingConfig` no encuentra la keystore | `openytmusic-release.jks` no está en la raíz del proyecto. Sin ella, compila sin firmar (`app-foss-release-unsigned.apk`) |
| Waydroid/emulador no aparece en `adb devices` | `adb connect <ip>:5555` (Waydroid suele ser `192.168.240.112`) |
| Playlists no aparecen tras iniciar sesión | YouTube entrega playlists privadas solo a sesiones completas. Cierra la app y ábrela; si persiste, **Cerrar sesión** y volver a capturar la sesión eligiendo la cuenta correcta |

## API (YouTube Music / InnerTube)

OpenYTMusic no depende de servicios de terceros para el contenido: se comunica directamente con
el **cliente InnerTube** de YouTube Music — la misma API interna que usa la aplicación oficial.
Toda la implementación vive en el módulo `innertube` (`com.openytmusic.app.innertube`).

Endpoints principales expuestos:

| Endpoint | Uso | Página tipada |
|---|---|---|
| `browse` | Home, explorar, álbumes, artistas, playlists | `HomePage`, `AlbumPage`, `ArtistPage`, `PlaylistPage` |
| `search` | Búsqueda global y sugerencias | `SearchPage`, `SearchSuggestionPage` |
| `player` | Obtención de streams para reproducir | `PlayerResponse` |
| `next` | Cola y canciones relacionadas | `NextPage`, `RelatedPage` |

Reglas de uso:

- Todo el tráfico es HTTPS; las peticiones llevan un `context` de cliente (idioma, región y client version).
- Las respuestas llegan como renderers de YouTube y el módulo las parsea a **modelos tipados**
  (`models/`), que es lo único que consume el módulo `app`.
- Si necesitas tocar algo de contenido: cambia solo lo que expone el módulo `innertube`;
  la capa de UI nunca habla con la API directamente.
- No modifiques la lógica de obtención de streams sin validar en dispositivo: cualquier cambio
  ahí afecta la reproducción global de la app.
- La sesión se captura del `CookieManager` del WebView de login (misma autenticación que la web:
  `SAPISID` + `SAPISIDHASH`). No hay atajos ni credenciales guardadas en texto plano fuera del
  almacén de preferencias de la app.

## Testear vía código

```bash
./gradlew :app:compileFossDebugKotlin   # verificación de tipos (rápida)
./gradlew :app:lintFossDebug            # linter de Android/Kotlin
./gradlew :innertube:test               # tests unitarios del cliente InnerTube
./gradlew :selene:test                  # tests unitarios de selene (browse/search/next/queue)
./gradlew :kugou:test                   # tests unitarios del cliente de letras KuGou
```

Instalación directa en dispositivo/emulador:

```bash
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

La estrategia de testing es conservadora: los cambios deben validarse en dispositivo real
(reproducción, colas, letras y RPC) antes de abrir un PR. Checklist mínimo por cambio:

1. `./gradlew :app:compileFossReleaseKotlin` sin errores.
2. Reproducción real con `cliente=ANDROID` en el log y `error=null` en `dumpsys media_session`.
3. Saltar 2-3 canciones y confirmar que la resolución sigue siendo inmediata.

## Guía para contribuidores

1. **Acceso**: solo por invitación del autor (colaborador con rol `Write`). No se otorgan roles
   `Admin` ni acceso a la keystore, secretos o credenciales de publicación.
2. **Flujo de trabajo**:
   - Crear una rama descriptiva desde `main` (`git checkout -b fix/nombre-corto`).
   - Implementar el cambio siguiendo el estilo existente (Kotlin, Compose, strings en `values/`
     y `values-es/`).
   - Verificar compilación y linter antes del push.
   - Abrir **Pull Request** hacia `main`.
3. **Política del repo**:
   - Prohibido subir APKs, keystores, tokens o secretos (el `.gitignore` los excluye).
   - Prohibido copiar o divulgar el código fuera del repo.
4. **Reporting**: los bugs se describen en un issue con pasos de reproducción, build y
   logs de `logcat` (`adb logcat` filtrando por `KernelVelqi`).

## Estado

- **Versión**: 0.5.0 (`versionCode` 31)
- **APK release**: universal, ~7,7 MB firmado
- **Visibilidad**: privado — no público, no forkable, sin mirrors
- **Propósito del repo**: copia de seguridad en la nube y colaboración cerrada
- **Licencia**: código cerrado — todos los derechos reservados por Leo

## Disclaimer

Este proyecto y su contenido no están afiliados, financiados, autorizados, respaldados ni
asociados de ninguna forma con YouTube, Google LLC ni sus afiliados y subsidiarias. Cualquier
marca comercial, servicio o propiedad intelectual de terceros mencionada pertenece a sus
respectivos dueños.
