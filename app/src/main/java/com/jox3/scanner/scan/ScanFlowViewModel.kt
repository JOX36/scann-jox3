package com.jox3.scanner.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.scan.engine.ListaValidada
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Modos de escaneo, equivalentes uno a uno al menú del script de Python
 * original (`run_single_chat_scan`, `run_host_search_all_chats`,
 * `run_host_search_in_single_chat`, `run_latin_content_search` y
 * `escanear_archivo_local_latino`).
 */
enum class TipoModo {
    CUALQUIER_LISTA,
    HOST_EN_TODOS_LOS_CHATS,
    HOST_EN_UN_CHAT,
    LATINO_EN_UN_CHAT,
    ARCHIVO_LOCAL,
}

/** Estados del flujo completo: cargar chats → elegir modo → (host/chat) → escanear → ver resultados. */
sealed class EstadoFlujoEscaneo {
    object CargandoChats : EstadoFlujoEscaneo()

    data class EligiendoModo(val chats: List<ChatTelegram>) : EstadoFlujoEscaneo()

    /** Pide el host a buscar; [modo] decide a dónde va después de confirmarlo. */
    data class EscribiendoHost(val chats: List<ChatTelegram>, val modo: TipoModo) : EstadoFlujoEscaneo()

    /** Elegir un único chat. [host] nulo significa que el modo es "solo LATINO", no búsqueda por host. */
    data class EligiendoChatUnico(val chats: List<ChatTelegram>, val host: String?) : EstadoFlujoEscaneo()

    data class SeleccionandoChats(
        val chats: List<ChatTelegram>,
        val seleccionados: Set<Long>,
    ) : EstadoFlujoEscaneo()

    data class Escaneando(
        val procesados: Int,
        val total: Int,
        val encontradas: List<ListaValidada>,
        val errores: List<String> = emptyList(),
        // Se fija una sola vez al entrar a este estado (no cambia con
        // .copy() salvo que se pase explícito) — con esto la UI calcula
        // "validaciones/seg" sin que el repositorio tenga que reportarlo.
        val inicioMs: Long = System.currentTimeMillis(),
        // Diagnóstico por chat (mensajes leídos / enlaces candidatos) —
        // ver [EventoEscaneo.InfoChat]. No son errores, se muestran siempre.
        val diagnostico: List<String> = emptyList(),
        // Registro paso a paso de todo el escaneo (ver [EventoEscaneo.LogPaso])
        // — temporal, pedido explícito del usuario para ver de principio a
        // fin qué hizo la app, sin tener que interpretar solo contadores.
        val registro: List<String> = emptyList(),
    ) : EstadoFlujoEscaneo()

    data class Resultados(
        val listas: List<ListaValidada>,
        val errores: List<String> = emptyList(),
        val diagnostico: List<String> = emptyList(),
        val registro: List<String> = emptyList(),
    ) : EstadoFlujoEscaneo()
}

class ScanFlowViewModel(
    private val chatsRepository: ChatsRepository = FakeChatsRepository(),
    private val escanerRepository: EscanerRepository = FakeEscanerRepository(),
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoFlujoEscaneo>(EstadoFlujoEscaneo.CargandoChats)
    val estado: StateFlow<EstadoFlujoEscaneo> = _estado.asStateFlow()

    private var chatsCache: List<ChatTelegram> = emptyList()

    // Para "Detener y ver resultados parciales": el job se cancela y estas
    // listas (las mismas que ya va llenando [procesarEvento]) se usan tal
    // cual para armar el estado [EstadoFlujoEscaneo.Resultados] sin
    // esperar a [EventoEscaneo.Finalizado].
    private var scanJob: Job? = null
    private var encontradasActuales: List<ListaValidada> = emptyList()
    private var erroresActuales: List<String> = emptyList()
    private var diagnosticoActual: List<String> = emptyList()
    private var registroActual: List<String> = emptyList()

    init {
        cargarChats()
    }

    private fun cargarChats() {
        _estado.value = EstadoFlujoEscaneo.CargandoChats
        viewModelScope.launch {
            chatsCache = chatsRepository.listarChats()
            _estado.value = EstadoFlujoEscaneo.EligiendoModo(chatsCache)
        }
    }

    /**
     * El modo [TipoModo.ARCHIVO_LOCAL] no pasa por aquí: la pantalla abre el
     * selector de archivos del sistema directamente y llama a
     * [escanearArchivoLocal] con el contenido leído.
     */
    fun elegirModo(modo: TipoModo) {
        when (modo) {
            TipoModo.CUALQUIER_LISTA ->
                _estado.value = EstadoFlujoEscaneo.SeleccionandoChats(chatsCache, emptySet())
            TipoModo.HOST_EN_TODOS_LOS_CHATS, TipoModo.HOST_EN_UN_CHAT ->
                _estado.value = EstadoFlujoEscaneo.EscribiendoHost(chatsCache, modo)
            TipoModo.LATINO_EN_UN_CHAT ->
                _estado.value = EstadoFlujoEscaneo.EligiendoChatUnico(chatsCache, host = null)
            TipoModo.ARCHIVO_LOCAL -> Unit
        }
    }

    fun hostConfirmado(host: String) {
        val actual = _estado.value
        if (actual !is EstadoFlujoEscaneo.EscribiendoHost) return
        val hostLimpio = host.trim()
        if (hostLimpio.isBlank()) return

        when (actual.modo) {
            TipoModo.HOST_EN_TODOS_LOS_CHATS ->
                iniciarEscaneo(actual.chats, filtroHost = hostLimpio, soloLatino = false)
            TipoModo.HOST_EN_UN_CHAT ->
                _estado.value = EstadoFlujoEscaneo.EligiendoChatUnico(actual.chats, host = hostLimpio)
            else -> Unit
        }
    }

    fun chatUnicoElegido(chat: ChatTelegram) {
        val actual = _estado.value
        if (actual !is EstadoFlujoEscaneo.EligiendoChatUnico) return
        val host = actual.host
        iniciarEscaneo(listOf(chat), filtroHost = host, soloLatino = host == null)
    }

    /** Vuelve del selector de archivo cancelado, o de cualquier pantalla intermedia, al menú de modos. */
    fun volverAElegirModo() {
        _estado.value = EstadoFlujoEscaneo.EligiendoModo(chatsCache)
    }

    fun alternarSeleccion(chatId: Long) {
        val actual = _estado.value
        if (actual !is EstadoFlujoEscaneo.SeleccionandoChats) return
        val nuevaSeleccion = if (chatId in actual.seleccionados) {
            actual.seleccionados - chatId
        } else {
            actual.seleccionados + chatId
        }
        _estado.value = actual.copy(seleccionados = nuevaSeleccion)
    }

    fun iniciarEscaneoDeSeleccionados() {
        val actual = _estado.value
        if (actual !is EstadoFlujoEscaneo.SeleccionandoChats || actual.seleccionados.isEmpty()) return
        val chatsElegidos = actual.chats.filter { it.id in actual.seleccionados }
        iniciarEscaneo(chatsElegidos, filtroHost = null, soloLatino = false)
    }

    /** Escanea un archivo .txt local, ya leído por la UI vía Storage Access Framework. */
    fun escanearArchivoLocal(nombreArchivo: String, contenido: String) {
        encontradasActuales = emptyList()
        erroresActuales = emptyList()
        diagnosticoActual = emptyList()
        registroActual = emptyList()
        _estado.value = EstadoFlujoEscaneo.Escaneando(0, 0, emptyList())
        scanJob = viewModelScope.launch {
            val encontradas = mutableListOf<ListaValidada>()
            val errores = mutableListOf<String>()
            val diagnostico = mutableListOf<String>()
            val registro = mutableListOf<String>()
            escanearTextoLocal(nombreArchivo, contenido).collect { evento ->
                procesarEvento(evento, encontradas, errores, diagnostico, registro)
            }
        }
    }

    private fun iniciarEscaneo(chats: List<ChatTelegram>, filtroHost: String?, soloLatino: Boolean) {
        encontradasActuales = emptyList()
        erroresActuales = emptyList()
        diagnosticoActual = emptyList()
        registroActual = emptyList()
        _estado.value = EstadoFlujoEscaneo.Escaneando(0, 0, emptyList())
        scanJob = viewModelScope.launch {
            val encontradas = mutableListOf<ListaValidada>()
            val errores = mutableListOf<String>()
            val diagnostico = mutableListOf<String>()
            val registro = mutableListOf<String>()
            escanerRepository.escanear(chats, filtroHost, soloLatino).collect { evento ->
                procesarEvento(evento, encontradas, errores, diagnostico, registro)
            }
        }
    }

    /**
     * Corta el escaneo donde vaya (sin esperar a que termine de validar
     * todos los enlaces candidatos) y pasa a Resultados con lo que ya se
     * encontró hasta ese momento — igual que el botón "Detener y ver
     * resultados parciales" del diseño.
     */
    fun detenerYVerParciales() {
        scanJob?.cancel()
        scanJob = null
        _estado.value = EstadoFlujoEscaneo.Resultados(encontradasActuales, erroresActuales, diagnosticoActual, registroActual)
    }

    private fun procesarEvento(
        evento: EventoEscaneo,
        encontradas: MutableList<ListaValidada>,
        errores: MutableList<String>,
        diagnostico: MutableList<String>,
        registro: MutableList<String>,
    ) {
        when (evento) {
            is EventoEscaneo.Progreso -> {
                val estadoActual = _estado.value
                val inicioMs = (estadoActual as? EstadoFlujoEscaneo.Escaneando)?.inicioMs
                    ?: System.currentTimeMillis()
                _estado.value = EstadoFlujoEscaneo.Escaneando(
                    evento.procesados,
                    evento.total,
                    encontradas.toList(),
                    errores.toList(),
                    inicioMs,
                    diagnostico.toList(),
                    registro.toList(),
                )
            }
            is EventoEscaneo.ListaEncontrada -> {
                encontradas += evento.lista
                encontradasActuales = encontradas.toList()
                val estadoActual = _estado.value
                if (estadoActual is EstadoFlujoEscaneo.Escaneando) {
                    _estado.value = estadoActual.copy(encontradas = encontradas.toList())
                }
            }
            is EventoEscaneo.ErrorEnChat -> {
                errores += "${evento.chatTitulo}: ${evento.mensaje}"
                erroresActuales = errores.toList()
                val estadoActual = _estado.value
                if (estadoActual is EstadoFlujoEscaneo.Escaneando) {
                    _estado.value = estadoActual.copy(errores = errores.toList())
                }
            }
            is EventoEscaneo.InfoChat -> {
                diagnostico += "${evento.chatTitulo}: ${evento.mensajesLeidos} mensajes leídos, " +
                    "${evento.enlacesCandidatos} enlace(s) candidato(s)"
                diagnosticoActual = diagnostico.toList()
                val estadoActual = _estado.value
                if (estadoActual is EstadoFlujoEscaneo.Escaneando) {
                    _estado.value = estadoActual.copy(diagnostico = diagnostico.toList())
                }
            }
            is EventoEscaneo.LogPaso -> {
                registro += evento.mensaje
                registroActual = registro.toList()
                val estadoActual = _estado.value
                if (estadoActual is EstadoFlujoEscaneo.Escaneando) {
                    _estado.value = estadoActual.copy(registro = registro.toList())
                }
            }
            EventoEscaneo.Finalizado -> {
                scanJob = null
                _estado.value = EstadoFlujoEscaneo.Resultados(
                    encontradas.toList(),
                    errores.toList(),
                    diagnostico.toList(),
                    registro.toList(),
                )
            }
        }
    }

    /** Vuelve al menú de modos para escanear de nuevo. */
    fun reiniciar() = cargarChats()
}

class ScanFlowViewModelFactory(
    private val chatsRepository: ChatsRepository,
    private val escanerRepository: EscanerRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScanFlowViewModel(chatsRepository, escanerRepository) as T
    }
}
