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
            for (mensaje in obtenerMensajesRecientes(chat.id)) {
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
     */
    private suspend fun obtenerMensajesRecientes(chatId: Long, limite: Int = LIMITE_MENSAJES): List<TdApi.Message> {
        return try {
            val resultado = sesion.enviarYEsperar(
                TdApi.GetChatHistory(chatId, 0, 0, limite, false)
            ) as? TdApi.Messages
            resultado?.messages?.filterNotNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
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
    }
}
