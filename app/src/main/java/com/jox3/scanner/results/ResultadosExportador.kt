package com.jox3.scanner.results

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jox3.scanner.scan.engine.ListaValidada
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

    /**
     * Mismo formato decorado con emojis que escribe `print_scan_summary()`
     * en el script de Python (ver `OUTPUT_FILE` ahí) — pedido explícito del
     * usuario: el texto que llegaba a Telegram antes se veía así, y
     * prefiere ese formato al que tenía la app. Un bloque por lista,
     * campo por campo en el mismo orden/emoji que Python; "N/A" o "No
     * especificada" cuando el panel no dio ese dato, igual que allá.
     *
     * La línea de IP es la única que Python no tiene: es la función nueva
     * que pidió el usuario (ver [com.jox3.scanner.scan.engine.XtreamClient.resolverIp]) —
     * se agrega solo cuando se pudo resolver, como "alternativa" al host
     * por dominio, nunca inventada.
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
            val epgLink = "${cred.host}/xmltv.php?username=${cred.username}&password=${cred.password}"
            val conexiones = if (lista.conexionesMax != null) {
                "${lista.conexionesActivas ?: "N/A"}/${lista.conexionesMax}"
            } else "N/A"

            builder.appendLine("🎉 === Resultado M3U por JOX3 === 🎉")
            builder.appendLine("🌐 Host: ${cred.host}")
            if (lista.direccionIp != null) {
                builder.appendLine("🧭 IP del servidor (alternativa): ${lista.direccionIp}")
            }
            val porcentajeLatam = String.format(Locale.US, "%.1f", lista.porcentajeLatino * 100)
            builder.appendLine("🌎 Contenido LATAM: $porcentajeLatam% de las categorías")
            builder.appendLine("👤 Usuario: ${cred.username}")
            builder.appendLine("🔑 Contraseña: ${cred.password}")
            builder.appendLine("✅ Estado: ${if (lista.activa) "Active" else "N/A"}")
            builder.appendLine("🔗 Conexiones: $conexiones")
            builder.appendLine("📺 Canales: ${lista.cantidadCanales ?: "N/A"}")
            builder.appendLine("🎬 Series: ${lista.cantidadSeries ?: "N/A"}")
            builder.appendLine("🎞️ Películas: ${lista.cantidadPeliculas ?: "N/A"}")
            builder.appendLine("📅 Creado: ${lista.fechaCreacion ?: "No especificada"}")
            builder.appendLine("⏰ Vencimiento: ${lista.fechaVencimiento ?: "No especificada"}")
            builder.appendLine("⏳ Días restantes: ${lista.diasRestantes ?: "N/A"}")
            builder.appendLine("📍 Timezone: ${lista.zonaHoraria ?: "N/A"}")
            builder.appendLine("🧪 Prueba: ${if (lista.esPrueba) "Sí" else "No"}")
            builder.appendLine("📡 Formatos: ${lista.formatos ?: "N/A"}")
            builder.appendLine("📺 Enlace M3U: ${cred.urlOriginal}")
            builder.appendLine("📝 Enlace EPG: $epgLink")
            builder.appendLine("🔎 Escaneado: $escaneadoEn")
            builder.appendLine("📂 Encontrada en: ${lista.origenChat}")
            builder.appendLine()
        }

        return builder.toString()
    }

    /** Escribe el archivo en la carpeta externa de la app (ya expuesta por [FileProvider]) y lo devuelve. */
    fun guardarArchivo(context: Context, listas: List<ListaValidada>): File {
        val carpeta = context.getExternalFilesDir(null) ?: context.filesDir
        val archivo = File(carpeta, "Resultados_IPTV.txt")
        archivo.writeText(generarContenido(listas))
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
