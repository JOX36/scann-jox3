package com.jox3.scanner.ui.common

import androidx.compose.foundation.BorderStroke
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
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.BordeApp
import com.jox3.scanner.ui.theme.SuperficieApp

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
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, BordeApp),
        onClick = onVolver,
        modifier = Modifier.padding(bottom = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("←", fontSize = 15.sp, color = AcentoApp)
            Text("Volver", fontSize = 13.sp, color = AcentoApp)
        }
    }
}
