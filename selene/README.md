# Selene 🌙

**La API propia de Velqi Luna para YouTube Music.**

Escrita desde cero en Kotlin/JVM. Sin librerías de terceros de YouTube: nosotros
construimos el protocolo (JSON + cabeceras), hablamos directo con los endpoints
de YouTube y parseamos las respuestas. Solo infraestructura base: Ktor + kotlinx.serialization.

> Selene = la diosa griega de la Luna. Nuestra API, nuestra marca.

## Módulo

```
selene/
├── build.gradle.kts
├── README.md
└── src/main/java/com/rootleo/velqi/selene/
    ├── Selene.kt              ← Punto de entrada único (todo devuelve Result<T>)
    ├── cli/                   ← 🖥️ CLI de terminal (selene download, search, player...)
    ├── model/                 ← Modelos JSON compartidos + helpers de parseo
    ├── internal/              ← Cabeceras WEB_REMIX y contexto compartidos
    ├── player/                ← Endpoint player + rotación de clientes
    ├── streams/               ← Selección del mejor stream de audio
    ├── search/                ← Búsqueda + sugerencias
    ├── browse/                ← Páginas de álbum, artista y playlist
    └── queue/                 ← Cola de reproducción (next)
```

## 🖥️ CLI de terminal

Construye el ejecutable y úsalo desde tu terminal:

```bash
./gradlew :selene:installDist
./selene/build/install/selene/bin/selene help
```

```
selene download <link|videoId> [directorio]   ⬇️  Descarga el audio (mejor stream) con progreso
selene player   <link|videoId>                🎵 Metadatos + streams disponibles
selene search   <query>                       🔍 Busca canciones/álbumes/artistas/playlists
selene album    <browseId>                    💿 Página de álbum
selene artist   <browseId>                    👤 Página de artista
selene playlist <playlistId|link>             📋 Página de playlist
selene next     <link|videoId>                🎧 Cola de reproducción
```

Acepta links de YouTube: `youtu.be/ID`, `youtube.com/watch?v=ID`, `music.youtube.com/...`, `playlist?list=...`, o el videoId pelado.

## Uso rápido

```kotlin
// Reproducción
Selene.player("dQw4w9WgXcQ")            // → PlayerData (metadatos + streams)
Selene.bestAudio("dQw4w9WgXcQ")         // → AudioStream (mejor Opus)

// Búsqueda
Selene.search("YOASOBI")                // → mezcla "Todo" (canciones+álbumes+artistas+playlists)
Selene.search("YOASOBI", Selene.SearchFilter.SONG)  // → canciones filtradas
Selene.searchContinuation(token)        // → paginación
Selene.searchSuggestions("yoa")         // → autocompletado

// Páginas de contenido
Selene.album("MPREb_oNAdr9eUOfS")       // → AlbumPage (álbum + canciones + otras versiones)
Selene.artist("UCI6B8NkZKqlFWoiC_xE-hzA") // → ArtistPage (secciones)
Selene.playlist("RDCLAK5uy_...")        // → PlaylistPage (canciones + continuación)
Selene.playlistContinuation(token)      // → paginación

// Cola de reproducción
Selene.nextForVideo("dQw4w9WgXcQ")      // → NextResult (up next + tabs de letras/relacionado)
Selene.next(UpNextEndpoint(videoId, playlistId))  // → cola de playlist
Selene.next(endpoint, continuation)     // → paginar la cola
```

## API completa

### Reproducción

| Función | Devuelve | Notas |
|---|---|---|
| `player(videoId, playlistId?)` | `Result<PlayerData>` | Rotación ANDROID → ANDROID_VR → IOS; reporta UNPLAYABLE/LOGIN_REQUIRED sin romper |
| `bestAudio(videoId)` | `Result<AudioStream>` | Mejor stream: Opus > AAC > resto, mayor bitrate |
| `downloadAudio(videoId, dir, onProgress)` | `Result<File>` | Descarga el audio con progreso (útil para descargas offline) — chunking adaptativo + fallback entre formatos |

### Búsqueda

| Función | Devuelve | Notas |
|---|---|---|
| `search(query, filter?)` | `Result<SearchResult>` | Sin filtro = tab "Todo" mezclado; con filtro = shelf tipado + continuation |
| `searchContinuation(token)` | `Result<SearchResult>` | Paginación |
| `searchSuggestions(input)` | `Result<SearchSuggestions>` | Autocompletado |

**Filtros**: `SONG`, `VIDEO`, `ALBUM`, `ARTIST`, `PLAYLIST_FEATURED`, `PLAYLIST_COMMUNITY`

### Browse

| Función | Devuelve | Notas |
|---|---|---|
| `album(browseId)` | `Result<AlbumPage>` | Canciones completas (heredan portada/artistas del álbum) + otras versiones |
| `artist(browseId)` | `Result<ArtistPage>` | Header + secciones (Top songs, Albums, Singles, Playlists...) |
| `playlist(playlistId)` | `Result<PlaylistPage>` | Canciones + continuación |
| `playlistContinuation(token)` | `Result<PlaylistContinuationPage>` | Paginación |

### Cola de reproducción

| Función | Devuelve | Notas |
|---|---|---|
| `next(endpoint, continuation?)` | `Result<NextResult>` | Cola (up next) + canción actual + tabs de letras/relacionado + automix |
| `nextForVideo(videoId)` | `Result<NextResult>` | Atajo para un video suelto |

`NextResult` incluye `currentIndex` (canción actual), `lyricsBrowseId` / `relatedBrowseId`
(para pedir letras y relacionado), `continuation` (paginación) y `automixEndpoint`:
cuando la cola termina, llama a `next(automixEndpoint)` para extender la radio
(ej: un mix de 50 canciones).

### Modelo de dominio (`search/MediaItem.kt`)

`MediaItem` (sellado) → `SongItem` (videoId, título, artistas, álbum, duración, portada, explícito) · `AlbumItem` (browseId, playlistId, año) · `ArtistItem` · `PlaylistItem` (autor, nº canciones)

## Cómo funciona (la estrategia "que nunca caiga")

1. **Protocolo directo**: construimos el JSON de la petición y las cabeceras nosotros mismos, presentándonos como las apps oficiales (ANDROID, IOS, WEB_REMIX) con sus identificadores públicos.
2. **Rotación de clientes**: si un cliente es bloqueado o devuelve error, probamos el siguiente automáticamente.
3. **Manejo de errores elegante**: videos eliminados → `UNPLAYABLE` con motivo; videos con login → `LOGIN_REQUIRED`; nunca lanzan excepciones, siempre `Result`.
4. **Streams sin descifrar**: el cliente Android devuelve las URLs directas (sin `signatureCipher`), verificado con Range requests (`206 Partial Content`).
5. **Descarga por chunks con Range cerrado**: YouTube exige el header `Range` para servir los streams (sin Range o con `Range: bytes=0-` abierto devuelve `403 Forbidden`). `downloadAudio` descarga por chunks de 4 MB y, si recibe 403, reintenta el mismo offset con chunks de 900 KB (debajo del límite).
6. **Detección del límite anti-descarga de música (~1 MB)**: YouTube aplica un cap de ~1 MB a los streams de **música de sellos discográficos** (VEVO, 88rising, canales "- Topic"...), desde cualquier IP — verificado desde IP de datacenter Y residencial (Despacito, Joji, ZUTOMAYO capados; Rick Astley, canal del artista, libre). Selene lo detecta, limpia el archivo parcial y proba el siguiente formato de audio; si todos están limitados, devuelve un mensaje claro en vez de un archivo truncado. Diagnóstico en un comando: `bash selene/scripts/diag-cap.sh`.

   > 🔮 **Próximo paso (plan B)**: cliente **WEB** con descifrado de firmas JS — los clientes móviles (ANDROID/IOS) son los que YouTube capea; el cliente web usa otra vía de servicio. Es el "seguro de vida" del roadmap.

## Tests

```bash
./gradlew :selene:test
```

18 tests de integración contra YouTube real: player + streams (206), búsqueda con filtros + tab "Todo" (con parseo de tipo de item), paginación, sugerencias, álbum, artista, playlist, cola de reproducción, continuaciones y automix. Más la CLI verificada desde terminal (descarga real de un link).

> 🐛 **Bug cazado en el camino**: al refactorizar el descargador, el `offset` del loop nunca avanzaba → el mismo chunk de 900 KB se reescribía en bucle (archivo de 104 MB idéntico 113 veces). Se arregló con `offset += received` + validación final del tamaño del archivo contra `contentLength`.

## Roadmap

- [x] Player con rotación de clientes
- [x] Búsqueda + filtros + sugerencias
- [x] Páginas de álbum/artista/playlist
- [x] Cola de reproducción (`next`) con automix
- [ ] Home/explore
- [x] CLI de terminal para testing
- [ ] Capa de resiliencia: pool de versiones auto-actualizable + health-check canario
- [ ] Descifrado de firmas (seguro de vida si YouTube cifra las URLs)
- [ ] Login con cuenta de Google (cookies + SAPISID)