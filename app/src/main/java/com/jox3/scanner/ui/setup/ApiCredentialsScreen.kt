package com.jox3.scanner.ui.setup

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
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Pantalla única de primer arranque: pide el API_ID / API_HASH de Telegram
 * (se crean una sola vez en https://my.telegram.org/apps, son gratis). Se
 * guardan cifrados en el dispositivo — ver [com.jox3.scanner.telegram.CredencialesApiTelegram] —
 * y esta pantalla no vuelve a aparecer.
 */
@Composable
fun ApiCredentialsScreen(onGuardar: (apiId: Int, apiHash: String) -> Unit) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("Configura tu API de Telegram", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Es un paso único. Crea una app gratis en my.telegram.org/apps y " +
                "copia aquí el API ID y el API Hash que te den. Se guardan cifrados " +
                "en este dispositivo, nunca se suben a ningún lado.",
            color = TextoSecundario,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = apiId,
            onValueChange = { apiId = it.filter(Char::isDigit) },
            label = { Text("API ID") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = apiHash,
            onValueChange = { apiHash = it.trim() },
            label = { Text("API Hash") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        val idValido = apiId.toIntOrNull()
        Button(
            onClick = { idValido?.let { onGuardar(it, apiHash) } },
            enabled = idValido != null && apiHash.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Guardar y continuar")
        }
    }
}
