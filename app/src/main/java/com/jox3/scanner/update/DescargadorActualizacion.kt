package com.jox3.scanner.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Nombre del archivo descargado dentro de la carpeta pública de Descargas.
 * Se sobreescribe en cada actualización (no se versiona el nombre) para no
 * ir acumulando .apk viejos en el dispositivo del usuario.
 */
const val NOMBRE_APK_DESCARGADO = "scann-jox3-update.apk"

/**
 * Encapsula el flujo de descarga + instalación de una actualización:
 * 1. Verifica si la app tiene permiso para instalar paquetes de origen
 *    desconocido (requerido desde Android 8, API 26) y si no lo tiene,
 *    abre la pantalla de ajustes para que el usuario lo conceda.
 * 2. Encola la descarga del .apk con el DownloadManager del sistema
 *    (igual que descargar cualquier archivo desde un navegador).
 * 3. Cuando la descarga termina, [UpdateDownloadReceiver] recibe el aviso
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
     * [UpdateDownloadReceiver] usa para confirmar que el broadcast recibido
     * corresponde a esta descarga y no a otra en curso en el dispositivo.
     */
    fun encolarDescarga(urlApk: String, version: String): Long {
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
}
