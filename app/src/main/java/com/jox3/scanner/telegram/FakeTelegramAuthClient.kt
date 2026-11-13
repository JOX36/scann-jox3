package com.jox3.scanner.telegram

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Implementación TEMPORAL mientras se integra TDLib de verdad.
 *
 * Simula los tiempos de red reales para que la UI (spinners, estados de
 * carga) se pueda probar y ajustar ya mismo, sin esperar a tener TDLib
 * compilando. Acepta cualquier teléfono y el código "12345".
 */
class FakeTelegramAuthClient : TelegramAuthClient {

    private val _estado = MutableStateFlow<TelegramAuthState>(TelegramAuthState.SinIniciar)
    override val estado: StateFlow<TelegramAuthState> = _estado

    override suspend fun conectar() {
        _estado.value = TelegramAuthState.Conectando
        delay(800)
        _estado.value = TelegramAuthState.EsperandoTelefono
    }

    override suspend fun enviarTelefono(telefono: String) {
        _estado.value = TelegramAuthState.Conectando
        delay(1000)
        _estado.value = TelegramAuthState.EsperandoCodigo
    }

    override suspend fun enviarCodigo(codigo: String) {
        _estado.value = TelegramAuthState.Conectando
        delay(800)
        if (codigo == "12345") {
            _estado.value = TelegramAuthState.Autenticado
        } else {
            _estado.value = TelegramAuthState.Error("Código incorrecto (prueba con 12345 en esta versión de prueba)")
        }
    }

    override suspend fun enviarPassword2FA(password: String) {
        _estado.value = TelegramAuthState.Conectando
        delay(800)
        _estado.value = TelegramAuthState.Autenticado
    }
}
