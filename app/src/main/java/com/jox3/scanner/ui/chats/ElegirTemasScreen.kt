package com.jox3.scanner.ui.chats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.ForoConTemas
import com.jox3.scanner.ui.common.BarraVolver
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.BordeApp
import com.jox3.scanner.ui.theme.OnAcentoApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Para los grupos con "Temas": elegir cuáles temas escanear. Por defecto
 * vienen todos marcados (igual que escanear el grupo completo).
 */
@Composable
fun ElegirTemasScreen(
    foros: List<ForoConTemas>,
    seleccion: Map<Long, Set<Int>>,
    onAlternarTema: (chatId: Long, temaId: Int) -> Unit,
    onAlternarTodos: (chatId: Long) -> Unit,
    onEscanear: () -> Unit,
    onVolver: () -> Unit,
) {
    val hayAlgoMarcado = foros.any { seleccion[it.chat.id].orEmpty().isNotEmpty() }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        BarraVolver(onVolver)
        Text("Elige los temas a escanear", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
        Spacer(Modifier.height(4.dp))
        Text(
            "Estos grupos tienen Temas. Desmarca los que no quieras leer.",
            color = TextoSecundario,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(foros, key = { it.chat.id }) { foro ->
                val marcados = seleccion[foro.chat.id].orEmpty()
                Surface(
                    color = SuperficieApp,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, BordeApp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(foro.chat.titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
                        Text(
                            if (marcados.size == foro.temas.size) "Todos los temas (${foro.temas.size})"
                            else "${marcados.size} de ${foro.temas.size} temas",
                            color = AcentoApp,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { onAlternarTodos(foro.chat.id) }.padding(vertical = 6.dp),
                        )
                        foro.temas.forEach { tema ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAlternarTema(foro.chat.id, tema.id) },
                            ) {
                                Checkbox(
                                    checked = tema.id in marcados,
                                    onCheckedChange = { onAlternarTema(foro.chat.id, tema.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AcentoApp,
                                        uncheckedColor = TextoSecundario,
                                        checkmarkColor = OnAcentoApp,
                                    ),
                                )
                                Text(tema.nombre, fontSize = 14.sp, color = TextoPrimario)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onEscanear, enabled = hayAlgoMarcado, modifier = Modifier.fillMaxWidth()) {
            Text("Escanear")
        }
    }
}
