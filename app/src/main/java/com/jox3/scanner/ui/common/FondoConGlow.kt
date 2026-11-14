package com.jox3.scanner.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.AcentoSecundario
import com.jox3.scanner.ui.theme.FondoApp

/**
 * Fondo de toda la app: igual que en el preview "Glass HUD" elegido por el
 * usuario — negro verdoso + dos manchas de luz difuminadas (esmeralda
 * arriba a la derecha, turquesa abajo a la izquierda) detrás de las
 * tarjetas "de vidrio" de cada pantalla. El difuminado real (`blur`) solo
 * se ve en Android 12+; en versiones anteriores el degradado se nota sin
 * difuminar, sin que eso rompa nada.
 */
@Composable
fun FondoConGlow(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(FondoApp)) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-90).dp)
                .blur(70.dp)
                .background(Brush.radialGradient(listOf(AcentoApp.copy(alpha = 0.22f), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-80).dp, y = 60.dp)
                .blur(70.dp)
                .background(Brush.radialGradient(listOf(AcentoSecundario.copy(alpha = 0.16f), Color.Transparent))),
        )
        content()
    }
}
