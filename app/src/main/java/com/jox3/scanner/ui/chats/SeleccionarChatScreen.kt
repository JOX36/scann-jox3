package com.jox3.scanner.ui.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.ui.theme.SuperficieApp

/** Pantalla para elegir un único chat (modos "...en un chat específico"). */
@Composable
fun SeleccionarChatScreen(chats: List<ChatTelegram>, onChatElegido: (ChatTelegram) -> Unit) {
    var busqueda by remember { mutableStateOf("") }
    val chatsFiltrados = if (busqueda.isBlank()) {
        chats
    } else {
        chats.filter { it.titulo.contains(busqueda, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Elige un chat", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text("Buscar chat...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(chatsFiltrados, key = { it.id }) { chat ->
                Surface(
                    color = SuperficieApp,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onChatElegido(chat) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(chat.titulo, fontSize = 15.sp)
                        EtiquetaTipoChat(chat.tipo)
                    }
                }
            }
        }
    }
}
