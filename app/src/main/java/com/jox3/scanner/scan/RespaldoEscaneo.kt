package com.jox3.scanner.scan

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.jox3.scanner.scan.engine.ListaValidada
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copia en disco de lo que lleva el escaneo, para no perder el trabajo si Android o un
 * error cierran la app (antes todo vivía solo en memoria y al reiniciarse se perdía).
 */
@Serializable
data class RespaldoEscaneo(
    val listas: List<ListaValidada>,
    val errores: List<String>,
    val diagnostico: List<String>,
    val nombreArchivo: String,
    /** `true` mientras el escaneo no terminó: si al abrir la app sigue en `true`, se interrumpió. */
    val enCurso: Boolean,
)

object AlmacenRespaldo {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun archivo(carpeta: File) = File(carpeta, "respaldo_escaneo.json")

    @Synchronized
    fun guardar(carpeta: File, respaldo: RespaldoEscaneo) {
        runCatching {
            val temporal = File(carpeta, "respaldo_escaneo.json.tmp")
            temporal.writeText(json.encodeToString(respaldo))
            temporal.renameTo(archivo(carpeta))
        }
    }

    @Synchronized
    fun cargar(carpeta: File): RespaldoEscaneo? = runCatching {
        archivo(carpeta).takeIf { it.exists() }?.readText()?.let { json.decodeFromString<RespaldoEscaneo>(it) }
    }.getOrNull()
}

/** Diagnóstico de por qué se cerró la app la última vez (error propio o decisión del sistema). */
object DiagnosticoCierre {
    private fun archivoError(carpeta: File) = File(carpeta, "ultimo_error.txt")

    /** Guarda en disco la traza de cualquier error no controlado antes de que la app se cierre. */
    fun instalar(carpeta: File) {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            runCatching {
                val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                archivoError(carpeta).writeText("$fecha · hilo ${hilo.name}\n${error.stackTraceToString().take(3000)}")
            }
            anterior?.uncaughtException(hilo, error)
        }
    }

    /** Texto con el motivo del último cierre (traza propia si hubo, o el motivo que registró Android), o `null`. */
    fun motivoUltimoCierre(context: Context, carpeta: File): String? {
        val error = archivoError(carpeta).takeIf { it.exists() }?.readText()?.also { runCatching { archivoError(carpeta).delete() } }
        if (error != null) return "error de la app → ${error.lines().take(4).joinToString(" | ")}"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val salida = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() }.getOrNull()
            ?: return null
        val motivo = when (salida.reason) {
            ApplicationExitInfo.REASON_CRASH -> "error de la app (crash)"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "error nativo (TDLib/librería)"
            ApplicationExitInfo.REASON_ANR -> "la app dejó de responder"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Android la cerró por falta de memoria"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Android la cerró por uso excesivo de recursos"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "la cerraste tú"
            ApplicationExitInfo.REASON_USER_STOPPED -> "la detuviste desde Ajustes"
            ApplicationExitInfo.REASON_SIGNALED -> "el sistema la terminó"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "cambió un permiso"
            ApplicationExitInfo.REASON_OTHER -> "otro motivo del sistema (ahorro de batería u optimización)"
            else -> "código ${salida.reason}"
        }
        return motivo + (salida.description?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    }
}
