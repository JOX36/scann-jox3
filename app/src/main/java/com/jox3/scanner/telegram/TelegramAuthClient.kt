package com.jox3.scanner.telegram

import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato para el cliente de Telegram, independiente de la librería real
 * que lo implemente.
 *
 * PENDIENTE (siguiente paso técnico, a propósito no incluido en esta etapa):
 * la implementación real contra TDLib (TdLibAuthClient), usando las clases
 * oficiales org.drinkless.td.libcore.telegram.Client / TdApi. La build nativa
 * de TDLib (CMake + NDK, generación de TdApi.java desde td_api.tl) depende de
 * la versión exacta del submódulo tdlib y de cómo quieras compilarla (fuente
 * vs. binario precompilado), así que se resuelve en un PR aparte en vez de
 * adivinar una configuración que podría no compilarte. Mientras tanto,
 * FakeTelegramAuthClient permite probar toda la UI de login.
 */
interface TelegramAuthClient {
    val estado: StateFlow<TelegramAuthState>

    suspend fun conectar()
    suspend fun enviarTelefono(telefono: String)
    suspend fun enviarCodigo(codigo: String)
    suspend fun enviarPassword2FA(password: String)
}
