package com.jox3.scanner.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoPrimario

/**
 * Botón "← Volver" para las pantallas intermedias del flujo de escaneo
 * (elegir host, elegir un chat, elegir varios chats) — pedido explícito
 * del usuario: antes, si tocaba la opción equivocada, no había forma de
 * retroceder dentro de la app; tenía que cerrarla del todo y volver a
 * abrirla para empezar de nuevo.
 */
@Composable
fun BarraVolver(onVolver: () -> Unit) {
    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(10.dp),
        onClick = onVolver,
        modifier = Modifier.padding(bottom = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("←", fontSize = 15.sp, color = TextoPrimario)
            Text("Volver", fontSize = 13.sp, color = TextoPrimario)
        }
    }
}
