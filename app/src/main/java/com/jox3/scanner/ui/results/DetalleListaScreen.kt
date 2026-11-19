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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.jox3.scanner.scan.engine.XtreamClient
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jox3.scanner.results.ResultadosExportador
import com.jox3.scanner.scan.engine.DetalleLista
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.ui.common.BarraVolver
import com.jox3.scanner.ui.theme.AcentoApp
import com.jox3.scanner.ui.theme.BordeApp
import com.jox3.scanner.ui.theme.SuperficieApp
import com.jox3.scanner.ui.theme.TextoPrimario
import com.jox3.scanner.ui.theme.TextoSecundario

/**
 * Detalle de una lista: muestra el mismo bloque completo que el usuario
 * pidió (el del ejemplo que mandó: host, credenciales, estado, conexiones,
 * fechas, timezone con bandera, servidor real con ISP, enlaces M3U/EPG/por
 * IP y los nombres de TV en vivo, series y películas) directamente en
 * pantalla, sin tener que ir a Telegram.
 *
 * Es solo consulta: nada se guarda ni se envía hasta que el usuario toca
 * "Guardar .txt" o "Enviar a Telegram" (botones propios de esta lista,
 * aparte de los de la pantalla de Resultados).
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
    val texto = remember(lista, detalle) { ResultadosExportador.generarContenidoDetalle(lista, detalle) }
    val contexto = LocalContext.current
    val cred = lista.credenciales
    val m3uPorIp = detalle?.enlaceM3uPorIp
        ?: lista.direccionIp?.takeIf { it.isNotBlank() }?.let { XtreamClient.enlacePorIp(cred, it) }

    fun copiar(etiqueta: String, valor: String) {
        val cm = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(etiqueta, valor))
        Toast.makeText(contexto, "$etiqueta copiado", Toast.LENGTH_SHORT).show()
    }

    /**
     * Abre JOX3 TV con `jox3://add?url=<M3U codificado>`: JOX3 TV pide confirmación, valida la
     * cuenta y la guarda. Se apunta al paquete de JOX3 TV para que ninguna otra app lo intercepte.
     */
    fun abrirEnJox3Tv() {
        val enlace = android.net.Uri.parse("jox3://add?url=" + android.net.Uri.encode(cred.urlOriginal))
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, enlace).setPackage("com.jox3.iptvexo")
        try {
            contexto.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(contexto, "JOX3 TV no está instalada o hay que actualizarla a la última versión.", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        BarraVolver(onVolver)
        Text(lista.credenciales.host, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoPrimario)
        Spacer(Modifier.height(12.dp))

        if (cargando) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = AcentoApp, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Consultando el panel (categorías y servidor)...", color = TextoSecundario, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
        } else if (detalle == null) {
            Text(
                "No se pudo traer la información extra de este panel (canales, series, películas, servidor).",
                color = TextoSecundario,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onConsultar, modifier = Modifier.fillMaxWidth()) {
                Text("Reintentar consulta")
            }
            Spacer(Modifier.height(12.dp))
        }

        if (lista.activa) {
            Button(onClick = { abrirEnJox3Tv() }, modifier = Modifier.fillMaxWidth()) {
                Text("▶  Abrir en JOX3 TV")
            }
            Spacer(Modifier.height(12.dp))
        }
        Text("Copiar enlaces", color = TextoSecundario, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { copiar("Enlace M3U", cred.urlOriginal) }, modifier = Modifier.weight(1f)) {
                Text("M3U", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { copiar("Enlace EPG", ResultadosExportador.epgLink(cred)) }, modifier = Modifier.weight(1f)) {
                Text("EPG", fontSize = 13.sp)
            }
            if (m3uPorIp != null) {
                OutlinedButton(onClick = { copiar("M3U por IP", m3uPorIp) }, modifier = Modifier.weight(1f)) {
                    Text("Por IP", fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { copiar("Información completa", texto) }, modifier = Modifier.fillMaxWidth()) {
            Text("Copiar todo el texto")
        }
        Spacer(Modifier.height(12.dp))

        Surface(
            color = SuperficieApp,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BordeApp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    texto,
                    color = TextoPrimario,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (onGuardarTxt != null || onEnviarTelegram != null) {
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
