package com.jox3.scanner.scan.data

/** Tipo de chat de Telegram, igual a como TDLib distingue chats privados, grupos y canales. */
enum class TipoChat {
    PRIVADO,
    GRUPO,
    CANAL,
}

/** Un chat/grupo/canal de Telegram donde buscar listas. */
data class ChatTelegram(
    val id: Long,
    val titulo: String,
    val tipo: TipoChat,
    val mensajesAprox: Int = 0,
)
