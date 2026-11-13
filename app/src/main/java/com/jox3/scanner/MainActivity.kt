package com.jox3.scanner

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jox3.scanner.scan.EstadoFlujoEscaneo
import com.jox3.scanner.scan.ScanFlowViewModel
import com.jox3.scanner.scan.ScanFlowViewModelFactory
import com.jox3.scanner.scan.TdChatsRepository
import com.jox3.scanner.scan.TdEscanerRepository
import com.jox3.scanner.scan.TipoModo
import com.jox3.scanner.telegram.CredencialesApiTelegram
import com.jox3.scanner.telegram.TdLibAuthClient
import com.jox3.scanner.ui.chats.ChatsScreen
import com.jox3.scanner.ui.chats.SeleccionarChatScreen
import com.jox3.scanner.ui.login.LoginScreen
import com.jox3.scanner.ui.login.LoginViewModel
import com.jox3.scanner.ui.login.LoginViewModelFactory
import com.jox3.scanner.ui.results.ResultsScreen
import com.jox3.scanner.ui.scan.HostInputScreen
import com.jox3.scanner.ui.scan.ModoEscaneoScreen
import com.jox3.scanner.ui.scan.ScanProgressScreen
import com.jox3.scanner.ui.setup.ApiCredentialsScreen
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.ScannJox3Theme
import com.jox3.scanner.BuildConfig
import com.jox3.scanner.update.InfoActualizacion
import com.jox3.scanner.update.UpdateBanner
import com.jox3.scanner.update.UpdateViewModel

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

@Composable
private fun PantallaPrincipal(authClient: TdLibAuthClient) {
    val updateViewModel: UpdateViewModel = viewModel()
    val estadoActualizacion by updateViewModel.estado.collectAsState()

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

    Column(modifier = Modifier.fillMaxSize()) {
        UpdateBanner(
            estado = estadoActualizacion,
            onActualizar = { info -> updateViewModel.iniciarDescarga(info) },
            onAbrirAjustesPermiso = { info ->
                infoPendientePermiso = info
                lanzadorAjustesPermiso.launch(updateViewModel.intentSolicitarPermiso())
            },
        )
        PantallaEscaneo(authClient)
    }
}

@Composable
private fun PantallaEscaneo(authClient: TdLibAuthClient) {
    val chatsRepository = remember { TdChatsRepository(authClient) }
    val escanerRepository = remember { TdEscanerRepository(authClient) }
    val viewModel: ScanFlowViewModel = viewModel(
        factory = ScanFlowViewModelFactory(chatsRepository, escanerRepository),
    )
    val estado by viewModel.estado.collectAsState()
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
            ScanProgressScreen(procesados = s.procesados, total = s.total, encontradas = s.encontradas)
        }

        is EstadoFlujoEscaneo.Resultados -> {
            ResultsScreen(listas = s.listas, onEscanearDeNuevo = viewModel::reiniciar)
        }
    }
}

/** Nombre a mostrar del archivo elegido en el selector del sistema (si no se puede leer, cae a null). */
private fun nombreDeArchivo(contexto: Context, uri: Uri): String? {
    return contexto.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (indice >= 0) cursor.getString(indice) else null
    }
}
