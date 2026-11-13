package com.jox3.scanner.scan.engine

import com.jox3.scanner.scan.data.CredencialesXtream
import com.jox3.scanner.update.NetworkModule
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable

@Serializable
private data class RespuestaCuenta(val user_info: InfoUsuario? = null)

@Serializable
private data class InfoUsuario(val auth: Int? = null, val status: String? = null)

@Serializable
private data class Categoria(val category_name: String? = null)

@Serializable
private data class CanalLive(val name: String? = null)

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

    private fun urlBase(cred: CredencialesXtream) =
        "${cred.host}/player_api.php?username=${cred.username}&password=${cred.password}"

    /** `true` si la cuenta existe y está activa (`auth == 1` y `status == "Active"`). */
    suspend fun verificarCuenta(cred: CredencialesXtream): Boolean {
        return runCatching {
            val respuesta: RespuestaCuenta = NetworkModule.cliente.get(urlBase(cred)).body()
            respuesta.user_info?.auth == 1 && respuesta.user_info.status == "Active"
        }.getOrDefault(false)
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
            val categorias: List<Categoria> =
                NetworkModule.cliente.get("${urlBase(cred)}&action=$accion").body()
            categorias.mapNotNull { it.category_name }
        }.getOrDefault(emptyList())
    }

    /**
     * Respaldo cuando las categorías no alcanzan para decidir: una muestra
     * de nombres de canal en vivo (hasta [limite]).
     */
    suspend fun obtenerMuestraCanales(cred: CredencialesXtream, limite: Int = 300): List<String> {
        return runCatching {
            val canales: List<CanalLive> =
                NetworkModule.cliente.get("${urlBase(cred)}&action=get_live_streams").body()
            canales.mapNotNull { it.name }.take(limite)
        }.getOrDefault(emptyList())
    }
}
