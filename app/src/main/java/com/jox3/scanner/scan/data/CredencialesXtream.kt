package com.jox3.scanner.scan.data

import java.net.URLDecoder

/**
 * Credenciales extraídas de un enlace tipo Xtream Codes
 * (host/get.php?username=...&password=...&type=...).
 *
 * Equivalente a `extraer_credenciales_xtream()` del script de Python: parseo
 * manual (separar por "://", "/", "?", "&", "=") en vez de un parser de URL
 * estricto. Esto es a propósito — la primera versión usaba `io.ktor.http.Url`,
 * que valida la URL contra la gramática RFC 3986 y lanza una excepción ante
 * cualquier cosa fuera de lo esperado. Los mensajes reales de canales de
 * IPTV suelen traer el enlace pegado a emojis/decoración sin espacio de por
 * medio (ej. "...type=m3u_plus🔥🔥"), así que la regex de [extraerEnlaces]
 * los capturaba como parte del enlace y `Url(...)` reventaba con cada uno —
 * la causa real de escaneos que daban "0 activas de 0" incluso con decenas
 * de enlaces detectados. El parseo manual, como el de Python, no exige una
 * URL perfecta: solo necesita encontrar "username=" y "password=" en algún
 * lugar de la query.
 */
data class CredencialesXtream(
    val host: String,
    val username: String,
    val password: String,
    val urlOriginal: String,
)

/**
 * Devuelve `null` si [url] no es un enlace Xtream válido (le faltan
 * username/password, o no tiene la forma esquema://host/...?...).
 */
fun extraerCredencialesXtream(url: String): CredencialesXtream? {
    val indiceQuery = url.indexOf('?')
    if (indiceQuery == -1) return null

    val antesDeQuery = url.substring(0, indiceQuery)
    val partesEsquema = antesDeQuery.split("://", limit = 2)
    if (partesEsquema.size != 2) return null
    val esquema = partesEsquema[0]
    val host = partesEsquema[1].substringBefore("/").trim()
    if (host.isBlank()) return null

    // Corta en '#' (fragmento) como haría cualquier navegador, y en
    // cualquier basura pegada al final sin "&" de por medio.
    val query = url.substring(indiceQuery + 1).substringBefore("#")
    val parametros = query.split("&").mapNotNull { parNombreValor ->
        val partes = parNombreValor.split("=", limit = 2)
        if (partes.size != 2) null else partes[0] to decodificarParametro(partes[1])
    }.toMap()

    val username = parametros["username"]?.takeIf { it.isNotBlank() } ?: return null
    val password = parametros["password"]?.takeIf { it.isNotBlank() } ?: return null

    return CredencialesXtream(host = "$esquema://$host", username = username, password = password, urlOriginal = url)
}

/** Decodifica %XX si el valor viene URL-encoded; si no se puede, lo deja tal cual. */
private fun decodificarParametro(valor: String): String =
    runCatching { URLDecoder.decode(valor, "UTF-8") }.getOrDefault(valor)

/**
 * Busca enlaces de listas (`.../get.php?...`) dentro de un texto libre, como
 * el contenido de un mensaje de Telegram. Equivalente a `M3U_PATTERN` del
 * script de Python: no valida los parámetros aquí, solo encuentra
 * candidatos — eso lo hace [extraerCredencialesXtream] después.
 */
private val patronEnlaceM3U = Regex("""https?://[^\s'"<>]+?/get\.php\?[^\s'"<>]+""", RegexOption.IGNORE_CASE)

/** Caracteres que de verdad pueden aparecer en una URL — todo lo demás pegado al final se recorta. */
private val caracterUrlValido = Regex("""[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]""")

/**
 * Recorta del final cualquier carácter que no sea válido en una URL (emoji,
 * signos de puntuación decorativos, etc.) que la regex de arriba haya
 * capturado pegado al enlace por no venir separado por un espacio.
 */
private fun recortarBasuraFinal(enlace: String): String {
    var fin = enlace.length
    while (fin > 0 && !caracterUrlValido.matches(enlace[fin - 1].toString())) fin--
    return enlace.substring(0, fin)
}

fun extraerEnlaces(texto: String): List<String> =
    patronEnlaceM3U.findAll(texto)
        .map { recortarBasuraFinal(it.value) }
        .filter { it.contains("get.php?") }
        .distinct()
        .toList()
