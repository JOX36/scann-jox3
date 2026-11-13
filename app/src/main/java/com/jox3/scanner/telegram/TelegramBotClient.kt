package com.jox3.scanner.telegram

import com.jox3.scanner.update.NetworkModule
import io.ktor.client.request.forms.formData
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
    suspend fun enviarArchivo(botToken: String, chatId: String, archivo: File): String? {
        return runCatching {
            val respuesta = NetworkModule.cliente.submitFormWithBinaryData(
                url = "https://api.telegram.org/bot$botToken/sendDocument",
                formData = formData {
                    append("chat_id", chatId)
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
}
