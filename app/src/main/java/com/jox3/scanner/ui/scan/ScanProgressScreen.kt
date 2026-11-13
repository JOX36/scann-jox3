package com.jox3.scanner.ui.scan

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.ErrorApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoSecundario

/** Pantalla "Escaneo": progreso en vivo mientras se validan los enlaces encontrados. */
@Composable
fun ScanProgressScreen(
    procesados: Int,
    total: Int,
    encontradas: List<ListaValidada>,
    errores: List<String> = emptyList(),
) {
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

        Spacer(Modifier.height(20.dp))

        Text("Encontradas (${encontradas.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyColumn {
            items(encontradas) { lista -> FilaListaEncontrada(lista) }
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
