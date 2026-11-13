package com.jox3.scanner.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    data class Descargando(val info: InfoActualizacion) : EstadoActualizacion()
}

/**
 * Orquesta la comprobación de actualizaciones contra GitHub Releases y la
 * descarga a través de [DescargadorActualizacion]. La instalación en sí
 * ocurre fuera de este ViewModel: la completa [UpdateDownloadReceiver]
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
        descargador.encolarDescarga(info.urlDescargaApk, info.version)
        _estado.value = EstadoActualizacion.Descargando(info)
    }

    /** Llamada al volver de la pantalla de ajustes de permiso. */
    fun permisoConcedidoReintentarDescarga(info: InfoActualizacion) {
        iniciarDescarga(info)
    }

    fun intentSolicitarPermiso() = descargador.intentSolicitarPermisoInstalacion()
}
