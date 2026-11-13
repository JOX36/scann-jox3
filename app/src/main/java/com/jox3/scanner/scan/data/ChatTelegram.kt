package com.jox3.scanner.scan.data

/** Un chat/grupo/canal de Telegram donde buscar listas. */
data class ChatTelegram(
    val id: Long,
    val titulo: String,
    val mensajesAprox: Int,
)
