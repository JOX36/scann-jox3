package com.jox3.scanner.scan.engine

import java.io.Reader

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
fun resumirArrayJson(lector: Reader, maxNombres: Int): ResumenArrayJson? {
    val buffer = CharArray(8192)
    val nombres = ArrayList<String>()
    var profundidad = 0
    var empezo = false
    var enCadena = false
    var escape = false
    var unicodePendiente = 0
    var unicodeValor = 0
    val cadena = StringBuilder()
    var ultimoSig = ' ' // último carácter significativo antes de abrir la cadena actual
    var previoAlAbrir = ' '
    var claveActual = ""
    var cantidad = 0

    while (true) {
        val n = lector.read(buffer)
        if (n < 0) break
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
                ' ', '\n', '\r', '\t', '﻿' -> {}
                '"' -> {
                    if (!empezo) return null
                    enCadena = true
                    previoAlAbrir = ultimoSig
                    cadena.setLength(0)
                }
                '[', '{' -> {
                    if (!empezo) {
                        if (c != '[') return null
                        empezo = true
                    }
                    profundidad++
                    if (profundidad == 2 && c == '{') cantidad++
                    ultimoSig = c
                }
                ']', '}' -> { profundidad--; ultimoSig = c; if (profundidad <= 0) return ResumenArrayJson(cantidad, nombres) }
                else -> {
                    if (!empezo) return null
                    ultimoSig = if (c == ',' || c == ':') c else 'x'
                }
            }
        }
    }
    return if (empezo && profundidad == 0) ResumenArrayJson(cantidad, nombres) else null
}
