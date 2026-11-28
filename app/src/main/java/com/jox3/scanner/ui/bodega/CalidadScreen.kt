package com.jox3.scanner.ui.bodega

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.bodega.EntradaBodega
import com.jox3.scanner.scan.bodega.InformeCalidad
import com.jox3.scanner.scan.bodega.RepositorioBodega
import com.jox3.scanner.scan.bodega.diasHoy
import com.jox3.scanner.ui.common.FuenteHud
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.AcentoSecundario
import com.jox3.scanner.ui.theme.AmarilloApp
import com.jox3.scanner.ui.theme.BordeHud
import com.jox3.scanner.ui.theme.ErrorApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

private enum class FiltroCalidad(val etiqueta: String) {
    TODAS("Todas"), LATAM("LATAM"), OTRAS("Otras"), MEJORES("Buenas o mejores"),
}

private fun colorNota(puntaje: Int): Color = when {
    puntaje >= 80 -> AcentoApp
    puntaje >= 60 -> AcentoSecundario
    puntaje >= 40 -> AmarilloApp
    else -> ErrorApp
}

private fun hace(t: Long): String {
    if (t <= 0L) return ""
    val m = (System.currentTimeMillis() - t) / 60_000L
    return when {
        m < 1 -> "medida ahora"
        m < 60 -> "medida hace $m min"
        m < 1440 -> "medida hace ${m / 60} h"
        else -> "medida hace ${m / 1440} d"
    }
}

private data class ResumenServidor(val host: String, val promedio: Int, val mejor: Int, val listas: Int)

/**
 * "Verificadas": las listas cuya calidad ya se midió, guardadas para elegir en cualquier momento.
 * Tocar una abre su información completa; ↻ la vuelve a medir y 🗑 la quita de esta sección.
 */
@Composable
fun CalidadScreen(
    informes: List<InformeCalidad>,
    entradas: List<EntradaBodega>,
    aviso: String?,
    analizando: Boolean,
    onAbrir: (EntradaBodega) -> Unit,
    onRemedir: (InformeCalidad) -> Unit,
    onQuitar: (InformeCalidad) -> Unit,
) {
    var consulta by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf(FiltroCalidad.TODAS) }
    val porClave = remember(entradas) { entradas.associateBy { RepositorioBodega.clave(it.lista.credenciales) } }

    val visibles = remember(informes, consulta, filtro) {
        val q = consulta.trim().lowercase()
        informes.filter { i ->
            val pasa = when (filtro) {
                FiltroCalidad.TODAS -> true
                FiltroCalidad.LATAM -> i.esLatino
                FiltroCalidad.OTRAS -> !i.esLatino
                FiltroCalidad.MEJORES -> i.puntaje >= 60
            }
            pasa && (q.isEmpty() || i.host.lowercase().contains(q) || i.usuario.lowercase().contains(q) || i.origen.lowercase().contains(q))
        }
    }
    val servidores = remember(visibles) {
        visibles.groupBy { it.host }
            .map { (h, l) -> ResumenServidor(h, l.map { it.puntaje }.average().toInt(), l.maxOf { it.puntaje }, l.size) }
            .sortedByDescending { it.promedio }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("Listas verificadas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
            Text(
                "${informes.size} lista(s) · ${informes.map { it.host }.distinct().size} servidor(es) · guardadas, sin volver a medir",
                color = TextoSecundario, fontSize = 12.sp,
            )
            if (aviso != null) Text(aviso, color = AcentoApp, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "Prueba orientativa: mide la respuesta del panel y unos segundos de un canal en vivo. " +
                    "La nota (0–100) mezcla respuesta, velocidad, estabilidad, vigencia y conexiones permitidas.",
                color = TextoSecundario, fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = consulta, onValueChange = { consulta = it },
                label = { Text("Buscar host, usuario o chat") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FiltroCalidad.entries) { f ->
                    FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text(f.etiqueta) })
                }
            }
            Spacer(Modifier.height(10.dp))
            if (informes.isEmpty()) {
                Text(
                    if (analizando) "Midiendo… las listas irán apareciendo aquí." else
                        "Aún no hay listas verificadas. En la sección Todas toca «☑ Seleccionar varias», marca las listas y elige «📊 Analizar calidad»; quedarán guardadas aquí.",
                    color = TextoSecundario, fontSize = 13.sp,
                )
            } else if (visibles.isEmpty()) {
                Text("Ninguna lista coincide con la búsqueda o el filtro.", color = TextoSecundario, fontSize = 13.sp)
            } else {
                Text("Mejores servidores", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
                Spacer(Modifier.height(6.dp))
            }
        }
        items(servidores.take(5), key = { "s_" + it.host }) { s ->
            Surface(
                color = SuperficieApp, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BordeHud),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.host, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextoPrimario)
                        Text("${s.listas} lista(s) · mejor ${s.mejor}", color = TextoSecundario, fontSize = 11.sp)
                    }
                    Text("${s.promedio}", fontFamily = FuenteHud, fontWeight = FontWeight.Black, fontSize = 22.sp, color = colorNota(s.promedio))
                }
            }
        }
        if (visibles.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Listas (de mejor a peor)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
                Spacer(Modifier.height(6.dp))
            }
        }
        items(visibles.size, key = { "l_" + visibles[it].clave }) { idx ->
            val inf = visibles[idx]
            val entrada = porClave[inf.clave]
            val dias = entrada?.lista?.diasHoy() ?: inf.diasRestantes
            val caida = entrada != null && (!entrada.vigente || (entrada.lista.diasHoy() ?: 0) < 0)
            val color = colorNota(inf.puntaje)
            Surface(
                color = SuperficieApp, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                onClick = { entrada?.let(onAbrir) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${idx + 1} · ${inf.host}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextoPrimario)
                            Text("${inf.usuario} · ${inf.origen}", color = TextoSecundario, fontSize = 11.sp)
                            Text(hace(inf.medidoEn), color = TextoSecundario, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${inf.puntaje}", fontFamily = FuenteHud, fontWeight = FontWeight.Black, fontSize = 26.sp, color = color)
                            Text(inf.veredicto, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    inf.respuestaMs?.let {
                        Linea("⚡ Respuesta", "$it ms" + (inf.variacionMs?.let { v -> " (±$v)" } ?: "") + " · ${inf.pingsOk}/3 ok")
                    }
                    if (inf.arranqueMs != null && inf.mbps != null) {
                        Linea("▶ Canal en vivo", "abre en ${"%.1f".format(inf.arranqueMs / 1000.0)} s · ${"%.1f".format(inf.mbps)} Mbps")
                    }
                    dias?.let { Linea("📅 Vigencia", "$it día(s)") }
                    if (inf.conexionesMax != null) Linea("🔗 Conexiones", "${inf.conexionesActivas ?: 0}/${inf.conexionesMax}")
                    val contenido = listOfNotNull(
                        inf.canales?.let { "$it canales" }, inf.peliculas?.let { "$it películas" }, inf.series?.let { "$it series" },
                    )
                    if (contenido.isNotEmpty()) Linea("📺 Contenido", contenido.joinToString(" · "))
                    if (caida) Text("⚠ La Bodega la marca caída o vencida desde la última revalidación.", color = ErrorApp, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    inf.avisos.forEach {
                        Text("⚠ $it", color = AmarilloApp, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onRemedir(inf) }, enabled = !analizando && entrada != null) { Text("↻ Volver a medir") }
                        TextButton(onClick = { onQuitar(inf) }) { Text("🗑 Quitar", color = ErrorApp) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun Linea(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, color = TextoSecundario, fontSize = 12.sp)
        Text(valor, color = TextoPrimario, fontSize = 12.sp, fontFamily = FuenteHud)
    }
}
