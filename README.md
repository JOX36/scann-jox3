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
      1. **Cualquier lista** *(eliminada en la Etapa 22; ahora es "Contenido
         LATINO en varios chats")*: elegir uno o varios chats y escanear.
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

## Etapa 14: botones atrás, filtro por tipo de chat y detalle por lista con información a demanda

Tres pedidos explícitos del usuario, además de una corrección de un bug
real reportado con captura de pantalla:

- [x] **Bug: el enlace por IP no se generaba** para una lista con host de
      dominio (confirmado con el usuario que el host era un dominio, no
      una IP). La causa no era un fallo real de DNS sino el tiempo de
      espera (`XtreamClient.TIMEOUT_DNS_MS`, antes 4s) agotándose bajo la
      carga de un escaneo con muchos enlaces en paralelo. Se subió a 12s.
- [x] **Botones "atrás"** en todo el flujo de escaneo (elegir host,
      elegir chat único, elegir varios chats): antes, si el usuario se
      equivocaba de pantalla no había forma de volver sin cerrar la app.
      Nuevo `ScanFlowViewModel.volver()` + composable reusable
      `ui/common/BarraVolver.kt`.
- [x] **Filtro por tipo de chat** (Todos / Grupos / Canales / Chats) en
      las pantallas de selección de chats, para no tener que revisar
      chats de a uno cuando hay muchos — `ui/common/FiltroTipoChatChips.kt`.
- [x] **Detalle por lista con información a demanda, sin ir a Telegram**:
      al tocar un resultado se abre una pantalla de detalle
      (`DetalleListaScreen`) con los datos que ya se tenían del escaneo
      (usuario, contraseña, vencimiento, timezone, etc.) y un botón
      "Consultar información extra" que, solo si el usuario lo toca,
      pide aparte (`consultarDetalleLista`, en `DetalleLista.kt`):
      listado completo de canales/series/películas por nombre, y
      geolocalización de la IP real del servidor (ciudad, país con
      bandera, ISP) vía `ipapi.co` (se eligió por soportar HTTPS en el
      plan gratuito — `ip-api.com` solo da HTTP gratis, y Android
      bloquea tráfico sin cifrar por defecto). Deliberadamente esto NO
      se agregó al escaneo masivo: pedir nombre completo de canales o
      geolocalizar la IP de cientos de listas a la vez sería mucho más
      lento y más carga de red de la necesaria para listas que el
      usuario ni va a mirar en detalle.
- [x] **Guardar/enviar esa lista sola, independiente del resto**: la
      pantalla de detalle tiene sus propios botones "Guardar .txt" /
      "Enviar a Telegram" (`ResultadosExportador.generarContenidoDetalle`
      / `guardarArchivoDetalle`), separados de los botones de Resultados
      que guardan/mandan todas las listas juntas. Y solo aparecen
      después de que el usuario pidió la información extra — así, si
      solo está mirando (consultando) no se envía ni guarda nada; eso
      pasa únicamente si el usuario toca el botón.
- [x] El formato del `.txt` masivo (Resultados → Guardar/Enviar) también
      se enriqueció con tres datos que no cuestan una llamada de red
      nueva porque ya estaban disponibles o son pura construcción de
      texto: emoji de estado/días por vencer (🟢/🟡/🔴 según
      `diasRestantes`), bandera de país aproximada a partir del
      timezone (`XtreamClient.PAIS_POR_TIMEZONE` — mapa a propósito
      incompleto: antes mostrar nada que arriesgar una bandera
      equivocada) y el enlace M3U armado con la IP alternativa en vez
      del dominio, cuando esa IP ya se había resuelto.

## Etapa 15: diseño visual "Glass HUD" aplicado a las pantallas reales

El usuario eligió la opción "Glass HUD" entre los 3 previews de diseño
futurista (Neon Grid / Glass HUD / Aurora Terminal) armados en la Etapa
14; esta etapa la aplica a las pantallas reales de la app, no solo a la
maqueta de muestra.

- [x] Paleta nueva en `ui/theme/Theme.kt`: fondo casi negro con tinte
      verdoso (`#07100F`), acento esmeralda (`#2EE6A8`) + turquesa
      (`#22B8CF`) para los degradados, tarjetas "de vidrio" (fondo
      blanco al ~8%, `SuperficieApp`) con borde sutil (`BordeApp`,
      blanco al ~9%) en vez del gris plano de antes.
- [x] Fondo de toda la app (`ui/common/FondoConGlow.kt`, nuevo): dos
      manchas de luz difuminadas detrás de las pantallas, igual que en
      el preview — el difuminado real solo se nota en Android 12+, en
      versiones anteriores el degradado se ve sin difuminar, sin que
      eso rompa nada (es el comportamiento documentado de
      `Modifier.blur` en versiones viejas).
- [x] Pantalla "¿Cómo quieres buscar?" con chips de ícono: la primera
      opción ("Cualquier lista") con el degradado esmeralda→turquesa
      destacado, igual que el preview elegido; el resto con un chip
      más neutro.
- [x] Tarjetas de chats, resultados, paneles de diagnóstico/registro y
      el detalle por lista (de la Etapa 14) con el mismo tratamiento
      "de vidrio": borde sutil + esquinas más redondeadas (16-20dp en
      vez de 12dp).
- [x] Barra inferior de navegación y botón "Volver" con el acento
      esmeralda en vez del turquesa plano de antes.
- [ ] Pantallas de login y de credenciales de la API de Telegram (poco
      vistas, solo al configurar la app por primera vez) quedan con los
      componentes estándar de Material3 por ahora — ya se ven con la
      paleta oscura nueva del tema, pero no tienen el tratamiento "de
      vidrio" con borde.

## Etapa 16: elegir temas de un grupo, texto legible y detalle completo

- [x] **Elegir qué temas escanear**: antes de escanear, si algún chat
      elegido es un grupo con "Temas" (y tiene más de uno), aparece una
      pantalla (`ElegirTemasScreen`) con los temas de cada grupo
      marcados por defecto; se desmarcan los que no se quieran leer.
      Todos marcados = lectura normal de siempre; algunos = solo esos
      (`ChatTelegram.temaIds`); ninguno = ese grupo se omite. Modo
      "host en todos los chats" no pregunta (son demasiados chats). Si
      la consulta de temas falla, escanea directo como antes.
- [x] **Texto negro ilegible**: al reemplazar el `Surface` raíz por
      `FondoConGlow` en la Etapa 15, todo `Text` sin color explícito
      quedó en negro sobre el fondo oscuro (títulos de chats, etc.).
      `FondoConGlow` ahora provee `LocalContentColor` claro.
- [x] **Detalle individual = el ejemplo del usuario**: la pantalla de
      detalle muestra el mismo bloque completo de su `.txt` de ejemplo
      (estado, conexiones, fechas, timezone con bandera, servidor real
      con ISP, M3U/EPG/M3U por IP y listas de TV en vivo/series/
      películas), seleccionable/copiable. La consulta extra arranca sola
      al abrir (con "Reintentar" si falla). Guardar/Enviar siguen siendo
      botones propios de esa lista: mirar no envía nada.

## Etapa 17: actualización con instalador automático y progreso visible

Visto en una grabación del usuario: al tocar "Actualizar ahora" la app
no mostraba el avance (solo un spinner casi invisible), había que bajar
las notificaciones para ver el %, y al terminar el instalador no se abría
solo: había que ir a Descargas y tocar el archivo.

- [x] Barra de progreso con porcentaje en Ajustes (y en el banner),
      consultando el `DownloadManager` desde el `UpdateViewModel`.
- [x] Al terminar la descarga, la app abre sola el instalador del
      sistema (desde `MainActivity`, en primer plano; si estaba en
      segundo plano, apenas el usuario vuelve). Antes lo intentaba un
      `BroadcastReceiver`, que Android 10+ no deja abrir pantallas desde
      el fondo — se eliminó (`UpdateDownloadReceiver`).
- [x] Botón "Instalar ahora" de respaldo y "Reintentar descarga" si falla.
- Siguen siendo del sistema (la app no puede saltarlos): confirmar
  "¿Deseas actualizar esta app?" y el aviso de Play Protect.

## Etapa 18: lectura de mensajes más resistente a los "TDLib no respondió"

Reportado con capturas: chats (incluso un solo tema) fallaban seguido con
"TDLib no respondió en 25s a GetForumTopicHistory / GetChatHistory".

- [x] **Ya no se pierde lo leído**: antes, si UNA página del historial
      (de 100 mensajes) no respondía a tiempo, se descartaban también
      todos los mensajes ya leídos de ese chat/tema (hasta 10.000) y el
      chat quedaba en 0. Ahora se conserva lo ya leído (lo más reciente)
      y solo se registra el corte.
- [x] **Reintentos**: cada petición a TDLib que no responde se reintenta
      hasta 3 veces con pausa creciente (los errores reales de Telegram
      no se reintentan).
- [x] **Ritmo**: pausa de 250 ms entre páginas, para no disparar el
      límite de peticiones (FLOOD_WAIT) de Telegram.
- [x] **Conexión visible**: el registro ahora indica el estado de la
      conexión de TDLib (conectado / conectando / esperando red) al
      empezar y en cada reintento; si no estaba conectado, espera hasta
      30 s a que conecte antes de leer.
- [x] Se "abre" el chat (`OpenChat`) antes de leerlo y se cierra al
      terminar, como recomienda TDLib.

## Etapa 19

- **Detalle resumido**: al consultar una lista ya no se descargan miles de canales/series/películas; solo se piden las **categorías** (`get_live_categories`, `get_vod_categories`, `get_series_categories`) y se muestran como en el ejemplo (`📡 TV en vivo (N): categorías…`).
- **Copiar enlaces**: botones M3U, EPG, Por IP y "Copiar todo el texto" en la pantalla de detalle.
- **Botones inferiores**: "Buscar" y "Resultados" cierran el detalle y llevan de verdad a su pantalla (Buscar → menú de modos; Resultados → último resultado, que ahora se conserva aparte). El botón atrás del sistema cierra el detalle / retrocede un paso.
- **Registro en vivo**: la pantalla de escaneo muestra "📨 N mensajes escaneados" y el chat que se está leyendo.

## Etapa 20

- **Pestaña "Verificar"**: pega una lista en cualquier formato conocido (enlace `get.php`, `player_api.php` o `xmltv.php`; bloque con Host/Usuario/Contraseña como el que genera la app; o `host|usuario|clave`) y muestra el mismo resultado detallado que el detalle de una lista, con copiar enlaces, guardar .txt y enviar a Telegram. Por ahora solo ese resultado (sin extras).
- **Semáforo de días restantes** (revisado contra `validate_m3u_url` del script de Python; el cálculo de días coincide): una cuenta con `exp_date` 0 (sin vencimiento) ya no sale como "-20000 días 🔴" y las listas sin fecha ya no se pintan en verde (⚪); el estado "Active" sigue en 🟢.
- La hora de "Escaneado" ahora es la local del teléfono (antes salía en UTC).
- Etapa 20b: "Creado" y "Vencimiento" también se muestran en la hora local del teléfono (antes UTC). Los días restantes no cambian.

## Etapa 21

- **Actualización desde la app: "el paquete no es válido"**. Causa probable: el DownloadManager no sobreescribe un archivo existente (guarda `...-1.apk`), así que la app abría el `.apk` viejo de una descarga anterior. Ahora se borran las descargas previas antes de bajar y, al terminar, se comprueba que el `.apk` sea legible (`getPackageArchiveInfo`); si no, sale "Reintentar" en vez de abrir un instalador que falla.

## Etapa 22

- **Detector LATAM corregido**: antes palabras genéricas en español (DEPORTES, NOTICIAS, CINE…) bastaban para pasar el umbral, así que un panel de España (Movistar, LaLiga…) salía como "LATAM 23 %" y casi todo daba LATAM. Ahora solo cuentan señales latinas reales (países, LATINO/LATAM, novelas, cadenas como Caracol/RCN/Televisa…), se compara por palabra completa y sin tildes (INTERNACIONAL ya no cuenta como NACIONAL), y los paneles de España se marcan aparte (🇪🇸).
- **Modos**: se elimina "Cualquier lista"; nuevo modo **"Contenido LATINO en varios chats"** (elegir varios grupos, canales o chats; solo se quedan las listas activas LATAM).
- **IP alternativa**: solo IPv4 y se omite si es de Cloudflare (antes salía una IPv6 sin corchetes, enlace inválido, y la IP de un proxy).
- Pendiente de decidir: opción "cualquier español" (incluir también España) como interruptor.

## Etapa 23

- **"N/A" en Canales/Series/Películas**: los listados completos (`get_live_streams`, `get_vod_streams`, `get_series`) pesan varios MB en paneles grandes y se cortaban con el timeout general de 10 s. Ahora esas tres peticiones tienen 60 s. El detalle también muestra los totales: `📡 TV en vivo (118 categorías · 5230 canales)`.
- **Nombre del archivo como en el script de Python**: `Resultados_Latinos_<chat>.txt` (p. ej. `Resultados_Latinos_G-PANDORA`), `Resultados_<host>.txt` en búsquedas por host y `Resultados_Latinos_<archivo>.txt` para un .txt local; al enviarlo a Telegram lleva ese nombre como texto de pie ("Resultados Latinos G-PANDORA").
- **Una sola lista (Verificar / detalle)**: "Enviar a Telegram" manda el resultado **suelto como mensaje(s) de texto**, no como .txt. Telegram admite 4096 caracteres por mensaje; el informe llega en mensajes por bloques, sin numerar: datos de la cuenta, 📡 TV en vivo, 🎬 Series y 🎥 Películas (con el pie junto al último). Si un bloque solo supera los 4096 caracteres de Telegram, se parte en la última coma (4000 c/u). 0,4 s entre envíos.

## Etapa 24

- **Escaneo en segundo plano**: al salir de la app, Android bajaba su prioridad (congela la red/corrutinas o mata el proceso) y la lectura de mensajes o la validación de listas se cortaba. Ahora, mientras hay un escaneo (o una verificación) en curso, un **servicio en primer plano** (`ServicioEscaneo`, tipo `dataSync`) mantiene viva la app, con una notificación que muestra el avance (`listas · mensajes · chat que se lee`) y un wake lock parcial para que la CPU no se duerma con la pantalla apagada. Se detiene solo al terminar. En Android 13+ pide el permiso de notificaciones (el servicio funciona aunque se rechace).
- **Aviso al terminar** (Etapa 24b): si saliste de la app, al acabar el escaneo o la verificación llega una notificación ("Escaneo terminado: N lista(s) activa(s)…") que abre la app al tocarla; si la app está a la vista no se muestra.
- Etapa 24c: endurecido el servicio — el texto de la notificación se actualiza con `notify` (sin relanzar `startForegroundService` desde segundo plano, que en Android 12+ puede lanzar `ForegroundServiceStartNotAllowedException`) y todo va dentro de `runCatching`: un fallo del aviso no puede tumbar el escaneo.
- Limitación: si el sistema o el usuario cierra la app a la fuerza, el escaneo se pierde; algunos fabricantes (Xiaomi, Oppo, Huawei…) además requieren quitar la optimización de batería a la app (Ajustes → Batería).

## Etapa 25

- **Recuperación tras un cierre inesperado**: el escaneo se guarda en disco (`respaldo_escaneo.json`) cada vez que se valida una lista. Si la app se cierra y se reinicia, las listas ya validadas reaparecen en la pestaña **Resultados** con un aviso ("El escaneo anterior se interrumpió. Se recuperaron N lista(s)…"). No se guarda el avance de lectura de mensajes: hay que volver a escanear lo que faltaba.
- **Diagnóstico del cierre**: se guarda la traza de cualquier error no controlado (`ultimo_error.txt`) y se consulta el motivo que registró Android (`ApplicationExitInfo`: falta de memoria, error nativo, ahorro de batería, etc.). El motivo sale en ese mismo aviso, para saber por qué se cerró.

## Etapa 25b

- El aviso de recuperación ahora sale en "Diagnóstico por chat" (antes aparecía bajo "No se pudieron leer 1 chat(s)").
- Primer caso real: el motivo fue `código 16 … installPackageLI` = **se instaló una versión nueva de la app encima** (Android cierra el proceso al actualizar). No era un fallo del escaneo; ahora ese código se muestra en español.

## Etapa 26 — El escaneo ya no se corta al cerrar la pantalla

- **Causa real del "se interrumpió"**: el escaneo vivía en un ViewModel atado a la pantalla (Activity). Al cerrar la app desde "recientes" o con atrás, la pantalla se destruía, el ViewModel se limpiaba y el escaneo se cancelaba aunque el servicio en primer plano mantuviera el proceso vivo. No era una actualización ni el sistema matando la app.
- Los ViewModels del escaneo y del verificador ahora viven a nivel de proceso (`AlmacenViewModelsProceso`): el escaneo sigue con o sin pantalla y al volver a abrir se retoma el progreso real.
- El control del servicio en primer plano y el aviso "Escaneo terminado" pasaron de la pantalla al ViewModel (`EstadoApp.visible` indica si la app está a la vista), así funcionan aunque la pantalla no exista.
- El motivo del cierre solo cita el registro de Android si es posterior al último guardado del escaneo; si no, indica que se cortó al cerrarse la pantalla (se acabó culpar a una instalación antigua).

## Etapa 27 — Se acabó el cierre por falta de memoria

- **Causa real del cierre** (la mostró el propio diagnóstico): `OutOfMemoryError`. Cada lista validada descargaba y cargaba entero el listado de canales, películas y series (decenas de miles de objetos) y con hasta 50 validaciones a la vez se agotaba el heap de 256 MB.
- Ahora esos listados se leen en streaming (`resumirArrayJson`): solo se cuenta cuántos elementos hay y se guarda una muestra de 300 nombres para la detección LATAM. El uso de memoria ya no depende del tamaño del panel.
- `largeHeap` activado como margen extra.

## Etapa 28 — Escaneo que se quedaba "congelado"

- El motor de red usaba solo 4 hilos bloqueantes; con 50 validaciones a la vez las peticiones a paneles caídos/lentos se encolaban y el progreso parecía detenido (p. ej. "70 de 2478"). Ahora 64 hilos.
- La lectura de canales/películas/series es suspendible (no bloquea hilos y respeta los timeouts).
- Tope de 150 s por lista: una que se cuelgue ya no retiene su turno para siempre.

## Etapa 29 — Nombre "JOX3 Radar", ícono y look HUD

- Nombre de la app: **JOX3 Radar** (launcher, login, notificaciones, descargas).
- Ícono adaptable propio (radar esmeralda sobre fondo oscuro, con versión monocromática para Android 13+).
- Pantalla de bienvenida animada (~1,7 s) con el radar y el nombre.
- Escaneo: radar giratorio, porcentaje grande en fuente monoespaciada y barra de progreso animada.
- Cifras en fuente monoespaciada, bordes con brillo esmeralda y tarjetas de resultado con borde por estado (verde / amarillo por vencer / rojo expirada) y barra de % LATAM.
- Limpieza de código muerto (UpdateBanner, PanelColapsable, FakeTelegramAuthClient y funciones sin uso).

## Etapa 30 — Resumen final, barra inferior de cristal y arranque sin repeticiones

- **Resumen del escaneo** al terminar (o detener): escaneadas, ON, OFF, latinas, no latinas, España, por vencer, mensajes leídos y duración, con % de listas activas. Cuenta TODAS las listas validadas (antes las caídas se descartaban en modo LATINO y siempre salían en 0). El contador en vivo del escaneo usa los mismos datos.
- **Colores con significado fijo**: verde = activa, rojo = caída, turquesa = LATAM (antes era amarillo, igual que "por vencer"), violeta = España, amarillo = por vencer.
- **Barra inferior nueva**: cápsula de cristal con borde brillante, iconos vectoriales y píldora con degradado y resplandor en la pestaña activa (adiós a los emojis de colores sobre gris plano).
- **Rotación**: la Activity ya no se recrea al girar la pantalla (`configChanges`), así que no se reinicia nada. La animación de bienvenida solo aparece en el arranque en frío del proceso, no al girar ni al reabrir la app con el proceso vivo.

## Etapa 31 — Cada contador es un botón: elige qué listas ver

- Resultados ahora es una sola lista desplazable: arriba el resumen, **justo debajo la cascada de listas** del contador elegido, y al final los botones y el diagnóstico (antes los paneles de diagnóstico empujaban las listas fuera de la pantalla).
- Tocar **Escaneadas / ON / OFF / Latinas / No latinas / España / Por vencer** muestra esas listas; tocar una lista activa abre su información completa.
- Se conservan todas las listas validadas (también las caídas y las no latinas) solo para esta vista; los archivos .txt y el envío a Telegram siguen usando únicamente lo que pide el modo (p. ej. solo latinas).

## Etapa 32 — "Abrir en JOX3 TV"

- En el detalle de una lista activa hay un botón **Abrir en JOX3 TV**: abre el reproductor con `jox3://add?url=<M3U>`; JOX3 TV pide confirmación, valida la cuenta y la agrega.
- Si JOX3 TV no está instalada (o es anterior al enlace `jox3://add`) se avisa con un mensaje en vez de fallar.

## Etapa 33 — Buscar un host: cuántas listas necesitas

- Al buscar un host (en todos los chats o en uno) ahora se pregunta **cuántas listas de ese host necesitas** (vacío = todas las que aparezcan).
- En cuanto se juntan esas listas activas el escaneo se detiene solo y muestra los resultados (sin seguir leyendo ni validando).
- Si hay menos de las pedidas se muestran las que se encontraron y un aviso amarillo ("Pediste N y solo se encontraron M"); si no hay ninguna, un aviso rojo dice claramente que no se encontró el host.

## Etapa 34 — Pestaña "Bodega"

- Nueva pestaña **Bodega**: cada lista activa que encuentre un escaneo (o que verifiques en "Verificar") se guarda sola, se envíe o no a Telegram. Se guarda en un archivo privado del teléfono (`bodega.json`, ~1 MB por unas miles de listas; la app no se respalda en la nube).
- Sin duplicados: misma lista = mismo servidor + usuario (se actualiza con los datos más recientes).
- Orden: host de A a Z y, dentro de cada host, de más a menos días de vigencia. Los días se calculan HOY desde la fecha de vencimiento, así siempre están al día.
- Buscador (host, usuario o chat) y filtros: Todas, LATAM, Vigentes, Por vencer, Caídas/vencidas.
- Tocar una lista abre su información completa (con "Abrir en JOX3 TV"); **Revalidar todas** vuelve a comprobarlas (10 a la vez) y marca las que cayeron; **Limpiar caídas** y el icono de papelera las quitan.

## Etapa 35 — Compartir un archivo o texto a JOX3 Radar

- **JOX3 Radar** aparece en "Compartir" (desde un chat, el gestor de archivos, etc.) para archivos de texto / .m3u y para texto suelto, y también en "Abrir con". Al elegirlo se abre la app y se escanean sus enlaces como en el modo "archivo local" (resultados con resumen, Bodega y filtros).
- El escaneo de archivo local ahora es **en paralelo** (30 a la vez, tope de 150 s por lista) en vez de uno por uno.
- Si ya hay un escaneo en curso avisa y no lo interrumpe. Límite de lectura: 8 MB.
- Corrección: el archivo compartido se borraba de la cola antes de leerse y Compose cancelaba la lectura (la app se abría y "no hacía nada"); ahora se borra al final.

## Etapa 36

La app ahora entiende texto en formato Markdown (mensajes de chats, archivos `.md` compartidos, texto pegado en el Verificador): `[texto](enlace)` extrae solo el enlace, se quitan `**negritas**`, `__subrayados__` y `` `código` `` pegados, y las barras de escape como `\_` se limpian. Así la contraseña ya no se queda con un `)` o `*` al final.

## Etapa 37

La Bodega tiene un nuevo filtro **Otras** (listas no latinas), junto al de **LATAM**, para separarlas fácilmente.

## Nota sobre el CI

El workflow de GitHub Actions (`.github/workflows/build.yml`) compila el
proyecto en cada push/PR. El wrapper de Gradle (`gradlew`) se generó sin
conexión a internet en el entorno donde se creó este commit, así que el
primer build real (con acceso a Google Maven / Maven Central) ocurre en
GitHub Actions, no fue verificado localmente antes de este commit.
