package com.jox3.scanner.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jox3.scanner.results.ResultadosExportador
import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.scan.data.TemaChat
import com.jox3.scanner.scan.data.TipoChat
import com.jox3.scanner.scan.engine.DetalleLista
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.scan.engine.consultarDetalleLista
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
    LATINO_EN_VARIOS_CHATS,
    HOST_EN_TODOS_LOS_CHATS,
    HOST_EN_UN_CHAT,
    LATINO_EN_UN_CHAT,
    ARCHIVO_LOCAL,
}

/** Un grupo con "Temas" y los temas entre los que el usuario puede elegir. */
data class ForoConTemas(val chat: ChatTelegram, val temas: List<TemaChat>)

/** Estados del flujo completo: cargar chats → elegir modo → (host/chat) → escanear → ver resultados. */
sealed class EstadoFlujoEscaneo {
    object CargandoChats : EstadoFlujoEscaneo()

    data class EligiendoModo(val chats: List<ChatTelegram>) : EstadoFlujoEscaneo()

    /** Pide el host a buscar; [modo] decide a dónde va después de confirmarlo. */
    data class EscribiendoHost(val chats: List<ChatTelegram>, val modo: TipoModo) : EstadoFlujoEscaneo()

    /**
     * Elegir un único chat. [host] nulo significa que el modo es "solo
     * LATINO", no búsqueda por host. [origenModo] se guarda para que
     * [ScanFlowViewModel.volver] sepa a qué pantalla anterior volver
     * (a "escribir host" si vino de ahí, o directo al menú si no).
     */
    data class EligiendoChatUnico(
        val chats: List<ChatTelegram>,
        val host: String?,
        val origenModo: TipoModo,
    ) : EstadoFlujoEscaneo()

    data class SeleccionandoChats(
        val chats: List<ChatTelegram>,
        val seleccionados: Set<Long>,
        /** `true` cuando se llegó desde "Contenido LATINO en varios chats": solo se quedan listas LATAM. */
        val soloLatino: Boolean = false,
    ) : EstadoFlujoEscaneo()

    /** Comprobando si algún chat elegido es un grupo con "Temas" antes de escanear. */
    object BuscandoTemas : EstadoFlujoEscaneo()

    /**
     * Algún chat elegido tiene "Temas": el usuario marca cuáles escanear.
     * [seleccion] guarda, por chat, los ids de tema marcados (por defecto todos).
     * [anterior] es la pantalla a la que vuelve el botón "Volver".
     */
    data class EligiendoTemas(
        val anterior: EstadoFlujoEscaneo,
        val chats: List<ChatTelegram>,
        val filtroHost: String?,
        val soloLatino: Boolean,
        val foros: List<ForoConTemas>,
        val seleccion: Map<Long, Set<Int>>,
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
        // Lectura en vivo (ver [EventoEscaneo.Leyendo]): chat que se está
        // leyendo, mensajes leídos de él hasta ahora y total de los chats
        // ya terminados — para mostrar "van N mensajes escaneados".
        val chatLeyendo: String? = null,
        val mensajesChatActual: Int = 0,
        val mensajesChatsTerminados: Int = 0,
    ) : EstadoFlujoEscaneo()

    data class Resultados(
        val listas: List<ListaValidada>,
        val errores: List<String> = emptyList(),
        val diagnostico: List<String> = emptyList(),
        val registro: List<String> = emptyList(),
        /** Nombre base del .txt/Telegram, p. ej. "Resultados_Latinos_G-PANDORA" (igual que en el script de Python). */
        val nombreArchivo: String = "Resultados_IPTV",
    ) : EstadoFlujoEscaneo()
}

class ScanFlowViewModel(
    private val chatsRepository: ChatsRepository = FakeChatsRepository(),
    private val escanerRepository: EscanerRepository = FakeEscanerRepository(),
    /** Carpeta donde se guarda la copia del escaneo (ver [AlmacenRespaldo]); `null` = sin respaldo. */
    private val carpetaRespaldo: java.io.File? = null,
    /** Motivo del último cierre de la app (ver [DiagnosticoCierre]), para mostrarlo al recuperar. */
    motivoUltimoCierre: String? = null,
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

    // Detalle de una lista puntual (pedido explícito del usuario: poder
    // tocar un resultado y consultar su info extra sin ir a Telegram).
    // Vive aparte del [estado] principal porque no es un paso más del
    // flujo de escaneo: es una capa que se abre encima de "Resultados" y
    // se cierra sin afectar nada de lo ya escaneado.
    private val _listaDetalle = MutableStateFlow<ListaValidada?>(null)
    val listaDetalle: StateFlow<ListaValidada?> = _listaDetalle.asStateFlow()
    private val _detalleInfo = MutableStateFlow<DetalleLista?>(null)
    val detalleInfo: StateFlow<DetalleLista?> = _detalleInfo.asStateFlow()
    private val _detalleCargando = MutableStateFlow(false)
    val detalleCargando: StateFlow<Boolean> = _detalleCargando.asStateFlow()
    private var detalleJob: Job? = null

    private fun respaldar(
        encontradas: List<ListaValidada>, errores: List<String>, diagnostico: List<String>, enCurso: Boolean,
    ) {
        val carpeta = carpetaRespaldo ?: return
        val copia = RespaldoEscaneo(encontradas.toList(), errores.toList(), diagnostico.toList(), nombreArchivoActual, enCurso)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { AlmacenRespaldo.guardar(carpeta, copia) }
    }

    private var nombreArchivoActual = "Resultados_IPTV"

    // Último resultado de escaneo: la pestaña "Resultados" lo muestra aunque
    // la pestaña "Buscar" haya vuelto al menú de modos (antes ambas leían el
    // mismo estado y los botones inferiores no llevaban a donde se tocaba).
    private val _ultimoResultado = MutableStateFlow<EstadoFlujoEscaneo.Resultados?>(null)
    val ultimoResultado: StateFlow<EstadoFlujoEscaneo.Resultados?> = _ultimoResultado.asStateFlow()

    init {
        // Si la app se cerró en pleno escaneo, se recuperan las listas que ya estaban validadas.
        carpetaRespaldo?.let { carpeta ->
            AlmacenRespaldo.cargar(carpeta)?.takeIf { it.listas.isNotEmpty() }?.let { r ->
                val aviso = if (r.enCurso) {
                    listOf(
                        "⚠️ El escaneo anterior se interrumpió. Se recuperaron ${r.listas.size} lista(s) ya validada(s)" +
                            (motivoUltimoCierre?.let { " · motivo del cierre: $it" } ?: "") + ".",
                    )
                } else emptyList()
                val erroresConAviso = aviso + r.errores
                // El aviso queda guardado y la copia deja de figurar "en curso": no se repite en cada apertura.
                if (r.enCurso) AlmacenRespaldo.guardar(carpeta, r.copy(errores = erroresConAviso, enCurso = false))
                _ultimoResultado.value = EstadoFlujoEscaneo.Resultados(
                    r.listas, erroresConAviso, r.diagnostico, emptyList(), r.nombreArchivo,
                )
            }
        }
    }

    /** Botón "Buscar" de la barra inferior: cierra cualquier detalle y vuelve al menú de modos (salvo mientras se escanea). */
    fun irABuscar() {
        cerrarDetalle()
        when (_estado.value) {
            is EstadoFlujoEscaneo.Escaneando, EstadoFlujoEscaneo.CargandoChats -> Unit
            else -> volverAElegirModo()
        }
    }

    /** Botón "Resultados" de la barra inferior: cierra el detalle para mostrar la lista de resultados. */
    fun irAResultados() = cerrarDetalle()

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
            TipoModo.LATINO_EN_VARIOS_CHATS ->
                _estado.value = EstadoFlujoEscaneo.SeleccionandoChats(chatsCache, emptySet(), soloLatino = true)
            TipoModo.HOST_EN_TODOS_LOS_CHATS, TipoModo.HOST_EN_UN_CHAT ->
                _estado.value = EstadoFlujoEscaneo.EscribiendoHost(chatsCache, modo)
            TipoModo.LATINO_EN_UN_CHAT ->
                _estado.value = EstadoFlujoEscaneo.EligiendoChatUnico(chatsCache, host = null, origenModo = TipoModo.LATINO_EN_UN_CHAT)
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
                _estado.value = EstadoFlujoEscaneo.EligiendoChatUnico(actual.chats, host = hostLimpio, origenModo = TipoModo.HOST_EN_UN_CHAT)
            else -> Unit
        }
    }

    fun chatUnicoElegido(chat: ChatTelegram) {
        val actual = _estado.value
        if (actual !is EstadoFlujoEscaneo.EligiendoChatUnico) return
        val host = actual.host
        prepararEscaneo(listOf(chat), filtroHost = host, soloLatino = host == null)
    }

    /** Vuelve del selector de archivo cancelado, o de cualquier pantalla intermedia, al menú de modos. */
    fun volverAElegirModo() {
        _estado.value = EstadoFlujoEscaneo.EligiendoModo(chatsCache)
    }

    /**
     * Botón "← Volver" de las pantallas intermedias (pedido explícito del
     * usuario: antes no había forma de retroceder y había que cerrar la
     * app). Retrocede un paso en el flujo según en qué pantalla esté:
     * - "Escribir host" y "Elegir varios chats" vuelven directo al menú de
     *   modos (son el primer paso después de elegir el modo).
     * - "Elegir un chat" vuelve a "Escribir host" si ese modo pasaba por
     *   ahí (host en un chat específico), o al menú si no (solo LATINO).
     */
    fun volver() {
        when (val actual = _estado.value) {
            is EstadoFlujoEscaneo.EligiendoTemas -> _estado.value = actual.anterior
            is EstadoFlujoEscaneo.EscribiendoHost -> volverAElegirModo()
            is EstadoFlujoEscaneo.SeleccionandoChats -> volverAElegirModo()
            is EstadoFlujoEscaneo.EligiendoChatUnico -> {
                if (actual.origenModo == TipoModo.HOST_EN_UN_CHAT) {
                    _estado.value = EstadoFlujoEscaneo.EscribiendoHost(actual.chats, TipoModo.HOST_EN_UN_CHAT)
                } else {
                    volverAElegirModo()
                }
            }
            else -> Unit
        }
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
        prepararEscaneo(chatsElegidos, filtroHost = null, soloLatino = actual.soloLatino)
    }

    /**
     * Antes de escanear, mira si alguno de los chats es un grupo con
     * "Temas"; si lo es, deja elegir cuáles temas escanear (por defecto
     * todos). Si ninguno lo es, o la consulta falla, escanea directo como
     * siempre.
     */
    private fun prepararEscaneo(chats: List<ChatTelegram>, filtroHost: String?, soloLatino: Boolean) {
        val anterior = _estado.value
        _estado.value = EstadoFlujoEscaneo.BuscandoTemas
        viewModelScope.launch {
            val foros = chats.filter { it.tipo == TipoChat.GRUPO }.mapNotNull { chat ->
                val temas = runCatching { chatsRepository.listarTemas(chat.id) }.getOrDefault(emptyList())
                if (temas.size > 1) ForoConTemas(chat, temas) else null
            }
            if (foros.isEmpty()) {
                iniciarEscaneo(chats, filtroHost, soloLatino)
            } else {
                _estado.value = EstadoFlujoEscaneo.EligiendoTemas(
                    anterior = anterior,
                    chats = chats,
                    filtroHost = filtroHost,
                    soloLatino = soloLatino,
                    foros = foros,
                    seleccion = foros.associate { it.chat.id to it.temas.map { t -> t.id }.toSet() },
                )
            }
        }
    }

    fun alternarTema(chatId: Long, temaId: Int) {
        val actual = _estado.value
        if (actual !is EstadoFlujoEscaneo.EligiendoTemas) return
        val marcados = actual.seleccion[chatId].orEmpty()
        val nuevos = if (temaId in marcados) marcados - temaId else marcados + temaId
        _estado.value = actual.copy(seleccion = actual.seleccion + (chatId to nuevos))
    }

    /** Marca o desmarca todos los temas de un grupo de una vez. */
    fun alternarTodosLosTemas(chatId: Long) {
        val actual = _estado.value
        if (actual !is EstadoFlujoEscaneo.EligiendoTemas) return
        val foro = actual.foros.firstOrNull { it.chat.id == chatId } ?: return
        val todos = foro.temas.map { it.id }.toSet()
        val nuevos = if (actual.seleccion[chatId] == todos) emptySet() else todos
        _estado.value = actual.copy(seleccion = actual.seleccion + (chatId to nuevos))
    }

    fun confirmarTemas() {
        val actual = _estado.value
        if (actual !is EstadoFlujoEscaneo.EligiendoTemas) return
        val chatsFinales = actual.chats.mapNotNull { chat ->
            val foro = actual.foros.firstOrNull { it.chat.id == chat.id } ?: return@mapNotNull chat
            val marcados = actual.seleccion[chat.id].orEmpty()
            when {
                marcados.isEmpty() -> null // sin ningún tema marcado: ese grupo se omite
                marcados.size == foro.temas.size -> chat // todos: lectura normal de siempre
                else -> chat.copy(temaIds = marcados)
            }
        }
        if (chatsFinales.isEmpty()) return
        iniciarEscaneo(chatsFinales, actual.filtroHost, actual.soloLatino)
    }

    /** Escanea un archivo .txt local, ya leído por la UI vía Storage Access Framework. */
    fun escanearArchivoLocal(nombreArchivo: String, contenido: String) {
        nombreArchivoActual = "Resultados_Latinos_" + ResultadosExportador.nombreArchivoSeguro(nombreArchivo.substringBeforeLast('.'))
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
        // Mismos nombres que el script de Python: Resultados_<host>.txt / Resultados_Latinos_<chat>.txt
        nombreArchivoActual = when {
            filtroHost != null -> "Resultados_" + ResultadosExportador.nombreArchivoSeguro(filtroHost)
            soloLatino -> "Resultados_Latinos_" +
                if (chats.size == 1) ResultadosExportador.nombreArchivoSeguro(chats[0].titulo) else "varios_chats"
            else -> "Resultados_IPTV"
        }
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
        val parcial = EstadoFlujoEscaneo.Resultados(encontradasActuales, erroresActuales, diagnosticoActual, registroActual, nombreArchivoActual)
        respaldar(encontradasActuales, erroresActuales, diagnosticoActual, enCurso = false)
        _ultimoResultado.value = parcial
        _estado.value = parcial
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
                val estadoActual = _estado.value as? EstadoFlujoEscaneo.Escaneando
                _estado.value = (estadoActual ?: EstadoFlujoEscaneo.Escaneando(0, 0, emptyList())).copy(
                    procesados = evento.procesados,
                    total = evento.total,
                    encontradas = encontradas.toList(),
                    errores = errores.toList(),
                    diagnostico = diagnostico.toList(),
                    registro = registro.toList(),
                )
            }
            is EventoEscaneo.ListaEncontrada -> {
                encontradas += evento.lista
                encontradasActuales = encontradas.toList()
                respaldar(encontradas, errores, diagnostico, enCurso = true)
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
            is EventoEscaneo.Leyendo -> {
                val estadoActual = _estado.value
                if (estadoActual is EstadoFlujoEscaneo.Escaneando) {
                    _estado.value = estadoActual.copy(
                        chatLeyendo = evento.chatTitulo,
                        mensajesChatActual = evento.mensajesChatActual,
                    )
                }
            }
            is EventoEscaneo.InfoChat -> {
                (_estado.value as? EstadoFlujoEscaneo.Escaneando)?.let {
                    _estado.value = it.copy(
                        chatLeyendo = null,
                        mensajesChatActual = 0,
                        mensajesChatsTerminados = it.mensajesChatsTerminados + evento.mensajesLeidos,
                    )
                }
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
                val resultadoFinal = EstadoFlujoEscaneo.Resultados(
                    encontradas.toList(),
                    errores.toList(),
                    diagnostico.toList(),
                    registro.toList(),
                    nombreArchivoActual,
                )
                respaldar(encontradas, errores, diagnostico, enCurso = false)
                _ultimoResultado.value = resultadoFinal
                _estado.value = resultadoFinal
            }
        }
    }

    /** Vuelve al menú de modos para escanear de nuevo. */
    fun reiniciar() = cargarChats()

    /** Abre el detalle de [lista] (pantalla aparte); la consulta extra no arranca sola, ver [consultarDetalle]. */
    fun abrirDetalle(lista: ListaValidada) {
        detalleJob?.cancel()
        _listaDetalle.value = lista
        _detalleInfo.value = null
        _detalleCargando.value = false
    }

    /** Cierra el detalle y vuelve a Resultados. */
    fun cerrarDetalle() {
        detalleJob?.cancel()
        _listaDetalle.value = null
        _detalleInfo.value = null
        _detalleCargando.value = false
    }

    /** Pide la info extra de la lista abierta (canales/series/películas, geolocalización de IP) — ver [consultarDetalleLista]. */
    fun consultarDetalle() {
        val lista = _listaDetalle.value ?: return
        if (_detalleCargando.value || _detalleInfo.value != null) return
        _detalleCargando.value = true
        detalleJob = viewModelScope.launch {
            val resultado = runCatching { consultarDetalleLista(lista) }.getOrNull()
            _detalleInfo.value = resultado
            _detalleCargando.value = false
        }
    }
}

class ScanFlowViewModelFactory(
    private val chatsRepository: ChatsRepository,
    private val escanerRepository: EscanerRepository,
    private val carpetaRespaldo: java.io.File? = null,
    private val motivoUltimoCierre: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScanFlowViewModel(chatsRepository, escanerRepository, carpetaRespaldo, motivoUltimoCierre) as T
    }
}
