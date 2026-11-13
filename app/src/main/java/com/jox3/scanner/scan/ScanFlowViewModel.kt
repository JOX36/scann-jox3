package com.jox3.scanner.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.scan.engine.ListaValidada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estados del flujo completo: elegir chats → escanear → ver resultados. */
sealed class EstadoFlujoEscaneo {
    object CargandoChats : EstadoFlujoEscaneo()
    data class SeleccionandoChats(
        val chats: List<ChatTelegram>,
        val seleccionados: Set<Long>,
    ) : EstadoFlujoEscaneo()
    data class Escaneando(
        val procesados: Int,
        val total: Int,
        val encontradas: List<ListaValidada>,
    ) : EstadoFlujoEscaneo()
    data class Resultados(val listas: List<ListaValidada>) : EstadoFlujoEscaneo()
}

class ScanFlowViewModel(
    private val chatsRepository: ChatsRepository = FakeChatsRepository(),
    private val escanerRepository: EscanerRepository = FakeEscanerRepository(),
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoFlujoEscaneo>(EstadoFlujoEscaneo.CargandoChats)
    val estado: StateFlow<EstadoFlujoEscaneo> = _estado.asStateFlow()

    init {
        cargarChats()
    }

    private fun cargarChats() {
        _estado.value = EstadoFlujoEscaneo.CargandoChats
        viewModelScope.launch {
            val chats = chatsRepository.listarChats()
            _estado.value = EstadoFlujoEscaneo.SeleccionandoChats(chats, emptySet())
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

    fun iniciarEscaneo() {
        val actual = _estado.value
        if (actual !is EstadoFlujoEscaneo.SeleccionandoChats || actual.seleccionados.isEmpty()) return
        val chatsElegidos = actual.chats.filter { it.id in actual.seleccionados }

        viewModelScope.launch {
            val encontradas = mutableListOf<ListaValidada>()
            _estado.value = EstadoFlujoEscaneo.Escaneando(0, 0, emptyList())

            escanerRepository.escanear(chatsElegidos).collect { evento ->
                when (evento) {
                    is EventoEscaneo.Progreso -> {
                        _estado.value = EstadoFlujoEscaneo.Escaneando(evento.procesados, evento.total, encontradas.toList())
                    }
                    is EventoEscaneo.ListaEncontrada -> {
                        encontradas += evento.lista
                        val estadoActual = _estado.value
                        if (estadoActual is EstadoFlujoEscaneo.Escaneando) {
                            _estado.value = estadoActual.copy(encontradas = encontradas.toList())
                        }
                    }
                    EventoEscaneo.Finalizado -> {
                        _estado.value = EstadoFlujoEscaneo.Resultados(encontradas.toList())
                    }
                }
            }
        }
    }

    /** Vuelve a la pantalla de selección de chats para escanear de nuevo. */
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
