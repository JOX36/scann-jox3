package com.jox3.scanner.scan.bodega

import com.jox3.scanner.scan.data.CredencialesXtream
import com.jox3.scanner.scan.engine.ListaValidada
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Una lista guardada en la Bodega, con cuándo se vio por primera y última vez. */
@Serializable
data class EntradaBodega(
    val lista: ListaValidada,
    val vistaPrimera: Long,
    val vistaUltima: Long,
    /** `false` si en la última revalidación el panel ya no respondió como activo. */
    val vigente: Boolean = true,
)

private val formatoFecha = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * Días de vigencia calculados HOY a partir de la fecha de vencimiento guardada (así siempre están al
 * día sin volver a escanear). Sin fecha usa lo que se guardó; `null` = sin vencimiento conocido.
 */
fun ListaValidada.diasHoy(): Int? {
    val fecha = fechaVencimiento
    if (fecha != null) {
        try {
            val milis = LocalDateTime.parse(fecha, formatoFecha).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return Math.floorDiv(milis - System.currentTimeMillis(), 86_400_000L).toInt()
        } catch (e: Exception) {
            // formato inesperado: se usa el valor guardado
        }
    }
    return diasRestantes
}

/** Host sin esquema y en minúsculas: sirve para agrupar y ordenar alfabéticamente. */
fun hostDe(entrada: EntradaBodega): String =
    entrada.lista.credenciales.host.substringAfter("://").trimEnd('/').lowercase()

/** Orden de la Bodega: host A→Z y, dentro de un mismo host, de más a menos días de vigencia. */
fun ordenarBodega(entradas: List<EntradaBodega>): List<EntradaBodega> =
    entradas.sortedWith(
        compareBy<EntradaBodega> { hostDe(it) }
            .thenByDescending { it.lista.diasHoy() ?: Int.MIN_VALUE }
            .thenBy { it.lista.credenciales.username.lowercase() },
    )

/**
 * Almacén en un archivo JSON privado de la app (`bodega.json`; la app no se respalda en la nube,
 * ver `allowBackup="false"`). Unas miles de listas ocupan ~1 MB.
 */
object RepositorioBodega {
    private const val ARCHIVO = "bodega.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val candado = Any()

    /** Misma lista = mismo servidor + mismo usuario. */
    fun clave(c: CredencialesXtream): String =
        c.host.substringAfter("://").trimEnd('/').lowercase() + "|" + c.username

    fun cargar(carpeta: File): List<EntradaBodega> = synchronized(candado) { leer(carpeta) }

    /** Agrega o actualiza (sin duplicar) y devuelve la bodega completa. Solo se guardan listas activas. */
    fun agregar(carpeta: File, nuevas: List<ListaValidada>): List<EntradaBodega> = synchronized(candado) {
        val activas = nuevas.filter { it.activa }
        val actuales = leer(carpeta)
        if (activas.isEmpty()) return actuales
        val ahora = System.currentTimeMillis()
        val mapa = LinkedHashMap<String, EntradaBodega>()
        actuales.forEach { mapa[clave(it.lista.credenciales)] = it }
        for (l in activas) {
            val k = clave(l.credenciales)
            val previa = mapa[k]
            mapa[k] = EntradaBodega(l, previa?.vistaPrimera ?: ahora, ahora, vigente = true)
        }
        val resultado = mapa.values.toList()
        escribir(carpeta, resultado)
        resultado
    }

    /** Aplica el resultado de una revalidación: activa → se actualiza; ya no activa → se marca como caída. */
    fun aplicarRevalidacion(carpeta: File, resultados: List<Pair<EntradaBodega, ListaValidada?>>): List<EntradaBodega> =
        synchronized(candado) {
            val ahora = System.currentTimeMillis()
            val mapa = LinkedHashMap<String, EntradaBodega>()
            leer(carpeta).forEach { mapa[clave(it.lista.credenciales)] = it }
            for ((entrada, nueva) in resultados) {
                val k = clave(entrada.lista.credenciales)
                val actual = mapa[k] ?: continue
                mapa[k] = when {
                    nueva == null -> actual // sin respuesta a tiempo: no se saca conclusión
                    nueva.activa -> {
                        // Si la consulta de contenido falló esta vez (0 % sin señal alguna), no se pierde lo que ya se sabía.
                        val conservar = actual.lista.esLatino && !nueva.esLatino && nueva.porcentajeLatino == 0.0
                        val fusion = if (conservar) {
                            nueva.copy(esLatino = true, porcentajeLatino = actual.lista.porcentajeLatino, esEspana = false)
                        } else nueva
                        actual.copy(lista = fusion.copy(origenChat = actual.lista.origenChat), vistaUltima = ahora, vigente = true)
                    }
                    else -> actual.copy(vigente = false)
                }
            }
            val resultado = mapa.values.toList()
            escribir(carpeta, resultado)
            resultado
        }

    fun eliminar(carpeta: File, claves: Set<String>): List<EntradaBodega> = synchronized(candado) {
        val resultado = leer(carpeta).filterNot { clave(it.lista.credenciales) in claves }
        escribir(carpeta, resultado)
        resultado
    }

    private fun leer(carpeta: File): List<EntradaBodega> {
        val f = File(carpeta, ARCHIVO)
        if (!f.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<EntradaBodega>>(f.readText()) }.getOrDefault(emptyList())
    }

    private fun escribir(carpeta: File, entradas: List<EntradaBodega>) {
        val tmp = File(carpeta, "$ARCHIVO.tmp")
        tmp.writeText(json.encodeToString(entradas))
        tmp.renameTo(File(carpeta, ARCHIVO))
    }
}
