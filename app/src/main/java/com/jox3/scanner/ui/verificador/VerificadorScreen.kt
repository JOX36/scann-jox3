package com.jox3.scanner.ui.verificador

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.BordeApp
import com.jox3.scanner.ui.theme.ErrorApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

/** Pantalla "Verificador": pegar una lista en cualquier formato conocido y verificarla. */
@Composable
fun VerificadorScreen(
    texto: String,
    verificando: Boolean,
    error: String?,
    onTextoCambiado: (String) -> Unit,
    onVerificar: () -> Unit,
) {
    val contexto = LocalContext.current
    val colores = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextoPrimario,
        unfocusedTextColor = TextoPrimario,
        cursorColor = AcentoApp,
        focusedBorderColor = AcentoApp,
        unfocusedBorderColor = BordeApp,
        focusedLabelColor = AcentoApp,
        unfocusedLabelColor = TextoSecundario,
    )

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Verificador", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pega una lista en cualquier formato: enlace get.php / player_api / xmltv, " +
                "bloque con Host, Usuario y Contraseña, o «host|usuario|clave».",
            color = TextoSecundario,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = texto,
            onValueChange = onTextoCambiado,
            label = { Text("Lista") },
            minLines = 5,
            maxLines = 10,
            colors = colores,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = ErrorApp, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val cm = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val pegado = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(contexto)?.toString()
                    if (!pegado.isNullOrBlank()) onTextoCambiado(pegado)
                },
                enabled = !verificando,
                modifier = Modifier.weight(1f),
            ) { Text("Pegar") }
            OutlinedButton(
                onClick = { onTextoCambiado("") },
                enabled = !verificando && texto.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Limpiar") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onVerificar,
            enabled = texto.isNotBlank() && !verificando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (verificando) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Verificando...")
            } else {
                Text("Verificar")
            }
        }
        Spacer(Modifier.height(1.dp))
    }
}
