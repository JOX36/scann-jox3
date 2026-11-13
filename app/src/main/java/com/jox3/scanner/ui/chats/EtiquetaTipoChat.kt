package com.jox3.scanner.ui.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.jox3.scanner.scan.data.TipoChat
import com.jox3.scanner.ui.theme.TextoSecundario

/** Texto corto (con emoji) para mostrar si un chat es privado, grupo o canal. */
fun etiquetaDeTipo(tipo: TipoChat): String = when (tipo) {
    TipoChat.PRIVADO -> "👤 Privado"
    TipoChat.GRUPO -> "👥 Grupo"
    TipoChat.CANAL -> "📢 Canal"
}

@Composable
fun EtiquetaTipoChat(tipo: TipoChat) {
    Text(etiquetaDeTipo(tipo), color = TextoSecundario, fontSize = 11.sp)
}
