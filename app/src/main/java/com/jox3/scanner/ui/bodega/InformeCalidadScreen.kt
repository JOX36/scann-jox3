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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.scan.bodega.InformeCalidad
import com.jox3.scanner.ui.common.BarraVolver
import com.jox3.scanner.ui.common.FuenteHud
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.AcentoSecundario
import com.jox3.scanner.ui.theme.AmarilloApp
import com.jox3.scanner.ui.theme.BordeHud
import com.jox3.scanner.ui.theme.ErrorApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

private fun colorNota(puntaje: Int): Color = when {
    puntaje >= 80 -> AcentoApp
    puntaje >= 60 -> AcentoSecundario
    puntaje >= 40 -> AmarilloApp
    else -> ErrorApp
}

private data class ResumenServidor(val host: String, val promedio: Int, val mejor: Int, val listas: Int)

/** Informe de calidad: qué servidor y qué lista responden mejor, ordenados por nota (0–100). */
@Composable
fun InformeCalidadScreen(informes: List<InformeCalidad>, onVolver: () -> Unit) {
    val servidores = informes.groupBy { it.host }
        .map { (h, l) -> ResumenServidor(h, l.map { it.puntaje }.average().toInt(), l.maxOf { it.puntaje }, l.size) }
        .sortedByDescending { it.promedio }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            BarraVolver(onVolver)
            Text("Informe de calidad", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
            Text(
                "${informes.size} lista(s) · ${servidores.size} servidor(es)",
                color = TextoSecundario, fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Prueba orientativa: mide la respuesta del panel y unos segundos de un canal en vivo (usa una conexión un momento). " +
                    "La nota mezcla respuesta, velocidad, estabilidad, vigencia y conexiones permitidas.",
                color = TextoSecundario, fontSize = 11.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text("Servidores", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
            Spacer(Modifier.height(6.dp))
        }
        items(servidores, key = { "s_" + it.host }) { s ->
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
        item {
            Spacer(Modifier.height(16.dp))
            Text("Listas (de mejor a peor)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
            Spacer(Modifier.height(6.dp))
        }
        items(informes.size, key = { "l_" + informes[it].clave }) { i ->
            val inf = informes[i]
            val color = colorNota(inf.puntaje)
            Surface(
                color = SuperficieApp, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${i + 1} · ${inf.host}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextoPrimario)
                            Text("${inf.usuario} · ${inf.origen}", color = TextoSecundario, fontSize = 11.sp)
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
                    inf.diasRestantes?.let { Linea("📅 Vigencia", "$it día(s)") }
                    if (inf.conexionesMax != null) Linea("🔗 Conexiones", "${inf.conexionesActivas ?: 0}/${inf.conexionesMax}")
                    val contenido = listOfNotNull(
                        inf.canales?.let { "$it canales" }, inf.peliculas?.let { "$it películas" }, inf.series?.let { "$it series" },
                    )
                    if (contenido.isNotEmpty()) Linea("📺 Contenido", contenido.joinToString(" · "))
                    inf.avisos.forEach {
                        Text("⚠ $it", color = AmarilloApp, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
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
