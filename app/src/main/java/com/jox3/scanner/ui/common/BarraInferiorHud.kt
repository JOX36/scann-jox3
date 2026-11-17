package com.jox3.scanner.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.BordeHud
import com.jox3.scanner.ui.theme.DegradadoAcento
import com.jox3.scanner.ui.theme.OnAcentoApp
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Barra de pestañas "flotante" de cristal: cápsula translúcida con borde brillante; la pestaña
 * activa lleva una píldora con degradado esmeralda→turquesa y resplandor. Reemplaza a la
 * NavigationBar plana de Material (emojis de colores y fondo gris).
 */
@Composable
fun BarraInferiorHud(
    etiquetas: List<String>,
    iconos: List<ImageVector>,
    seleccionado: Int,
    onSeleccionar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, BordeHud),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(listOf(Color(0xE60F2420), Color(0xF20A1613))),
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                etiquetas.forEachIndexed { i, etiqueta ->
                    ItemBarra(etiqueta, iconos[i], activo = i == seleccionado, onClick = { onSeleccionar(i) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ItemBarra(
    etiqueta: String,
    icono: ImageVector,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorIcono by animateColorAsState(if (activo) OnAcentoApp else TextoSecundario, tween(250), label = "icono")
    val colorTexto by animateColorAsState(if (activo) AcentoApp else TextoSecundario, tween(250), label = "texto")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        val forma = RoundedCornerShape(18.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(58.dp)
                .height(34.dp)
                .then(
                    if (activo) Modifier
                        .shadow(12.dp, forma, ambientColor = AcentoApp, spotColor = AcentoApp)
                        .background(DegradadoAcento, forma)
                    else Modifier,
                ),
        ) {
            Icon(icono, contentDescription = etiqueta, tint = colorIcono, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(etiqueta, color = colorTexto, fontSize = 11.sp, fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium)
    }
}
