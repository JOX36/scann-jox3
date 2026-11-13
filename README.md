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
- [ ] **Integración real con Telegram (TDLib)** — por ahora el login usa
      `FakeTelegramAuthClient`, una implementación de prueba que simula los
      tiempos de red (acepta cualquier teléfono y el código `12345`), para
      poder probar y ajustar toda la UI sin depender de la build nativa.

## Por qué TDLib queda pendiente como paso aparte

TDLib es la librería oficial de Telegram para actuar como una cuenta de
usuario (no un bot) — es la única opción madura para esto en Android. Pero
su build real (CMake + NDK, generación de `TdApi.java` desde `td_api.tl`)
depende de decisiones concretas (versión del submódulo, binario propio vs.
precompilado) que conviene resolver juntos y verificar que compila, en vez
de dejar una configuración adivinada que podría no funcionarte. La interfaz
`TelegramAuthClient` ya está lista para recibir esa implementación real
(`TdLibAuthClient`) sin tocar la UI.

## Nota sobre el CI

El workflow de GitHub Actions (`.github/workflows/build.yml`) compila el
proyecto en cada push/PR. El wrapper de Gradle (`gradlew`) se generó sin
conexión a internet en el entorno donde se creó este commit, así que el
primer build real (con acceso a Google Maven / Maven Central) ocurre en
GitHub Actions, no fue verificado localmente antes de este commit.
