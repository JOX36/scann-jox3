package com.jox3.scanner.scan.engine

import com.jox3.scanner.scan.data.CredencialesXtream
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Resultado final de validar y clasificar un enlace encontrado. Los campos
 * de `diasRestantes` en adelante son el equivalente al diccionario que
 * arma `validate_m3u_url()` en el script de Python — `null` cuando el
 * panel no dio ese dato (no cuando la lista está inactiva: una lista
 * inactiva simplemente no tiene ninguno de estos datos).
 */
data class ListaValidada(
    val credenciales: CredencialesXtream,
    val activa: Boolean,
    val esLatino: Boolean,
    val porcentajeLatino: Double,
    val origenChat: String,
    val diasRestantes: Int? = null,
    val conexionesActivas: Int? = null,
    val conexionesMax: Int? = null,
    val esPrueba: Boolean = false,
    val zonaHoraria: String? = null,
    val cantidadCanales: Int? = null,
    val cantidadPeliculas: Int? = null,
    val cantidadSeries: Int? = null,
    val fechaVencimiento: String? = null,
    val fechaCreacion: String? = null,
    val formatos: String? = null,
    /** IP real del servidor cuando [credenciales].host es un dominio — ver [XtreamClient.resolverIp]. */
    val direccionIp: String? = null,
)

/** Muestra de nombres de canal usada para la detección LATAM — igual al `limite` que usaba Python. */
private const val MUESTRA_CANALES_PARA_LATINO = 300

/**
 * Verifica si la lista está activa y, si lo está, la enriquece con la
 * info de cuenta (días restantes, conexiones...) y la detección de
 * contenido latino (categorías primero; nombres de canal como respaldo si
 * las categorías no dan señal suficiente). Equivalente a
 * `validate_m3u_url()` + `enriquecer_con_categorias()` en el script de
 * Python.
 */
suspend fun validarYEnriquecer(cred: CredencialesXtream, origenChat: String): ListaValidada {
    val info = XtreamClient.obtenerInfoCuenta(cred)
    if (info == null) {
        return ListaValidada(cred, activa = false, esLatino = false, porcentajeLatino = 0.0, origenChat = origenChat)
    }

    // En paralelo (como ya hacía `obtenerCategorias` internamente): cada
    // llamada de red extra que se agregó acá (películas, series, IP) sería
    // una espera secuencial más por cada enlace si se hiciera una por una,
    // y con hasta 50 enlaces validándose a la vez eso suma rápido.
    lateinit var categorias: List<String>
    lateinit var canales: List<String>
    var peliculas: Int?
    var series: Int?
    var ip: String?
    coroutineScope {
        val categoriasAsync = async { XtreamClient.obtenerCategorias(cred) }
        val canalesAsync = async { XtreamClient.obtenerCanales(cred) }
        val peliculasAsync = async { XtreamClient.obtenerCantidadPeliculas(cred) }
        val seriesAsync = async { XtreamClient.obtenerCantidadSeries(cred) }
        val ipAsync = async { XtreamClient.resolverIp(cred.host) }
        categorias = categoriasAsync.await()
        canales = canalesAsync.await()
        peliculas = peliculasAsync.await()
        series = seriesAsync.await()
        ip = ipAsync.await()
    }
    var (esLatino, porcentaje) = esContenidoLatino(categorias)

    if (!esLatino && canales.isNotEmpty()) {
        val (esLatinoConCanales, porcentajeConCanales) = esContenidoLatino(categorias + canales.take(MUESTRA_CANALES_PARA_LATINO))
        esLatino = esLatinoConCanales
        porcentaje = porcentajeConCanales
    }

    return ListaValidada(
        credenciales = cred,
        activa = true,
        esLatino = esLatino,
        porcentajeLatino = porcentaje,
        origenChat = origenChat,
        diasRestantes = info.diasRestantes,
        conexionesActivas = info.conexionesActivas,
        conexionesMax = info.conexionesMax,
        esPrueba = info.esPrueba,
        zonaHoraria = info.zonaHoraria,
        cantidadCanales = canales.size.takeIf { it > 0 },
        cantidadPeliculas = peliculas,
        cantidadSeries = series,
        fechaVencimiento = info.fechaVencimiento,
        fechaCreacion = info.fechaCreacion,
        formatos = info.formatos,
        direccionIp = ip,
    )
}
