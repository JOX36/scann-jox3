package com.jox3.scanner.ui.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoSecundario

/** Pantalla "Chats": elegir en qué chats buscar listas antes de escanear. */
@Composable
fun ChatsScreen(
    chats: List<ChatTelegram>,
    seleccionados: Set<Long>,
    onAlternarSeleccion: (Long) -> Unit,
    onEscanear: () -> Unit,
) {
    var busqueda by remember { mutableStateOf("") }
    val chatsFiltrados = if (busqueda.isBlank()) {
        chats
    } else {
        chats.filter { it.titulo.contains(busqueda, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Elige los chats a escanear", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer2()
        Text(
            "Se buscarán listas M3U/Xtream en los mensajes de los chats marcados.",
            color = TextoSecundario,
            fontSize = 13.sp,
        )
        Spacer2()
        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text("Buscar chat...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer2()

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(chatsFiltrados, key = { it.id }) { chat ->
                FilaChat(
                    chat = chat,
                    marcado = chat.id in seleccionados,
                    onClick = { onAlternarSeleccion(chat.id) },
                )
            }
        }

        Button(
            onClick = onEscanear,
            enabled = seleccionados.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (seleccionados.isEmpty()) "Selecciona al menos un chat" else "Escanear (${seleccionados.size})")
        }
    }
}

@Composable
private fun FilaChat(chat: ChatTelegram, marcado: Boolean, onClick: () -> Unit) {
    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(chat.titulo, fontSize = 15.sp)
                EtiquetaTipoChat(chat.tipo)
                if (chat.mensajesAprox > 0) {
                    Text("${chat.mensajesAprox} mensajes aprox.", color = TextoSecundario, fontSize = 12.sp)
                }
            }
            Checkbox(checked = marcado, onCheckedChange = { onClick() })
        }
    }
}

@Composable
private fun Spacer2() {
    Spacer(Modifier.height(12.dp))
}
