package com.jox3.scanner.telegram

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementación real de [TelegramAuthClient] contra TDLib, usando la
 * librería prebuilt (ver app/build.gradle.kts) en vez de compilar TDLib
 * desde cero — decisión tomada explícitamente para no depender de una build
 * nativa (CMake + NDK) que este entorno no puede verificar localmente.
 *
 * TDLib dirige la autenticación emitiendo `TdApi.UpdateAuthorizationState`
 * a medida que cambia; esta clase traduce esos estados a [TelegramAuthState]
 * para que la UI (ya construida en Etapa 1) no tenga que cambiar.
 */
class TdLibAuthClient(
    private val contexto: Context,
    private val credenciales: CredencialesApiTelegram,
) : TelegramAuthClient {

    private val _estado = MutableStateFlow<TelegramAuthState>(TelegramAuthState.SinIniciar)
    override val estado: StateFlow<TelegramAuthState> = _estado.asStateFlow()

    private var cliente: Client? = null

    /**
     * Último estado derivado de la autorización real de TDLib (sin contar
     * errores puntuales de una solicitud). Un error de "código incorrecto",
     * por ejemplo, no hace que TDLib deje de esperar el código — así que al
     * reintentar hay que volver a este estado, no reconectar desde cero.
     */
    private var ultimoEstadoAutorizacion: TelegramAuthState = TelegramAuthState.SinIniciar

    override suspend fun conectar() {
        val clienteActual = cliente
        if (clienteActual != null) {
            _estado.value = ultimoEstadoAutorizacion
            return
        }

        val apiId = credenciales.obtenerApiId()
        val apiHash = credenciales.obtenerApiHash()
        if (apiId == null || apiHash == null) {
            _estado.value = TelegramAuthState.Error(
                "Faltan las credenciales de la API de Telegram (API_ID / API_HASH)."
            )
            return
        }

        _estado.value = TelegramAuthState.Conectando
        cliente = Client.create({ objeto -> manejarActualizacion(objeto) }, null, null)
    }

    override suspend fun enviarTelefono(telefono: String) {
        cliente?.send(TdApi.SetAuthenticationPhoneNumber(telefono, null)) { resultado ->
            manejarPosibleError(resultado)
        }
    }

    override suspend fun enviarCodigo(codigo: String) {
        cliente?.send(TdApi.CheckAuthenticationCode(codigo)) { resultado ->
            manejarPosibleError(resultado)
        }
    }

    override suspend fun enviarPassword2FA(password: String) {
        cliente?.send(TdApi.CheckAuthenticationPassword(password)) { resultado ->
            manejarPosibleError(resultado)
        }
    }

    private fun manejarActualizacion(objeto: TdApi.Object) {
        val actualizacion = objeto as? TdApi.UpdateAuthorizationState ?: return

        val nuevoEstado = when (actualizacion.authorizationState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                enviarParametrosIniciales()
                TelegramAuthState.Conectando
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramAuthState.EsperandoTelefono
            is TdApi.AuthorizationStateWaitCode -> TelegramAuthState.EsperandoCodigo
            is TdApi.AuthorizationStateWaitPassword -> TelegramAuthState.EsperandoPassword2FA
            is TdApi.AuthorizationStateReady -> TelegramAuthState.Autenticado
            is TdApi.AuthorizationStateClosed -> TelegramAuthState.Error("Se cerró la sesión de Telegram.")
            else -> return // Logging out, closing, etc. — no cambia la UI de login.
        }

        ultimoEstadoAutorizacion = nuevoEstado
        _estado.value = nuevoEstado
    }

    private fun enviarParametrosIniciales() {
        val apiId = credenciales.obtenerApiId() ?: return
        val apiHash = credenciales.obtenerApiHash() ?: return

        val parametros = TdApi.SetTdlibParameters().apply {
            databaseDirectory = "${contexto.filesDir.absolutePath}/tdlib"
            filesDirectory = "${contexto.filesDir.absolutePath}/tdlib-files"
            useMessageDatabase = true
            useChatInfoDatabase = true
            useFileDatabase = true
            useSecretChats = false
            this.apiId = apiId
            this.apiHash = apiHash
            systemLanguageCode = Locale.getDefault().language.ifBlank { "es" }
            deviceModel = Build.MODEL ?: "Android"
            systemVersion = Build.VERSION.RELEASE ?: "unknown"
            applicationVersion = "0.1.0"
        }
        cliente?.send(parametros) { resultado -> manejarPosibleError(resultado) }
    }

    private fun manejarPosibleError(resultado: TdApi.Object) {
        if (resultado is TdApi.Error) {
            _estado.value = TelegramAuthState.Error(resultado.message ?: "Error desconocido de Telegram")
        }
    }

    /**
     * Envía cualquier petición de TDLib y espera su respuesta como una
     * suspend fun normal, en vez del callback crudo de [Client.send]. La
     * usan las implementaciones reales de escaneo (listar chats, leer
     * historial) sobre esta misma sesión ya autenticada — no abren un
     * segundo Client.
     */
    suspend fun enviarYEsperar(peticion: TdApi.Function<*>): TdApi.Object = suspendCancellableCoroutine { continuacion ->
        val clienteActual = cliente
        if (clienteActual == null) {
            continuacion.resumeWithException(IllegalStateException("TDLib no está conectado todavía."))
            return@suspendCancellableCoroutine
        }
        clienteActual.send(peticion) { resultado ->
            if (resultado is TdApi.Error) {
                continuacion.resumeWithException(RuntimeException(resultado.message ?: "Error de Telegram"))
            } else {
                continuacion.resume(resultado)
            }
        }
    }

    companion object {
        @Volatile
        private var instancia: TdLibAuthClient? = null

        /**
         * TDLib solo permite que **un** `Client` tenga abierta a la vez la
         * base de datos local (`databaseDirectory`); un segundo `Client`
         * apuntando a la misma carpeta falla con
         * "Can't lock file ... because it is already in use by current
         * program". Si cada recreación de `MainActivity` (por ejemplo, al
         * salir y volver a entrar sin que Android mate el proceso del todo)
         * crea un `TdLibAuthClient` nuevo vía `remember { ... }`, el objeto
         * viejo — y su `Client` nativo — nunca se cierra, así que el nuevo
         * choca con él. La solución es que exista un único `TdLibAuthClient`
         * por proceso, reutilizado aunque se recree la Activity.
         */
        fun obtenerInstancia(contexto: Context, credenciales: CredencialesApiTelegram): TdLibAuthClient {
            return instancia ?: synchronized(this) {
                instancia ?: TdLibAuthClient(contexto.applicationContext, credenciales).also { instancia = it }
            }
        }
    }
}
