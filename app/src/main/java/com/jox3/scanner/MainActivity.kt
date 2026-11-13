package com.jox3.scanner

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jox3.scanner.results.ResultadosExportador
import com.jox3.scanner.scan.EstadoFlujoEscaneo
import com.jox3.scanner.scan.ScanFlowViewModel
import com.jox3.scanner.scan.ScanFlowViewModelFactory
import com.jox3.scanner.scan.TdChatsRepository
import com.jox3.scanner.scan.TdEscanerRepository
import com.jox3.scanner.scan.TipoModo
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.telegram.CredencialesApiTelegram
import com.jox3.scanner.telegram.CredencialesBotTelegram
import com.jox3.scanner.telegram.TdLibAuthClient
import com.jox3.scanner.telegram.TelegramBotClient
import com.jox3.scanner.ui.chats.ChatsScreen
import com.jox3.scanner.ui.chats.SeleccionarChatScreen
import com.jox3.scanner.ui.login.LoginScreen
import com.jox3.scanner.ui.login.LoginViewModel
import com.jox3.scanner.ui.login.LoginViewModelFactory
import com.jox3.scanner.ui.results.ResultsScreen
import com.jox3.scanner.ui.scan.HostInputScreen
import com.jox3.scanner.ui.scan.ModoEscaneoScreen
import com.jox3.scanner.ui.scan.ScanProgressScreen
import com.jox3.scanner.ui.settings.AjustesScreen
import com.jox3.scanner.ui.setup.ApiCredentialsScreen
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.ScannJox3Theme
import com.jox3.scanner.BuildConfig
import com.jox3.scanner.update.InfoActualizacion
import com.jox3.scanner.update.UpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScannJox3Theme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    val credenciales = remember { CredencialesApiTelegram(applicationContext) }
                    var credencialesListas by remember { mutableStateOf(credenciales.tieneCredenciales()) }

                    if (!credencialesListas) {
                        ApiCredentialsScreen(
                            onGuardar = { apiId, apiHash ->
                                credenciales.guardar(apiId, apiHash)
                                credencialesListas = true
                            },
                        )
                    } else {
                        val authClient = remember { TdLibAuthClient.obtenerInstancia(applicationContext, credenciales) }
                        var autenticado by remember { mutableStateOf(false) }

                        if (!autenticado) {
                            val viewModel = viewModel<LoginViewModel>(factory = LoginViewModelFactory(authClient))
                            LoginScreen(viewModel = viewModel, onAutenticado = { autenticado = true })
                        } else {
                            PantallaPrincipal(authClient)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pantallas accesibles desde las pestañas de abajo. "Buscar" cubre todo el
 * flujo de escaneo (elegir modo → ... → progreso); cuando ese flujo llega a
 * los resultados, también se pueden ver desde la pestaña "Resultados" sin
 * perder el estado, porque ambas leen del mismo [ScanFlowViewModel]
 * hospedado aquí arriba.
 */
private enum class Pestana(val etiqueta: String, val icono: String) {
    BUSCAR("Buscar", "🔎"),
    RESULTADOS("Resultados", "📋"),
    AJUSTES("Ajustes", "⚙️"),
}

@Composable
private fun PantallaPrincipal(authClient: TdLibAuthClient) {
    // Ambos ViewModels se hospedan aquí, por encima del selector de
    // pestañas, para que su estado (chats cargados, escaneo en curso,
    // comprobación de actualizaciones) no se pierda al cambiar de pestaña.
    val updateViewModel: UpdateViewModel = viewModel()
    val estadoActualizacion by updateViewModel.estado.collectAsState()

    val chatsRepository = remember { TdChatsRepository(authClient) }
    val escanerRepository = remember { TdEscanerRepository(authClient) }
    val scanViewModel: ScanFlowViewModel = viewModel(
        factory = ScanFlowViewModelFactory(chatsRepository, escanerRepository),
    )
    val estadoEscaneo by scanViewModel.estado.collectAsState()

    // Launcher para volver de la pantalla de "Instalar apps desconocidas"
    // y reintentar la descarga si el usuario concedió el permiso.
    var infoPendientePermiso by remember { mutableStateOf<InfoActualizacion?>(null) }
    val lanzadorAjustesPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        infoPendientePermiso?.let { updateViewModel.permisoConcedidoReintentarDescarga(it) }
        infoPendientePermiso = null
    }

    LaunchedEffect(Unit) {
        updateViewModel.comprobarActualizacion(versionInstalada = BuildConfig.VERSION_NAME)
    }

    var pestanaActual by remember { mutableIntStateOf(Pestana.BUSCAR.ordinal) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Pestana.entries.forEachIndexed { indice, pestana ->
                    NavigationBarItem(
                        selected = pestanaActual == indice,
                        onClick = { pestanaActual = indice },
                        icon = { Text(pestana.icono) },
                        label = { Text(pestana.etiqueta) },
                    )
                }
            }
        },
    ) { paddingInterno ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingInterno)) {
            when (Pestana.entries[pestanaActual]) {
                Pestana.BUSCAR -> PantallaEscaneo(estadoEscaneo, scanViewModel)
                Pestana.RESULTADOS -> PantallaResultadosTab(estadoEscaneo, scanViewModel)
                Pestana.AJUSTES -> {
                    val contexto = LocalContext.current
                    val credencialesBot = remember { CredencialesBotTelegram(contexto) }
                    AjustesScreen(
                        versionInstalada = BuildConfig.VERSION_NAME,
                        estadoActualizacion = estadoActualizacion,
                        onComprobarActualizacion = {
                            updateViewModel.comprobarActualizacion(versionInstalada = BuildConfig.VERSION_NAME)
                        },
                        onActualizar = { info -> updateViewModel.iniciarDescarga(info) },
                        onAbrirAjustesPermiso = { info ->
                            infoPendientePermiso = info
                            lanzadorAjustesPermiso.launch(updateViewModel.intentSolicitarPermiso())
                        },
                        botTokenGuardado = credencialesBot.obtenerBotToken(),
                        chatIdGuardado = credencialesBot.obtenerChatId(),
                        onGuardarBot = { token, chatId -> credencialesBot.guardar(token, chatId) },
                    )
                }
            }
        }
    }
}

/** Pestaña "Resultados": muestra el último resultado disponible, si ya hubo un escaneo. */
@Composable
private fun PantallaResultadosTab(estado: EstadoFlujoEscaneo, viewModel: ScanFlowViewModel) {
    val contexto = LocalContext.current
    when (estado) {
        is EstadoFlujoEscaneo.Resultados -> {
            ResultsScreenConectada(
                listas = estado.listas,
                errores = estado.errores,
                diagnostico = estado.diagnostico,
                registro = estado.registro,
                onEscanearDeNuevo = viewModel::reiniciar,
                onCompartirRegistro = { compartirRegistro(contexto, estado.errores, estado.diagnostico, estado.registro) },
            )
        }
        is EstadoFlujoEscaneo.Escaneando -> {
            ScanProgressScreen(
                procesados = estado.procesados,
                total = estado.total,
                encontradas = estado.encontradas,
                errores = estado.errores,
                diagnostico = estado.diagnostico,
                registro = estado.registro,
                inicioMs = estado.inicioMs,
                onDetener = viewModel::detenerYVerParciales,
                onCompartirRegistro = { compartirRegistro(contexto, estado.errores, estado.diagnostico, estado.registro) },
            )
        }
        else -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Aún no hay resultados. Ve a la pestaña \"Buscar\" para escanear.")
            }
        }
    }
}

@Composable
private fun PantallaEscaneo(estado: EstadoFlujoEscaneo, viewModel: ScanFlowViewModel) {
    val contexto = LocalContext.current

    // Modo "Escanear un archivo .txt del dispositivo": abre el selector de
    // archivos del sistema (Storage Access Framework) y, si el usuario
    // elige uno, lee su contenido y arranca el escaneo con él.
    val selectorArchivo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            viewModel.volverAElegirModo()
            return@rememberLauncherForActivityResult
        }
        val nombre = nombreDeArchivo(contexto, uri) ?: "archivo.txt"
        val contenido = contexto.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
        if (contenido != null) {
            viewModel.escanearArchivoLocal(nombre, contenido)
        } else {
            viewModel.volverAElegirModo()
        }
    }

    when (val s = estado) {
        EstadoFlujoEscaneo.CargandoChats -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = AcentoApp)
            }
        }

        is EstadoFlujoEscaneo.EligiendoModo -> {
            ModoEscaneoScreen(
                onModoElegido = { modo ->
                    if (modo == TipoModo.ARCHIVO_LOCAL) {
                        selectorArchivo.launch(arrayOf("text/plain"))
                    } else {
                        viewModel.elegirModo(modo)
                    }
                },
            )
        }

        is EstadoFlujoEscaneo.EscribiendoHost -> {
            HostInputScreen(onHostConfirmado = viewModel::hostConfirmado)
        }

        is EstadoFlujoEscaneo.EligiendoChatUnico -> {
            SeleccionarChatScreen(chats = s.chats, onChatElegido = viewModel::chatUnicoElegido)
        }

        is EstadoFlujoEscaneo.SeleccionandoChats -> {
            ChatsScreen(
                chats = s.chats,
                seleccionados = s.seleccionados,
                onAlternarSeleccion = viewModel::alternarSeleccion,
                onEscanear = viewModel::iniciarEscaneoDeSeleccionados,
            )
        }

        is EstadoFlujoEscaneo.Escaneando -> {
            ScanProgressScreen(
                procesados = s.procesados,
                total = s.total,
                encontradas = s.encontradas,
                errores = s.errores,
                diagnostico = s.diagnostico,
                registro = s.registro,
                inicioMs = s.inicioMs,
                onDetener = viewModel::detenerYVerParciales,
                onCompartirRegistro = { compartirRegistro(contexto, s.errores, s.diagnostico, s.registro) },
            )
        }

        is EstadoFlujoEscaneo.Resultados -> {
            ResultsScreenConectada(
                listas = s.listas,
                errores = s.errores,
                diagnostico = s.diagnostico,
                registro = s.registro,
                onEscanearDeNuevo = viewModel::reiniciar,
                onCompartirRegistro = { compartirRegistro(contexto, s.errores, s.diagnostico, s.registro) },
            )
        }
    }
}

/**
 * [ResultsScreen] con "Guardar .txt" y "Enviar a Telegram" ya conectados:
 * el primero arma el archivo y abre el selector de apps del sistema para
 * guardarlo/compartirlo; el segundo lo manda al bot configurado en
 * Ajustes ([CredencialesBotTelegram]) — equivalente a
 * `send_file_to_telegram()` del script de Python.
 */
@Composable
private fun ResultsScreenConectada(
    listas: List<ListaValidada>,
    errores: List<String>,
    diagnostico: List<String> = emptyList(),
    registro: List<String> = emptyList(),
    onEscanearDeNuevo: () -> Unit,
    onCompartirRegistro: (() -> Unit)? = null,
) {
    val contexto = LocalContext.current
    val credencialesBot = remember { CredencialesBotTelegram(contexto) }
    val scope = rememberCoroutineScope()

    ResultsScreen(
        listas = listas,
        errores = errores,
        diagnostico = diagnostico,
        registro = registro,
        onEscanearDeNuevo = onEscanearDeNuevo,
        onCompartirRegistro = onCompartirRegistro,
        onGuardarTxt = {
            val archivo = ResultadosExportador.guardarArchivo(contexto, listas)
            ResultadosExportador.compartirArchivo(contexto, archivo)
        },
        onEnviarTelegram = {
            val token = credencialesBot.obtenerBotToken()
            val chatId = credencialesBot.obtenerChatId()
            if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
                Toast.makeText(contexto, "Configura el bot de Telegram en Ajustes primero.", Toast.LENGTH_LONG).show()
            } else {
                val archivo = ResultadosExportador.guardarArchivo(contexto, listas)
                scope.launch {
                    val error = TelegramBotClient.enviarArchivo(token, chatId, archivo)
                    val mensaje = error?.let { "No se pudo enviar: $it" } ?: "Enviado a Telegram."
                    Toast.makeText(contexto, mensaje, Toast.LENGTH_LONG).show()
                }
            }
        },
    )
}

/**
 * Arma el .txt con errores + diagnóstico por chat + registro paso a paso
 * (ver [ResultadosExportador.generarContenidoRegistro]) y abre el selector
 * de apps del sistema para guardarlo/compartirlo — pedido explícito del
 * usuario para poder mandar ese texto completo sin depender de una captura
 * de pantalla que recorta el panel colapsable.
 */
private fun compartirRegistro(
    contexto: Context,
    errores: List<String>,
    diagnostico: List<String>,
    registro: List<String>,
) {
    val archivo = ResultadosExportador.guardarArchivoRegistro(contexto, errores, diagnostico, registro)
    ResultadosExportador.compartirArchivo(contexto, archivo)
}

/** Nombre a mostrar del archivo elegido en el selector del sistema (si no se puede leer, cae a null). */
private fun nombreDeArchivo(contexto: Context, uri: Uri): String? {
    return contexto.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (indice >= 0) cursor.getString(indice) else null
    }
}
