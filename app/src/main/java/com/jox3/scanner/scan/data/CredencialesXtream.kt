package com.jox3.scanner.scan.data

import io.ktor.http.Url

/**
 * Credenciales extraídas de un enlace tipo Xtream Codes
 * (host/get.php?username=...&password=...&type=...).
 *
 * Equivalente a `extraer_credenciales_xtream()` del script de Python, pero
 * usando el parser de URLs de Ktor (robusto ante el orden de los parámetros)
 * en vez de una regex por posición.
 */
data class CredencialesXtream(
    val host: String,
    val username: String,
    val password: String,
    val urlOriginal: String,
)

/**
 * Devuelve `null` si [url] no es un enlace Xtream válido (le faltan
 * username/password, o no se puede parsear como URL).
 */
fun extraerCredencialesXtream(url: String): CredencialesXtream? {
    return runCatching {
        val parsed = Url(url)
        val username = parsed.parameters["username"]?.takeIf { it.isNotBlank() } ?: return null
        val password = parsed.parameters["password"]?.takeIf { it.isNotBlank() } ?: return null

        val puerto = if (parsed.specifiedPort > 0) ":${parsed.specifiedPort}" else ""
        val host = "${parsed.protocol.name}://${parsed.host}$puerto"

        CredencialesXtream(host = host, username = username, password = password, urlOriginal = url)
    }.getOrNull()
}

/**
 * Busca enlaces de listas (`.../get.php?...`) dentro de un texto libre, como
 * el contenido de un mensaje de Telegram. Equivalente a `M3U_PATTERN` del
 * script de Python: no valida los parámetros aquí, solo encuentra
 * candidatos — eso lo hace [extraerCredencialesXtream] después.
 */
private val patronEnlaceM3U = Regex("""https?://[^\s'"<>]+?/get\.php\?[^\s'"<>]+""", RegexOption.IGNORE_CASE)

fun extraerEnlaces(texto: String): List<String> =
    patronEnlaceM3U.findAll(texto).map { it.value }.distinct().toList()
