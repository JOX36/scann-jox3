package com.jox3.scanner.scan.bodega

import com.jox3.scanner.scan.engine.InfoCuentaXtream
import com.jox3.scanner.scan.engine.XtreamClient
import kotlinx.coroutines.delay

/** Informe de calidad de una lista de la Bodega (ver [medirCalidad]). */
data class InformeCalidad(
    val clave: String,
    val host: String,
    val usuario: String,
    val origen: String,
    /** Mediana del tiempo de respuesta del panel (ms) y variación entre la más rápida y la más lenta. */
    val respuestaMs: Int?,
    val variacionMs: Int?,
    val pingsOk: Int,
    /** Tiempo hasta recibir datos de un canal en vivo (ms) y velocidad del flujo (Mbps). */
    val arranqueMs: Int?,
    val mbps: Double?,
    val diasRestantes: Int?,
    val conexionesMax: Int?,
    val conexionesActivas: Int?,
    val canales: Int?,
    val peliculas: Int?,
    val series: Int?,
    val puntaje: Int,
    val veredicto: String,
    val avisos: List<String>,
)

/** 100 cuando [v] llega a [mejor], 0 cuando llega a [peor] (sirve en ambos sentidos), lineal en medio. */
private fun tramo(v: Double, mejor: Double, peor: Double): Double =
    (((peor - v) / (peor - mejor)).coerceIn(0.0, 1.0)) * 100

private fun veredictoDe(puntaje: Int) = when {
    puntaje >= 80 -> "Excelente"
    puntaje >= 60 -> "Buena"
    puntaje >= 40 -> "Regular"
    else -> "Floja"
}

fun informeFallido(entrada: EntradaBodega, motivo: String) = InformeCalidad(
    clave = RepositorioBodega.clave(entrada.lista.credenciales),
    host = hostDe(entrada),
    usuario = entrada.lista.credenciales.username,
    origen = entrada.lista.origenChat,
    respuestaMs = null, variacionMs = null, pingsOk = 0, arranqueMs = null, mbps = null,
    diasRestantes = entrada.lista.diasHoy(), conexionesMax = null, conexionesActivas = null,
    canales = entrada.lista.cantidadCanales, peliculas = entrada.lista.cantidadPeliculas, series = entrada.lista.cantidadSeries,
    puntaje = 0, veredicto = "Sin respuesta", avisos = listOf(motivo),
)

/**
 * Mide una lista: 3 consultas al panel (respuesta y estabilidad) y unos segundos de un canal en vivo
 * (arranque y velocidad), y los mezcla con la vigencia y las conexiones permitidas en una nota de 0 a 100.
 * Es una prueba orientativa: un canal en vivo llega a su propio ritmo, así que la velocidad indica si el
 * servidor sostiene el flujo, no el máximo de tu internet.
 */
suspend fun medirCalidad(entrada: EntradaBodega): InformeCalidad {
    val lista = entrada.lista
    val cred = lista.credenciales
    val tiempos = mutableListOf<Long>()
    var info: InfoCuentaXtream? = null
    repeat(3) {
        val t0 = System.nanoTime()
        val i = XtreamClient.obtenerInfoCuenta(cred)
        val ms = (System.nanoTime() - t0) / 1_000_000L
        if (i != null) { tiempos += ms; info = i }
        delay(200)
    }
    val cuenta = info ?: return informeFallido(entrada, "El panel no respondió como activo (caída, vencida o bloqueada).")

    val ordenados = tiempos.sorted()
    val respuesta = ordenados[ordenados.size / 2].toInt()
    val variacion = if (ordenados.size >= 2) (ordenados.last() - ordenados.first()).toInt() else null

    val avisos = mutableListOf<String>()
    val max = cuenta.conexionesMax
    val activas = cuenta.conexionesActivas
    if (max != null && activas != null && activas >= max) avisos += "Conexiones al máximo ($activas/$max): puede que no abra canales ahora."

    var medicion: XtreamClient.MedicionStream? = null
    for (id in XtreamClient.idsCanalesMuestra(cred)) {
        medicion = XtreamClient.medirStream(cred, id)
        if (medicion != null) break
    }
    if (medicion == null) avisos += "No se pudo medir la velocidad del canal (no abrió o no mandó datos)."
    else if (medicion.mbps < 1.0) avisos += "Flujo lento (${"%.1f".format(medicion.mbps)} Mbps): posibles cortes en HD."

    val dias = cuenta.diasRestantes
    if (dias != null && dias < 0) avisos += "La lista ya venció."
    else if (dias != null && dias < 7) avisos += "Vence en $dias día(s)."

    // Componentes (0–100) y su peso; si no hay medición del canal, el peso se reparte entre los demás.
    val partes = mutableListOf<Pair<Double, Double>>() // valor to peso
    partes += tramo(respuesta.toDouble(), 250.0, 2500.0) to 0.20
    medicion?.let {
        val velocidad = (tramo(it.arranqueMs.toDouble(), 800.0, 6000.0) + tramo(it.mbps, 6.0, 1.0)) / 2
        partes += velocidad to 0.35
    }
    val estabilidad = (tiempos.size / 3.0) * 70 + (variacion?.let { tramo(it.toDouble(), 100.0, 1500.0) * 0.3 } ?: 0.0)
    partes += estabilidad to 0.15
    partes += (dias?.let { tramo(it.toDouble(), 60.0, 0.0) } ?: 70.0) to 0.20
    partes += (max?.let { tramo(it.toDouble(), 3.0, 1.0) } ?: 60.0) to 0.10
    val puntaje = (partes.sumOf { it.first * it.second } / partes.sumOf { it.second }).toInt().coerceIn(0, 100)

    return InformeCalidad(
        clave = RepositorioBodega.clave(cred),
        host = hostDe(entrada),
        usuario = cred.username,
        origen = lista.origenChat,
        respuestaMs = respuesta, variacionMs = variacion, pingsOk = tiempos.size,
        arranqueMs = medicion?.arranqueMs?.toInt(), mbps = medicion?.mbps,
        diasRestantes = dias, conexionesMax = max, conexionesActivas = activas,
        canales = lista.cantidadCanales, peliculas = lista.cantidadPeliculas, series = lista.cantidadSeries,
        puntaje = puntaje, veredicto = veredictoDe(puntaje), avisos = avisos,
    )
}
