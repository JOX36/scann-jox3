package com.jox3.scanner.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * Nombre del archivo descargado dentro de la carpeta pública de Descargas.
 * Se sobreescribe en cada actualización (no se versiona el nombre) para no
 * ir acumulando .apk viejos en el dispositivo del usuario.
 */
const val NOMBRE_APK_DESCARGADO = "scann-jox3-update.apk"

/** Estado de una descarga en curso del DownloadManager, ya traducido a lo que la app necesita. */
data class ProgresoDescarga(val estado: Estado, val porcentaje: Int?) {
    enum class Estado { EN_CURSO, COMPLETA, FALLIDA }
}

/**
 * Encapsula el flujo de descarga + instalación de una actualización:
 * 1. Verifica si la app tiene permiso para instalar paquetes de origen
 *    desconocido (requerido desde Android 8, API 26) y si no lo tiene,
 *    abre la pantalla de ajustes para que el usuario lo conceda.
 * 2. Encola la descarga del .apk con el DownloadManager del sistema
 *    (igual que descargar cualquier archivo desde un navegador).
 * 3. Cuando la descarga termina, el ViewModel detecta el fin (consultando el DownloadManager)
 *    del sistema y lanza el instalador — el usuario siempre ve y confirma
 *    la pantalla estándar de instalación de Android; nunca se instala nada
 *    en silencio.
 */
class DescargadorActualizacion(private val contexto: Context) {

    /** `true` si la app ya puede instalar paquetes sin pasar por ajustes. */
    fun puedeInstalarPaquetes(): Boolean {
        return contexto.packageManager.canRequestPackageInstalls()
    }

    /**
     * Construye el Intent que abre la pantalla del sistema donde el usuario
     * concede el permiso "Instalar apps desconocidas" para esta app.
     */
    fun intentSolicitarPermisoInstalacion(): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:${contexto.packageName}".toUri()
        }
    }

    /**
     * Encola la descarga del .apk indicado en [urlApk] usando el
     * DownloadManager del sistema. Devuelve el id de descarga, que
     * el ViewModel usa para seguir que el broadcast recibido
     * corresponde a esta descarga y no a otra en curso en el dispositivo.
     */
    fun encolarDescarga(urlApk: String, version: String): Long {
        // El DownloadManager NO sobreescribe un archivo con el mismo nombre:
        // lo guarda como "...-1.apk". Entonces [intentInstalar] abría el .apk
        // VIEJO (de una descarga anterior, a veces incompleta) y el sistema
        // decía "el paquete no es válido". Se borra lo anterior antes de bajar.
        borrarDescargasPrevias()
        val manager = contexto.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val solicitud = DownloadManager.Request(Uri.parse(urlApk))
            .setTitle("IPTV Scanner $version")
            .setDescription("Descargando actualización...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                contexto,
                Build.VERSION.RELEASE, // subcarpeta estable; el nombre real del archivo es fijo
                NOMBRE_APK_DESCARGADO,
            )
            .setAllowedOverMetered(true)
            .setMimeType("application/vnd.android.package-archive")

        return manager.enqueue(solicitud)
    }

    private fun borrarDescargasPrevias() {
        contexto.getExternalFilesDir(Build.VERSION.RELEASE)?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /** `true` si el .apk descargado existe y Android lo puede leer como paquete (no está corrupto ni incompleto). */
    fun apkDescargadoEsValido(): Boolean {
        val archivo = File(contexto.getExternalFilesDir(Build.VERSION.RELEASE), NOMBRE_APK_DESCARGADO)
        if (!archivo.exists() || archivo.length() == 0L) return false
        return contexto.packageManager.getPackageArchiveInfo(archivo.absolutePath, 0) != null
    }

    /** Consulta el avance de la descarga [id]; `null` si el sistema ya no la conoce (cancelada/borrada). */
    fun consultarProgreso(id: Long): ProgresoDescarga? {
        val manager = contexto.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val estado = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bajados = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val porcentaje = if (total > 0) ((bajados * 100) / total).toInt().coerceIn(0, 100) else null
            return when (estado) {
                DownloadManager.STATUS_SUCCESSFUL -> ProgresoDescarga(ProgresoDescarga.Estado.COMPLETA, 100)
                DownloadManager.STATUS_FAILED -> ProgresoDescarga(ProgresoDescarga.Estado.FALLIDA, porcentaje)
                else -> ProgresoDescarga(ProgresoDescarga.Estado.EN_CURSO, porcentaje)
            }
        }
    }

    /**
     * Intent del instalador del sistema para el .apk ya descargado, o `null`
     * si el archivo no está. Se lanza desde la propia app (en primer plano)
     * y no desde un receiver en segundo plano, porque Android 10+ bloquea
     * que una app abra pantallas desde el fondo — por eso antes el
     * instalador no aparecía solo al terminar la descarga.
     */
    fun intentInstalar(): Intent? {
        val archivo = File(contexto.getExternalFilesDir(Build.VERSION.RELEASE), NOMBRE_APK_DESCARGADO)
        if (!archivo.exists()) return null
        val uriApk = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", archivo)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriApk, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
