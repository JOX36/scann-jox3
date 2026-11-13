package com.jox3.scanner

import android.os.Bundle
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jox3.scanner.scan.EstadoFlujoEscaneo
import com.jox3.scanner.scan.ScanFlowViewModel
import com.jox3.scanner.scan.ScanFlowViewModelFactory
import com.jox3.scanner.scan.TdChatsRepository
import com.jox3.scanner.scan.TdEscanerRepository
import com.jox3.scanner.telegram.CredencialesApiTelegram
import com.jox3.scanner.telegram.TdLibAuthClient
import com.jox3.scanner.ui.chats.ChatsScreen
import com.jox3.scanner.ui.login.LoginScreen
import com.jox3.scanner.ui.login.LoginViewModel
import com.jox3.scanner.ui.login.LoginViewModelFactory
import com.jox3.scanner.ui.results.ResultsScreen
import com.jox3.scanner.ui.scan.ScanProgressScreen
import com.jox3.scanner.ui.setup.ApiCredentialsScreen
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.ScannJox3Theme
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
                        val authClient = remember { TdLibAuthClient(applicationContext, credenciales) }
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
        updateViewModel.comprobarActualizacion(versionInstalada = "0.1.0")
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

        is EstadoFlujoEscaneo.SeleccionandoChats -> {
            ChatsScreen(
                chats = s.chats,
                seleccionados = s.seleccionados,
                onAlternarSeleccion = viewModel::alternarSeleccion,
                onEscanear = viewModel::iniciarEscaneo,
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
