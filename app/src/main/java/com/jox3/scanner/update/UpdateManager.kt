package com.jox3.scanner.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode

/** Repositorio de GitHub donde se publican los releases con el .apk. */
private const val OWNER = "JOX36"
private const val REPO = "scann-jox3"
private const val URL_ULTIMO_RELEASE = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

/** Nombre esperado del asset .apk dentro de cada release. */
private const val NOMBRE_APK_ESPERADO = "app-debug.apk"

/** Resultado de comprobar si hay una actualización disponible. */
data class InfoActualizacion(
    val version: String,
    val notas: String?,
    val urlDescargaApk: String,
    val urlRelease: String,
)

/**
 * Consulta el último release publicado en GitHub y decide si es una
 * actualización respecto a [versionInstalada]. Devuelve `null` si no hay
 * release más nuevo, si el release no trae un .apk adjunto, o si la consulta
 * falla (sin conexión, GitHub caído, límite de rate anónimo agotado, etc.) —
 * en cualquiera de esos casos la app simplemente no muestra el aviso, nunca
 * bloquea el uso normal.
 */
suspend fun buscarActualizacion(
    httpClient: HttpClient,
    versionInstalada: String,
): InfoActualizacion? {
    val respuesta = runCatching {
        httpClient.get(URL_ULTIMO_RELEASE) {
            header("Accept", "application/vnd.github+json")
        }
    }.getOrNull() ?: return null

    if (respuesta.status != HttpStatusCode.OK) return null

    val release = runCatching { respuesta.body<com.jox3.scanner.update.GithubRelease>() }
        .getOrNull() ?: return null

    if (!esVersionMasNueva(versionInstalada, release.tag_name)) return null

    val apk = release.assets.firstOrNull { it.name.equals(NOMBRE_APK_ESPERADO, ignoreCase = true) }
        ?: release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        ?: return null

    return InfoActualizacion(
        version = release.tag_name,
        notas = release.body,
        urlDescargaApk = apk.browser_download_url,
        urlRelease = release.html_url,
    )
}
