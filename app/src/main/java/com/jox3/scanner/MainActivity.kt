package com.jox3.scanner

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jox3.scanner.results.ResultadosExportador
import com.jox3.scanner.scan.EstadoFlujoEscaneo
import com.jox3.scanner.scan.ScanFlowViewModel
import com.jox3.scanner.scan.ScanFlowViewModelFactory
import com.jox3.scanner.scan.TdChatsRepository
import com.jox3.scanner.scan.TdEscanerRepository
import com.jox3.scanner.scan.TipoModo
import com.jox3.scanner.scan.engine.DetalleLista
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.ui.chats.ElegirTemasScreen
import com.jox3.scanner.ui.common.FondoConGlow
import com.jox3.scanner.ui.results.DetalleListaScreen
import com.jox3.scanner.ui.verificador.VerificadorScreen
import com.jox3.scanner.verificador.VerificadorViewModel
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
import com.jox3.scanner.ui.theme.AcentoSuaveApp
import com.jox3.scanner.ui.theme.BordeApp
import com.jox3.scanner.ui.theme.ScannJox3Theme
import com.jox3.scanner.ui.theme.SuperficieFuerteApp
import com.jox3.scanner.ui.theme.TextoTerciarioApp
import com.jox3.scanner.BuildConfig
import com.jox3.scanner.update.EstadoActualizacion
import com.jox3.scanner.update.InfoActualizacion
import com.jox3.scanner.update.UpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        com.jox3.scanner.scan.EstadoApp.visible = true
    }

    override fun onStop() {
        com.jox3.scanner.scan.EstadoApp.visible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Guarda la traza si la app se cierra por un error, para poder mostrar el motivo al reabrir.
        com.jox3.scanner.scan.DiagnosticoCierre.instalar(filesDir)
        setContent {
            ScannJox3Theme {
                FondoConGlow(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
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
    VERIFICADOR("Verificar", "✅"),
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
        viewModelStoreOwner = com.jox3.scanner.scan.AlmacenViewModelsProceso,
        factory = run {
            val contextoApp = LocalContext.current
            val motivo = remember { com.jox3.scanner.scan.DiagnosticoCierre.motivoUltimoCierre(contextoApp, contextoApp.filesDir) }
            ScanFlowViewModelFactory(chatsRepository, escanerRepository, contextoApp.filesDir, motivo, contextoApp.applicationContext)
        },
    )
    val estadoEscaneo by scanViewModel.estado.collectAsState()
    val contextoVerificador = LocalContext.current.applicationContext
    val verificadorViewModel: VerificadorViewModel = viewModel(
        viewModelStoreOwner = com.jox3.scanner.scan.AlmacenViewModelsProceso,
        factory = com.jox3.scanner.verificador.VerificadorViewModelFactory(contextoVerificador),
    )
    val estadoVerificador by verificadorViewModel.estado.collectAsState()

    // Permiso de notificaciones (Android 13+) la primera vez que se escanea; el servicio y los
    // avisos los gestiona el propio ViewModel (ver ScanFlowViewModel.vigilarServicio).
    val permisoNotificaciones = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { /* con o sin permiso el servicio funciona; solo cambia si se ve la notificación */ }
    val escaneando = estadoEscaneo is EstadoFlujoEscaneo.Escaneando
    LaunchedEffect(escaneando) {
        if (escaneando && android.os.Build.VERSION.SDK_INT >= 33) {
            permisoNotificaciones.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

    // Al terminar la descarga, abre el instalador del sistema solo — desde
    // la propia app en primer plano (un receiver en segundo plano no puede
    // en Android 10+). Si la app estaba en segundo plano al terminar, se
    // abre en cuanto el usuario vuelve a ella. Una sola vez por descarga.
    val actividad = LocalContext.current as? ComponentActivity
    LaunchedEffect(estadoActualizacion) {
        val estadoActual = estadoActualizacion
        if (actividad != null && estadoActual is EstadoActualizacion.ListoParaInstalar && !estadoActual.lanzado) {
            actividad.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updateViewModel.intentInstalar()?.let { actividad.startActivity(it) }
                updateViewModel.instaladorLanzado()
            }
        }
    }

    var pestanaActual by remember { mutableIntStateOf(Pestana.BUSCAR.ordinal) }

    // Botón atrás del sistema: primero cierra el detalle, luego retrocede un
    // paso del flujo de búsqueda; en otras pestañas vuelve a "Buscar".
    val detalleAbierto by scanViewModel.listaDetalle.collectAsState()
    BackHandler(enabled = detalleAbierto != null) { scanViewModel.cerrarDetalle() }
    BackHandler(
        enabled = detalleAbierto == null && pestanaActual == Pestana.BUSCAR.ordinal &&
            (estadoEscaneo is EstadoFlujoEscaneo.EscribiendoHost ||
                estadoEscaneo is EstadoFlujoEscaneo.SeleccionandoChats ||
                estadoEscaneo is EstadoFlujoEscaneo.EligiendoChatUnico ||
                estadoEscaneo is EstadoFlujoEscaneo.EligiendoTemas),
    ) { scanViewModel.volver() }
    BackHandler(enabled = pestanaActual == Pestana.VERIFICADOR.ordinal && estadoVerificador.lista != null) {
        verificadorViewModel.nueva()
    }
    BackHandler(enabled = pestanaActual != Pestana.BUSCAR.ordinal) { pestanaActual = Pestana.BUSCAR.ordinal }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = SuperficieFuerteApp,
                contentColor = TextoTerciarioApp,
                modifier = Modifier.border(BorderStroke(1.dp, BordeApp)).padding(top = 1.dp),
            ) {
                Pestana.entries.forEachIndexed { indice, pestana ->
                    NavigationBarItem(
                        selected = pestanaActual == indice,
                        onClick = {
                            // Cerrar cualquier detalle/paso intermedio para que el botón
                            // lleve de verdad a la pestaña tocada (antes había que usar "atrás").
                            when (Pestana.entries[indice]) {
                                Pestana.BUSCAR -> scanViewModel.irABuscar()
                                Pestana.RESULTADOS -> scanViewModel.irAResultados()
                                Pestana.VERIFICADOR -> if (pestanaActual == indice) verificadorViewModel.nueva()
                                Pestana.AJUSTES -> Unit
                            }
                            pestanaActual = indice
                        },
                        icon = { Text(pestana.icono) },
                        label = { Text(pestana.etiqueta) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AcentoApp,
                            selectedTextColor = AcentoApp,
                            indicatorColor = AcentoSuaveApp,
                            unselectedIconColor = TextoTerciarioApp,
                            unselectedTextColor = TextoTerciarioApp,
                        ),
                    )
                }
            }
        },
    ) { paddingInterno ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingInterno)) {
            when (Pestana.entries[pestanaActual]) {
                Pestana.BUSCAR -> PantallaEscaneo(estadoEscaneo, scanViewModel)
                Pestana.RESULTADOS -> PantallaResultadosTab(estadoEscaneo, scanViewModel)
                Pestana.VERIFICADOR -> {
                    val listaVerificada = estadoVerificador.lista
                    if (listaVerificada == null) {
                        VerificadorScreen(
                            texto = estadoVerificador.texto,
                            verificando = estadoVerificador.verificando,
                            error = estadoVerificador.error,
                            onTextoCambiado = verificadorViewModel::cambiarTexto,
                            onVerificar = verificadorViewModel::verificar,
                        )
                    } else {
                        DetalleListaConectada(
                            lista = listaVerificada,
                            detalle = estadoVerificador.detalle,
                            cargando = estadoVerificador.detalleCargando,
                            onVolver = verificadorViewModel::nueva,
                            onConsultar = verificadorViewModel::consultarDetalle,
                        )
                    }
                }
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
                        onInstalar = { updateViewModel.intentInstalar()?.let { contexto.startActivity(it) } },
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
    val listaDetalle by viewModel.listaDetalle.collectAsState()
    val ultimo by viewModel.ultimoResultado.collectAsState()
    val mostrar: EstadoFlujoEscaneo = if (estado is EstadoFlujoEscaneo.Escaneando) estado else (ultimo ?: estado)
    when (mostrar) {
        is EstadoFlujoEscaneo.Resultados -> {
            val estado = mostrar
            val listaElegida = listaDetalle
            if (listaElegida != null) {
                val detalleInfo by viewModel.detalleInfo.collectAsState()
                val detalleCargando by viewModel.detalleCargando.collectAsState()
                DetalleListaConectada(
                    lista = listaElegida,
                    detalle = detalleInfo,
                    cargando = detalleCargando,
                    onVolver = viewModel::cerrarDetalle,
                    onConsultar = viewModel::consultarDetalle,
                )
            } else {
                ResultsScreenConectada(
                    listas = estado.listas,
                    errores = estado.errores,
                    diagnostico = estado.diagnostico,
                    registro = estado.registro,
                    onEscanearDeNuevo = viewModel::reiniciar,
                    onCompartirRegistro = { compartirRegistro(contexto, estado.errores, estado.diagnostico, estado.registro) },
                    onListaElegida = viewModel::abrirDetalle,
                    nombreArchivo = estado.nombreArchivo,
                )
            }
        }
        is EstadoFlujoEscaneo.Escaneando -> {
            val estado = mostrar
            ScanProgressScreen(
                procesados = estado.procesados,
                total = estado.total,
                encontradas = estado.encontradas,
                errores = estado.errores,
                diagnostico = estado.diagnostico,
                registro = estado.registro,
                inicioMs = estado.inicioMs,
                chatLeyendo = estado.chatLeyendo,
                mensajesChatActual = estado.mensajesChatActual,
                mensajesTotales = estado.mensajesChatsTerminados,
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

        EstadoFlujoEscaneo.BuscandoTemas -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = AcentoApp)
            }
        }

        is EstadoFlujoEscaneo.EligiendoTemas -> {
            ElegirTemasScreen(
                foros = s.foros,
                seleccion = s.seleccion,
                onAlternarTema = viewModel::alternarTema,
                onAlternarTodos = viewModel::alternarTodosLosTemas,
                onEscanear = viewModel::confirmarTemas,
                onVolver = viewModel::volver,
            )
        }

        is EstadoFlujoEscaneo.EscribiendoHost -> {
            HostInputScreen(onHostConfirmado = viewModel::hostConfirmado, onVolver = viewModel::volver)
        }

        is EstadoFlujoEscaneo.EligiendoChatUnico -> {
            SeleccionarChatScreen(chats = s.chats, onChatElegido = viewModel::chatUnicoElegido, onVolver = viewModel::volver)
        }

        is EstadoFlujoEscaneo.SeleccionandoChats -> {
            ChatsScreen(
                chats = s.chats,
                seleccionados = s.seleccionados,
                onAlternarSeleccion = viewModel::alternarSeleccion,
                onEscanear = viewModel::iniciarEscaneoDeSeleccionados,
                onVolver = viewModel::volver,
                soloLatino = s.soloLatino,
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
                chatLeyendo = s.chatLeyendo,
                mensajesChatActual = s.mensajesChatActual,
                mensajesTotales = s.mensajesChatsTerminados,
                onDetener = viewModel::detenerYVerParciales,
                onCompartirRegistro = { compartirRegistro(contexto, s.errores, s.diagnostico, s.registro) },
            )
        }

        is EstadoFlujoEscaneo.Resultados -> {
            val listaDetalle by viewModel.listaDetalle.collectAsState()
            val listaElegida = listaDetalle
            if (listaElegida != null) {
                val detalleInfo by viewModel.detalleInfo.collectAsState()
                val detalleCargando by viewModel.detalleCargando.collectAsState()
                DetalleListaConectada(
                    lista = listaElegida,
                    detalle = detalleInfo,
                    cargando = detalleCargando,
                    onVolver = viewModel::cerrarDetalle,
                    onConsultar = viewModel::consultarDetalle,
                )
            } else {
                ResultsScreenConectada(
                    listas = s.listas,
                    errores = s.errores,
                    diagnostico = s.diagnostico,
                    registro = s.registro,
                    onEscanearDeNuevo = viewModel::reiniciar,
                    onCompartirRegistro = { compartirRegistro(contexto, s.errores, s.diagnostico, s.registro) },
                    onListaElegida = viewModel::abrirDetalle,
                    nombreArchivo = s.nombreArchivo,
                )
            }
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
    onListaElegida: (ListaValidada) -> Unit = {},
    nombreArchivo: String = "Resultados_IPTV",
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
        onListaElegida = onListaElegida,
        onGuardarTxt = {
            val archivo = ResultadosExportador.guardarArchivo(contexto, listas, nombreArchivo)
            ResultadosExportador.compartirArchivo(contexto, archivo)
        },
        onEnviarTelegram = {
            val token = credencialesBot.obtenerBotToken()
            val chatId = credencialesBot.obtenerChatId()
            if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
                Toast.makeText(contexto, "Configura el bot de Telegram en Ajustes primero.", Toast.LENGTH_LONG).show()
            } else {
                val archivo = ResultadosExportador.guardarArchivo(contexto, listas, nombreArchivo)
                scope.launch {
                    val error = TelegramBotClient.enviarArchivo(token, chatId, archivo, caption = nombreArchivo.replace('_', ' '))
                    val mensaje = error?.let { "No se pudo enviar: $it" } ?: "Enviado a Telegram."
                    Toast.makeText(contexto, mensaje, Toast.LENGTH_LONG).show()
                }
            }
        },
    )
}

/**
 * [DetalleListaScreen] con "Guardar .txt" y "Enviar a Telegram" ya
 * conectados, igual que [ResultsScreenConectada] pero para una sola lista
 * con su [DetalleLista] — pedido explícito del usuario: poder guardar o
 * enviar SOLO la lista que está mirando, sin mandar también el resto.
 * Esos botones no aparecen hasta que [detalle] no sea `null` (ver
 * [DetalleListaScreen]), así que no hace falta más condición acá.
 */
@Composable
private fun DetalleListaConectada(
    lista: ListaValidada,
    detalle: DetalleLista?,
    cargando: Boolean,
    onVolver: () -> Unit,
    onConsultar: () -> Unit,
) {
    val contexto = LocalContext.current
    val credencialesBot = remember { CredencialesBotTelegram(contexto) }
    val scope = rememberCoroutineScope()

    // Al abrir el detalle la consulta arranca sola: el usuario pidió ver
    // la info completa (como en su ejemplo) sin tener que ir a Telegram ni
    // tocar un botón extra. Guardar/Enviar siguen siendo acciones aparte.
    LaunchedEffect(lista) { onConsultar() }

    DetalleListaScreen(
        lista = lista,
        detalle = detalle,
        cargando = cargando,
        onVolver = onVolver,
        onConsultar = onConsultar,
        onGuardarTxt = if (cargando) null else {
            {
                val archivo = ResultadosExportador.guardarArchivoDetalle(contexto, lista, detalle)
                ResultadosExportador.compartirArchivo(contexto, archivo)
            }
        },
        onEnviarTelegram = if (cargando) null else {
            {
                val token = credencialesBot.obtenerBotToken()
                val chatId = credencialesBot.obtenerChatId()
                if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
                    Toast.makeText(contexto, "Configura el bot de Telegram en Ajustes primero.", Toast.LENGTH_LONG).show()
                } else {
                    // Una sola lista: llega suelta como mensaje(s) de texto (dividido si es largo), no como .txt.
                    val texto = ResultadosExportador.generarContenidoDetalle(lista, detalle)
                    scope.launch {
                        val error = TelegramBotClient.enviarTexto(token, chatId, texto)
                        val mensaje = error?.let { "No se pudo enviar: $it" } ?: "Enviado a Telegram."
                        Toast.makeText(contexto, mensaje, Toast.LENGTH_LONG).show()
                    }
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
