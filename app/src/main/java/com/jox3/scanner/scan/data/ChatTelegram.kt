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
    /**
     * Si no es nulo, solo se leen estos temas (ids de foro) de un grupo con
     * "Temas" — elegidos por el usuario antes de escanear. Nulo = todos.
     */
    val temaIds: Set<Int>? = null,
)

/** Un tema (topic) de un grupo con "Temas" activado. */
data class TemaChat(val id: Int, val nombre: String)
