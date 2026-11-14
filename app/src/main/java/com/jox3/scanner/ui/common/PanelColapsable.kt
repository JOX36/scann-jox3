package com.jox3.scanner.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Panel "▸ Título (N)" que se expande al tocarlo — mismo patrón que ya
 * usaba `PanelRegistro` para el registro del escaneo, generalizado acá
 * para listas largas (canales/series/películas de una lista, en el
 * detalle). Arranca colapsado porque una lista puede traer cientos de
 * nombres.
 */
@Composable
fun PanelColapsable(titulo: String, items: List<String>, empiezaExpandido: Boolean = false) {
    var expandido by remember { mutableStateOf(empiezaExpandido) }

    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expandido = !expandido },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${if (expandido) "▾" else "▸"} $titulo (${items.size})",
                    color = TextoPrimario,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (expandido) {
                Spacer(Modifier.height(6.dp))
                if (items.isEmpty()) {
                    Text("(sin datos)", color = TextoSecundario, fontSize = 11.sp)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(items) { linea ->
                            Text("• $linea", color = TextoSecundario, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
