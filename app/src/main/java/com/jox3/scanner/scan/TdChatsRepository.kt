package com.jox3.scanner.scan

import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.scan.data.TipoChat
import com.jox3.scanner.telegram.TdLibAuthClient
import org.drinkless.tdlib.TdApi

/**
 * Lista los chats reales del usuario sobre la misma sesión de TDLib que ya
 * autenticó el login (Etapa 4) — reemplaza a [FakeChatsRepository].
 */
class TdChatsRepository(private val sesion: TdLibAuthClient) : ChatsRepository {

    override suspend fun listarChats(): List<ChatTelegram> {
        // TDLib solo tiene en memoria los chats que ya "cargó" de la lista
        // principal; getChats trae como máximo lo que ya está cargado, así
        // que si el usuario tiene más de un puñado de chats hay que pedirle
        // a TDLib que siga cargando (loadChats) hasta que avise que no queda
        // nada más — si no, getChats devuelve solo una parte (bug reportado:
        // "no muestra el 100% de mis chats").
        cargarTodosLosChats()

        val chats = sesion.enviarYEsperar(TdApi.GetChats(TdApi.ChatListMain(), LIMITE_ALTO)) as? TdApi.Chats
            ?: return emptyList()

        return chats.chatIds.toList().mapNotNull { chatId ->
            val chat = sesion.enviarYEsperar(TdApi.GetChat(chatId)) as? TdApi.Chat ?: return@mapNotNull null
            ChatTelegram(
                id = chatId,
                titulo = chat.title.ifBlank { "Chat sin nombre" },
                tipo = tipoDeChat(chat),
                // TDLib no da un conteo de mensajes barato sin leer el
                // historial completo; se omite (la pantalla lo oculta si es 0).
                mensajesAprox = 0,
            )
        }
    }

    private suspend fun cargarTodosLosChats() {
        while (true) {
            try {
                sesion.enviarYEsperar(TdApi.LoadChats(TdApi.ChatListMain(), LOTE_CARGA))
            } catch (e: Exception) {
                // TDLib responde con un error cuando ya no quedan más chats
                // por cargar en esta lista — es la señal normal de "ya
                // terminé", no un fallo real.
                break
            }
        }
    }

    private fun tipoDeChat(chat: TdApi.Chat): TipoChat {
        return when (val tipo = chat.type) {
            is TdApi.ChatTypeSupergroup -> if (tipo.isChannel) TipoChat.CANAL else TipoChat.GRUPO
            is TdApi.ChatTypeBasicGroup -> TipoChat.GRUPO
            else -> TipoChat.PRIVADO
        }
    }

    private companion object {
        const val LOTE_CARGA = 100
        const val LIMITE_ALTO = 2000
    }
}
