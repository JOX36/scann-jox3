package com.jox3.scanner.scan.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Información "extra" de una lista, consultada a demanda cuando el usuario
 * abre su detalle — pedido explícito del usuario: poder ver esto sin ir a
 * Telegram, y que sea una consulta aparte (no algo que se calcule para
 * TODAS las listas durante el escaneo masivo, lo cual sería mucho más
 * tráfico de red del necesario para datos que la mayoría de las veces no
 * se van a mirar).
 */
data class DetalleLista(
    val canales: List<String>,
    val peliculas: List<String>,
    val series: List<String>,
    val geoIp: InfoGeoIp?,
    /** La IP sobre la que se consultó [geoIp] (ya resuelta por DNS, o la IP literal del host) — `null` si no hubo ninguna disponible. */
    val ipConsultada: String?,
    /** El enlace M3U con el host cambiado por [ipConsultada] — `null` si no hay IP disponible. */
    val enlaceM3uPorIp: String?,
)

/** Pide todo lo de [DetalleLista] en paralelo — equivalente a lo que ya hace `validarYEnriquecer` para el escaneo masivo, pero para una sola lista. */
suspend fun consultarDetalleLista(lista: ListaValidada): DetalleLista = coroutineScope {
    val cred = lista.credenciales
    val canalesAsync = async { XtreamClient.obtenerCanales(cred) }
    val peliculasAsync = async { XtreamClient.obtenerNombresPeliculas(cred) }
    val seriesAsync = async { XtreamClient.obtenerNombresSeries(cred) }
    val ip = XtreamClient.ipEfectiva(cred, lista.direccionIp)
    val geoAsync = async { ip?.let { XtreamClient.geolocalizarIp(it) } }

    DetalleLista(
        canales = canalesAsync.await(),
        peliculas = peliculasAsync.await(),
        series = seriesAsync.await(),
        geoIp = geoAsync.await(),
        ipConsultada = ip,
        enlaceM3uPorIp = ip?.let { XtreamClient.enlacePorIp(cred, it) },
    )
}
