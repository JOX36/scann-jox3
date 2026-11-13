package com.jox3.scanner.telegram

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda el API_ID / API_HASH de Telegram (obtenidos una sola vez en
 * https://my.telegram.org/apps) cifrados en el dispositivo, igual que el
 * `.env` del script de Python pero sin quedar nunca en texto plano ni en el
 * repositorio.
 *
 * Son credenciales de la APLICACIÓN (identifican que la app habla con la
 * API de Telegram), no la cuenta del usuario — esa se autentica aparte con
 * teléfono + código en el flujo de login.
 */
class CredencialesApiTelegram(context: Context) {

    private val preferencias = run {
        val llaveMaestra = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "credenciales_api_telegram",
            llaveMaestra,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun obtenerApiId(): Int? = preferencias.getInt(CLAVE_API_ID, -1).takeIf { it > 0 }

    fun obtenerApiHash(): String? = preferencias.getString(CLAVE_API_HASH, null)

    fun tieneCredenciales(): Boolean = obtenerApiId() != null && !obtenerApiHash().isNullOrBlank()

    fun guardar(apiId: Int, apiHash: String) {
        preferencias.edit()
            .putInt(CLAVE_API_ID, apiId)
            .putString(CLAVE_API_HASH, apiHash)
            .apply()
    }

    private companion object {
        const val CLAVE_API_ID = "api_id"
        const val CLAVE_API_HASH = "api_hash"
    }
}
