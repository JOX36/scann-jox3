package com.jox3.scanner.results

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jox3.scanner.scan.data.CredencialesXtream
import com.jox3.scanner.scan.engine.DetalleLista
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.scan.engine.XtreamClient
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Genera el archivo de resultados y lo comparte (botón "Guardar .txt" /
 * "Enviar a Telegram" de Resultados) — contenido equivalente al
 * `OUTPUT_FILE` que escribe `print_scan_summary()` en el script de
 * Python, con los mismos datos por lista (vencimiento, conexiones, etc).
 */
object ResultadosExportador {

    private val formateadorEscaneo = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

    /** Mismo umbral que usa [com.jox3.scanner.ui.results.ResultsScreen] para "por vencer". */
    private const val UMBRAL_POR_VENCER = 15

    /** 🟢 activa / 🟡 por vencer / 🔴 vencida — igual al ejemplo que pasó el usuario (ya no el ✅ fijo que tenía antes). */
    private fun emojiEstado(dias: Int?): String = when {
        dias == null -> "🟢"
        dias < 0 -> "🔴"
        dias < UMBRAL_POR_VENCER -> "🟡"
        else -> "🟢"
    }

    private fun epgLink(cred: CredencialesXtream): String =
        "${cred.host}/xmltv.php?username=${cred.username}&password=${cred.password}"

    private fun conexionesTexto(lista: ListaValidada): String =
        if (lista.conexionesMax != null) "${lista.conexionesActivas ?: "N/A"}/${lista.conexionesMax}" else "N/A"

    private fun porcentajeLatamTexto(lista: ListaValidada): String =
        String.format(Locale.US, "%.1f", lista.porcentajeLatino * 100)

    /**
     * Mismo formato decorado con emojis del ejemplo que pasó el usuario
     * (el que ya le llegaba antes a Telegram) — bloque por lista, mismo
     * orden/emojis. Acá van solo los datos que YA se calculan durante el
     * escaneo masivo (sin consultas extra por lista): geolocalización del
     * servidor y los nombres completos de canales/series/películas quedan
     * para [generarContenidoDetalle], que se pide aparte, solo para la
     * lista que el usuario realmente abre — pedirlos para cientos de
     * listas de una sola vez sería mucho tráfico de más para datos que la
     * mayoría de las veces no se van a mirar.
     */
    fun generarContenido(listas: List<ListaValidada>): String {
        val activas = listas.filter { it.activa }
        val latam = activas.filter { it.esLatino }
        val builder = StringBuilder()
        builder.appendLine("IPTV Super Scanner — Resultados")
        builder.appendLine("${activas.size} activas de ${listas.size} validadas · ${latam.size} de LATAM")
        builder.appendLine("=".repeat(60))
        builder.appendLine()

        val escaneadoEn = formateadorEscaneo.format(Instant.now())

        activas.forEach { lista ->
            val cred = lista.credenciales
            val banderaTz = XtreamClient.banderaDesdeTimezone(lista.zonaHoraria)

            builder.appendLine("🎉 === Resultado M3U por JOX3 === 🎉")
            builder.appendLine()
            builder.appendLine("🌐 Host: ${cred.host}")
            if (lista.direccionIp != null) {
                builder.appendLine("📌 M3U por IP: ${XtreamClient.enlacePorIp(cred, lista.direccionIp)}")
            }
            builder.appendLine("👤 Usuario: ${cred.username}")
            builder.appendLine("🔑 Contraseña: ${cred.password}")
            builder.appendLine("${emojiEstado(lista.diasRestantes)} Estado: ${lista.estadoTexto ?: "Active"}")
            builder.appendLine("🔗 Conexiones: ${conexionesTexto(lista)}")
            builder.appendLine("📅 Creado: ${lista.fechaCreacion ?: "No especificada"}")
            builder.appendLine("🗓️ Vencimiento: ${lista.fechaVencimiento ?: "No especificada"}")
            builder.appendLine("⌛ Días restantes: ${lista.diasRestantes ?: "N/A"} ${emojiEstado(lista.diasRestantes)}")
            builder.appendLine("🕒 Timezone: ${lista.zonaHoraria ?: "N/A"}${if (banderaTz.isNotEmpty()) " $banderaTz" else ""}")
            builder.appendLine("🧪 Prueba: ${if (lista.esPrueba) "Sí" else "No"}")
            builder.appendLine("🎞️ Formatos: ${lista.formatos ?: "N/A"}")
            builder.appendLine("🌎 Contenido LATAM: ${porcentajeLatamTexto(lista)}% de las categorías")
            builder.appendLine("📺 Canales: ${lista.cantidadCanales ?: "N/A"}")
            builder.appendLine("🎬 Series: ${lista.cantidadSeries ?: "N/A"}")
            builder.appendLine("🎥 Películas: ${lista.cantidadPeliculas ?: "N/A"}")
            builder.appendLine("📺 Enlace M3U: ${cred.urlOriginal}")
            builder.appendLine("📜 Enlace EPG: ${epgLink(cred)}")
            builder.appendLine("📂 Encontrada en: ${lista.origenChat}")
            builder.appendLine("📅 Escaneado: $escaneadoEn")
            builder.appendLine()
        }

        return builder.toString()
    }

    /**
     * El bloque completo "de detalle" de una sola lista — pedido explícito
     * del usuario: poder tocar un resultado y ver su información extra sin
     * ir a Telegram (geolocalización del servidor, listas completas de
     * canales/series/películas por nombre, enlace M3U directo por IP), y
     * poder guardarla/enviarla aparte, sin que eso mande también el resto
     * de las listas. Mismos campos que [generarContenido] más lo que trae
     * [detalle] (ver [com.jox3.scanner.scan.engine.consultarDetalleLista]).
     */
    fun generarContenidoDetalle(lista: ListaValidada, detalle: DetalleLista): String {
        val cred = lista.credenciales
        val banderaTz = XtreamClient.banderaDesdeTimezone(lista.zonaHoraria)
        val builder = StringBuilder()
        val escaneadoEn = formateadorEscaneo.format(Instant.now())

        builder.appendLine("🎉 === Resultado M3U por JOX3 === 🎉")
        builder.appendLine()
        builder.appendLine("🌐 Host: ${cred.host}")
        builder.appendLine("👤 Usuario: ${cred.username}")
        builder.appendLine("🔑 Contraseña: ${cred.password}")
        builder.appendLine("${emojiEstado(lista.diasRestantes)} Estado: ${lista.estadoTexto ?: "Active"}")
        builder.appendLine("🔗 Conexiones: ${conexionesTexto(lista)}")
        builder.appendLine("📅 Creado: ${lista.fechaCreacion ?: "No especificada"}")
        builder.appendLine("🗓️ Vencimiento: ${lista.fechaVencimiento ?: "No especificada"}")
        builder.appendLine("⌛ Días restantes: ${lista.diasRestantes ?: "N/A"} ${emojiEstado(lista.diasRestantes)}")
        builder.appendLine("🕒 Timezone: ${lista.zonaHoraria ?: "N/A"}${if (banderaTz.isNotEmpty()) " $banderaTz" else ""}")
        builder.appendLine("🧪 Prueba: ${if (lista.esPrueba) "Sí" else "No"}")
        builder.appendLine("🎞️ Formatos: ${lista.formatos ?: "N/A"}")
        if (detalle.geoIp != null) {
            val bandera = XtreamClient.banderaDesdeCodigoPais(detalle.geoIp.codigoPais)
            val lugar = listOfNotNull(detalle.geoIp.ciudad, detalle.geoIp.pais).joinToString(", ")
            val isp = detalle.geoIp.isp?.let { " | ISP: $it" } ?: ""
            builder.appendLine("📍 Servidor: $bandera $lugar$isp")
        }
        builder.appendLine("📺 Enlace M3U: ${cred.urlOriginal}")
        builder.appendLine("📜 Enlace EPG: ${epgLink(cred)}")
        if (detalle.enlaceM3uPorIp != null) {
            builder.appendLine("📌 M3U por IP: ${detalle.enlaceM3uPorIp}")
        }
        builder.appendLine()
        builder.appendLine("📡 TV en vivo (${detalle.canales.size}): ${detalle.canales.joinToString(", ")}")
        builder.appendLine()
        builder.appendLine("🎬 Series (${detalle.series.size}): ${detalle.series.joinToString(", ")}")
        builder.appendLine()
        builder.appendLine("🎥 Películas (${detalle.peliculas.size}): ${detalle.peliculas.joinToString(", ")}")
        builder.appendLine()
        builder.appendLine("📂 Encontrada en: ${lista.origenChat}")
        builder.appendLine("📅 Escaneado: $escaneadoEn")

        return builder.toString()
    }

    /** Escribe el archivo en la carpeta externa de la app (ya expuesta por [FileProvider]) y lo devuelve. */
    fun guardarArchivo(context: Context, listas: List<ListaValidada>): File {
        val carpeta = context.getExternalFilesDir(null) ?: context.filesDir
        val archivo = File(carpeta, "Resultados_IPTV.txt")
        archivo.writeText(generarContenido(listas))
        return archivo
    }

    /** Igual que [guardarArchivo] pero para una sola lista con su detalle (ver [generarContenidoDetalle]). */
    fun guardarArchivoDetalle(context: Context, lista: ListaValidada, detalle: DetalleLista): File {
        val carpeta = context.getExternalFilesDir(null) ?: context.filesDir
        val nombreHost = lista.credenciales.host
            .substringAfter("://")
            .replace(Regex("[^A-Za-z0-9.-]"), "_")
        val archivo = File(carpeta, "Detalle_$nombreHost.txt")
        archivo.writeText(generarContenidoDetalle(lista, detalle))
        return archivo
    }

    /**
     * Arma un .txt con el panel de errores, el diagnóstico por chat y el
     * registro paso a paso completo (ver [com.jox3.scanner.scan.EventoEscaneo.LogPaso])
     * tal cual se ven en la app — pedido explícito del usuario para poder
     * mandarlos sin depender de una captura de pantalla que recorta el
     * panel colapsable.
     */
    fun generarContenidoRegistro(
        errores: List<String>,
        diagnostico: List<String>,
        registro: List<String>,
    ): String {
        val builder = StringBuilder()
        builder.appendLine("IPTV Super Scanner — Registro del escaneo")
        builder.appendLine("=".repeat(60))

        if (errores.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("Chats con error (${errores.size}):")
            errores.forEach { builder.appendLine("  • $it") }
        }

        if (diagnostico.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("Diagnóstico por chat:")
            diagnostico.forEach { builder.appendLine("  • $it") }
        }

        if (registro.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("Registro paso a paso (${registro.size} paso(s)):")
            registro.forEach { builder.appendLine("  $it") }
        }

        if (errores.isEmpty() && diagnostico.isEmpty() && registro.isEmpty()) {
            builder.appendLine()
            builder.appendLine("(Sin datos: el escaneo no llegó a leer ningún chat ni registrar ningún paso.)")
        }

        return builder.toString()
    }

    /** Igual que [guardarArchivo] pero para el registro/diagnóstico (ver [generarContenidoRegistro]). */
    fun guardarArchivoRegistro(
        context: Context,
        errores: List<String>,
        diagnostico: List<String>,
        registro: List<String>,
    ): File {
        val carpeta = context.getExternalFilesDir(null) ?: context.filesDir
        val archivo = File(carpeta, "Registro_Escaneo.txt")
        archivo.writeText(generarContenidoRegistro(errores, diagnostico, registro))
        return archivo
    }

    /** Abre el selector de apps del sistema para guardar/compartir el .txt ya escrito. */
    fun compartirArchivo(context: Context, archivo: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Guardar resultados"))
    }
}
