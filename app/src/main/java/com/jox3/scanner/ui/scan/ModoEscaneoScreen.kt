package com.jox3.scanner.ui.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.TipoModo
import com.jox3.scanner.ui.theme.BordeApp
import com.jox3.scanner.ui.theme.DegradadoAcento
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.SuperficieFuerteApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Pantalla "Modo de escaneo": elegir cómo buscar, igual que el menú del
 * script de Python original (que tenía 5 opciones antes de escanear).
 * Estilo "Glass HUD" (opción elegida por el usuario entre los 3 previews).
 */
@Composable
fun ModoEscaneoScreen(onModoElegido: (TipoModo) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("¿Cómo quieres buscar?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
        Spacer(Modifier.height(4.dp))
        Text(
            "Los mismos modos del script de escritorio, adaptados a la app.",
            color = TextoSecundario,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        OpcionModo(
            icono = "🔎",
            destacado = true,
            titulo = "Cualquier lista",
            descripcion = "Elige uno o varios chats y busca cualquier lista en ellos.",
            onClick = { onModoElegido(TipoModo.CUALQUIER_LISTA) },
        )
        OpcionModo(
            icono = "🌐",
            titulo = "Buscar un host en todos los chats",
            descripcion = "Escribe un dominio/host y lo busca en todos tus chats, sin elegir uno a uno.",
            onClick = { onModoElegido(TipoModo.HOST_EN_TODOS_LOS_CHATS) },
        )
        OpcionModo(
            icono = "🎯",
            titulo = "Buscar un host en un chat específico",
            descripcion = "Escribe un host y elige un único chat donde buscarlo.",
            onClick = { onModoElegido(TipoModo.HOST_EN_UN_CHAT) },
        )
        OpcionModo(
            icono = "🌎",
            titulo = "Solo contenido LATINO en un chat",
            descripcion = "Elige un chat y se queda solo con las listas activas de contenido latino.",
            onClick = { onModoElegido(TipoModo.LATINO_EN_UN_CHAT) },
        )
        OpcionModo(
            icono = "📄",
            titulo = "Escanear un archivo .txt del dispositivo",
            descripcion = "Busca listas latinas dentro de un archivo de texto que elijas.",
            onClick = { onModoElegido(TipoModo.ARCHIVO_LOCAL) },
        )
    }
}

@Composable
private fun OpcionModo(
    icono: String,
    titulo: String,
    descripcion: String,
    onClick: () -> Unit,
    destacado: Boolean = false,
) {
    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BordeApp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        brush = if (destacado) DegradadoAcento else SolidColor(SuperficieFuerteApp),
                        shape = RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(icono, fontSize = 18.sp)
            }
            Column {
                Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextoPrimario)
                Spacer(Modifier.height(3.dp))
                Text(descripcion, color = TextoSecundario, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
