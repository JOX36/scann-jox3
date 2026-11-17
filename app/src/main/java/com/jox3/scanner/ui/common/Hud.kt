package com.jox3.scanner.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.AcentoSecundario
import com.jox3.scanner.ui.theme.FondoApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Fuente de las cifras ("HUD"): monoespaciada para que los números no bailen al cambiar. */
val FuenteHud = FontFamily.Monospace

/** Nombre comercial de la app. */
const val NOMBRE_APP = "JOX3 Radar"

/** Radar animado: anillos, barrido giratorio con estela y un "objetivo" que parpadea. */
@Composable
fun RadarAnimado(modifier: Modifier = Modifier, tamano: Dp = 120.dp) {
    val transicion = rememberInfiniteTransition(label = "radar")
    val angulo by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "angulo",
    )
    val parpadeo by transicion.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "parpadeo",
    )
    Canvas(modifier = modifier.size(tamano)) {
        val centro = center
        val radio = size.minDimension / 2f
        val trazo = Stroke(width = 1.4.dp.toPx())
        for (i in 1..3) {
            drawCircle(AcentoApp.copy(alpha = 0.28f), radius = radio * i / 3f, center = centro, style = trazo)
        }
        drawLine(AcentoApp.copy(alpha = 0.18f), Offset(centro.x - radio, centro.y), Offset(centro.x + radio, centro.y), 1.dp.toPx())
        drawLine(AcentoApp.copy(alpha = 0.18f), Offset(centro.x, centro.y - radio), Offset(centro.x, centro.y + radio), 1.dp.toPx())
        rotate(angulo, pivot = centro) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.72f to Color.Transparent,
                    1f to AcentoApp.copy(alpha = 0.55f),
                    center = centro,
                ),
                radius = radio,
                center = centro,
            )
            drawLine(AcentoApp, centro, Offset(centro.x + radio, centro.y), 2.dp.toPx())
        }
        // "Objetivo" fijo que parpadea.
        val rad = Math.toRadians(-40.0)
        val punto = Offset(centro.x + (radio * 0.62f * cos(rad)).toFloat(), centro.y + (radio * 0.62f * sin(rad)).toFloat())
        drawCircle(AcentoSecundario.copy(alpha = parpadeo), radius = 3.2.dp.toPx(), center = punto)
        drawCircle(AcentoApp, radius = 2.6.dp.toPx(), center = centro)
    }
}

/** Pantalla de bienvenida: radar + nombre, ~1,6 s, y se desvanece sola. */
@Composable
fun PantallaInicio(onTerminado: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1700)
        visible = false
        delay(400)
        onTerminado()
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(400))) {
        Box(modifier = Modifier.fillMaxSize().background(FondoApp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RadarAnimado(tamano = 150.dp)
                Spacer(Modifier.height(28.dp))
                Text(
                    NOMBRE_APP.uppercase(),
                    color = TextoPrimario,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FuenteHud,
                    letterSpacing = 6.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text("Escáner de listas IPTV", color = TextoSecundario, fontSize = 12.sp, letterSpacing = 2.sp)
            }
        }
    }
}
