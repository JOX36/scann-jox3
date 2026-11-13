package com.jox3.scanner.update

import kotlinx.serialization.Serializable

/**
 * Representa un elemento de la respuesta de la API pública de GitHub con la
 * lista de releases de este repositorio:
 *   GET https://api.github.com/repos/{owner}/{repo}/releases
 *
 * Solo se mapean los campos que realmente usamos; el resto de los que
 * devuelve GitHub se ignoran automáticamente.
 */
@Serializable
data class GithubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val html_url: String,
    val assets: List<GithubAsset> = emptyList(),
)

/**
 * Un archivo adjunto al release (en nuestro caso, el .apk compilado que se
 * sube manualmente o vía CI a cada release de GitHub).
 */
@Serializable
data class GithubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long = 0L,
)
