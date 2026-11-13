package com.jox3.scanner.results

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jox3.scanner.scan.engine.ListaValidada
import java.io.File

/**
 * Genera el archivo de resultados y lo comparte (botón "Guardar .txt" /
 * "Enviar a Telegram" de Resultados) — contenido equivalente al
 * `OUTPUT_FILE` que escribe `print_scan_summary()` en el script de
 * Python, con los mismos datos por lista (vencimiento, conexiones, etc).
 */
object ResultadosExportador {

    fun generarContenido(listas: List<ListaValidada>): String {
        val activas = listas.filter { it.activa }
        val latam = activas.filter { it.esLatino }
        val builder = StringBuilder()
        builder.appendLine("IPTV Super Scanner — Resultados")
        builder.appendLine("${activas.size} activas de ${listas.size} validadas · ${latam.size} de LATAM")
        builder.appendLine("=".repeat(60))

        activas.forEach { lista ->
            builder.appendLine()
            builder.appendLine(lista.credenciales.urlOriginal)
            builder.appendLine("  Host: ${lista.credenciales.host}")
            builder.appendLine("  Usuario: ${lista.credenciales.username}")
            builder.appendLine("  Contraseña: ${lista.credenciales.password}")
            val vencimiento = when {
                lista.diasRestantes == null -> "No especificada"
                lista.diasRestantes < 0 -> "Vencida"
                else -> "${lista.diasRestantes} días restantes"
            }
            builder.appendLine("  Vencimiento: $vencimiento")
            if (lista.conexionesMax != null) {
                builder.appendLine("  Conexiones: ${lista.conexionesActivas ?: 0}/${lista.conexionesMax}")
            }
            if (lista.cantidadCanales != null) {
                builder.appendLine("  Canales: ${lista.cantidadCanales}")
            }
            if (!lista.zonaHoraria.isNullOrBlank()) {
                builder.appendLine("  Zona horaria del servidor: ${lista.zonaHoraria}")
            }
            builder.appendLine("  Contenido LATAM: ${if (lista.esLatino) "Sí (${(lista.porcentajeLatino * 100).toInt()}%)" else "No"}")
            builder.appendLine("  Encontrada en: ${lista.origenChat}")
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
