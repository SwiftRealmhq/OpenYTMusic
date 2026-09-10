# 🌙 Velqi Luna

> 🎉 **Felicidades.** Si estás leyendo esto es porque eres un **contribuidor autorizado** de Velqi Luna.
> Si eres el creador original, ignora este mensaje.

> **v0.3.0** · Repositorio **privado** · Código **cerrado**
> Autor: **Leo** · Acceso restringido únicamente al creador y contribuidores autorizados.

> ⚠️ **CONFIDENCIAL** — Este repositorio contiene el código fuente completo de Velqi Luna. El acceso está
> limitado por invitación explícita del autor. Queda prohibida la copia, distribución, publicación,
> fork o divulgación de cualquier parte del código, total o parcial, sin autorización escrita previa.

---

## Stack técnico

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin (JVM target 17) |
| UI | Jetpack Compose + Material Design 3 (dynamic color) |
| Reproducción | AndroidX Media3 (ExoPlayer) |
| Persistencia | Room + DataStore (Preferences) |
| Red | Ktor client + OkHttp |
| Extracción de streams | Cliente InnerTube propio (rotación de clientes, streams muxed) |
| Letras | LrcLib + KuGou + YouTube transcript (con fallback en cascada) |
| RPC | Discord Rich Presence (módulo `discord-rpc`) |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |

## Arquitectura de módulos

```
velqi/
├── app/                      # Módulo principal (UI, navegación, reproductor, servicios)
├── innertube/                # Cliente InnerTube: parseo de respuestas de YouTube Music
├── lrclib/                   # Cliente de LrcLib (letras sincronizadas)
├── kugou/                    # Cliente de KuGou (letras)
├── discord-rpc/              # Gateway de Discord (Rich Presence)
├── material-color-utilities/ # Utilidades de color Material (tema dinámico)
└── desktop/                  # Variante de escritorio (experimental)
```

El módulo `app` depende de los módulos de extracción a través de `com.rootleo.velqi.innertube`
y expone la lógica de negocio vía ViewModels (`com.rootleo.velqi.viewmodels`). La capa de
reproducción vive en `com.rootleo.velqi.playback` (servicio de media, colas, radios).

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
   - Abrir **Pull Request** hacia `main` (push directo a `main` está bloqueado).
3. **Política del repo**:
   - `main` está **protegida**: requiere revisión y PR aprobado; sin force push.
   - Prohibido subir APKs, keystores, tokens o secretos (el `.gitignore` los excluye).
   - Prohibido copiar o divulgar el código fuera del repo.
4. **Reporting**: los bugs se describen en un issue con pasos de reproducción, build y
   logs de `logcat` (`adb logcat` filtrando por `velqi`).

## Estado

- **Versión**: 0.3.0 (release universal, firmada)
- **Visibilidad**: privado — no público, no forkable, sin mirrors
- **Propósito del repo**: copia de seguridad en la nube y colaboración cerrada
- **Licencia**: código cerrado — todos los derechos reservados por Leo

## Disclaimer

Este proyecto y su contenido no están afiliados, financiados, autorizados, respaldados ni
asociados de ninguna forma con YouTube, Google LLC ni sus afiliados y subsidiarias. Cualquier
marca comercial, servicio o propiedad intelectual de terceros mencionada pertenece a sus
respectivos dueños.