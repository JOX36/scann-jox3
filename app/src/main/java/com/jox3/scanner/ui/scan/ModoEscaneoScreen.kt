package com.jox3.scanner.ui.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.TipoModo
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Pantalla "Modo de escaneo": elegir cómo buscar, igual que el menú del
 * script de Python original (que tenía 5 opciones antes de escanear).
 */
@Composable
fun ModoEscaneoScreen(onModoElegido: (TipoModo) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("¿Cómo quieres buscar?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Los mismos modos del script de escritorio, adaptados a la app.",
            color = TextoSecundario,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        OpcionModo(
            titulo = "Cualquier lista",
            descripcion = "Elige uno o varios chats y busca cualquier lista en ellos.",
            onClick = { onModoElegido(TipoModo.CUALQUIER_LISTA) },
        )
        OpcionModo(
            titulo = "Buscar un host en todos los chats",
            descripcion = "Escribe un dominio/host y lo busca en todos tus chats, sin elegir uno a uno.",
            onClick = { onModoElegido(TipoModo.HOST_EN_TODOS_LOS_CHATS) },
        )
        OpcionModo(
            titulo = "Buscar un host en un chat específico",
            descripcion = "Escribe un host y elige un único chat donde buscarlo.",
            onClick = { onModoElegido(TipoModo.HOST_EN_UN_CHAT) },
        )
        OpcionModo(
            titulo = "Solo contenido LATINO en un chat",
            descripcion = "Elige un chat y se queda solo con las listas activas de contenido latino.",
            onClick = { onModoElegido(TipoModo.LATINO_EN_UN_CHAT) },
        )
        OpcionModo(
            titulo = "Escanear un archivo .txt del dispositivo",
            descripcion = "Busca listas latinas dentro de un archivo de texto que elijas.",
            onClick = { onModoElegido(TipoModo.ARCHIVO_LOCAL) },
        )
    }
}

@Composable
private fun OpcionModo(titulo: String, descripcion: String, onClick: () -> Unit) {
    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(descripcion, color = TextoSecundario, fontSize = 12.sp)
        }
    }
}
