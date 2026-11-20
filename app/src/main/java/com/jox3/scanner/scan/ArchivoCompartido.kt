package com.jox3.scanner.scan

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream

/**
 * Archivo o texto que otra app comparte con JOX3 Radar ("Compartir → JOX3 Radar" desde un chat o
 * "Abrir con" desde el gestor de archivos). La Activity lo deja aquí y la pantalla principal lo
 * toma cuando ya hay sesión y arranca el escaneo.
 */
object ArchivoCompartido {
    class Pendiente(val uri: Uri?, val texto: String?, val nombre: String?)

    var pendiente by mutableStateOf<Pendiente?>(null)

    private const val MAX_BYTES = 8 * 1024 * 1024

    /** Si el intent trae algo para escanear lo deja pendiente y devuelve true. */
    fun tomar(intent: Intent?): Boolean {
        intent ?: return false
        val accion = intent.action
        if (accion != Intent.ACTION_SEND && accion != Intent.ACTION_VIEW) return false
        val uri: Uri? = if (accion == Intent.ACTION_VIEW) {
            intent.data
        } else if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        val texto = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        if (uri == null && texto == null) return false
        pendiente = Pendiente(uri, if (uri == null) texto else null, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        return true
    }

    /** Devuelve (nombre, contenido) leyendo el archivo; `null` si no se pudo leer. Llamar fuera del hilo principal. */
    fun leer(contexto: Context, p: Pendiente): Pair<String, String>? {
        if (p.texto != null) return (p.nombre?.takeIf { it.isNotBlank() } ?: "Texto compartido") to p.texto
        val uri = p.uri ?: return null
        return runCatching {
            val nombre = nombreDe(contexto.contentResolver, uri)
            val salida = ByteArrayOutputStream()
            contexto.contentResolver.openInputStream(uri)?.use { entrada ->
                val buffer = ByteArray(16 * 1024)
                while (salida.size() < MAX_BYTES) {
                    val n = entrada.read(buffer)
                    if (n < 0) break
                    salida.write(buffer, 0, n)
                }
            } ?: return null
            nombre to salida.toString(Charsets.UTF_8.name())
        }.getOrNull()
    }

    private fun nombreDe(resolver: ContentResolver, uri: Uri): String {
        val consultado = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return consultado?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "archivo_compartido"
    }
}
