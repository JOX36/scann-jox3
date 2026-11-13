package com.jox3.scanner.scan.engine

import com.jox3.scanner.scan.data.CredencialesXtream
import com.jox3.scanner.update.NetworkModule
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
private data class RespuestaCuenta(
    val user_info: InfoUsuario? = null,
    val server_info: InfoServidor? = null,
)

/**
 * `exp_date`/`active_cons`/`max_connections`/`is_trial` llegan como texto
 * en unos paneles y como número crudo en otros (el script de Python los
 * trata igual, convirtiéndolos con `str(...)`). Declararlos como [String]
 * directo rompería la decodificación de TODA la respuesta ante el panel
 * que mande el tipo "equivocado" — con un solo campo así, la cuenta
 * quedaría marcada como inactiva sin serlo. [JsonElement] acepta
 * cualquiera de las dos formas sin fallar; [comoTexto] saca el valor ya
 * como texto, venga de donde venga.
 */
@Serializable
private data class InfoUsuario(
    val auth: Int? = null,
    val status: String? = null,
    val exp_date: JsonElement? = null,
    val active_cons: JsonElement? = null,
    val max_connections: JsonElement? = null,
    val is_trial: JsonElement? = null,
)

private fun JsonElement?.comoTexto(): String? = (this as? JsonPrimitive)?.content

@Serializable
private data class InfoServidor(val timezone: String? = null)

@Serializable
private data class Categoria(val category_name: String? = null)

@Serializable
private data class CanalLive(val name: String? = null)

/**
 * Info de cuenta ya interpretada (fechas, conexiones) — equivalente al
 * diccionario que arma `validate_m3u_url` en el script de Python.
 * [diasRestantes] es `null` cuando el panel no da `exp_date` (lista sin
 * fecha de vencimiento, no necesariamente activa para siempre).
 */
data class InfoCuentaXtream(
    val activa: Boolean,
    val diasRestantes: Int?,
    val conexionesActivas: Int?,
    val conexionesMax: Int?,
    val esPrueba: Boolean,
    val zonaHoraria: String?,
)

/**
 * Cliente del panel Xtream Codes (`player_api.php`). Equivalente a las
 * funciones `fetch_categorias_ligeras` / `fetch_muestra_canales` del script
 * de Python, usando el `HttpClient` compartido de [NetworkModule].
 *
 * Cualquier fallo de red o de parseo se traga y devuelve un resultado vacío
 * (lista inactiva / sin categorías) en vez de propagar la excepción: un
 * panel caído no debe tumbar el escaneo completo.
 */
object XtreamClient {

    private val urlPlayerApi = { cred: CredencialesXtream -> "${cred.host}/player_api.php" }

    /**
     * Trae y ya interpreta los datos de cuenta que el script de Python
     * calcula en `validate_m3u_url` (días restantes, conexiones, si es
     * prueba, zona horaria del servidor) — una sola llamada a
     * `player_api.php`, igual que allá. Devuelve `null` si la cuenta no
     * existe o no está activa.
     */
    suspend fun obtenerInfoCuenta(cred: CredencialesXtream): InfoCuentaXtream? {
        return runCatching {
            val respuesta: RespuestaCuenta = NetworkModule.cliente.get(urlPlayerApi(cred)) {
                parameter("username", cred.username)
                parameter("password", cred.password)
            }.body()

            val info = respuesta.user_info ?: return@runCatching null
            if (info.auth != 1 || info.status != "Active") return@runCatching null

            // `exp_date` viene como timestamp Unix en texto; "días
            // restantes" se redondea igual que Python (diferencia de
            // fechas en días, puede dar negativo si ya venció).
            val diasRestantes = info.exp_date.comoTexto()?.toLongOrNull()?.let { segundos ->
                val milisVencimiento = segundos * 1000
                val milisRestantes = milisVencimiento - System.currentTimeMillis()
                Math.floorDiv(milisRestantes, 86_400_000L).toInt()
            }

            InfoCuentaXtream(
                activa = true,
                diasRestantes = diasRestantes,
                conexionesActivas = info.active_cons.comoTexto()?.toIntOrNull(),
                conexionesMax = info.max_connections.comoTexto()?.toIntOrNull(),
                esPrueba = info.is_trial.comoTexto() == "1",
                zonaHoraria = respuesta.server_info?.timezone,
            )
        }.getOrNull()
    }

    /**
     * Nombres de categoría de live/VOD/series, en una sola lista — se piden
     * las tres en paralelo igual que en Python (`asyncio.gather` allá,
     * `coroutineScope { async {} }` acá).
     */
    suspend fun obtenerCategorias(cred: CredencialesXtream): List<String> = coroutineScope {
        val live = async { obtenerCategoria(cred, "get_live_categories") }
        val vod = async { obtenerCategoria(cred, "get_vod_categories") }
        val series = async { obtenerCategoria(cred, "get_series_categories") }
        live.await() + vod.await() + series.await()
    }

    private suspend fun obtenerCategoria(cred: CredencialesXtream, accion: String): List<String> {
        return runCatching {
            val categorias: List<Categoria> = NetworkModule.cliente.get(urlPlayerApi(cred)) {
                parameter("username", cred.username)
                parameter("password", cred.password)
                parameter("action", accion)
            }.body()
            categorias.mapNotNull { it.category_name }
        }.getOrDefault(emptyList())
    }

    /**
     * Nombres de TODOS los canales en vivo del panel — se usan tanto para
     * el respaldo de detección LATAM (con un recorte a una muestra, ver
     * [ValidadorListas]) como para mostrar la cantidad real de canales
     * (`canales` en el diseño de Resultados), sin pedirlo dos veces.
     */
    suspend fun obtenerCanales(cred: CredencialesXtream): List<String> {
        return runCatching {
            val canales: List<CanalLive> = NetworkModule.cliente.get(urlPlayerApi(cred)) {
                parameter("username", cred.username)
                parameter("password", cred.password)
                parameter("action", "get_live_streams")
            }.body()
            canales.mapNotNull { it.name }
        }.getOrDefault(emptyList())
    }
}
