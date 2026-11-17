package com.jox3.scanner.scan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Servicio en primer plano que se mantiene mientras hay un escaneo en curso.
 *
 * Sin esto, al salir de la app Android reduce su prioridad: congela las
 * corrutinas/red (Doze, app en segundo plano) o incluso mata el proceso, y el
 * escaneo se cortaba ("TDLib no respondió", lectura detenida, validación de
 * listas parada). Con un servicio en primer plano (notificación visible) el
 * proceso conserva prioridad alta y la red; el wake lock parcial evita que la
 * CPU se duerma con la pantalla apagada. La lógica del escaneo sigue en el
 * ViewModel; el servicio solo "ancla" el proceso.
 */
class ServicioEscaneo : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val texto = intent?.getStringExtra(EXTRA_TEXTO) ?: "Escaneando…"
        enEjecucion = true
        val notificacion = construirNotificacion(this, texto)
        ServiceCompat.startForeground(
            this,
            ID_NOTIFICACION,
            notificacion,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "scann-jox3:escaneo").apply {
                setReferenceCounted(false)
                acquire(MAX_DURACION_MS)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        enEjecucion = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var enEjecucion = false

        private fun construirNotificacion(contexto: Context, texto: String): Notification {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CANAL, "Escaneo en curso", NotificationManager.IMPORTANCE_LOW),
                )
            }
            val abrir = contexto.packageManager.getLaunchIntentForPackage(contexto.packageName)?.let {
                android.app.PendingIntent.getActivity(
                    contexto, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            return NotificationCompat.Builder(contexto, CANAL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("JOX3 Radar escaneando")
                .setContentText(texto)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(abrir)
                .build()
        }

        private const val CANAL = "escaneo"
        private const val ID_NOTIFICACION = 4201
        private const val EXTRA_TEXTO = "texto"
        private const val MAX_DURACION_MS = 6 * 60 * 60 * 1000L

        private const val CANAL_AVISOS = "avisos"
        private const val ID_AVISO = 4202

        /**
         * Aviso de "escaneo terminado" (sonido normal, se puede tocar para abrir la app);
         * solo tiene sentido mostrarlo cuando la app NO está en pantalla.
         */
        fun notificarTerminado(contexto: Context, titulo: String, texto: String) {
            val nm = contexto.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(CANAL_AVISOS, "Avisos de resultados", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val abrir = contexto.packageManager.getLaunchIntentForPackage(contexto.packageName)?.let {
                android.app.PendingIntent.getActivity(
                    contexto, 1, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            val n = NotificationCompat.Builder(contexto, CANAL_AVISOS)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setAutoCancel(true)
                .setContentIntent(abrir)
                .build()
            runCatching { nm.notify(ID_AVISO, n) } // sin permiso de notificaciones simplemente no se muestra
        }

        /**
         * Inicia el servicio, o solo actualiza el texto de la notificación si ya está activo.
         * Con el servicio ya en marcha se actualiza con `notify` (sin volver a llamar a
         * `startForegroundService`, que desde segundo plano en Android 12+ puede lanzar
         * ForegroundServiceStartNotAllowedException y cerrar la app). Todo va protegido:
         * un fallo del aviso nunca debe tumbar el escaneo.
         */
        fun iniciarOActualizar(contexto: Context, texto: String) {
            runCatching {
                if (enEjecucion) {
                    contexto.getSystemService(NotificationManager::class.java)
                        .notify(ID_NOTIFICACION, construirNotificacion(contexto, texto))
                } else {
                    val intent = Intent(contexto, ServicioEscaneo::class.java).putExtra(EXTRA_TEXTO, texto)
                    androidx.core.content.ContextCompat.startForegroundService(contexto, intent)
                }
            }
        }

        fun detener(contexto: Context) {
            runCatching { contexto.stopService(Intent(contexto, ServicioEscaneo::class.java)) }
        }
    }
}
