package com.jox3.scanner.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.ErrorApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario
import kotlinx.coroutines.delay

/** Pantalla "Escaneo": progreso en vivo mientras se validan los enlaces encontrados. */
@Composable
fun ScanProgressScreen(
    procesados: Int,
    total: Int,
    encontradas: List<ListaValidada>,
    errores: List<String> = emptyList(),
    inicioMs: Long = System.currentTimeMillis(),
    onDetener: (() -> Unit)? = null,
) {
    // Reloj propio de la pantalla (no viene del ViewModel) solo para que
    // "validaciones/seg" se recalcule mientras el escaneo avanza, sin que
    // el repositorio tenga que reportar el tiempo transcurrido.
    var ahoraMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            ahoraMs = System.currentTimeMillis()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Escaneando...", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        val progreso = if (total > 0) procesados.toFloat() / total else 0f
        LinearProgressIndicator(
            progress = progreso,
            color = AcentoApp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (total > 0) "$procesados de $total enlaces validados" else "Buscando enlaces en los mensajes...",
            color = TextoSecundario,
            fontSize = 13.sp,
        )

        if (errores.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PanelErrores(errores)
        }

        if (total > 0) {
            Spacer(Modifier.height(16.dp))
            val activas = encontradas.count { it.activa }
            val caidas = encontradas.count { !it.activa }
            val latam = encontradas.count { it.esLatino }
            val segundos = ((ahoraMs - inicioMs).coerceAtLeast(1000L)) / 1000.0
            val porSegundo = procesados / segundos

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EstadisticaCelda("Activas", activas.toString(), AcentoApp, Modifier.weight(1f))
                EstadisticaCelda("Caídas / offline", caidas.toString(), ErrorApp, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EstadisticaCelda("De LATAM", latam.toString(), TextoPrimario, Modifier.weight(1f))
                EstadisticaCelda("Validaciones/seg", "≈${porSegundo.toInt()}", TextoPrimario, Modifier.weight(1f))
            }

            if (onDetener != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onDetener, modifier = Modifier.fillMaxWidth()) {
                    Text("Detener y ver resultados parciales")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("Encontradas (${encontradas.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyColumn {
            items(encontradas) { lista -> FilaListaEncontrada(lista) }
        }
    }
}

@Composable
private fun EstadisticaCelda(etiqueta: String, valor: String, colorValor: Color, modifier: Modifier = Modifier) {
    Surface(color = SuperficieApp, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(etiqueta, color = TextoSecundario, fontSize = 11.sp)
            Text(valor, color = colorValor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Lista de chats que no se pudieron leer durante el escaneo (error real de TDLib). */
@Composable
internal fun PanelErrores(errores: List<String>) {
    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "No se pudieron leer ${errores.size} chat(s)",
                color = ErrorApp,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            errores.forEach { error ->
                Text("• $error", color = TextoSecundario, fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun FilaListaEncontrada(lista: ListaValidada) {
    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(lista.credenciales.host, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = if (!lista.activa) ErrorApp else if (lista.esLatino) AcentoApp else TextoSecundario
                val texto = when {
                    !lista.activa -> "Inactiva"
                    lista.esLatino -> "LATAM · ${(lista.porcentajeLatino * 100).toInt()}%"
                    else -> "Activa · no LATAM (${(lista.porcentajeLatino * 100).toInt()}%)"
                }
                Text(texto, color = color, fontSize = 12.sp)
            }
            Text("Origen: ${lista.origenChat}", color = TextoSecundario, fontSize = 11.sp)
        }
    }
}
