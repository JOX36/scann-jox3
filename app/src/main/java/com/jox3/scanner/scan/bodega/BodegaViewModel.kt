package com.jox3.scanner.scan.bodega

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jox3.scanner.scan.engine.DetalleLista
import com.jox3.scanner.scan.engine.consultarDetalleLista
import com.jox3.scanner.scan.engine.validarYEnriquecer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

data class EstadoRevalidacion(val enCurso: Boolean = false, val hechas: Int = 0, val total: Int = 0)

/** Pestaña "Bodega": todas las listas activas que la app ha encontrado, para buscarlas y reabrirlas. */
class BodegaViewModel(contexto: Context) : ViewModel() {
    private val carpeta = contexto.applicationContext.filesDir

    private val _entradas = MutableStateFlow<List<EntradaBodega>>(emptyList())
    val entradas: StateFlow<List<EntradaBodega>> = _entradas.asStateFlow()

    private val _revalidacion = MutableStateFlow(EstadoRevalidacion())
    val revalidacion: StateFlow<EstadoRevalidacion> = _revalidacion.asStateFlow()

    private val _detalleEntrada = MutableStateFlow<EntradaBodega?>(null)
    val detalleEntrada: StateFlow<EntradaBodega?> = _detalleEntrada.asStateFlow()
    private val _detalleInfo = MutableStateFlow<DetalleLista?>(null)
    val detalleInfo: StateFlow<DetalleLista?> = _detalleInfo.asStateFlow()
    private val _detalleCargando = MutableStateFlow(false)
    val detalleCargando: StateFlow<Boolean> = _detalleCargando.asStateFlow()
    private var detalleJob: Job? = null
    private var revalidarJob: Job? = null

    init {
        refrescar()
    }

    /** Vuelve a leer el archivo (los escaneos guardan por su cuenta; se llama al abrir la pestaña). */
    fun refrescar() {
        viewModelScope.launch {
            _entradas.value = withContext(Dispatchers.IO) { RepositorioBodega.cargar(carpeta) }
        }
    }

    fun eliminar(entrada: EntradaBodega) {
        viewModelScope.launch {
            _entradas.value = withContext(Dispatchers.IO) {
                RepositorioBodega.eliminar(carpeta, setOf(RepositorioBodega.clave(entrada.lista.credenciales)))
            }
        }
    }

    /** Quita las que ya no responden como activas o ya vencieron. */
    fun limpiarCaidasYVencidas() {
        val claves = _entradas.value
            .filter { !it.vigente || (it.lista.diasHoy() ?: 0) < 0 }
            .map { RepositorioBodega.clave(it.lista.credenciales) }
            .toSet()
        if (claves.isEmpty()) return
        viewModelScope.launch {
            _entradas.value = withContext(Dispatchers.IO) { RepositorioBodega.eliminar(carpeta, claves) }
        }
    }

    /** Vuelve a comprobar todas las listas guardadas (10 a la vez) y marca las que ya cayeron. */
    fun revalidarTodas() {
        if (_revalidacion.value.enCurso) return
        val base = _entradas.value
        if (base.isEmpty()) return
        _revalidacion.value = EstadoRevalidacion(true, 0, base.size)
        revalidarJob = viewModelScope.launch {
            val hechas = AtomicInteger(0)
            val semaforo = Semaphore(10)
            val resultados = withContext(Dispatchers.IO) {
                base.map { entrada ->
                    async {
                        semaforo.withPermit {
                            val nueva = withTimeoutOrNull(120_000L) {
                                validarYEnriquecer(entrada.lista.credenciales, entrada.lista.origenChat)
                            }
                            _revalidacion.value = EstadoRevalidacion(true, hechas.incrementAndGet(), base.size)
                            entrada to nueva
                        }
                    }
                }.awaitAll()
            }
            _entradas.value = withContext(Dispatchers.IO) { RepositorioBodega.aplicarRevalidacion(carpeta, resultados) }
            _revalidacion.value = EstadoRevalidacion()
        }
    }

    fun abrirDetalle(entrada: EntradaBodega) {
        detalleJob?.cancel()
        _detalleEntrada.value = entrada
        _detalleInfo.value = null
        _detalleCargando.value = false
    }

    fun cerrarDetalle() {
        detalleJob?.cancel()
        _detalleEntrada.value = null
        _detalleInfo.value = null
        _detalleCargando.value = false
    }

    fun consultarDetalle() {
        val entrada = _detalleEntrada.value ?: return
        if (_detalleCargando.value || _detalleInfo.value != null) return
        _detalleCargando.value = true
        detalleJob = viewModelScope.launch {
            _detalleInfo.value = runCatching { consultarDetalleLista(entrada.lista) }.getOrNull()
            _detalleCargando.value = false
        }
    }
}

class BodegaViewModelFactory(private val contexto: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BodegaViewModel(contexto) as T
}
