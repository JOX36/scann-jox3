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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

data class EstadoAnalisis(val enCurso: Boolean = false, val hechas: Int = 0, val total: Int = 0)

data class EstadoRevalidacion(val enCurso: Boolean = false, val hechas: Int = 0, val total: Int = 0)

/** Pestaña "Bodega": todas las listas activas que la app ha encontrado, para buscarlas y reabrirlas. */
class BodegaViewModel(contexto: Context) : ViewModel() {
    private val carpeta = contexto.applicationContext.filesDir

    private val _entradas = MutableStateFlow<List<EntradaBodega>>(emptyList())
    val entradas: StateFlow<List<EntradaBodega>> = _entradas.asStateFlow()

    private val _revalidacion = MutableStateFlow(EstadoRevalidacion())
    val revalidacion: StateFlow<EstadoRevalidacion> = _revalidacion.asStateFlow()

    private val _analisis = MutableStateFlow(EstadoAnalisis())
    val analisis: StateFlow<EstadoAnalisis> = _analisis.asStateFlow()
    /** Informes de calidad guardados (listas ya verificadas), de mejor a peor nota. */
    private val _informes = MutableStateFlow<List<InformeCalidad>>(emptyList())
    val informes: StateFlow<List<InformeCalidad>> = _informes.asStateFlow()
    private var informesCargados = false
    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso.asStateFlow()
    /** 0 = todas las listas, 1 = verificadas (se recuerda al abrir el detalle de una lista). */
    private val _seccion = MutableStateFlow(0)
    val seccion: StateFlow<Int> = _seccion.asStateFlow()
    fun elegirSeccion(i: Int) { _seccion.value = i }
    private var analisisJob: Job? = null
    private val candadosPorHost = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private val _detalleEntrada = MutableStateFlow<EntradaBodega?>(null)
    val detalleEntrada: StateFlow<EntradaBodega?> = _detalleEntrada.asStateFlow()
    private val _detalleInfo = MutableStateFlow<DetalleLista?>(null)
    val detalleInfo: StateFlow<DetalleLista?> = _detalleInfo.asStateFlow()
    private val _detalleCargando = MutableStateFlow(false)
    val detalleCargando: StateFlow<Boolean> = _detalleCargando.asStateFlow()
    private var detalleJob: Job? = null
    private var revalidarJob: Job? = null

    init {
        viewModelScope.launch {
            val (listas, guardados) = withContext(Dispatchers.IO) {
                RepositorioBodega.cargar(carpeta) to RepositorioCalidad.cargar(carpeta)
            }
            _entradas.value = listas
            _informes.value = guardados.sortedByDescending { it.puntaje }
            informesCargados = true
        }
        // Un informe solo sirve mientras su lista siga en la Bodega (de ahí salen las credenciales).
        viewModelScope.launch {
            _entradas.collect { lista ->
                if (!informesCargados) return@collect
                val claves = lista.map { RepositorioBodega.clave(it.lista.credenciales) }.toSet()
                val antes = _informes.value
                val despues = antes.filter { it.clave in claves }
                if (despues.size != antes.size) {
                    _informes.value = despues
                    withContext(Dispatchers.IO) { RepositorioCalidad.guardar(carpeta, despues) }
                }
            }
        }
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

    /** Quita de una vez varias listas (selección múltiple de la pantalla). */
    fun eliminarVarias(entradas: List<EntradaBodega>) {
        val claves = entradas.map { RepositorioBodega.clave(it.lista.credenciales) }.toSet()
        if (claves.isEmpty()) return
        viewModelScope.launch {
            _entradas.value = withContext(Dispatchers.IO) { RepositorioBodega.eliminar(carpeta, claves) }
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

    /**
     * Mide la calidad de las listas dadas (hasta 4 a la vez, pero de una en una por servidor para no
     * saturar a un panel con pocas conexiones). Cada informe se guarda: la lista queda en "Verificadas"
     * y no hace falta medirla otra vez.
     */
    fun analizarCalidad(listas: List<EntradaBodega>) {
        if (_analisis.value.enCurso || listas.isEmpty()) return
        _analisis.value = EstadoAnalisis(true, 0, listas.size)
        _aviso.value = null
        analisisJob = viewModelScope.launch {
            val hechas = AtomicInteger(0)
            val semaforo = Semaphore(4)
            var sinRespuesta = 0
            var medidas = 0
            try {
                withContext(Dispatchers.IO) {
                    listas.map { entrada ->
                        async {
                            val candado = candadosPorHost.getOrPut(hostDe(entrada)) { Mutex() }
                            candado.withLock {
                                semaforo.withPermit {
                                    val informe = withTimeoutOrNull(90_000L) { medirCalidad(entrada) }
                                        ?: informeFallido(entrada, "Se agotó el tiempo de la prueba (90 s).")
                                    if (informe.respuestaMs != null) {
                                        synchronized(this@BodegaViewModel) { medidas++ }
                                        _informes.update { previos ->
                                            (previos.filter { it.clave != informe.clave } + informe).sortedByDescending { it.puntaje }
                                        }
                                    } else {
                                        synchronized(this@BodegaViewModel) { sinRespuesta++ }
                                    }
                                    _analisis.value = EstadoAnalisis(true, hechas.incrementAndGet(), listas.size)
                                }
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { RepositorioCalidad.guardar(carpeta, _informes.value) }
                _aviso.value = "Medidas: $medidas" + if (sinRespuesta > 0) " · sin respuesta: $sinRespuesta (no se guardan)" else ""
                _analisis.value = EstadoAnalisis()
                _seccion.value = 1
            }
        }
    }

    /** Vuelve a medir una lista ya verificada (los datos cambian con el tiempo). */
    fun remedir(informe: InformeCalidad) {
        val entrada = _entradas.value.firstOrNull { RepositorioBodega.clave(it.lista.credenciales) == informe.clave } ?: return
        analizarCalidad(listOf(entrada))
    }

    fun quitarInforme(clave: String) {
        _informes.update { it.filter { x -> x.clave != clave } }
        viewModelScope.launch(Dispatchers.IO) { RepositorioCalidad.guardar(carpeta, _informes.value) }
    }

    /** Detiene la prueba; los informes ya medidos se conservan. */
    fun cancelarAnalisis() {
        analisisJob?.cancel()
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
