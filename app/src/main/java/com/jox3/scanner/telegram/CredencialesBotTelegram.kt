package com.jox3.scanner.telegram

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda el BOT_TOKEN / CHAT_ID de un bot de Telegram (para el botón
 * "Enviar a Telegram" de Resultados) cifrados en el dispositivo — mismo
 * patrón que [CredencialesApiTelegram], nunca en texto plano ni en el
 * repositorio. Son credenciales de un BOT (creado con @BotFather), no la
 * cuenta de usuario que ya autenticó el login.
 */
class CredencialesBotTelegram(context: Context) {

    private val preferencias = run {
        val llaveMaestra = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "credenciales_bot_telegram",
            llaveMaestra,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun obtenerBotToken(): String? = preferencias.getString(CLAVE_BOT_TOKEN, null)

    fun obtenerChatId(): String? = preferencias.getString(CLAVE_CHAT_ID, null)

    fun tieneCredenciales(): Boolean = !obtenerBotToken().isNullOrBlank() && !obtenerChatId().isNullOrBlank()

    fun guardar(botToken: String, chatId: String) {
        preferencias.edit()
            .putString(CLAVE_BOT_TOKEN, botToken)
            .putString(CLAVE_CHAT_ID, chatId)
            .apply()
    }

    private companion object {
        const val CLAVE_BOT_TOKEN = "bot_token"
        const val CLAVE_CHAT_ID = "chat_id"
    }
}
