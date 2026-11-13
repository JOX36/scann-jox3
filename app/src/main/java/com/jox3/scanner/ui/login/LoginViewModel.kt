package com.jox3.scanner.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jox3.scanner.telegram.TelegramAuthClient
import com.jox3.scanner.telegram.TelegramAuthState
import kotlinx.coroutines.launch

class LoginViewModel(private val authClient: TelegramAuthClient) : ViewModel() {

    val estado = authClient.estado

    init {
        viewModelScope.launch { authClient.conectar() }
    }

    fun onTelefonoConfirmado(telefono: String) {
        viewModelScope.launch { authClient.enviarTelefono(telefono) }
    }

    fun onCodigoConfirmado(codigo: String) {
        viewModelScope.launch { authClient.enviarCodigo(codigo) }
    }

    fun onPasswordConfirmada(password: String) {
        viewModelScope.launch { authClient.enviarPassword2FA(password) }
    }

    fun reintentar() {
        viewModelScope.launch { authClient.conectar() }
    }
}

class LoginViewModelFactory(
    private val authClient: TelegramAuthClient
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LoginViewModel(authClient) as T
    }
}
