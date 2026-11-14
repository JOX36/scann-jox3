package com.jox3.scanner.scan.engine

import com.jox3.scanner.scan.data.CredencialesXtream
import com.jox3.scanner.update.NetworkModule
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.net.InetAddress
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
    val created_at: JsonElement? = null,
    val active_cons: JsonElement? = null,
    val max_connections: JsonElement? = null,
    val is_trial: JsonElement? = null,
    val allowed_output_formats: List<String>? = null,
)

/** Mismo formato que usa `print_scan_summary()` en Python (`strftime('%Y-%m-%d %H:%M')`, en UTC). */
private val formateadorFecha = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

/** `exp_date`/`created_at` llegan como timestamp Unix (segundos) en texto; `null` si el panel no lo da. */
private fun JsonElement?.comoFechaUtc(): String? =
    this.comoTexto()?.toLongOrNull()?.let { segundos -> formateadorFecha.format(Instant.ofEpochSecond(segundos)) }

private fun JsonElement?.comoTexto(): String? = (this as? JsonPrimitive)?.content

@Serializable
private data class InfoServidor(val timezone: String? = null)

@Serializable
private data class Categoria(val category_name: String? = null)

@Serializable
private data class CanalLive(val name: String? = null)

/** `get_vod_streams`/`get_series` traen más campos, pero acá solo hace falta el nombre. */
@Serializable
private data class StreamConNombre(val name: String? = null)

@Serializable
private data class RespuestaGeoIp(
    val city: String? = null,
    val country_name: String? = null,
    val country: String? = null,
    val org: String? = null,
    val error: Boolean? = null,
)

/** Geolocalización aproximada de una IP — ver [XtreamClient.geolocalizarIp]. */
data class InfoGeoIp(val ciudad: String?, val pais: String?, val codigoPais: String?, val isp: String?)

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
    val fechaVencimiento: String? = null,
    val fechaCreacion: String? = null,
    val formatos: String? = null,
    /** Texto crudo del panel ("Active"), tal cual lo manda — para mostrarlo igual que el formato de referencia del usuario. */
    val estadoTexto: String? = null,
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
                fechaVencimiento = info.exp_date.comoFechaUtc(),
                fechaCreacion = info.created_at.comoFechaUtc(),
                formatos = info.allowed_output_formats?.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { it.lowercase() },
                estadoTexto = info.status,
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

    /**
     * Cantidad de elementos de `get_vod_streams`/`get_series` — igual que
     * `fetch_stream_count()` en Python: no interesa el contenido de cada
     * elemento, solo cuántos hay, así que se decodifica como lista de
     * [JsonElement] genérico para no depender de la forma exacta que use
     * cada panel.
     */
    private suspend fun contarStream(cred: CredencialesXtream, accion: String): Int? {
        return runCatching {
            val elementos: List<JsonElement> = NetworkModule.cliente.get(urlPlayerApi(cred)) {
                parameter("username", cred.username)
                parameter("password", cred.password)
                parameter("action", accion)
            }.body()
            elementos.size
        }.getOrNull()
    }

    /** Cantidad de películas (VOD) del panel — equivalente a `movies` en Python. */
    suspend fun obtenerCantidadPeliculas(cred: CredencialesXtream): Int? = contarStream(cred, "get_vod_streams")

    /** Cantidad de series del panel — equivalente a `series` en Python. */
    suspend fun obtenerCantidadSeries(cred: CredencialesXtream): Int? = contarStream(cred, "get_series")

    private suspend fun obtenerNombresStream(cred: CredencialesXtream, accion: String): List<String> {
        return runCatching {
            val elementos: List<StreamConNombre> = NetworkModule.cliente.get(urlPlayerApi(cred)) {
                parameter("username", cred.username)
                parameter("password", cred.password)
                parameter("action", accion)
            }.body()
            elementos.mapNotNull { it.name }
        }.getOrDefault(emptyList())
    }

    /**
     * Nombres completos de películas/series del panel — a diferencia de
     * [obtenerCantidadPeliculas]/[obtenerCantidadSeries] (que solo cuentan),
     * estas sí traen la lista completa. Pedido explícito del usuario: poder
     * ver el detalle de una lista (qué tiene) sin ir a Telegram. Se llaman
     * solo cuando el usuario abre el detalle de ESA lista puntual — pedirlas
     * para las cientos de listas de un escaneo masivo sería mucho más
     * tráfico del necesario para algo que la mayoría de las veces no se va
     * a mirar.
     */
    suspend fun obtenerNombresPeliculas(cred: CredencialesXtream): List<String> = obtenerNombresStream(cred, "get_vod_streams")
    suspend fun obtenerNombresSeries(cred: CredencialesXtream): List<String> = obtenerNombresStream(cred, "get_series")

    // Con muchos enlaces validándose a la vez (hasta MAX_VALIDACIONES_EN_PARALELO
    // en ValidadorListas), cada uno ya dispara varias peticiones de red en
    // paralelo (categorías, canales, películas, series, IP); bajo esa carga
    // la resolución DNS puede tardar en arrancar no porque el DNS esté
    // lento, sino porque el hilo/conexión que la hace está ocupado con
    // otra cosa. 4s alcanzaba a cortarla antes de que arrancara de verdad.
    private const val TIMEOUT_DNS_MS = 12_000L

    /**
     * Resuelve la IP real del servidor detrás del host de la lista (pedido
     * explícito del usuario: "quisiera saber si también se puede incluir la
     * dirección IP como alternativa"). El script de Python no hace esto —
     * es una funcionalidad nueva, solo para la app.
     *
     * Devuelve `null` si [host] YA es una dirección IP literal (no hay
     * nada que "resolver": la IP ya es la que se está usando, mostrarla de
     * nuevo como "alternativa" no agrega información), si no se pudo
     * resolver (dominio caído, DNS lento/caído) o si tardó más de
     * [TIMEOUT_DNS_MS].
     */
    suspend fun resolverIp(host: String): String? {
        val sinEsquema = host.substringAfter("://").substringBefore("/").substringBefore(":")
        if (sinEsquema.isBlank() || esDireccionIpLiteral(sinEsquema)) return null

        return withTimeoutOrNull(TIMEOUT_DNS_MS) {
            withContext(Dispatchers.IO) {
                try {
                    InetAddress.getByName(sinEsquema).hostAddress
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private val patronIpv4 = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")

    /**
     * IPv4 ("192.168.0.1") literal — ya es la IP, no un nombre de dominio.
     * (El puerto, si lo hay, ya se recortó antes de llamar a esta función;
     * IPv6 no se maneja — ni acá ni en [extraerCredencialesXtream] — por
     * ser un caso prácticamente inexistente en paneles Xtream.)
     */
    private fun esDireccionIpLiteral(host: String): Boolean = patronIpv4.matches(host)

    /**
     * La IP a geolocalizar/mostrar para esta lista: la ya resuelta por DNS
     * si el host era un dominio ([direccionIpResuelta], de
     * [ValidadorListas]), o el host mismo si YA era una IP literal. `null`
     * solo si no hay ninguna IP disponible (dominio que no se pudo resolver).
     */
    fun ipEfectiva(cred: CredencialesXtream, direccionIpResuelta: String?): String? {
        if (direccionIpResuelta != null) return direccionIpResuelta
        val sinEsquema = cred.host.substringAfter("://").substringBefore("/").substringBefore(":")
        return sinEsquema.takeIf { esDireccionIpLiteral(it) }
    }

    /**
     * El mismo enlace M3U pero con el host cambiado por [ip] — pedido
     * explícito del usuario ("quisiera... incluir la dirección IP como
     * alternativa"): una lista que se encontró por dominio puede a veces
     * reproducirse igual pegándole directo a la IP del servidor, sin pasar
     * por el DNS. Pura edición de texto, sin red: cambia solo el
     * host/puerto al principio de [CredencialesXtream.urlOriginal] y deja
     * el resto (ruta, usuario, contraseña) intacto.
     */
    fun enlacePorIp(cred: CredencialesXtream, ip: String): String {
        val sinEsquema = cred.host.substringAfter("://")
        val puerto = sinEsquema.substringAfter(":", "")
        val hostConIp = if (puerto.isNotEmpty()) "$ip:$puerto" else ip
        val prefijoNuevo = "${cred.host.substringBefore("://")}://$hostConIp"
        return cred.urlOriginal.replaceFirst(cred.host, prefijoNuevo)
    }

    /**
     * Geolocalización aproximada del servidor por IP (ciudad, país, ISP) —
     * pedido explícito del usuario: "no sé cuál es el servidor real".
     * Usa ipapi.co (gratis, HTTPS, sin API key) — solo para la IP de la
     * lista que el usuario abrió, nunca durante el escaneo masivo, para no
     * sumarle una consulta externa más a cada uno de cientos de enlaces.
     * Se traga cualquier error (límite de la API gratis, IP no encontrada,
     * sin red) y devuelve `null` en vez de romper el detalle completo por
     * un dato que es "extra", no esencial.
     */
    suspend fun geolocalizarIp(ip: String): InfoGeoIp? {
        return runCatching {
            val respuesta: RespuestaGeoIp = NetworkModule.cliente.get("https://ipapi.co/$ip/json/").body()
            if (respuesta.error == true) return@runCatching null
            InfoGeoIp(
                ciudad = respuesta.city,
                pais = respuesta.country_name,
                codigoPais = respuesta.country,
                isp = respuesta.org,
            )
        }.getOrNull()
    }

    /**
     * Código ISO-3166 de 2 letras ("CL") → su emoji de bandera, con el
     * truco estándar de "regional indicator symbols" (cada letra A-Z tiene
     * un símbolo Unicode paralelo que, de a dos, el sistema dibuja como
     * bandera) — funciona para cualquier país sin necesitar una tabla fija.
     */
    fun banderaDesdeCodigoPais(codigo: String?): String {
        if (codigo == null || codigo.length != 2) return ""
        val base = 0x1F1E6
        val letras = codigo.uppercase()
        if (letras.any { it !in 'A'..'Z' }) return ""
        return letras.map { String(Character.toChars(base + (it - 'A'))) }.joinToString("")
    }

    /**
     * Mapa de zona horaria → código de país, a mano y NO exhaustivo (solo
     * Latinoamérica y un puñado de zonas comunes): IANA no guarda el país
     * de cada zona, así que esto es lo más parecido a una bandera para el
     * campo "Timezone" sin inventar una que no se pueda sostener. Si la
     * zona no está en esta lista, [banderaDesdeTimezone] no muestra nada
     * en vez de arriesgar una bandera equivocada.
     */
    private val PAIS_POR_TIMEZONE: Map<String, String> = mapOf(
        "America/Bogota" to "CO",
        "America/Mexico_City" to "MX",
        "America/Santiago" to "CL",
        "America/Lima" to "PE",
        "America/Argentina/Buenos_Aires" to "AR",
        "America/Caracas" to "VE",
        "America/La_Paz" to "BO",
        "America/Guatemala" to "GT",
        "America/Tegucigalpa" to "HN",
        "America/Managua" to "NI",
        "America/Panama" to "PA",
        "America/Santo_Domingo" to "DO",
        "America/Asuncion" to "PY",
        "America/Montevideo" to "UY",
        "America/El_Salvador" to "SV",
        "America/Costa_Rica" to "CR",
        "America/Puerto_Rico" to "PR",
        "America/New_York" to "US",
        "America/Chicago" to "US",
        "America/Denver" to "US",
        "America/Los_Angeles" to "US",
        "America/Toronto" to "CA",
        "America/Sao_Paulo" to "BR",
        "Europe/Madrid" to "ES",
        "Europe/Lisbon" to "PT",
        "Europe/London" to "GB",
    )

    fun banderaDesdeTimezone(zona: String?): String =
        banderaDesdeCodigoPais(zona?.let { PAIS_POR_TIMEZONE[it] })
}
