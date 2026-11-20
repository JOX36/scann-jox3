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

/** Qué lista se muestra: cada contador del resumen es un botón que elige uno de estos filtros. */
private enum class FiltroResultados(val etiqueta: String) {
    ESCANEADAS("Escaneadas"),
    ON("Activas (ON)"),
    OFF("Caídas (OFF)"),
    LATAM("Latinas"),
    NO_LATAM("No latinas"),
    ESPANA("España"),
    POR_VENCER("Por vencer"),
}

/**
 * Pantalla "Resultados": resumen final (cada contador es un botón) y, justo debajo, la cascada de
 * listas del filtro elegido; al tocar una se abre su información completa. Todo en una sola
 * lista desplazable, así los paneles de diagnóstico ya no empujan las listas fuera de la pantalla.
 */
@Composable
fun ResultsScreen(
    listas: List<ListaValidada>,
    resumen: com.jox3.scanner.scan.ResumenEscaneo? = null,
    todas: List<ListaValidada> = emptyList(),
    hostBuscado: String? = null,
    cantidadPedida: Int? = null,
    onEscanearDeNuevo: () -> Unit,
    errores: List<String> = emptyList(),
    diagnostico: List<String> = emptyList(),
    registro: List<String> = emptyList(),
    onGuardarTxt: (() -> Unit)? = null,
    onEnviarTelegram: (() -> Unit)? = null,
    onCompartirRegistro: (() -> Unit)? = null,
    onListaElegida: (ListaValidada) -> Unit = {},
) {
    // Sin "todas" (resultado recuperado tras un cierre) se trabaja solo con las guardadas.
    val universo = if (todas.isNotEmpty()) todas else listas
    val activas = universo.filter { it.activa }
    val caidas = universo.filter { !it.activa }
    val latam = activas.filter { it.esLatino }
    val porVencer = activas.filter { it.diasRestantes != null && it.diasRestantes < UMBRAL_POR_VENCER }

    var filtro by remember { mutableStateOf(if (latam.isNotEmpty()) FiltroResultados.LATAM else FiltroResultados.ON) }
    val listaMostrada = when (filtro) {
        FiltroResultados.ESCANEADAS -> universo
        FiltroResultados.ON -> activas
        FiltroResultados.OFF -> caidas
        FiltroResultados.LATAM -> latam
        FiltroResultados.NO_LATAM -> activas.filter { !it.esLatino }
        FiltroResultados.ESPANA -> activas.filter { it.esEspana }
        FiltroResultados.POR_VENCER -> porVencer
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        item {
            Text("Resultados", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
            Spacer(Modifier.height(12.dp))
            if (hostBuscado != null) {
                val halladas = activas.size
                val (texto, color) = when {
                    halladas == 0 -> "No se encontró ninguna lista activa del host \"$hostBuscado\" en lo que se escaneó." to ErrorApp
                    cantidadPedida != null && halladas < cantidadPedida ->
                        "Pediste $cantidadPedida y solo se encontraron $halladas activa(s) del host \"$hostBuscado\"." to AmarilloApp
                    else -> "Host \"$hostBuscado\": $halladas lista(s) activa(s) encontrada(s)." to AcentoApp
                }
                Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
                    Text(texto, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            if (resumen != null) {
                ResumenFinal(
                    resumen,
                    porVencer = porVencer.size,
                    filtro = filtro,
                    onFiltro = { filtro = it },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EstadisticaChip("Activas", activas.size.toString(), AcentoApp, Modifier.weight(1f), seleccionado = filtro == FiltroResultados.ON, onClick = { filtro = FiltroResultados.ON })
                    EstadisticaChip("Validadas", universo.size.toString(), TextoPrimario, Modifier.weight(1f), seleccionado = filtro == FiltroResultados.ESCANEADAS, onClick = { filtro = FiltroResultados.ESCANEADAS })
                    EstadisticaChip("LATAM", latam.size.toString(), com.jox3.scanner.ui.theme.AcentoSecundario, Modifier.weight(1f), seleccionado = filtro == FiltroResultados.LATAM, onClick = { filtro = FiltroResultados.LATAM })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "${filtro.etiqueta} · ${listaMostrada.size}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextoPrimario,
            )
            Spacer(Modifier.height(4.dp))
        }

        if (listaMostrada.isEmpty()) {
            item {
                Text(
                    when {
                        universo.isEmpty() && errores.isNotEmpty() ->
                            "No se encontraron enlaces. Revisa los chats con error abajo: puede ser la causa real."
                        hostBuscado != null && universo.isEmpty() -> "No hay listas de \"$hostBuscado\". Prueba con otro host o con más chats."
                        universo.isEmpty() -> "No se encontraron enlaces con este modo de búsqueda."
                        else -> "Ninguna lista en esta categoría."
                    },
                    color = TextoSecundario,
                    fontSize = 13.sp,
                )
            }
        } else {
            items(listaMostrada) { lista ->
                FilaResultado(lista, onClick = if (lista.activa) ({ onListaElegida(lista) }) else null)
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            if (onGuardarTxt != null || onEnviarTelegram != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (onGuardarTxt != null) {
                        OutlinedButton(onClick = onGuardarTxt, modifier = Modifier.weight(1f)) { Text("Guardar .txt") }
                    }
                    if (onEnviarTelegram != null) {
                        Button(onClick = onEnviarTelegram, modifier = Modifier.weight(1f)) { Text("Enviar a Telegram") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onEscanearDeNuevo, modifier = Modifier.fillMaxWidth()) { Text("Escanear de nuevo") }
        }

        // Diagnóstico al final: sirve para depurar, pero no debe tapar las listas.
        if (errores.isNotEmpty() || diagnostico.isNotEmpty() || registro.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                if (errores.isNotEmpty()) {
                    PanelErrores(errores)
                    Spacer(Modifier.height(12.dp))
                }
                if (diagnostico.isNotEmpty()) {
                    PanelDiagnostico(diagnostico)
                    Spacer(Modifier.height(12.dp))
                }
                if (registro.isNotEmpty()) {
                    PanelRegistro(registro)
                    Spacer(Modifier.height(8.dp))
                }
                if (onCompartirRegistro != null) {
                    OutlinedButton(onClick = onCompartirRegistro, modifier = Modifier.fillMaxWidth()) {
                        Text("Descargar/compartir registro (.txt)")
                    }
                }
            }
        }
    }
}

/**
 * Resumen final del escaneo: totales de TODAS las listas validadas (no solo las que el modo deja en
 * pantalla). Colores con significado fijo en toda la app: verde = activa, rojo = caída, turquesa = LATAM,
 * violeta = España, amarillo = por vencer.
 */
@Composable
private fun ResumenFinal(
    r: com.jox3.scanner.scan.ResumenEscaneo,
    porVencer: Int,
    filtro: FiltroResultados,
    onFiltro: (FiltroResultados) -> Unit,
) {
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
                EstadisticaChip("Escaneadas", r.escaneadas.toString(), TextoPrimario, Modifier.weight(1f), filtro == FiltroResultados.ESCANEADAS) { onFiltro(FiltroResultados.ESCANEADAS) }
                EstadisticaChip("ON", r.activas.toString(), AcentoApp, Modifier.weight(1f), filtro == FiltroResultados.ON) { onFiltro(FiltroResultados.ON) }
                EstadisticaChip("OFF", r.caidas.toString(), ErrorApp, Modifier.weight(1f), filtro == FiltroResultados.OFF) { onFiltro(FiltroResultados.OFF) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EstadisticaChip("Latinas", r.latinas.toString(), com.jox3.scanner.ui.theme.AcentoSecundario, Modifier.weight(1f), filtro == FiltroResultados.LATAM) { onFiltro(FiltroResultados.LATAM) }
                EstadisticaChip("No latinas", r.noLatinas.toString(), TextoPrimario, Modifier.weight(1f), filtro == FiltroResultados.NO_LATAM) { onFiltro(FiltroResultados.NO_LATAM) }
                EstadisticaChip("España", r.espana.toString(), com.jox3.scanner.ui.theme.VioletaApp, Modifier.weight(1f), filtro == FiltroResultados.ESPANA) { onFiltro(FiltroResultados.ESPANA) }
            }
            Spacer(Modifier.height(10.dp))
            val min = r.duracionSeg / 60
            val seg = r.duracionSeg % 60
            if (porVencer > 0) {
                FiltroChip("⏳ $porVencer por vencer", seleccionado = filtro == FiltroResultados.POR_VENCER) { onFiltro(FiltroResultados.POR_VENCER) }
                Spacer(Modifier.height(8.dp))
            }
            val partes = buildList {
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
private fun EstadisticaChip(
    etiqueta: String,
    valor: String,
    colorValor: Color,
    modifier: Modifier = Modifier,
    seleccionado: Boolean = false,
    onClick: () -> Unit = {},
) {
    Surface(
        color = if (seleccionado) colorValor.copy(alpha = 0.16f) else SuperficieApp,
        shape = RoundedCornerShape(16.dp),
        border = if (seleccionado) BorderStroke(1.5.dp, colorValor) else BorderStroke(1.dp, com.jox3.scanner.ui.theme.BordeHud),
        onClick = onClick,
        modifier = modifier,
    ) {
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
                if (lista.activa) {
                    InsigniaVencimiento(lista.diasRestantes)
                } else {
                    Surface(color = ErrorApp.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
                        Text("OFF", color = ErrorApp, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
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
