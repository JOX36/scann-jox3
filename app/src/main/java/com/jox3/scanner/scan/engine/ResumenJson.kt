package com.jox3.scanner.scan.engine

/** Resultado de recorrer un array JSON sin construirlo en memoria. */
class ResumenArrayJson(val cantidad: Int, val nombres: List<String>)

/**
 * Recorre un array JSON de objetos (p. ej. `get_live_streams`, `get_vod_streams`) en streaming:
 * cuenta los elementos y guarda solo los primeros [maxNombres] valores del campo `name`.
 *
 * Antes cada lista validada cargaba en memoria TODO el listado de canales, películas y series
 * (decenas de miles de objetos) y, con hasta 50 validaciones a la vez, el heap de 256 MB se
 * agotaba (OutOfMemoryError) y la app se cerraba a mitad del escaneo. Acá la memoria usada es
 * constante sin importar el tamaño de la respuesta.
 *
 * Devuelve `null` si el contenido no es un array JSON (p. ej. un error en HTML o un objeto).
 */
class ResumidorArrayJson(private val maxNombres: Int) {
    private val nombres = ArrayList<String>()
    private var profundidad = 0
    private var empezo = false
    private var enCadena = false
    private var escape = false
    private var unicodePendiente = 0
    private var unicodeValor = 0
    private val cadena = StringBuilder()
    private var ultimoSig = ' ' // último carácter significativo antes de abrir la cadena actual
    private var previoAlAbrir = ' '
    private var claveActual = ""
    private var cantidad = 0
    private var terminado = false
    private var invalido = false

    /** Procesa [n] caracteres de [buffer]. Devuelve `true` cuando ya no hace falta leer más (array cerrado o contenido inválido). */
    fun alimentar(buffer: CharArray, n: Int): Boolean {
        if (terminado || invalido) return true
        for (i in 0 until n) {
            val c = buffer[i]
            if (enCadena) {
                if (unicodePendiente > 0) {
                    unicodeValor = unicodeValor * 16 + Character.digit(c, 16).coerceAtLeast(0)
                    unicodePendiente--
                    if (unicodePendiente == 0 && cadena.length < 200) cadena.append(unicodeValor.toChar())
                } else if (escape) {
                    escape = false
                    when (c) {
                        'u' -> { unicodePendiente = 4; unicodeValor = 0 }
                        'n', 'r', 't', 'b', 'f' -> if (cadena.length < 200) cadena.append(' ')
                        else -> if (cadena.length < 200) cadena.append(c)
                    }
                } else if (c == '\\') {
                    escape = true
                } else if (c == '"') {
                    enCadena = false
                    if (profundidad == 2) {
                        if (previoAlAbrir == ':') {
                            if (claveActual == "name" && nombres.size < maxNombres) nombres.add(cadena.toString())
                        } else {
                            claveActual = cadena.toString()
                        }
                    }
                    ultimoSig = 's'
                } else if (profundidad == 2 && cadena.length < 200) {
                    cadena.append(c)
                }
                continue
            }
            when (c) {
                ' ', '\n', '\r', '\t', '\uFEFF' -> {}
                '"' -> {
                    if (!empezo) { invalido = true; return true }
                    enCadena = true
                    previoAlAbrir = ultimoSig
                    cadena.setLength(0)
                }
                '[', '{' -> {
                    if (!empezo) {
                        if (c != '[') { invalido = true; return true }
                        empezo = true
                    }
                    profundidad++
                    if (profundidad == 2 && c == '{') cantidad++
                    ultimoSig = c
                }
                ']', '}' -> {
                    profundidad--
                    ultimoSig = c
                    if (profundidad <= 0) { terminado = true; return true }
                }
                else -> {
                    if (!empezo) { invalido = true; return true }
                    ultimoSig = if (c == ',' || c == ':') c else 'x'
                }
            }
        }
        return false
    }

    fun resultado(): ResumenArrayJson? =
        if (!invalido && empezo && (terminado || profundidad == 0)) ResumenArrayJson(cantidad, nombres) else null
}
