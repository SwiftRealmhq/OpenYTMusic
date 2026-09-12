<div align="center">

<img src="assets/OpenYTMusic-PNGPUROpng" alt="OpenYTMusic" width="160"/>

# OpenYTMusic

**v0.5.0** · Cliente de YouTube Music · Material Design 3

Desarrollado por: Leo

</div>

---

> ⚠️ **CONFIDENCIAL** — Repositorio **privado** de código **cerrado**. El acceso está limitado
> por invitación explícita del autor. Queda prohibida la copia, distribución, publicación, fork
> o divulgación de cualquier parte del código, total o parcial, sin autorización escrita previa.

---

## Stack técnico

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin (JVM target 17) |
| UI | Jetpack Compose + Material Design 3 |
| Reproducción | AndroidX Media3 (ExoPlayer) |
| Persistencia | Room + DataStore (Preferences) |
| Red | Ktor client + OkHttp |
| Extracción de streams | Cliente InnerTube propio (rotación de clientes, streams muxed) |
| Letras | LrcLib + KuGou + YouTube transcript (con fallback en cascada) |
| RPC | Discord Rich Presence (módulo `discord-rpc`) |
| Tema | Rojo YouTube Music (`#FF0000`), acento saturado, dark puro |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |

## Arquitectura de módulos

```
OpenYTMusic/
├── app/                      # Módulo principal (UI, navegación, reproductor, servicios)
├── innertube/                # Cliente InnerTube: parseo de respuestas de YouTube Music
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
- Temporizador de sueño: barra de tiempo libre, "detener al terminar la canción" con contador real
- Letras sincronizadas (LrcLib / KuGou / transcript) y sin sincronizar
- Discord Rich Presence con timestamp tipo Spotify
- Descargas offline (Selene), colas, radios y mezcla infinita
- Tema oscuro puro con acento rojo vivo y color dinámico opcional desde la portada

## Requisitos de build

- **JDK 17** (se verifica con `java -version`)
- **Android SDK** con `compileSdk 35` / `build-tools 35`
- Gradle wrapper incluido en el repo (no requiere instalación de Gradle)

## Compilar

```bash
# Debug (desarrollo / testeo)
./gradlew :app:assembleFossDebug

# Release (firma aparte, no automatizada)
./gradlew :app:assembleFossRelease
```

Salidas:

| Variante | Ruta |
|---|---|
| Debug | `app/build/outputs/apk/foss/debug/app-foss-debug.apk` |
| Release | `app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk` |

> El release requiere firma manual (`apksigner`) con la keystore privada. La keystore **no** vive
> en este repositorio y **no se comparte** con contribuidores.

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

## Testear vía código

- Ejecutar el **linter** de Kotlin: `./gradlew :app:lintFossDebug`
- Tests unitarios del cliente InnerTube: `./gradlew :innertube:test`
- Verificación de tipos sin emitir artefactos: `./gradlew :app:compileFossDebugKotlin`
- Instalación directa en dispositivo/emulador: `adb install -r app-foss-debug.apk`

La estrategia de testing es conservadora: los cambios deben validarse en dispositivo real
(reproducción, colas, letras y RPC) antes de abrir un PR.

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
   logs de `logcat` (`adb logcat` filtrando por `openytmusic`).

## Estado

- **Versión**: 0.5.0
- **Visibilidad**: privado — no público, no forkable, sin mirrors
- **Propósito del repo**: copia de seguridad en la nube y colaboración cerrada
- **Licencia**: código cerrado — todos los derechos reservados por Leo

## Disclaimer

Este proyecto y su contenido no están afiliados, financiados, autorizados, respaldados ni
asociados de ninguna forma con YouTube, Google LLC ni sus afiliados y subsidiarias. Cualquier
marca comercial, servicio o propiedad intelectual de terceros mencionada pertenece a sus
respectivos dueños.
