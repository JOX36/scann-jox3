package com.jox3.scanner.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Escucha `ACTION_DOWNLOAD_COMPLETE` del sistema. Cuando la descarga que
 * encoló [DescargadorActualizacion] termina, arma la URI del .apk a través
 * de FileProvider (obligatorio desde Android 7, API 24, para compartir
 * archivos entre apps) y lanza el instalador del sistema.
 *
 * No instala nada por sí mismo: `ACTION_VIEW` con ese MIME type abre la
 * pantalla estándar de instalación de Android, donde el usuario confirma.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val idDescarga = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (idDescarga == -1L) return

        val archivo = File(
            context.getExternalFilesDir(Build.VERSION.RELEASE),
            NOMBRE_APK_DESCARGADO,
        )
        if (!archivo.exists()) return

        val uriApk = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            archivo,
        )

        val intentInstalar = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriApk, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intentInstalar)
    }
}
