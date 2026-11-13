package com.jox3.scanner.scan

import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.scan.data.extraerCredencialesXtream
import com.jox3.scanner.scan.data.extraerEnlaces
import com.jox3.scanner.scan.engine.validarYEnriquecer
import com.jox3.scanner.telegram.TdLibAuthClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lee el historial reciente de los chats seleccionados buscando enlaces de
 * listas, usando la misma sesión de TDLib del login — reemplaza a
 * [FakeEscanerRepository]. El motor de validación (extracción de
 * credenciales + [validarYEnriquecer]) es el mismo de la Etapa 3, sin
 * cambios.
 */
class TdEscanerRepository(private val sesion: TdLibAuthClient) : EscanerRepository {

    override fun escanear(
        chats: List<ChatTelegram>,
        filtroHost: String?,
        soloLatino: Boolean,
    ): Flow<EventoEscaneo> = channelFlow {
        // Registro temporal de diagnóstico: una línea por cada paso real
        // que da el escaneo, en orden — pedido explícito del usuario para
        // poder ver "qué hizo la app" de principio a fin sin tener que
        // interpretar solo contadores. Se manda como evento normal
        // ([EventoEscaneo.LogPaso]), nada especial del lado de TDLib.
        val registrar: suspend (String) -> Unit = { mensaje -> send(EventoEscaneo.LogPaso(mensaje)) }

        send(EventoEscaneo.Progreso(0, 0))
        registrar("Escaneo iniciado: ${chats.size} chat(s) seleccionado(s).")

        // Primero se junta el texto de todos los mensajes de los chats
        // elegidos y se extraen los enlaces candidatos — igual que el
        // script de Python lee los mensajes antes de validar cada enlace.
        val enlacesPorOrigen = mutableListOf<Pair<String, String>>()
        for (chat in chats) {
            registrar("📂 Leyendo \"${chat.titulo}\"...")
            val mensajes = try {
                obtenerMensajesRecientes(chat.id, chat.titulo, registrar)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Antes este error se tragaba en silencio y el chat
                // simplemente "no encontraba nada" sin avisar por qué.
                val detalle = e.message ?: "error desconocido de TDLib"
                registrar("❌ \"${chat.titulo}\": $detalle")
                send(EventoEscaneo.ErrorEnChat(chat.titulo, detalle))
                null
            }

            // Si [mensajes] es null fue por un error ya reportado arriba; si
            // no, se reporta igual cuántos mensajes se leyeron y cuántos
            // enlaces candidatos salieron de ellos — sin esto, "0 resultados
            // sin ningún error" no decía si el chat estaba realmente vacío,
            // si tenía mensajes pero ninguno con enlace, o si el enlace
            // encontrado simplemente no quedó activo al validar.
            if (mensajes != null) {
                var candidatosDeEsteChat = 0
                for (mensaje in mensajes) {
                    val texto = textoDelMensaje(mensaje)
                    if (texto.isBlank()) continue
                    extraerEnlaces(texto).forEach { enlace ->
                        enlacesPorOrigen += chat.titulo to enlace
                        candidatosDeEsteChat++
                    }
                }
                registrar("✅ \"${chat.titulo}\": ${mensajes.size} mensaje(s) leído(s), $candidatosDeEsteChat enlace(s) candidato(s).")
                send(EventoEscaneo.InfoChat(chat.titulo, mensajes.size, candidatosDeEsteChat))
            }
        }

        var enlacesUnicos = enlacesPorOrigen.distinctBy { it.second }
        if (!filtroHost.isNullOrBlank()) {
            val totalAntesDelFiltro = enlacesUnicos.size
            enlacesUnicos = enlacesUnicos.filter { (_, enlace) -> enlace.contains(filtroHost, ignoreCase = true) }
            registrar("🔧 Filtro de host \"$filtroHost\": $totalAntesDelFiltro → ${enlacesUnicos.size} enlace(s).")
        }
        send(EventoEscaneo.Progreso(0, enlacesUnicos.size))
        registrar("🔎 Validando ${enlacesUnicos.size} enlace(s) candidato(s) (hasta $MAX_VALIDACIONES_EN_PARALELO en paralelo)...")

        // Validar uno por uno (como antes) hacía que UN solo panel colgado
        // detuviera el escaneo completo en "0 de N validados" para
        // siempre — ahora, ya con timeout en [NetworkModule], tampoco
        // convenía quedarse secuencial: con decenas/cientos de enlaces
        // reales, 10s de timeout por enlace uno tras otro igual tarda
        // minutos. El script de Python ya validaba hasta
        // [MAX_VALIDACIONES_EN_PARALELO] enlaces a la vez
        // (`MAX_CONCURRENT_VALIDATIONS`); acá es el equivalente con un
        // `Semaphore` para no abrir cientos de conexiones de golpe.
        val procesados = AtomicInteger(0)
        val activas = AtomicInteger(0)
        val semaforo = Semaphore(MAX_VALIDACIONES_EN_PARALELO)
        coroutineScope {
            enlacesUnicos.map { (origen, enlace) ->
                async {
                    semaforo.withPermit {
                        val credenciales = extraerCredencialesXtream(enlace)
                        if (credenciales == null) {
                            registrar("⚠️ Enlace sin usuario/contraseña reconocible (desde \"$origen\"): $enlace")
                        } else {
                            val validada = validarYEnriquecer(credenciales, origen)
                            if (validada.activa) activas.incrementAndGet()
                            registrar(
                                if (validada.activa) "✅ Activa: ${validada.credenciales.host} (desde \"$origen\")"
                                else "❌ Inactiva/caída: ${validada.credenciales.host} (desde \"$origen\")"
                            )
                            // El modo "LATINO" del script de Python solo se
                            // queda con listas activas y de contenido
                            // latino tras validarlas.
                            if (!soloLatino || (validada.activa && validada.esLatino)) {
                                send(EventoEscaneo.ListaEncontrada(validada))
                            }
                        }
                        send(EventoEscaneo.Progreso(procesados.incrementAndGet(), enlacesUnicos.size))
                    }
                }
            }.awaitAll()
        }
        registrar("🏁 Escaneo terminado: ${activas.get()} activa(s) de ${enlacesUnicos.size} validada(s).")
        send(EventoEscaneo.Finalizado)
    }

    /**
     * Trae un lote de los mensajes más recientes del chat (no el historial
     * completo, para no hacer el escaneo demasiado lento en chats grandes).
     *
     * TDLib puede devolver una página vacía o más corta de lo pedido aunque
     * todavía NO se haya llegado al final real del historial — es
     * comportamiento documentado, no un error (confirmado por un
     * mantenedor de TDLib: https://github.com/tdlib/td/issues/11). Antes
     * esta función hacía una sola llamada y se quedaba con lo que fuera que
     * volviera; si esa primera llamada venía vacía (algo común en chats que
     * la sesión no había leído recientemente), el chat quedaba "sin
     * mensajes" sin ser realmente así — esto era la causa real del "0
     * resultados, sin error, en menos de un segundo" que se reportó.
     *
     * Si el chat tiene "Temas" (forum topics) activado, [GetChatHistory] no
     * es confiable para traer todo: cada tema se comporta casi como un
     * sub-chat aparte y hace falta pedir la lista de temas y leer el
     * historial de cada uno por separado (ver [obtenerMensajesDeForo]) —
     * si no, el escaneo solo ve el tema "General" (o ni eso) y nunca los
     * enlaces publicados en el resto de temas. Esto era la causa real de
     * que un chat con "listas por secciones" diera "0 de 0" aunque otros
     * chats normales ya funcionaran bien.
     */
    private suspend fun obtenerMensajesRecientes(
        chatId: Long,
        chatTitulo: String,
        registrar: suspend (String) -> Unit,
        limite: Int = LIMITE_MENSAJES,
    ): List<TdApi.Message> {
        val esForo = esChatConTemas(chatId)
        registrar("   ¿tiene Temas? ${if (esForo) "sí" else "no"}")

        if (esForo) {
            // Si la lectura por temas no trae NADA — ya sea porque
            // GetForumTopics/GetForumTopicHistory no respondieron a tiempo
            // (visto en la práctica incluso con GetForumTopics, la llamada
            // más liviana de las dos) o porque de verdad no había mensajes
            // en ningún tema — se cae al historial normal como último
            // recurso en vez de devolver "0 mensajes" directo. En un foro
            // de verdad esto trae al menos el tema "General"; y si
            // [esChatConTemas] se equivocó con este chat puntual (no es
            // realmente un foro), esto es lo que debía leerse desde el
            // principio.
            var mensajesDeForo: List<TdApi.Message> = emptyList()
            try {
                mensajesDeForo = obtenerMensajesDeForo(chatId, limite, registrar)
                if (mensajesDeForo.isEmpty()) {
                    registrar("   lectura por Temas no trajo mensajes → probando historial normal...")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                registrar("   lectura por Temas falló (${e.message ?: "error desconocido"}) → probando historial normal...")
            }
            if (mensajesDeForo.isNotEmpty()) return mensajesDeForo
        } else {
            registrar("   leyendo historial normal...")
        }
        return obtenerMensajesDeHistorialNormal(chatId, limite)
    }

    /** `true` si el chat es un supergrupo con "Temas" (forum topics) activado. */
    private suspend fun esChatConTemas(chatId: Long): Boolean {
        val chat = sesion.enviarYEsperar(TdApi.GetChat(chatId)) as? TdApi.Chat ?: return false
        val tipo = chat.type as? TdApi.ChatTypeSupergroup ?: return false
        val supergrupo = sesion.enviarYEsperar(TdApi.GetSupergroup(tipo.supergroupId)) as? TdApi.Supergroup
            ?: return false
        return supergrupo.isForum
    }

    private suspend fun obtenerMensajesDeHistorialNormal(chatId: Long, limite: Int): List<TdApi.Message> {
        val mensajes = mutableListOf<TdApi.Message>()
        var fromMessageId = 0L
        var intentosVacios = 0

        while (mensajes.size < limite && intentosVacios < MAX_INTENTOS_VACIOS) {
            // Deja que cualquier error de TDLib se propague: quien llama
            // decide qué hacer con él (ver [escanear], que lo convierte en
            // un [EventoEscaneo.ErrorEnChat] visible en vez de tragárselo).
            val resultado = sesion.enviarYEsperar(
                TdApi.GetChatHistory(chatId, fromMessageId, 0, LOTE_POR_PAGINA, false)
            ) as? TdApi.Messages

            val lote = resultado?.messages?.filterNotNull().orEmpty()

            if (lote.isEmpty()) {
                intentosVacios++
                continue
            }

            intentosVacios = 0
            mensajes += lote
            fromMessageId = lote.last().id
        }

        return mensajes
    }

    /**
     * Junta los mensajes de TODOS los temas del chat (incluido "General"),
     * pidiendo primero la lista completa de temas ([GetForumTopics],
     * paginada) y leyendo el historial de cada uno por separado
     * ([GetForumTopicHistory], con el mismo reintento ante páginas vacías
     * que [obtenerMensajesDeHistorialNormal]) — cada tema puede aportar
     * hasta [limitePorTema] mensajes.
     *
     * Antes, si UN solo tema no respondía a tiempo (visto en la práctica:
     * `GetForumTopicHistory` puede tardar más de los 15s de margen si
     * Telegram aplica un FLOOD_WAIT a ese método — TDLib lo reintenta por
     * dentro sin avisar, así que desde acá se ve como si nunca respondiera),
     * la excepción se propagaba y se perdían TAMBIÉN los mensajes de los
     * temas anteriores que sí habían respondido bien, marcando el chat
     * entero como "no se pudo leer" aunque la mayoría de temas funcionara.
     * Ahora cada tema se intenta por separado: uno que falle no tira el
     * resto a la basura.
     */
    private suspend fun obtenerMensajesDeForo(
        chatId: Long,
        limitePorTema: Int,
        registrar: suspend (String) -> Unit,
    ): List<TdApi.Message> {
        // Si esta llamada falla, se deja propagar sin atrapar — quien llama
        // ([obtenerMensajesRecientes]) ya sabe convertirlo en el mensaje de
        // "lectura por Temas falló" y caer al historial normal.
        val ids = obtenerIdsDeTemas(chatId)
        registrar("   ${ids.size} tema(s) encontrado(s)")

        val mensajes = mutableListOf<TdApi.Message>()
        for (temaId in ids) {
            try {
                val mensajesDelTema = obtenerMensajesDeTema(chatId, temaId, limitePorTema)
                registrar("   tema $temaId: ${mensajesDelTema.size} mensaje(s)")
                mensajes += mensajesDelTema
            } catch (e: CancellationException) {
                throw e // un cancelado real (ej. "Detener y ver parciales") sí debe propagarse
            } catch (e: Exception) {
                // Se sigue con el resto de temas; si todos fallan, el chat
                // queda igual que antes (0 mensajes), pero si solo uno
                // falla ya no se pierde lo que los demás sí trajeron.
                registrar("   tema $temaId: falló (${e.message ?: "error desconocido"})")
            }
        }
        return mensajes
    }

    private suspend fun obtenerIdsDeTemas(chatId: Long): List<Int> {
        val ids = mutableListOf<Int>()
        var offsetDate = 0
        var offsetMessageId = 0L
        var offsetTemaId = 0

        while (true) {
            val resultado = sesion.enviarYEsperar(
                TdApi.GetForumTopics(chatId, "", offsetDate, offsetMessageId, offsetTemaId, LOTE_TEMAS)
            ) as? TdApi.ForumTopics ?: break

            val lote = resultado.topics?.filterNotNull().orEmpty()
            if (lote.isEmpty()) break

            ids += lote.map { it.info.forumTopicId }
            if (lote.size < LOTE_TEMAS) break

            offsetDate = resultado.nextOffsetDate
            offsetMessageId = resultado.nextOffsetMessageId
            offsetTemaId = resultado.nextOffsetForumTopicId
        }

        return ids
    }

    private suspend fun obtenerMensajesDeTema(chatId: Long, temaId: Int, limite: Int): List<TdApi.Message> {
        val mensajes = mutableListOf<TdApi.Message>()
        var fromMessageId = 0L
        var intentosVacios = 0

        while (mensajes.size < limite && intentosVacios < MAX_INTENTOS_VACIOS) {
            val resultado = sesion.enviarYEsperar(
                TdApi.GetForumTopicHistory(chatId, temaId, fromMessageId, 0, LOTE_POR_PAGINA)
            ) as? TdApi.Messages

            val lote = resultado?.messages?.filterNotNull().orEmpty()

            if (lote.isEmpty()) {
                intentosVacios++
                continue
            }

            intentosVacios = 0
            mensajes += lote
            fromMessageId = lote.last().id
        }

        return mensajes
    }

    /** Extrae el texto o el pie de foto/video/documento donde puede venir un enlace. */
    private fun textoDelMensaje(mensaje: TdApi.Message): String {
        return when (val contenido = mensaje.content) {
            is TdApi.MessageText -> contenido.text.text
            is TdApi.MessagePhoto -> contenido.caption.text
            is TdApi.MessageVideo -> contenido.caption.text
            is TdApi.MessageDocument -> contenido.caption.text
            else -> ""
        }
    }

    private companion object {
        const val LIMITE_MENSAJES = 200
        const val LOTE_POR_PAGINA = 50
        const val MAX_INTENTOS_VACIOS = 5
        const val LOTE_TEMAS = 100
        const val MAX_VALIDACIONES_EN_PARALELO = 50
    }
}
