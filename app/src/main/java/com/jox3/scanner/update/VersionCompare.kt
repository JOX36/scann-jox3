package com.jox3.scanner.update

/**
 * Compara versiones estilo "0.1.0" / "v0.2.1" (SemVer simplificado, sin
 * sufijos de pre-release). Suficiente para decidir si el release publicado
 * en GitHub es más nuevo que el `versionName` instalado.
 *
 * Se tolera un prefijo "v" opcional (los tags suelen llamarse "v0.2.0") y
 * cualquier número de segmentos (si faltan, se asumen 0).
 */
fun esVersionMasNueva(actual: String, candidata: String): Boolean {
    val partesActual = normalizar(actual)
    val partesCandidata = normalizar(candidata)
    val longitud = maxOf(partesActual.size, partesCandidata.size)

    for (i in 0 until longitud) {
        val a = partesActual.getOrElse(i) { 0 }
        val c = partesCandidata.getOrElse(i) { 0 }
        if (c != a) return c > a
    }
    return false
}

private fun normalizar(version: String): List<Int> {
    return version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .split(".")
        .map { segmento -> segmento.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
