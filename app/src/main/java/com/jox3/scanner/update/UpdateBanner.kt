package com.jox3.scanner.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.BordeApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Banner compacto que se muestra arriba de la pantalla de inicio cuando hay
 * una actualización disponible. No bloquea el uso de la app: el usuario
 * puede ignorarlo y seguir usando la app con la versión actual.
 */
@Composable
fun UpdateBanner(
    estado: EstadoActualizacion,
    onActualizar: (InfoActualizacion) -> Unit,
    onAbrirAjustesPermiso: (InfoActualizacion) -> Unit,
) {
    when (estado) {
        is EstadoActualizacion.Disponible -> {
            BannerBase {
                Column(Modifier.weight(1f)) {
                    Text("Actualización disponible", color = TextoSecundario, fontSize = 12.sp)
                    Text("Versión ${estado.info.version}", fontSize = 15.sp)
                }
                Button(onClick = { onActualizar(estado.info) }) { Text("Actualizar") }
            }
        }

        is EstadoActualizacion.PermisoRequerido -> {
            BannerBase {
                Column(Modifier.weight(1f)) {
                    Text("Falta un permiso", color = TextoSecundario, fontSize = 12.sp)
                    Text("Permitir instalar desde esta app", fontSize = 14.sp)
                }
                Button(onClick = { onAbrirAjustesPermiso(estado.info) }) { Text("Permitir") }
            }
        }

        is EstadoActualizacion.Descargando -> {
            BannerBase {
                CircularProgressIndicator(color = AcentoApp, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                EspacioHorizontal()
                Text("Descargando actualización...", fontSize = 13.sp)
            }
        }

        EstadoActualizacion.Comprobando,
        EstadoActualizacion.AlDia,
        EstadoActualizacion.SinComprobar -> {
            // No hay nada que mostrar: la app sigue con su pantalla normal.
        }
    }
}

@Composable
private fun BannerBase(contenido: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BordeApp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = contenido,
        )
    }
}

@Composable
private fun EspacioHorizontal() {
    Spacer(Modifier.width(12.dp))
}
