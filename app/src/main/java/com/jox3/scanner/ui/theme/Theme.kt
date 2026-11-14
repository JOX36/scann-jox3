package com.jox3.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Paleta "Glass HUD" — la opción que el usuario eligió entre los 3 previews
// de diseño futurista (Neon Grid / Glass HUD / Aurora Terminal): fondo casi
// negro con tinte verdoso, tarjetas "de vidrio" translúcidas con borde
// sutil, y acento esmeralda-turquesa en vez del turquesa plano de antes.
val FondoApp = Color(0xFF07100F)
val SuperficieApp = Color(0x14FFFFFF) // tarjeta "de vidrio": blanco al ~8%, sobre FondoApp
val SuperficieFuerteApp = Color(0x29FFFFFF) // variante un poco más visible (iconos, chips sin seleccionar)
val BordeApp = Color(0x17FFFFFF) // borde sutil de las tarjetas de vidrio
val AcentoApp = Color(0xFF2EE6A8) // esmeralda
val AcentoSecundario = Color(0xFF22B8CF) // turquesa — para los degradados junto con AcentoApp
val AcentoSuaveApp = Color(0x242EE6A8) // fondo suave para íconos destacados (14% del acento)
val TextoPrimario = Color(0xFFF2FBF7)
val TextoSecundario = Color(0xFF8CA69D)
val TextoTerciarioApp = Color(0xFF526059) // el más apagado: íconos/etiquetas inactivos de la barra inferior
val ErrorApp = Color(0xFFF87171)
val AmarilloApp = Color(0xFFFBBF24)
val OnAcentoApp = Color(0xFF06231F) // texto oscuro sobre fondos con AcentoApp

/** Degradado esmeralda→turquesa para los chips de ícono "destacados" (igual que en el preview elegido). */
val DegradadoAcento = Brush.linearGradient(listOf(AcentoApp, AcentoSecundario))

private val EsquemaOscuro = darkColorScheme(
    primary = AcentoApp,
    background = FondoApp,
    surface = FondoApp, // sólido: las tarjetas "de vidrio" usan SuperficieApp (translúcida) por su cuenta, encima de este fondo
    onPrimary = OnAcentoApp,
    onBackground = TextoPrimario,
    onSurface = TextoPrimario,
    error = ErrorApp,
)

@Composable
fun ScannJox3Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaOscuro,
        content = content,
    )
}
