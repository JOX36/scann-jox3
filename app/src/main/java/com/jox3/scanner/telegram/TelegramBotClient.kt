package com.jox3.scanner.telegram

import com.jox3.scanner.update.NetworkModule
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.delay
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.File

/**
 * Envía un archivo a un chat de Telegram vía la API de bots
 * (`sendDocument`) — equivalente a `send_file_to_telegram()` del script de
 * Python, pero usando el [BOT_TOKEN]/[CHAT_ID] guardados en
 * [CredencialesBotTelegram] en vez de variables de entorno.
 */
object TelegramBotClient {

    /** `null` si se envió bien; si no, el mensaje de error para mostrarlo. */
    suspend fun enviarArchivo(botToken: String, chatId: String, archivo: File, caption: String? = null): String? {
        return runCatching {
            val respuesta = NetworkModule.cliente.submitFormWithBinaryData(
                url = "https://api.telegram.org/bot$botToken/sendDocument",
                formData = formData {
                    append("chat_id", chatId)
                    if (!caption.isNullOrBlank()) append("caption", caption)
                    append(
                        "document",
                        archivo.readBytes(),
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"${archivo.name}\"")
                        },
                    )
                },
            )
            if (respuesta.status == HttpStatusCode.OK) {
                null
            } else {
                "Telegram respondió ${respuesta.status.value}"
            }
        }.getOrElse { e -> e.message ?: "Error desconocido enviando a Telegram" }
    }

    /** Límite de Telegram por mensaje: 4096 caracteres; se deja margen por emojis/numeración. */
    private const val MAX_CARACTERES_MENSAJE = 3800

    /** Parte [texto] en trozos de a lo más [limite] caracteres, cortando por líneas (y por palabras/caracteres si una línea sola es más larga). */
    fun dividirTexto(texto: String, limite: Int = MAX_CARACTERES_MENSAJE): List<String> {
        val trozos = mutableListOf<String>()
        val actual = StringBuilder()
        fun cerrar() { if (actual.isNotBlank()) trozos += actual.toString().trimEnd(); actual.clear() }
        for (linea in texto.lines()) {
            var resto = linea
            while (resto.length > limite) {
                // línea gigante (p. ej. cientos de categorías separadas por coma): cortar en la última ", " posible
                val corte = resto.lastIndexOf(", ", limite).takeIf { it > limite / 2 }?.plus(2) ?: limite
                cerrar()
                trozos += resto.substring(0, corte).trimEnd()
                resto = resto.substring(corte)
            }
            if (actual.length + resto.length + 1 > limite) cerrar()
            actual.append(resto).append('\n')
        }
        cerrar()
        return trozos
    }

    /**
     * Envía [texto] como mensajes sueltos (no como .txt), divididos si superan el
     * límite de Telegram. `null` si todo se envió; si no, el error.
     */
    suspend fun enviarTexto(botToken: String, chatId: String, texto: String): String? {
        val partes = dividirTexto(texto)
        partes.forEachIndexed { indice, parte ->
            val cuerpo = if (partes.size > 1) "(${indice + 1}/${partes.size})\n$parte" else parte
            val error = runCatching {
                val respuesta = NetworkModule.cliente.submitForm(
                    url = "https://api.telegram.org/bot$botToken/sendMessage",
                    formParameters = parameters {
                        append("chat_id", chatId)
                        append("text", cuerpo)
                        append("disable_web_page_preview", "true")
                    },
                )
                if (respuesta.status == HttpStatusCode.OK) null else "Telegram respondió ${respuesta.status.value}"
            }.getOrElse { e -> e.message ?: "Error desconocido enviando a Telegram" }
            if (error != null) return "Mensaje ${indice + 1}/${partes.size}: $error"
            if (indice < partes.lastIndex) delay(400) // evita el límite de ~1 mensaje/s por chat
        }
        return null
    }
}
