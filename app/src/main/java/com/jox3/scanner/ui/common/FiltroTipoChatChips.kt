package com.jox3.scanner.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.data.TipoChat
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * `null` = sin filtrar (todos los tipos). Pedido explícito del usuario:
 * poder filtrar la lista de chats por grupos, canales o chats privados,
 * en vez de tener que buscarlos todos juntos por nombre.
 */
private val OPCIONES_FILTRO: List<Pair<String, TipoChat?>> = listOf(
    "Todos" to null,
    "Grupos" to TipoChat.GRUPO,
    "Canales" to TipoChat.CANAL,
    "Chats" to TipoChat.PRIVADO,
)

@Composable
fun FiltroTipoChatChips(
    filtroActual: TipoChat?,
    onFiltroElegido: (TipoChat?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        items(OPCIONES_FILTRO) { (etiqueta, tipo) ->
            val seleccionado = tipo == filtroActual
            Surface(
                color = if (seleccionado) AcentoApp else SuperficieApp,
                shape = RoundedCornerShape(50),
                onClick = { onFiltroElegido(tipo) },
            ) {
                Text(
                    etiqueta,
                    color = if (seleccionado) Color(0xFF06231F) else TextoSecundario,
                    fontSize = 12.sp,
                    fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
