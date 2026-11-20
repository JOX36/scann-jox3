package com.jox3.scanner.ui.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.ui.common.BarraVolver
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

/** Pantalla para escribir el host/dominio a buscar (modos "buscar un host..."). */
@Composable
fun HostInputScreen(onHostConfirmado: (String, Int?) -> Unit, onVolver: () -> Unit) {
    var host by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        BarraVolver(onVolver)
        Text("¿Qué host quieres buscar?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
        Spacer(Modifier.height(4.dp))
        Text("Por ejemplo: servidor.com", color = TextoSecundario, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = cantidad,
            onValueChange = { nuevo -> cantidad = nuevo.filter { it.isDigit() }.take(4) },
            label = { Text("¿Cuántas listas de ese host necesitas?") },
            supportingText = { Text("Déjalo vacío para traer todas las que aparezcan. Si hay menos, te muestro las que encuentre.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onHostConfirmado(host, cantidad.toIntOrNull()?.takeIf { it > 0 }) },
            enabled = host.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continuar") }
    }
}
