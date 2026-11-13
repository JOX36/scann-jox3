package com.jox3.scanner.scan.engine

import com.jox3.scanner.scan.data.CredencialesXtream

/** Resultado final de validar y clasificar un enlace encontrado. */
data class ListaValidada(
    val credenciales: CredencialesXtream,
    val activa: Boolean,
    val esLatino: Boolean,
    val porcentajeLatino: Double,
    val origenChat: String,
)

/**
 * Verifica si la lista está activa y, si lo está, la enriquece con la
 * detección de contenido latino (categorías primero; nombres de canal como
 * respaldo si las categorías no dan señal suficiente). Equivalente a
 * `enriquecer_con_categorias()` en el script de Python.
 */
suspend fun validarYEnriquecer(cred: CredencialesXtream, origenChat: String): ListaValidada {
    val activa = XtreamClient.verificarCuenta(cred)
    if (!activa) {
        return ListaValidada(cred, activa = false, esLatino = false, porcentajeLatino = 0.0, origenChat = origenChat)
    }

    val categorias = XtreamClient.obtenerCategorias(cred)
    var (esLatino, porcentaje) = esContenidoLatino(categorias)

    if (!esLatino) {
        val canales = XtreamClient.obtenerMuestraCanales(cred)
        if (canales.isNotEmpty()) {
            val (esLatinoConCanales, porcentajeConCanales) = esContenidoLatino(categorias + canales)
            esLatino = esLatinoConCanales
            porcentaje = porcentajeConCanales
        }
    }

    return ListaValidada(cred, activa = true, esLatino = esLatino, porcentajeLatino = porcentaje, origenChat = origenChat)
}
