package com.jox3.scanner.scan

import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.scan.data.extraerCredencialesXtream
import com.jox3.scanner.scan.data.extraerEnlaces
import com.jox3.scanner.scan.engine.validarYEnriquecer
import com.jox3.scanner.telegram.TdLibAuthClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.drinkless.tdlib.TdApi

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
    ): Flow<EventoEscaneo> = flow {
        emit(EventoEscaneo.Progreso(0, 0))

        // Primero se junta el texto de todos los mensajes de los chats
        // elegidos y se extraen los enlaces candidatos — igual que el
        // script de Python lee los mensajes antes de validar cada enlace.
        val enlacesPorOrigen = mutableListOf<Pair<String, String>>()
        for (chat in chats) {
            val mensajes = try {
                obtenerMensajesRecientes(chat.id)
            } catch (e: Exception) {
                // Antes este error se tragaba en silencio y el chat
                // simplemente "no encontraba nada" sin avisar por qué.
                emit(EventoEscaneo.ErrorEnChat(chat.titulo, e.message ?: "error desconocido de TDLib"))
                emptyList()
            }
            for (mensaje in mensajes) {
                val texto = textoDelMensaje(mensaje)
                if (texto.isBlank()) continue
                extraerEnlaces(texto).forEach { enlace -> enlacesPorOrigen += chat.titulo to enlace }
            }
        }

        var enlacesUnicos = enlacesPorOrigen.distinctBy { it.second }
        if (!filtroHost.isNullOrBlank()) {
            enlacesUnicos = enlacesUnicos.filter { (_, enlace) -> enlace.contains(filtroHost, ignoreCase = true) }
        }
        emit(EventoEscaneo.Progreso(0, enlacesUnicos.size))

        enlacesUnicos.forEachIndexed { indice, (origen, enlace) ->
            val credenciales = extraerCredencialesXtream(enlace)
            if (credenciales != null) {
                val validada = validarYEnriquecer(credenciales, origen)
                // El modo "LATINO" del script de Python solo se queda con
                // listas activas y de contenido latino tras validarlas.
                if (!soloLatino || (validada.activa && validada.esLatino)) {
                    emit(EventoEscaneo.ListaEncontrada(validada))
                }
            }
            emit(EventoEscaneo.Progreso(indice + 1, enlacesUnicos.size))
        }
        emit(EventoEscaneo.Finalizado)
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
     * Ahora se pagina pidiendo página tras página (con [fromMessageId]
     * apuntando siempre al mensaje más viejo ya recibido) hasta juntar
     * [limite] mensajes, y si una página vuelve vacía se reintenta unas
     * pocas veces antes de asumir que de verdad no hay más historial.
     */
    private suspend fun obtenerMensajesRecientes(chatId: Long, limite: Int = LIMITE_MENSAJES): List<TdApi.Message> {
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
    }
}
