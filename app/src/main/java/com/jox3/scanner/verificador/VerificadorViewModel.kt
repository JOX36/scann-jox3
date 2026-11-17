package com.jox3.scanner.verificador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jox3.scanner.scan.data.extraerCredencialesDeTexto
import com.jox3.scanner.scan.engine.DetalleLista
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.scan.engine.consultarDetalleLista
import com.jox3.scanner.scan.engine.validarYEnriquecer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoVerificador(
    val texto: String = "",
    val verificando: Boolean = false,
    val error: String? = null,
    val lista: ListaValidada? = null,
    val detalle: DetalleLista? = null,
    val detalleCargando: Boolean = false,
)

/** Verifica UNA lista pegada a mano (cualquier formato conocido), sin escanear chats. */
class VerificadorViewModel(private val contextoApp: android.content.Context? = null) : ViewModel() {

    private fun avisar(texto: String) {
        val ctx = contextoApp ?: return
        if (!com.jox3.scanner.scan.EstadoApp.visible) {
            com.jox3.scanner.scan.ServicioEscaneo.notificarTerminado(ctx, "Verificación terminada", texto)
        }
    }

    private val _estado = MutableStateFlow(EstadoVerificador())
    val estado: StateFlow<EstadoVerificador> = _estado.asStateFlow()
    private var job: Job? = null

    fun cambiarTexto(texto: String) = _estado.update { it.copy(texto = texto, error = null) }

    fun verificar() {
        val cred = extraerCredencialesDeTexto(_estado.value.texto)
        if (cred == null) {
            _estado.update { it.copy(error = "No encontré host, usuario y contraseña en el texto pegado.") }
            return
        }
        job?.cancel()
        _estado.update { it.copy(verificando = true, error = null) }
        job = viewModelScope.launch {
            val lista = validarYEnriquecer(cred, ORIGEN)
            if (!lista.activa) {
                _estado.update {
                    it.copy(
                        verificando = false,
                        error = "La lista no está activa: credenciales incorrectas, vencida o el panel no responde.",
                    )
                }
                avisar("La lista no está activa o no respondió.")
                return@launch
            }
            _estado.update { it.copy(verificando = false, lista = lista, detalle = null, detalleCargando = true) }
            val detalle = runCatching { consultarDetalleLista(lista) }.getOrNull()
            _estado.update { it.copy(detalle = detalle, detalleCargando = false) }
            avisar("${lista.credenciales.host}: activa")
        }
    }

    /** Pide de nuevo la info extra (categorías, servidor) si falló o aún no se ha pedido. */
    fun consultarDetalle() {
        val actual = _estado.value
        val lista = actual.lista ?: return
        if (actual.detalleCargando || actual.detalle != null) return
        _estado.update { it.copy(detalleCargando = true) }
        job = viewModelScope.launch {
            val detalle = runCatching { consultarDetalleLista(lista) }.getOrNull()
            _estado.update { it.copy(detalle = detalle, detalleCargando = false) }
        }
    }

    /** Vuelve a la pantalla de pegar (limpia texto y resultado). */
    fun nueva() {
        job?.cancel()
        _estado.value = EstadoVerificador()
    }

    companion object {
        const val ORIGEN = "Verificador"
    }
}

class VerificadorViewModelFactory(private val contextoApp: android.content.Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = VerificadorViewModel(contextoApp) as T
}
