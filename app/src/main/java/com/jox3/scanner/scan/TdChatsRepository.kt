package com.jox3.scanner.scan

import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.telegram.TdLibAuthClient
import org.drinkless.tdlib.TdApi

/**
 * Lista los chats reales del usuario sobre la misma sesión de TDLib que ya
 * autenticó el login (Etapa 4) — reemplaza a [FakeChatsRepository].
 */
class TdChatsRepository(private val sesion: TdLibAuthClient) : ChatsRepository {

    override suspend fun listarChats(): List<ChatTelegram> {
        // TDLib primero necesita "cargar" la lista principal de chats en su
        // base local antes de poder devolverlos; getChats trae hasta
        // [LIMITE] de los más recientes/activos.
        val chats = sesion.enviarYEsperar(TdApi.GetChats(TdApi.ChatListMain(), LIMITE)) as? TdApi.Chats
            ?: return emptyList()

        return chats.chatIds.toList().mapNotNull { chatId ->
            val chat = sesion.enviarYEsperar(TdApi.GetChat(chatId)) as? TdApi.Chat ?: return@mapNotNull null
            ChatTelegram(
                id = chatId,
                titulo = chat.title.ifBlank { "Chat sin nombre" },
                // TDLib no da un conteo de mensajes barato sin leer el
                // historial completo; se omite (la pantalla lo oculta si es 0).
                mensajesAprox = 0,
            )
        }
    }

    private companion object {
        const val LIMITE = 100
    }
}
