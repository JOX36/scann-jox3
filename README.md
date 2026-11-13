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

## Estado actual (Etapa 6)

- [x] **Menú de modos de escaneo**, igual que el menú del script de Python
      original (que tenía 5 opciones antes de escanear). Antes la app iba
      directo de "elegir chats" a "escanear todo"; ahora primero se elige el
      modo:
      1. **Cualquier lista**: el flujo de siempre (elegir uno o varios chats
         y escanear todo lo que haya).
      2. **Buscar un host en todos los chats**: escribe un host/dominio y lo
         busca en todos tus chats, sin elegir uno a uno.
      3. **Buscar un host en un chat específico**: escribe un host y elige un
         único chat.
      4. **Solo contenido LATINO en un chat**: elige un único chat y se queda
         solo con las listas activas y de contenido latino.
      5. **Escanear un archivo .txt del dispositivo**: abre el selector de
         archivos del sistema, lee el .txt elegido y se queda solo con las
         listas latinas que encuentre — equivalente al modo "archivo local"
         del script (que en la versión de escritorio leía una carpeta fija
         de Termux; aquí se eligen con el selector nativo de Android).
- [x] `EscanerRepository.escanear(...)` ahora acepta un filtro de host
      opcional y un flag "solo latino", reutilizados por los 3 primeros
      modos sin duplicar el motor de validación.

## Firma estable y actualizaciones automáticas reales

Hasta ahora cada build de CI quedaba firmado con una keystore de debug
distinta (cada runner de GitHub Actions es una máquina nueva, así que
generaba una keystore nueva cada vez). Android no permite instalar un APK
"encima" de otro firmado con otra llave, así que cada instalación nueva
exigía desinstalar primero — y eso borraba el API_ID/API_HASH guardados y
la sesión de TDLib, obligando a volver a escribirlos y a iniciar sesión de
nuevo cada vez.

- [x] Keystore de debug fija, incluida en el repo (`keystore/debug.keystore`,
      sin datos sensibles — es solo una firma de desarrollo, no la cuenta de
      Google Play). A partir de este cambio, instalar un build nuevo
      actualiza la app sin perder las credenciales ni la sesión. **Nota**:
      esta primera vez sí hay que desinstalar manualmente la versión
      anterior una única vez, porque quedó firmada con una llave distinta a
      la fija.
- [x] `versionName`/`versionCode` ya no quedan fijos en "0.1.0": CI los
      genera con el número de build de GitHub Actions (`0.1.<run_number>`),
      así que cada build es "más nueva" de verdad que la anterior y la app
      puede saber si ya está al día.
- [x] El workflow de CI ahora publica un **Release de GitHub** con el `.apk`
      adjunto en cada `push` (no en PRs abiertas todavía sin mergear) — de
      ahí es de donde `UpdateManager.kt` (Etapa 2) busca actualizaciones. Sin
      este paso, el código de auto-actualización estaba listo pero nunca
      tenía nada que encontrar.

## Estado actual (Etapa 7): análisis del video y 5 arreglos juntos

El usuario mandó un video mostrando la app en uso y reportó 6 problemas.
Se investigaron y se arreglaron juntos, en un solo lote:

- [x] **"No muestra el 100% de mis chats, solo una parte"**: `TdChatsRepository`
      llamaba a `GetChats` una sola vez con un límite fijo, pero TDLib solo
      devuelve ahí los chats que ya tiene cargados en memoria. Ahora, antes de
      pedirlos, se llama a `LoadChats` en bucle hasta que TDLib avisa (con un
      error, su forma normal de decir "ya no hay más") que no quedan chats
      nuevos por cargar, y solo entonces se llama a `GetChats` con un límite
      alto (2000).
- [x] **"No especifica si es chat, grupo o canal"**: cada chat ahora guarda su
      [`TipoChat`] (PRIVADO/GRUPO/CANAL), calculado a partir del tipo real que
      da TDLib (`ChatTypeSupergroup.isChannel`, `ChatTypeBasicGroup`, o
      privado en cualquier otro caso), y se muestra como etiqueta junto al
      nombre en las dos pantallas de selección de chats.
- [x] **"No busca contenido en los grupos" (0 resultados)**: el motor de
      escaneo tragaba en silencio cualquier error de TDLib al leer el
      historial de un chat, así que un chat que fallara simplemente
      "no encontraba nada" sin decir por qué. Ahora esos errores se capturan
      y se muestran en un panel de aviso ("No se pudieron leer N chat(s)")
      tanto en la pantalla de progreso como en los resultados — la próxima
      vez que se repita el escaneo de un grupo con 0 resultados, este panel
      dirá si es un error real de TDLib o si de verdad no había enlaces
      (comportamiento idéntico al script de Python, que tampoco encuentra
      nada si no hay URLs de Xtream en los mensajes).
- [x] **"No hay un botón de ajustes para buscar actualizaciones"**: nueva
      pantalla de **Ajustes**, con la versión instalada y un botón
      "Buscar actualizaciones" que llama a `UpdateViewModel` a mano, sin
      esperar a que la comprobación automática del arranque encuentre algo.
- [x] **"No hay pestañas" / "muy pobre y sin opciones"**: la pantalla
      principal ahora tiene barra de navegación inferior con 3 pestañas
      (**Buscar**, **Resultados**, **Ajustes**). El `ScanFlowViewModel` y el
      `UpdateViewModel` viven por encima del selector de pestañas, así que
      cambiar de pestaña no reinicia el escaneo en curso ni la comprobación
      de actualización.
- [x] Campo de búsqueda por nombre en las dos pantallas de selección de
      chats (`ChatsScreen`, `SeleccionarChatScreen`), para no tener que
      desplazarse por toda la lista a mano — útil ahora que se cargan todos
      los chats, no solo los primeros 100.

## Etapa 8: por qué el escaneo daba 0 activas o marcaba todo como inactivo

Dos videos del usuario mostraron, en chats distintos, dos síntomas que
parecían no tener relación ("0 activas de 0" con decenas de enlaces
encontrados, y "todas inactivas" en otro chat) pero compartían la misma
raíz: el motor de validación nunca se probó contra mensajes reales de
canales de IPTV, que vienen con basura pegada al enlace y casi siempre
sirven el panel por `http://` plano.

- [x] **`extraerCredencialesXtream` reventaba con enlaces reales**: usaba
      `io.ktor.http.Url`, que valida la URL contra la gramática RFC 3986 y
      lanza una excepción ante cualquier cosa fuera de lo esperado. Los
      canales de IPTV suelen pegar el enlace directo a emojis/decoración
      sin espacio de por medio (`...type=m3u_plus🔥🔥`), así que la regex
      de extracción los capturaba como parte del enlace y `Url(...)`
      fallaba con cada uno — la causa real de un escaneo con decenas de
      enlaces "validados" que terminaba en "0 activas de 0". Reemplazado
      por un parseo manual (separar por `://`, `/`, `?`, `&`, `=`), igual
      de permisivo que el `urlsplit`/`parse_qs` que ya usaba el script de
      Python, más un recorte de la basura pegada al final de cada enlace
      capturado.
- [x] **Android bloqueaba todo el tráfico sin cifrar**: desde Android 9
      (API 28), el sistema rechaza por defecto cualquier petición
      `http://` (sin TLS) si la app no lo permite explícitamente. Los
      paneles Xtream casi siempre se sirven así, nunca por `https://`, así
      que *toda* verificación de lista fallaba antes de salir del
      teléfono y volvía como "Inactiva" sin importar si el panel sí
      funcionaba. Arreglado con `android:usesCleartextTraffic="true"` en
      `AndroidManifest.xml`.
- [x] **`XtreamClient` pegaba usuario/contraseña crudos en el string de la
      URL**: una contraseña generada por el proveedor con `&`, `+` o `#`
      rompía la query igual que el problema anterior (`&` abre un
      parámetro nuevo, `#` corta todo lo que sigue). Reemplazado por
      `parameter(...)` de Ktor, que codifica (%XX) el valor
      automáticamente antes de mandarlo, igual que un navegador.

## Etapa 9: chats con "Temas" (forum topics) daban 0 de 0 siempre

Después de las correcciones de la Etapa 8, un chat específico seguía
dando "0 activas de 0" al instante y sin error, incluso confirmando que
sus enlaces venían pegados como texto plano (no en archivos adjuntos) y
con el formato exacto que la regex de extracción ya sabía reconocer
(`http://host:80/get.php?username=...&password=...&type=m3u_plus`). La
diferencia con los demás chats que sí funcionaban: este tenía **"Temas"
activado** (la función de Telegram que divide un supergrupo en pestañas
separadas, como "General", etc.).

- [x] **`GetChatHistory` no basta para un chat con Temas**: cuando un
      supergrupo tiene Temas activado, sus mensajes no se leen de forma
      confiable con el historial "normal" del chat — cada tema se
      comporta casi como un sub-chat aparte. Sin un manejo especial, el
      escaneo solo veía (en el mejor de los casos) el tema "General", y
      nunca los enlaces publicados en el resto de temas/secciones — la
      causa real de que este chat en particular diera "0 de 0" mientras
      otros chats normales ya encontraban enlaces sin problema.
      Arreglado detectando primero si el chat es un supergrupo-foro
      (`GetSupergroup(...).isForum`) y, si lo es, pidiendo la lista
      completa de temas (`GetForumTopics`, paginada) y leyendo el
      historial de cada uno por separado (`GetForumTopicHistory`, con el
      mismo reintento ante páginas vacías que ya se usaba para
      `GetChatHistory`) en vez de una sola llamada al historial general.

## Etapa 10: pantalla de Resultados a juego con el diseño + enviar a Telegram

El diseño que se propuso para Resultados (chips de Activas/Validadas/LATAM,
filtros Todas/Por vencer/LATAM, insignia de días para vencer, botones
Guardar .txt / Enviar a Telegram) ahora refleja los mismos datos que ya
calculaba `validate_m3u_url()` en el script de Python — no datos inventados:

- [x] **Días para vencer, conexiones, zona horaria**: `XtreamClient` ahora
      trae en una sola llamada a `player_api.php` lo mismo que Python
      (`exp_date` → días restantes, `active_cons`/`max_connections`,
      `is_trial`, `server_info.timezone`). El diseño original mostraba una
      "ubicación" (ciudad/país) que no existe en los datos reales — no hay
      geolocalización en el script de Python, así que en vez de inventarla
      se muestra la zona horaria real que sí da el panel.
- [x] **Cantidad de canales**: se reutiliza la misma llamada a
      `get_live_streams` que ya se hacía para la detección LATAM (antes
      recortada a 300 para esa muestra); ahora se pide completa y se usa
      tanto para la muestra como para el conteo, sin pedirla dos veces.
- [x] **Filtros Todas / Por vencer / LATAM**: "Por vencer" son las listas
      activas con menos de 15 días restantes — mismo umbral de color
      (amarillo) que usaba el script de Python en su tabla.
- [x] **"Detener y ver resultados parciales"**: cancela el escaneo donde
      vaya y pasa a Resultados con lo ya encontrado, en vez de esperar a
      que termine de validar todos los enlaces candidatos.
- [x] **Guardar .txt / Enviar a Telegram**: "Guardar" arma un archivo con
      el mismo contenido que escribía Python y abre el selector de apps
      del sistema para guardarlo o compartirlo. "Enviar a Telegram" lo
      manda a un bot — equivalente a `send_file_to_telegram()`, pero el
      `BOT_TOKEN`/`CHAT_ID` se configuran en Ajustes y se guardan cifrados
      en el dispositivo (ver `CredencialesBotTelegram`), nunca en el `.env`
      ni en el repositorio.

## Etapa 9 bis: por qué el escaneo se quedaba pegado para siempre validando

Un bug distinto al de la Etapa 9: ya con los enlaces bien extraídos, el
escaneo podía quedarse congelado en "0 de N enlaces validados" sin avanzar
nunca y sin ningún error visible.

- [x] **Sin timeout + validación uno por uno**: `NetworkModule` no tenía
      ningún timeout configurado — una conexión a un panel vencido que
      nunca responde (ni rechaza la conexión, simplemente no contesta) se
      queda esperando indefinidamente. Como además los enlaces se
      validaban de a uno (secuencial), bastaba UN solo panel así entre
      decenas/cientos de enlaces reales para trabar el escaneo completo.
      El script de Python ya evitaba esto con `REQUEST_TIMEOUT` (10s) y
      `MAX_CONCURRENT_VALIDATIONS` (50 en paralelo); acá es el mismo
      arreglo: `HttpTimeout` de 10s en Ktor y validación en paralelo con
      un `Semaphore(50)`.

## Etapa 10 bis: el escaneo se quedaba pegado en "Buscando enlaces en los mensajes..." para siempre

Un tercer bug de "se queda pegado", distinto a los dos anteriores (que ya
estaban en la fase de VALIDAR enlaces): este pasaba ANTES, mientras
todavía se estaban juntando los mensajes de los chats elegidos — la app
se quedaba en "Encontradas (0)" sin avanzar ni dar ningún error, durante
más de 5 minutos.

- [x] **`enviarYEsperar` (TDLib) sin timeout**: igual que `NetworkModule`
      no tenía timeout para las conexiones HTTP a los paneles Xtream
      (Etapa 9 bis), la función que espera la respuesta de TDLib a
      cualquier petición tampoco tenía ninguno. Si TDLib nunca llama al
      callback para una petición puntual — visto en la práctica con
      `GetForumTopics`/`GetForumTopicHistory` en algunos chats — la
      corrutina se queda esperando para siempre y el escaneo entero no
      avanza, sin ningún mensaje de error visible. Arreglado con
      `withTimeout(15s)` alrededor de la espera; si se cumple el plazo,
      ese chat puntual se reporta como error (ver "Chats con error" en
      la pantalla de escaneo) y el escaneo sigue con el resto de chats en
      vez de quedarse trabado para siempre.

## Etapa 10 ter: chats con "Temas" seguían fallando del todo por un tema puntual + la app nunca detectaba actualizaciones

Dos bugs más encontrados al probar el arreglo anterior en el teléfono real:

- [x] **Un tema de un foro que tarda más de lo normal tiraba a la basura
      TODO el chat**: con el timeout de TDLib ya puesto (Etapa 10 bis), un
      chat con Temas donde un tema puntual tardaba más de la cuenta en
      responder a `GetForumTopicHistory` (visto en la práctica: Telegram
      puede aplicar un `FLOOD_WAIT` a ese método si se piden muchos temas
      seguidos, y TDLib lo reintenta por dentro sin avisar — desde acá se
      ve igual que si nunca fuera a responder) hacía que se perdieran
      TAMBIÉN los mensajes de los demás temas de ese mismo chat que sí
      habían respondido bien, y el chat completo quedaba marcado como "no
      se pudo leer". Ahora cada tema se intenta por separado: uno que
      falle ya no tira el resto a la basura, y el timeout por petición
      sube de 15s a 25s para dar más margen a un posible `FLOOD_WAIT`.
- [x] **La app nunca detectaba actualizaciones, aunque hubiera una nueva**:
      el workflow de CI publica cada release con `--prerelease` (es un
      .apk de debug, no una versión "estable"), y el endpoint de GitHub
      que usaba la app (`/releases/latest`) EXCLUYE por diseño cualquier
      release marcado como prerelease o draft — con todos los releases de
      este repo marcados así, ese endpoint siempre respondía 404 y la app
      nunca encontraba nada que ofrecer. Se cambió a la lista de releases
      (`/releases?per_page=1`, que sí incluye prereleases y ya viene
      ordenada del más reciente al más viejo) para decidir si hay una
      versión más nueva.

## Etapa 10 quater: diagnóstico por chat (mensajes leídos / enlaces candidatos)

Con "0 resultados" y ningún chat marcado como error, no había forma de ver
desde la app si el chat realmente no tenía mensajes, si tenía mensajes pero
ninguno con un enlace de lista, o si el enlace sí se encontró pero no quedó
activo al validarlo. Ahora, tanto en la pantalla de escaneo como en
Resultados, aparece un panel neutro (no es un error) con una línea por cada
chat leído con éxito: `<chat>: N mensajes leídos, M enlace(s) candidato(s)`.

- `N = 0` → el chat no tenía mensajes en el rango leído, o no se pudo leer
  realmente nada (revisar también si aparece en "Chats con error").
- `N > 0` pero `M = 0` → se leyeron mensajes, pero ninguno tenía un enlace
  con la forma `.../get.php?...` — puede ser que ese chat no comparta
  listas en ese formato, o que el enlace esté roto/pegado de forma rara.
- `M > 0` pero igual sale "0 activas" → el/los enlaces se encontraron bien,
  el problema está en la validación contra el panel (caído, credenciales
  vencidas, etc.), no en la lectura de Telegram.

## Etapa 10 quinquies: respaldo al historial normal si la lectura por Temas no trae nada

Con el diagnóstico de la Etapa 10 quater se confirmó que, para un chat
puntual, ni siquiera `GetForumTopics` (la llamada más liviana de la lectura
por Temas, solo para listar los temas) llegaba a responder — no era ya un
caso de `GetForumTopicHistory` tardándose por un `FLOOD_WAIT` puntual, sino
algo más estructural con ese chat específico.

- [x] En vez de seguir ajustando timeouts a ciegas, `obtenerMensajesRecientes`
      ahora tiene un último recurso: si la lectura por Temas no trae NINGÚN
      mensaje (falle en `GetForumTopics`, en algún `GetForumTopicHistory`, o
      de verdad no haya nada en ningún tema), se cae al historial normal
      (`GetChatHistory`) antes de rendirse. En un foro de verdad eso trae
      al menos los mensajes del tema "General"; y si la detección de
      "¿tiene Temas?" se equivocó con ese chat puntual (no era realmente un
      foro), esto es lo que debía leerse desde un principio.

## Etapa 11: registro paso a paso del escaneo (temporal, para diagnóstico)

Pedido explícito del usuario: poder ver, de principio a fin, exactamente
qué hizo la app durante un escaneo — no solo los contadores finales — sin
tener que adivinar dónde se perdió algo.

- [x] Nuevo panel colapsable **"Ver registro detallado"** (arranca
      cerrado, toca el encabezado para abrirlo), disponible tanto en la
      pantalla de escaneo como en Resultados. Muestra una línea por cada
      paso real, en orden: `Escaneo iniciado: N chat(s)...` → por cada
      chat, si tiene Temas o no, cuántos temas/mensajes trajo cada uno (o
      si falló y cayó al historial normal), cuántos enlaces candidatos
      salió de él → `Validando N enlaces...` → una línea por cada enlace
      (✅ activa / ❌ inactiva / ⚠️ sin usuario-contraseña reconocible) →
      `Escaneo terminado: X activas de N validadas.`
- [x] Es un registro explícitamente temporal, pensado para diagnosticar
      estos últimos problemas de lectura por Temas — no un reemplazo de
      los paneles de errores/diagnóstico por chat, que siguen igual.

## Etapa 11 bis: botón para descargar/compartir el registro completo

El panel "Ver registro detallado" (Etapa 11) podía tener muchas líneas, más
de las que caben en una sola captura de pantalla — hacía falta poder
mandar el texto completo tal cual, sin recortes.

- [x] Nuevo botón **"Descargar/compartir registro (.txt)"**, junto a los
      paneles de errores/diagnóstico/registro (tanto en la pantalla de
      escaneo como en Resultados): arma un .txt con los tres (chats con
      error, diagnóstico por chat, y el registro paso a paso completo) y
      abre el selector de apps del sistema para guardarlo o compartirlo
      — mismo mecanismo que ya usaba "Guardar .txt" de los resultados
      (reutiliza el `FileProvider` existente).

## Etapa 12: el límite de mensajes leídos por chat era 50 veces más chico que en Python

El registro paso a paso (Etapa 11) permitió ver exactamente qué pasaba en
un chat con "cientos de listas activas" que solo arrojaba 2 enlaces
candidatos: 3 de sus 4 temas se cortaron justo en 200 mensajes leídos —
el tope que tenía la app —, dejando fuera prácticamente todo el historial
real del chat.

- [x] El script de Python lee hasta `MESSAGE_SCAN_LIMIT = 10000` mensajes
      por chat (`scan_messages()`); la app tenía ese tope en apenas 200.
      Se sube a 10000 para igualar al script — un chat con mucho
      historial (o con varios temas, cada uno con su propio tope) ya no
      se corta cerca del principio.
- [x] De paso, el tamaño de página por petición a TDLib sube de 50 a 100
      (el máximo real que permite TDLib), para que llegar al nuevo tope
      de 10000 necesite la mitad de ida-y-vueltas.
- Como contraparte: un chat con mucho historial real y varios temas
  puede tardar bastante más en leerse por completo (cientos de páginas
  en vez de unas pocas) — es el mismo trade-off que ya asume el script
  de Python con su propio límite de 10000.

## Etapa 13: resultados con el mismo formato decorado de Python + IP alternativa del servidor

Dos pedidos explícitos del usuario tras ver que el escaneo ya encuentra
mucho más contenido (Etapa 12): el texto que exporta/manda la app
("Guardar .txt" / "Enviar a Telegram") se veía plano, muy distinto del
formato con emojis que el script de Python escribe en `OUTPUT_FILE`
(`print_scan_summary()`), que es el que el usuario prefiere; y quiso
saber si, para una lista encontrada por nombre de dominio, también se
puede mostrar la IP real del servidor como alternativa.

- [x] `ResultadosExportador.generarContenido()` ahora arma, por cada
      lista activa, el mismo bloque decorado que Python — mismo orden y
      mismos emojis (🎉 host, usuario, contraseña, estado, conexiones,
      canales, series, películas, fecha de creación, vencimiento, días
      restantes, timezone, si es prueba, formatos permitidos, enlace
      M3U, enlace EPG, fecha de escaneo) — en vez del formato plano que
      tenía antes. Afecta a la vez "Guardar .txt" y "Enviar a Telegram"
      porque los dos ya pasaban por esta misma función.
- [x] Para traer series/películas/fecha de creación/formatos permitidos,
      que la app todavía no pedía, se agregaron al panel Xtream
      (`XtreamClient`): `created_at`/`allowed_output_formats` en
      `player_api.php`, y conteo de `get_vod_streams`/`get_series` —
      igual que hace Python en `validate_m3u_url()`/`fetch_stream_count()`.
      Las cinco llamadas nuevas de red por enlace (categorías, canales,
      películas, series, IP) se piden en paralelo, no una por una, para
      no sumarle tiempo extra al escaneo con muchos enlaces a la vez.
- [x] IP alternativa del servidor (pedido nuevo, sin equivalente en el
      script de Python): cuando el host de la lista es un dominio (no ya
      una IP), `XtreamClient.resolverIp()` hace una resolución DNS
      (`InetAddress.getByName`, con tope de 4s para no trabar el
      escaneo si el DNS está lento/caído) y, si la consigue, se agrega
      como línea "IP del servidor (alternativa)" en el archivo
      exportado y como dato extra en la fila de cada lista en
      Resultados. Si el host YA es una IP, no se muestra nada (no hay
      nada que "alternar").

## Nota sobre el CI

El workflow de GitHub Actions (`.github/workflows/build.yml`) compila el
proyecto en cada push/PR. El wrapper de Gradle (`gradlew`) se generó sin
conexión a internet en el entorno donde se creó este commit, así que el
primer build real (con acceso a Google Maven / Maven Central) ocurre en
GitHub Actions, no fue verificado localmente antes de este commit.
