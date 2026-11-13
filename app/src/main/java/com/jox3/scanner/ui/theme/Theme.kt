package com.jox3.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mismos colores que la vista previa de diseño: fondo oscuro + acento turquesa.
val FondoApp = Color(0xFF101317)
val SuperficieApp = Color(0xFF161A20)
val AcentoApp = Color(0xFF2DD4BF)
val TextoPrimario = Color(0xFFE8EBEF)
val TextoSecundario = Color(0xFF8A93A0)
val ErrorApp = Color(0xFFF87171)
val AmarilloApp = Color(0xFFFBBF24)

private val EsquemaOscuro = darkColorScheme(
    primary = AcentoApp,
    background = FondoApp,
    surface = SuperficieApp,
    onPrimary = Color(0xFF06231F),
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
