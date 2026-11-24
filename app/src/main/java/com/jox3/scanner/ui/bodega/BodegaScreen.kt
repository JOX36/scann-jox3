package com.jox3.scanner.ui.bodega

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jox3.scanner.scan.bodega.EntradaBodega
import com.jox3.scanner.scan.bodega.EstadoRevalidacion
import com.jox3.scanner.scan.bodega.diasHoy
import com.jox3.scanner.scan.bodega.hostDe
import com.jox3.scanner.scan.bodega.ordenarBodega
import com.jox3.scanner.ui.results.FilaResultado
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.ErrorApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

private enum class FiltroBodega(val etiqueta: String) {
    TODAS("Todas"),
    LATAM("LATAM"),
    OTRAS("Otras"),
    VIGENTES("Vigentes"),
    POR_VENCER("Por vencer"),
    CAIDAS("Caídas / vencidas"),
}

private const val DIAS_POR_VENCER = 15

private fun EntradaBodega.vencida() = (lista.diasHoy() ?: 0) < 0
private fun EntradaBodega.caida() = !vigente || vencida()

/**
 * Bodega: todas las listas activas que la app ha encontrado, ordenadas por host (A→Z) y, dentro de
 * cada host, de más a menos días de vigencia. Tocar una abre su información completa.
 */
@Composable
fun BodegaScreen(
    entradas: List<EntradaBodega>,
    revalidacion: EstadoRevalidacion,
    onAbrir: (EntradaBodega) -> Unit,
    onEliminar: (EntradaBodega) -> Unit,
    onEliminarVarias: (List<EntradaBodega>) -> Unit,
    onRevalidar: () -> Unit,
    onLimpiar: () -> Unit,
) {
    var consulta by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf(FiltroBodega.TODAS) }
    var porBorrar by remember { mutableStateOf<EntradaBodega?>(null) }
    // Selección múltiple: claves de las listas marcadas; `seleccionando` activa el modo.
    var seleccionando by remember { mutableStateOf(false) }
    var marcadas by remember { mutableStateOf(setOf<String>()) }
    var confirmarVarias by remember { mutableStateOf(false) }

    val visibles = remember(entradas, consulta, filtro) {
        val q = consulta.trim().lowercase()
        ordenarBodega(entradas).filter { e ->
            val dias = e.lista.diasHoy()
            val pasaFiltro = when (filtro) {
                FiltroBodega.TODAS -> true
                FiltroBodega.LATAM -> e.lista.esLatino
                FiltroBodega.OTRAS -> !e.lista.esLatino
                FiltroBodega.VIGENTES -> !e.caida()
                FiltroBodega.POR_VENCER -> !e.caida() && dias != null && dias < DIAS_POR_VENCER
                FiltroBodega.CAIDAS -> e.caida()
            }
            pasaFiltro && (q.isEmpty() || hostDe(e).contains(q) ||
                e.lista.credenciales.username.lowercase().contains(q) ||
                e.lista.origenChat.lowercase().contains(q))
        }
    }
    fun claveDe(e: EntradaBodega) = "${hostDe(e)}|${e.lista.credenciales.username}"
    val grupos = remember(visibles) { visibles.groupBy { hostDe(it) } }
    // Las marcadas que ya no existen (p. ej. tras borrar) no cuentan.
    val marcadasVigentes = entradas.filter { claveDe(it) in marcadas }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("Bodega", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
            Text(
                "${entradas.size} lista(s) · ${entradas.map { hostDe(it) }.distinct().size} host(s) · " +
                    "${entradas.count { !it.caida() }} vigente(s)",
                color = TextoSecundario,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = consulta,
                onValueChange = { consulta = it },
                label = { Text("Buscar host, usuario o chat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FiltroBodega.entries) { f ->
                    FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text(f.etiqueta) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (seleccionando) {
                Text("${marcadasVigentes.size} seleccionada(s)", color = AcentoApp, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val todasVisibles = visibles.map { claveDe(it) }.toSet()
                            marcadas = if (todasVisibles.all { it in marcadas }) marcadas - todasVisibles else marcadas + todasVisibles
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Todas las visibles") }
                    Button(
                        onClick = { confirmarVarias = true },
                        enabled = marcadasVigentes.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Eliminar (${marcadasVigentes.size})") }
                }
                TextButton(onClick = { seleccionando = false; marcadas = emptySet() }) { Text("Cancelar selección") }
            } else if (revalidacion.enCurso) {
                Text("Revalidando… ${revalidacion.hechas}/${revalidacion.total}", color = AcentoApp, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = if (revalidacion.total > 0) revalidacion.hechas.toFloat() / revalidacion.total else 0f,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onRevalidar, enabled = entradas.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text("Revalidar todas")
                    }
                    OutlinedButton(
                        onClick = onLimpiar,
                        enabled = entradas.any { it.caida() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Limpiar caídas") }
                }
                TextButton(onClick = { seleccionando = true }, enabled = entradas.isNotEmpty()) { Text("☑ Seleccionar varias para eliminar") }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (visibles.isEmpty()) {
            item {
                Text(
                    if (entradas.isEmpty()) {
                        "Aún no hay listas guardadas. Cada lista activa que encuentre un escaneo (o que verifiques) se guarda aquí sola."
                    } else {
                        "Ninguna lista coincide con la búsqueda o el filtro."
                    },
                    color = TextoSecundario,
                    fontSize = 13.sp,
                )
            }
        }

        grupos.forEach { (host, lista) ->
            item(key = "h_$host") {
                Text(
                    if (lista.size > 1) "$host · ${lista.size}" else host,
                    color = AcentoApp,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            items(lista, key = { "${hostDe(it)}|${it.lista.credenciales.username}" }) { entrada ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val clave = claveDe(entrada)
                    Box(modifier = Modifier.weight(1f)) {
                        FilaResultado(
                            // Días calculados hoy y estado según la última revalidación.
                            entrada.lista.copy(diasRestantes = entrada.lista.diasHoy(), activa = entrada.vigente),
                            onClick = {
                                if (seleccionando) marcadas = if (clave in marcadas) marcadas - clave else marcadas + clave
                                else onAbrir(entrada)
                            },
                        )
                    }
                    if (seleccionando) {
                        Checkbox(
                            checked = clave in marcadas,
                            onCheckedChange = { marcadas = if (clave in marcadas) marcadas - clave else marcadas + clave },
                        )
                    } else {
                        IconButton(onClick = { porBorrar = entrada }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Quitar de la bodega", tint = ErrorApp.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }

    if (confirmarVarias) {
        AlertDialog(
            onDismissRequest = { confirmarVarias = false },
            title = { Text("¿Quitar ${marcadasVigentes.size} lista(s) de la bodega?") },
            text = { Text("Se borran de la bodega; esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    onEliminarVarias(marcadasVigentes)
                    confirmarVarias = false; seleccionando = false; marcadas = emptySet()
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { confirmarVarias = false }) { Text("Cancelar") } },
        )
    }

    porBorrar?.let { e ->
        AlertDialog(
            onDismissRequest = { porBorrar = null },
            title = { Text("¿Quitar de la bodega?") },
            text = { Text(hostDe(e) + " · " + e.lista.credenciales.username) },
            confirmButton = { TextButton(onClick = { onEliminar(e); porBorrar = null }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { porBorrar = null }) { Text("Cancelar") } },
        )
    }
}
