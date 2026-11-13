package com.jox3.scanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.update.EstadoActualizacion
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Pantalla "Ajustes": versión instalada y botón para comprobar
 * actualizaciones a mano, además del mismo estado que ya muestra el banner
 * en la pantalla de escaneo (aquí con más detalle, sin depender de que el
 * usuario haya visto el banner al abrir la app).
 */
@Composable
fun AjustesScreen(
    versionInstalada: String,
    estadoActualizacion: EstadoActualizacion,
    onComprobarActualizacion: () -> Unit,
    onActualizar: (com.jox3.scanner.update.InfoActualizacion) -> Unit,
    onAbrirAjustesPermiso: (com.jox3.scanner.update.InfoActualizacion) -> Unit,
    botTokenGuardado: String? = null,
    chatIdGuardado: String? = null,
    onGuardarBot: ((String, String) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Ajustes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Surface(
            color = SuperficieApp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Versión instalada", color = TextoSecundario, fontSize = 12.sp)
                Text(versionInstalada, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        when (val estado = estadoActualizacion) {
            EstadoActualizacion.Comprobando -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AcentoApp, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  Comprobando...", fontSize = 13.sp)
                }
            }
            EstadoActualizacion.AlDia -> {
                Text("Ya tienes la última versión.", color = TextoSecundario, fontSize = 13.sp)
            }
            is EstadoActualizacion.Disponible -> {
                Column {
                    Text("Nueva versión disponible: ${estado.info.version}", fontSize = 14.sp)
                    if (!estado.info.notas.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(estado.info.notas, color = TextoSecundario, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onActualizar(estado.info) }) { Text("Actualizar ahora") }
                }
            }
            is EstadoActualizacion.PermisoRequerido -> {
                Column {
                    Text("Falta permitir instalar desde esta app.", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onAbrirAjustesPermiso(estado.info) }) { Text("Abrir ajustes") }
                }
            }
            is EstadoActualizacion.Descargando -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AcentoApp, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(" Descargando actualización...", fontSize = 13.sp)
                }
            }
            EstadoActualizacion.SinComprobar -> {
                Text("Aún no se ha comprobado si hay actualizaciones.", color = TextoSecundario, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onComprobarActualizacion, modifier = Modifier.fillMaxWidth()) {
            Text("Buscar actualizaciones")
        }

        if (onGuardarBot != null) {
            Spacer(Modifier.height(24.dp))
            SeccionBotTelegram(botTokenGuardado, chatIdGuardado, onGuardarBot)
        }
    }
}

/**
 * BOT_TOKEN / CHAT_ID para el botón "Enviar a Telegram" de Resultados —
 * mismo dato que el script de Python pedía en su `.env`, pero guardado
 * cifrado en el dispositivo (ver [com.jox3.scanner.telegram.CredencialesBotTelegram]),
 * nunca en el repositorio.
 */
@Composable
private fun SeccionBotTelegram(
    botTokenGuardado: String?,
    chatIdGuardado: String?,
    onGuardarBot: (String, String) -> Unit,
) {
    var botToken by remember { mutableStateOf(botTokenGuardado.orEmpty()) }
    var chatId by remember { mutableStateOf(chatIdGuardado.orEmpty()) }
    var guardado by remember { mutableStateOf(false) }

    Surface(color = SuperficieApp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bot de Telegram (para \"Enviar a Telegram\")", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Creado con @BotFather en Telegram. El chat ID puede ser el tuyo o el de un grupo/canal donde quieras recibir los resultados.",
                color = TextoSecundario,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it; guardado = false },
                label = { Text("Bot token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = chatId,
                onValueChange = { chatId = it; guardado = false },
                label = { Text("Chat ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onGuardarBot(botToken.trim(), chatId.trim())
                    guardado = true
                },
                enabled = botToken.isNotBlank() && chatId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar")
            }
            if (guardado) {
                Spacer(Modifier.height(6.dp))
                Text("Guardado.", color = AcentoApp, fontSize = 12.sp)
            }
        }
    }
}
