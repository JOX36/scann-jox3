package com.jox3.scanner.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.telegram.TelegramAuthState
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.TextoSecundario

@Composable
fun LoginScreen(viewModel: LoginViewModel, onAutenticado: () -> Unit) {
    val estado by viewModel.estado.collectAsState()

    LaunchedEffect(estado) {
        if (estado is TelegramAuthState.Autenticado) onAutenticado()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("IPTV Scanner", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Inicia sesión con tu cuenta de Telegram", color = TextoSecundario, fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))

        when (val s = estado) {
            is TelegramAuthState.SinIniciar,
            is TelegramAuthState.Conectando -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AcentoApp, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Conectando con Telegram...", color = TextoSecundario)
                }
            }

            is TelegramAuthState.EsperandoTelefono -> {
                var telefono by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    label = { Text("Número de teléfono") },
                    placeholder = { Text("+57 300 000 0000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.onTelefonoConfirmado(telefono) },
                    enabled = telefono.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continuar") }
            }

            is TelegramAuthState.EsperandoCodigo -> {
                var codigo by remember { mutableStateOf("") }
                Text("Escribe el código que te llegó por Telegram", color = TextoSecundario, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it },
                    label = { Text("Código") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.onCodigoConfirmado(codigo) },
                    enabled = codigo.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Verificar") }
            }

            is TelegramAuthState.EsperandoPassword2FA -> {
                var password by remember { mutableStateOf("") }
                Text("Tu cuenta tiene verificación en dos pasos", color = TextoSecundario, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.onPasswordConfirmada(password) },
                    enabled = password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Entrar") }
            }

            is TelegramAuthState.Autenticado -> {
                Text("¡Listo! Entrando...", color = AcentoApp)
            }

            is TelegramAuthState.Error -> {
                Text(s.mensaje, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.reintentar() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reintentar")
                }
            }
        }
    }
}
