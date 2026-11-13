package com.jox3.scanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.update.EstadoActualizacion
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Pantalla "Ajustes": versión instalada y botón para comprobar
 * actualizaciones a mano, además del mismo estado que ya muestra el banner
 * en la pantalla de escaneo (aquí con más detalle, sin depender de que el
 * usuario haya visto el banner al abrir la app).
 */
@Composable
fun AjustesScreen(
    versionInstalada: String,
    estadoActualizacion: EstadoActualizacion,
    onComprobarActualizacion: () -> Unit,
    onActualizar: (com.jox3.scanner.update.InfoActualizacion) -> Unit,
    onAbrirAjustesPermiso: (com.jox3.scanner.update.InfoActualizacion) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Ajustes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Surface(
            color = SuperficieApp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Versión instalada", color = TextoSecundario, fontSize = 12.sp)
                Text(versionInstalada, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        when (val estado = estadoActualizacion) {
            EstadoActualizacion.Comprobando -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AcentoApp, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  Comprobando...", fontSize = 13.sp)
                }
            }
            EstadoActualizacion.AlDia -> {
                Text("Ya tienes la última versión.", color = TextoSecundario, fontSize = 13.sp)
            }
            is EstadoActualizacion.Disponible -> {
                Column {
                    Text("Nueva versión disponible: ${estado.info.version}", fontSize = 14.sp)
                    if (!estado.info.notas.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(estado.info.notas, color = TextoSecundario, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onActualizar(estado.info) }) { Text("Actualizar ahora") }
                }
            }
            is EstadoActualizacion.PermisoRequerido -> {
                Column {
                    Text("Falta permitir instalar desde esta app.", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onAbrirAjustesPermiso(estado.info) }) { Text("Abrir ajustes") }
                }
            }
            is EstadoActualizacion.Descargando -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AcentoApp, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(" Descargando actualización...", fontSize = 13.sp)
                }
            }
            EstadoActualizacion.SinComprobar -> {
                Text("Aún no se ha comprobado si hay actualizaciones.", color = TextoSecundario, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onComprobarActualizacion, modifier = Modifier.fillMaxWidth()) {
            Text("Buscar actualizaciones")
        }
    }
}
