package com.jox3.scanner.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.engine.DetalleLista
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.scan.engine.XtreamClient
import com.jox3.scanner.ui.common.BarraVolver
import com.jox3.scanner.ui.common.PanelColapsable
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Detalle de una lista — pedido explícito del usuario: poder tocar un
 * resultado y ver su información extra (geolocalización del servidor,
 * canales/series/películas por nombre, enlace M3U directo por IP) SIN ir
 * a Telegram, y poder guardarla/enviarla aparte ("si solo es consulta no
 * lo envío, si quiero guardarla la envío").
 *
 * La consulta extra ([detalle]) es a demanda: arranca en `null` y esta
 * pantalla muestra un botón para pedirla recién cuando el usuario la
 * quiere, en vez de hacerla sola al abrir.
 */
@Composable
fun DetalleListaScreen(
    lista: ListaValidada,
    detalle: DetalleLista?,
    cargando: Boolean,
    onVolver: () -> Unit,
    onConsultar: () -> Unit,
    onGuardarTxt: (() -> Unit)?,
    onEnviarTelegram: (() -> Unit)?,
) {
    val cred = lista.credenciales

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        BarraVolver(onVolver)
        Text(cred.host, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Encontrada en: ${lista.origenChat}", color = TextoSecundario, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        FilaDato("Usuario", cred.username)
        FilaDato("Contraseña", cred.password)
        FilaDato("Conexiones", if (lista.conexionesMax != null) "${lista.conexionesActivas ?: 0}/${lista.conexionesMax}" else "N/A")
        FilaDato("Vencimiento", lista.fechaVencimiento ?: "No especificada")
        FilaDato("Días restantes", lista.diasRestantes?.toString() ?: "N/A")
        FilaDato("Timezone", lista.zonaHoraria ?: "N/A")
        FilaDato("Formatos", lista.formatos ?: "N/A")
        FilaDato("Contenido LATAM", if (lista.esLatino) "Sí (${(lista.porcentajeLatino * 100).toInt()}%)" else "No")

        Spacer(Modifier.height(16.dp))

        when {
            detalle == null && !cargando -> {
                Text(
                    "Canales, series, películas y el servidor real (geolocalización de la IP) se consultan aparte, solo si los pedís — no se piden para todas las listas del escaneo.",
                    color = TextoSecundario,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onConsultar, modifier = Modifier.fillMaxWidth()) {
                    Text("Consultar información extra")
                }
            }
            cargando -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = AcentoApp, modifier = Modifier.height(20.dp))
                    Text("Consultando el panel...", color = TextoSecundario, fontSize = 13.sp)
                }
            }
            else -> {
                requireNotNull(detalle)
                if (detalle.geoIp != null) {
                    val bandera = XtreamClient.banderaDesdeCodigoPais(detalle.geoIp.codigoPais)
                    val lugar = listOfNotNull(detalle.geoIp.ciudad, detalle.geoIp.pais).joinToString(", ")
                    Surface(color = SuperficieApp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("📍 Servidor real", color = TextoPrimario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("$bandera $lugar", color = TextoSecundario, fontSize = 12.sp)
                            if (!detalle.geoIp.isp.isNullOrBlank()) {
                                Text("ISP: ${detalle.geoIp.isp}", color = TextoSecundario, fontSize = 12.sp)
                            }
                            if (detalle.enlaceM3uPorIp != null) {
                                Spacer(Modifier.height(6.dp))
                                Text("M3U por IP:", color = TextoSecundario, fontSize = 11.sp)
                                Text(detalle.enlaceM3uPorIp, color = TextoPrimario, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                PanelColapsable("TV en vivo", detalle.canales)
                Spacer(Modifier.height(8.dp))
                PanelColapsable("Series", detalle.series)
                Spacer(Modifier.height(8.dp))
                PanelColapsable("Películas", detalle.peliculas)
            }
        }

        if (detalle != null && (onGuardarTxt != null || onEnviarTelegram != null)) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (onGuardarTxt != null) {
                    OutlinedButton(onClick = onGuardarTxt, modifier = Modifier.weight(1f)) {
                        Text("Guardar .txt")
                    }
                }
                if (onEnviarTelegram != null) {
                    Button(onClick = onEnviarTelegram, modifier = Modifier.weight(1f)) {
                        Text("Enviar a Telegram")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Text(etiqueta, color = TextoSecundario, fontSize = 12.5.sp)
        Text(valor, color = TextoPrimario, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}
