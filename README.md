# IPTV Scanner (scann-jox3)

Versión Android del script de Python "IPTV Super Scanner": escanea tus chats
de Telegram en busca de listas M3U/Xtream, las valida y detecta cuáles son
mayormente contenido latino.

Proyecto separado de iptv-exo (el reproductor); este solo escanea y valida.

## Requisitos para compilar

- Android Studio (última estable) o JDK 17 + Android SDK por línea de comandos
- `./gradlew assembleDebug` compila el APK de debug

## Estado actual (Etapa 1)

- [x] Estructura base del proyecto (Compose + Kotlin + Gradle)
- [x] Flujo de login (teléfono → código → opcional contraseña 2FA)
- [x] Almacenamiento seguro de credenciales listo para usarse (`security-crypto`)
- [x] **Integración real con Telegram (TDLib)** — implementada en Etapa 4,
      ver más abajo. `FakeTelegramAuthClient` queda en el repo sin usarse por
      defecto (sirve de referencia / para pruebas de UI).

## Estado actual (Etapa 2)

- [x] Comprobación de actualizaciones contra GitHub Releases
      (`GET /repos/JOX36/scann-jox3/releases/latest`)
- [x] Descarga del `.apk` adjunto al release con el `DownloadManager` del
      sistema
- [x] Flujo de permiso "instalar apps desconocidas" + instalación vía
      `FileProvider` cuando termina la descarga
- [ ] El `.apk` debe subirse manualmente (o por CI, en un paso futuro) como
      asset de cada release en GitHub, con el nombre `app-debug.apk` (o
      cualquier nombre que termine en `.apk` si ese no se encuentra)
- [ ] No hay comprobación periódica en segundo plano todavía: solo se
      comprueba al abrir la app

La actualización es siempre semi-automática, igual que en iptv-exo: la app
avisa y descarga, pero el usuario confirma la instalación en la pantalla
estándar de Android — nunca se instala nada en silencio.

## Estado actual (Etapa 3)

- [x] Motor de validación de listas, portado del script de Python:
      extracción de credenciales Xtream desde un enlace, verificación de
      cuenta (`player_api.php`), y detección de contenido latino por
      categorías + nombres de canal como respaldo (mismas palabras clave y
      umbral de 12% que el script)
- [x] Pantallas de selección de chats, progreso de escaneo en vivo y
      resultados finales (activas / LATAM), con el mismo tema visual de la
      vista previa de diseño
- [ ] **Lectura real de mensajes de Telegram** — por ahora `ChatsRepository`
      y `EscanerRepository` tienen implementaciones de prueba
      (`FakeChatsRepository`, `FakeEscanerRepository`) con chats y mensajes
      de ejemplo (incluye un enlace real para poder probar el motor de
      validación contra un panel de verdad). Se reemplazan por
      implementaciones con TDLib sin tocar las pantallas ni el motor.

## Estado actual (Etapa 4)

- [x] Login real contra Telegram con TDLib (`TdLibAuthClient`), reemplazando
      `FakeTelegramAuthClient` como implementación por defecto en
      `MainActivity.kt` — la interfaz `TelegramAuthClient` no cambió, así
      que las pantallas de login no se tocaron.
- [x] Pantalla única de primer arranque para guardar el API_ID / API_HASH de
      Telegram, cifrados en el dispositivo (`CredencialesApiTelegram`,
      sobre `security-crypto`).
- [x] `ChatsRepository` / `EscanerRepository` reales implementados en
      Etapa 5, ver más abajo.

### Cómo se eligió integrar TDLib

TDLib es la librería oficial de Telegram para actuar como una cuenta de
usuario (no un bot) — es la única opción madura para esto en Android.
Compilarla desde cero (CMake + NDK, generación de `TdApi.java` desde
`td_api.tl`) no se puede verificar en este entorno de desarrollo (sin
acceso a los repositorios de Google/Android), así que se optó a propósito
por un binario **prebuilt**: `com.github.tdlibx:td:1.8.56`, publicado vía
JitPack (repackaging del `tdlib/td` oficial). La contrapartida es depender
de que ese repositorio de terceros se mantenga; si en algún momento deja de
publicarse, el reemplazo es cambiar una sola dependencia en
`app/build.gradle.kts` sin tocar `TdLibAuthClient.kt`.

### Primer uso (API_ID / API_HASH)

TDLib exige credenciales de **aplicación** (distintas de tu cuenta de
Telegram): se crean gratis, una sola vez, en
[my.telegram.org/apps](https://my.telegram.org/apps). La primera vez que
abras la app te va a pedir el API ID y el API Hash — se guardan cifrados en
el teléfono y no se vuelven a pedir.

## Nota sobre el CI

El workflow de GitHub Actions (`.github/workflows/build.yml`) compila el
proyecto en cada push/PR. El wrapper de Gradle (`gradlew`) se generó sin
conexión a internet en el entorno donde se creó este commit, así que el
primer build real (con acceso a Google Maven / Maven Central) ocurre en
GitHub Actions, no fue verificado localmente antes de este commit.
