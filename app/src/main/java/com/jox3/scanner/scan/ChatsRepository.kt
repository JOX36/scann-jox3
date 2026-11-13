package com.jox3.scanner.scan

import com.jox3.scanner.scan.data.ChatTelegram
import kotlinx.coroutines.delay

/**
 * Fuente de los chats donde se puede escanear. La implementación real
 * (TDLib) llega junto con `TdLibAuthClient` — ver README. Mientras tanto,
 * [FakeChatsRepository] permite construir y probar toda la UI de selección
 * de chats, escaneo y resultados.
 */
interface ChatsRepository {
    suspend fun listarChats(): List<ChatTelegram>
}

/** Lista de chats de ejemplo, con nombres parecidos a los reales del usuario. */
class FakeChatsRepository : ChatsRepository {
    override suspend fun listarChats(): List<ChatTelegram> {
        delay(500)
        return listOf(
            ChatTelegram(1, "📺 IPTV LATINO PREMIUM", 1240),
            ChatTelegram(2, "👩‍🎤 G-PANDORA", 358),
            ChatTelegram(3, "🔥 LISTAS M3U FREE", 902),
            ChatTelegram(4, "🌎 TV GLOBAL MIX", 2140),
            ChatTelegram(5, "⚽ DEPORTES HD", 410),
        )
    }
}
