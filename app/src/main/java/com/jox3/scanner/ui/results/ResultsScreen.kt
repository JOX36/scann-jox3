package com.jox3.scanner.ui.results

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.ui.scan.PanelDiagnostico
import com.jox3.scanner.ui.scan.PanelErrores
import com.jox3.scanner.ui.scan.PanelRegistro
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.AmarilloApp
import com.jox3.scanner.ui.theme.BordeApp
import com.jox3.scanner.ui.theme.ErrorApp
import com.jox3.scanner.ui.theme.OnAcentoApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

/** Días restantes por debajo de los cuales una lista activa se considera "por vencer" (igual que Python). */
private const val UMBRAL_POR_VENCER = 15

private enum class FiltroResultados(val etiqueta: String) {
    TODAS("Todas"),
    POR_VENCER("Por vencer"),
    LATAM("LATAM"),
}

/** Pantalla "Resultados": el resumen final, igual que `print_scan_summary` del script de Python. */
@Composable
fun ResultsScreen(
    listas: List<ListaValidada>,
    resumen: com.jox3.scanner.scan.ResumenEscaneo? = null,
    onEscanearDeNuevo: () -> Unit,
    errores: List<String> = emptyList(),
    diagnostico: List<String> = emptyList(),
    registro: List<String> = emptyList(),
    onGuardarTxt: (() -> Unit)? = null,
    onEnviarTelegram: (() -> Unit)? = null,
    onCompartirRegistro: (() -> Unit)? = null,
    onListaElegida: (ListaValidada) -> Unit = {},
) {
    val activas = listas.filter { it.activa }
    val latam = activas.filter { it.esLatino }
    val porVencer = activas.filter { it.diasRestantes != null && it.diasRestantes < UMBRAL_POR_VENCER }

    var filtro by remember { mutableStateOf(FiltroResultados.TODAS) }
    val listaMostrada = when (filtro) {
        FiltroResultados.TODAS -> activas
        FiltroResultados.POR_VENCER -> porVencer
        FiltroResultados.LATAM -> latam
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Resultados", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
        Spacer(Modifier.height(12.dp))

        if (resumen != null) {
            ResumenFinal(resumen, porVencer = porVencer.size)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EstadisticaChip("Activas", activas.size.toString(), AcentoApp, Modifier.weight(1f))
                EstadisticaChip("Validadas", listas.size.toString(), TextoPrimario, Modifier.weight(1f))
                EstadisticaChip("LATAM", latam.size.toString(), com.jox3.scanner.ui.theme.AcentoSecundario, Modifier.weight(1f))
            }
        }

        if (errores.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PanelErrores(errores)
        }

        if (diagnostico.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PanelDiagnostico(diagnostico)
        }

        if (registro.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PanelRegistro(registro)
        }

        if (onCompartirRegistro != null && (errores.isNotEmpty() || diagnostico.isNotEmpty() || registro.isNotEmpty())) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCompartirRegistro, modifier = Modifier.fillMaxWidth()) {
                Text("Descargar/compartir registro (.txt)")
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FiltroResultados.entries.forEach { opcion ->
                FiltroChip(opcion.etiqueta, seleccionado = filtro == opcion, onClick = { filtro = opcion })
            }
        }

        Spacer(Modifier.height(16.dp))

        if (listaMostrada.isEmpty()) {
            Text(
                when {
                    listas.isEmpty() && errores.isNotEmpty() ->
                        "No se encontraron enlaces. Revisa los chats con error arriba: puede ser la causa real."
                    listas.isEmpty() -> "No se encontraron enlaces con este modo de búsqueda."
                    else -> "Ninguna lista coincide con este filtro."
                },
                color = TextoSecundario,
                fontSize = 13.sp,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(listaMostrada) { lista -> FilaResultado(lista, onClick = { onListaElegida(lista) }) }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (onGuardarTxt != null || onEnviarTelegram != null) {
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
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onEscanearDeNuevo, modifier = Modifier.fillMaxWidth()) {
            Text("Escanear de nuevo")
        }
    }
}

/**
 * Resumen final del escaneo: totales de TODAS las listas validadas (no solo las que el modo deja en
 * pantalla). Colores con significado fijo en toda la app: verde = activa, rojo = caída, turquesa = LATAM,
 * violeta = España, amarillo = por vencer.
 */
@Composable
private fun ResumenFinal(r: com.jox3.scanner.scan.ResumenEscaneo, porVencer: Int) {
    val tasa = if (r.escaneadas > 0) r.activas * 100 / r.escaneadas else 0
    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, com.jox3.scanner.ui.theme.BordeHud),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("RESUMEN DEL ESCANEO", color = TextoSecundario, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                Text("$tasa% activas", color = AcentoApp, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = com.jox3.scanner.ui.common.FuenteHud)
            }
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = tasa / 100f,
                color = AcentoApp,
                trackColor = ErrorApp.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EstadisticaChip("Escaneadas", r.escaneadas.toString(), TextoPrimario, Modifier.weight(1f))
                EstadisticaChip("ON", r.activas.toString(), AcentoApp, Modifier.weight(1f))
                EstadisticaChip("OFF", r.caidas.toString(), ErrorApp, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EstadisticaChip("Latinas", r.latinas.toString(), com.jox3.scanner.ui.theme.AcentoSecundario, Modifier.weight(1f))
                EstadisticaChip("No latinas", r.noLatinas.toString(), TextoPrimario, Modifier.weight(1f))
                EstadisticaChip("España", r.espana.toString(), com.jox3.scanner.ui.theme.VioletaApp, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            val min = r.duracionSeg / 60
            val seg = r.duracionSeg % 60
            val partes = buildList {
                if (porVencer > 0) add("⏳ $porVencer por vencer")
                if (r.mensajes > 0) add("📨 ${r.mensajes} mensajes")
                if (r.duracionSeg > 0) add("⏱ %d:%02d".format(min, seg))
            }
            if (partes.isNotEmpty()) {
                Text(partes.joinToString("  ·  "), color = TextoSecundario, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EstadisticaChip(etiqueta: String, valor: String, colorValor: Color, modifier: Modifier = Modifier) {
    Surface(color = SuperficieApp, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, com.jox3.scanner.ui.theme.BordeHud), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(valor, color = colorValor, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = com.jox3.scanner.ui.common.FuenteHud)
            Text(etiqueta.uppercase(), color = TextoSecundario, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun FiltroChip(etiqueta: String, seleccionado: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (seleccionado) AcentoApp else SuperficieApp,
        shape = RoundedCornerShape(50),
        border = if (seleccionado) null else BorderStroke(1.dp, BordeApp),
        onClick = onClick,
    ) {
        Text(
            etiqueta,
            color = if (seleccionado) OnAcentoApp else TextoSecundario,
            fontSize = 13.sp,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * Fila de resultado con los mismos datos que ya calcula
 * `validate_m3u_url()` en Python: días para vencer (o "Expirada"),
 * conexiones activas/máximas, zona horaria del servidor (no hay
 * geolocalización real — se usa este dato en vez de inventar una
 * ubicación) y cantidad de canales.
 */
@Composable
internal fun FilaResultado(lista: ListaValidada, onClick: (() -> Unit)? = null) {
    Surface(
        color = SuperficieApp,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            when {
                lista.diasRestantes == null -> BordeApp
                lista.diasRestantes < 0 -> ErrorApp.copy(alpha = 0.55f)
                lista.diasRestantes < UMBRAL_POR_VENCER -> AmarilloApp.copy(alpha = 0.55f)
                else -> AcentoApp.copy(alpha = 0.40f)
            },
        ),
        onClick = onClick ?: {},
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(lista.credenciales.host, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
                InsigniaVencimiento(lista.diasRestantes)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (lista.conexionesMax != null) {
                    Text("🔗 ${lista.conexionesActivas ?: 0}/${lista.conexionesMax}", color = TextoSecundario, fontSize = 12.sp)
                }
                if (!lista.zonaHoraria.isNullOrBlank()) {
                    Text("🕐 ${lista.zonaHoraria}", color = TextoSecundario, fontSize = 12.sp)
                }
                if (lista.cantidadCanales != null) {
                    Text("📶 ${lista.cantidadCanales} canales", color = TextoSecundario, fontSize = 12.sp)
                }
            }
            if (lista.esLatino) {
                Spacer(Modifier.height(4.dp))
                Text("🌎 LATAM · ${(lista.porcentajeLatino * 100).toInt()}%", color = AcentoApp, fontSize = 12.sp)
                Spacer(Modifier.height(3.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = lista.porcentajeLatino.toFloat().coerceIn(0f, 1f),
                    color = AcentoApp,
                    trackColor = AcentoApp.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
            }
            if (lista.esEspana) {
                Spacer(Modifier.height(4.dp))
                Text("🇪🇸 Contenido de España (no LATAM)", color = TextoSecundario, fontSize = 12.sp)
            }
            if (!lista.direccionIp.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("🧭 IP alternativa: ${lista.direccionIp}", color = TextoSecundario, fontSize = 11.sp)
            }
            Text("Origen: ${lista.origenChat}", color = TextoSecundario, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InsigniaVencimiento(diasRestantes: Int?) {
    val (texto, color) = when {
        diasRestantes == null -> "Sin vencimiento" to TextoSecundario
        diasRestantes < 0 -> "Expirada" to ErrorApp
        diasRestantes < UMBRAL_POR_VENCER -> "$diasRestantes días" to AmarilloApp
        else -> "$diasRestantes días" to AcentoApp
    }
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Text(texto, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = com.jox3.scanner.ui.common.FuenteHud, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}
