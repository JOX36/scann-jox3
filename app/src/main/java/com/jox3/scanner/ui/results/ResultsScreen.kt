package com.jox3.scanner.ui.results

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.ui.scan.FilaListaEncontrada
import com.jox3.scanner.ui.theme.TextoSecundario

/** Pantalla "Resultados": el resumen final, igual que `print_scan_summary` del script de Python. */
@Composable
fun ResultsScreen(listas: List<ListaValidada>, onEscanearDeNuevo: () -> Unit) {
    val activas = listas.filter { it.activa }
    val latam = activas.filter { it.esLatino }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Resultados", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "${activas.size} activas de ${listas.size}  ·  ${latam.size} de ${activas.size} son LATAM",
            color = TextoSecundario,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (listas.isEmpty()) {
            Text("No se encontraron enlaces en los chats seleccionados.", color = TextoSecundario, fontSize = 13.sp)
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(listas) { lista -> FilaListaEncontrada(lista) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onEscanearDeNuevo, modifier = Modifier.fillMaxWidth()) {
            Text("Escanear otros chats")
        }
    }
}
