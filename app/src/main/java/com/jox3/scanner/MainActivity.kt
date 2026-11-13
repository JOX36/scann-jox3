package com.jox3.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jox3.scanner.telegram.FakeTelegramAuthClient
import com.jox3.scanner.ui.login.LoginScreen
import com.jox3.scanner.ui.login.LoginViewModelFactory
import com.jox3.scanner.ui.theme.ScannJox3Theme

class MainActivity : ComponentActivity() {

    // TEMPORAL: se reemplaza por TdLibAuthClient cuando se integre TDLib.
    private val authClient = FakeTelegramAuthClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScannJox3Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var autenticado by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                    if (!autenticado) {
                        val viewModel = viewModel(factory = LoginViewModelFactory(authClient))
                        LoginScreen(viewModel = viewModel, onAutenticado = { autenticado = true })
                    } else {
                        PantallaPlaceholderInicio()
                    }
                }
            }
        }
    }
}

@Composable
private fun PantallaPlaceholderInicio() {
    // La pantalla real de inicio (menú de modos de búsqueda) llega en la
    // siguiente etapa, ya con la vista previa de diseño como referencia.
    Text("Sesión iniciada. Pantalla de inicio: siguiente etapa.")
}
