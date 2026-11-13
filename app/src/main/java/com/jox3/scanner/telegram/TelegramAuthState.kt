package com.jox3.scanner.telegram

/**
 * Estados del flujo de login de Telegram (equivalente a lo que el script de
 * Python hacía con client.connect() / send_code_request() / sign_in()).
 */
sealed class TelegramAuthState {
    /** Aún no se ha intentado conectar. */
    data object SinIniciar : TelegramAuthState()

    /** Conectando con los servidores de Telegram. */
    data object Conectando : TelegramAuthState()

    /** Esperando que el usuario escriba su número de teléfono. */
    data object EsperandoTelefono : TelegramAuthState()

    /** Código SMS/Telegram enviado, esperando que el usuario lo escriba. */
    data object EsperandoCodigo : TelegramAuthState()

    /** Cuenta con verificación en dos pasos: falta la contraseña. */
    data object EsperandoPassword2FA : TelegramAuthState()

    /** Sesión iniciada correctamente. */
    data object Autenticado : TelegramAuthState()

    data class Error(val mensaje: String) : TelegramAuthState()
}
