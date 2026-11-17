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

    /** Límite de Telegram por mensaje: 4096 caracteres; se deja margen por emojis. */
    private const val MAX_CARACTERES_MENSAJE = 4000

    /**
     * Divide un informe en mensajes: uno por bloque (separados por línea en blanco:
     * datos de la cuenta, TV en vivo, Series, Películas…). El pie final (pocas líneas)
     * se pega al bloque anterior si cabe. Un bloque que por sí solo pasa el límite
     * se parte en la última coma posible; sin numeración — son un mismo informe.
     */
    fun dividirTexto(texto: String, limite: Int = MAX_CARACTERES_MENSAJE): List<String> {
        val bloques = texto.trim().split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        // pie ("🔍 Verificada…/📅 Escaneado…") → junto al bloque anterior si cabe
        if (bloques.size >= 2) {
            val pie = bloques.last()
            val anterior = bloques[bloques.size - 2]
            if (pie.lines().size <= 3 && anterior.length + pie.length + 2 <= limite) {
                bloques.removeAt(bloques.size - 1)
                bloques[bloques.size - 1] = anterior + "\n\n" + pie
            }
        }
        val mensajes = mutableListOf<String>()
        for (bloque in bloques) {
            var resto = bloque
            while (resto.length > limite) {
                val corte = resto.lastIndexOf(", ", limite).takeIf { it > limite / 2 }?.plus(2)
                    ?: resto.lastIndexOf('\n', limite).takeIf { it > limite / 2 }?.plus(1)
                    ?: limite
                mensajes += resto.substring(0, corte).trimEnd()
                resto = resto.substring(corte).trimStart()
            }
            if (resto.isNotEmpty()) mensajes += resto
        }
        return mensajes
    }

    /**
     * Envía [texto] como mensajes sueltos (no como .txt), divididos si superan el
     * límite de Telegram. `null` si todo se envió; si no, el error.
     */
    suspend fun enviarTexto(botToken: String, chatId: String, texto: String): String? {
        val partes = dividirTexto(texto)
        partes.forEachIndexed { indice, parte ->
            val cuerpo = parte
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
