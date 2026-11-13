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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.ui.theme.SuperficieApp

/** Pantalla para elegir un único chat (modos "...en un chat específico"). */
@Composable
fun SeleccionarChatScreen(chats: List<ChatTelegram>, onChatElegido: (ChatTelegram) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Elige un chat", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(chats, key = { it.id }) { chat ->
                Surface(
                    color = SuperficieApp,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onChatElegido(chat) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Text(chat.titulo, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
