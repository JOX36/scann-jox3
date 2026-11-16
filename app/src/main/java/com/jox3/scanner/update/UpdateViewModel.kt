package com.jox3.scanner.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estados posibles del flujo de comprobación/descarga de actualizaciones. */
sealed class EstadoActualizacion {
    object SinComprobar : EstadoActualizacion()
    object Comprobando : EstadoActualizacion()
    object AlDia : EstadoActualizacion()
    data class Disponible(val info: InfoActualizacion) : EstadoActualizacion()
    data class PermisoRequerido(val info: InfoActualizacion) : EstadoActualizacion()
    data class Descargando(val info: InfoActualizacion, val porcentaje: Int? = null) : EstadoActualizacion()
    /** Descarga terminada; [lanzado] pasa a `true` cuando ya se abrió el instalador (para no repetirlo). */
    data class ListoParaInstalar(val info: InfoActualizacion, val lanzado: Boolean = false) : EstadoActualizacion()
    data class ErrorDescarga(val info: InfoActualizacion) : EstadoActualizacion()
}

/**
 * Orquesta la comprobación de actualizaciones contra GitHub Releases y la
 * descarga a través de [DescargadorActualizacion]. La instalación en sí
 * ocurre fuera de este ViewModel: la lanza la propia pantalla (MainActivity) al detectar que terminó
 * cuando el sistema avisa que la descarga terminó.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val descargador = DescargadorActualizacion(application)

    private val _estado = MutableStateFlow<EstadoActualizacion>(EstadoActualizacion.SinComprobar)
    val estado: StateFlow<EstadoActualizacion> = _estado.asStateFlow()

    /**
     * Comprueba si hay una actualización nueva. Se llama una vez al abrir
     * la app (ver MainActivity); no hay comprobación periódica en segundo
     * plano todavía — el usuario también puede volver a intentarlo a mano.
     */
    fun comprobarActualizacion(versionInstalada: String) {
        viewModelScope.launch {
            _estado.value = EstadoActualizacion.Comprobando
            val info = buscarActualizacion(NetworkModule.cliente, versionInstalada)
            _estado.value = if (info != null) {
                EstadoActualizacion.Disponible(info)
            } else {
                EstadoActualizacion.AlDia
            }
        }
    }

    /**
     * El usuario pulsó "Actualizar". Si falta el permiso de instalar apps
     * de origen desconocido, pasa a [EstadoActualizacion.PermisoRequerido]
     * para que la UI abra los ajustes; si ya está, descarga directamente.
     */
    fun iniciarDescarga(info: InfoActualizacion) {
        if (!descargador.puedeInstalarPaquetes()) {
            _estado.value = EstadoActualizacion.PermisoRequerido(info)
            return
        }
        val id = descargador.encolarDescarga(info.urlDescargaApk, info.version)
        _estado.value = EstadoActualizacion.Descargando(info, 0)
        // Se sigue el avance desde la propia app (porcentaje visible) y, al
        // terminar, la UI abre el instalador sola — antes había que ir a las
        // notificaciones / Descargas a tocar el archivo a mano.
        jobDescarga?.cancel()
        jobDescarga = viewModelScope.launch {
            while (true) {
                val progreso = descargador.consultarProgreso(id)
                when (progreso?.estado) {
                    ProgresoDescarga.Estado.COMPLETA -> {
                        // Si el archivo no es un .apk legible, se avisa como error de descarga
                        // (con "Reintentar") en vez de abrir un instalador que fallará.
                        _estado.value = if (descargador.apkDescargadoEsValido()) {
                            EstadoActualizacion.ListoParaInstalar(info)
                        } else {
                            EstadoActualizacion.ErrorDescarga(info)
                        }
                        return@launch
                    }
                    null, ProgresoDescarga.Estado.FALLIDA -> {
                        _estado.value = EstadoActualizacion.ErrorDescarga(info)
                        return@launch
                    }
                    ProgresoDescarga.Estado.EN_CURSO ->
                        _estado.value = EstadoActualizacion.Descargando(info, progreso.porcentaje)
                }
                delay(500)
            }
        }
    }

    private var jobDescarga: Job? = null

    /** Intent del instalador del sistema para el .apk descargado (ver [DescargadorActualizacion.intentInstalar]). */
    fun intentInstalar() = descargador.intentInstalar()

    /** La UI ya abrió el instalador: se marca para no volver a abrirlo en cada regreso a la app. */
    fun instaladorLanzado() {
        val actual = _estado.value
        if (actual is EstadoActualizacion.ListoParaInstalar) _estado.value = actual.copy(lanzado = true)
    }

    /** Llamada al volver de la pantalla de ajustes de permiso. */
    fun permisoConcedidoReintentarDescarga(info: InfoActualizacion) {
        iniciarDescarga(info)
    }

    fun intentSolicitarPermiso() = descargador.intentSolicitarPermisoInstalacion()
}
