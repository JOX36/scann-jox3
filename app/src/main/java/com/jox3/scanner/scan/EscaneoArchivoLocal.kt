package com.jox3.scanner.scan

import com.jox3.scanner.scan.data.extraerCredencialesXtream
import com.jox3.scanner.scan.data.extraerEnlaces
import com.jox3.scanner.scan.engine.validarYEnriquecer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import com.jox3.scanner.scan.engine.ListaValidada

/**
 * Escanea el contenido de texto de un archivo .txt elegido del dispositivo
 * (vía el selector de archivos del sistema), igual que el modo 5 del script
 * de Python original (`escanear_archivo_local_latino`): valida todos los
 * enlaces encontrados en el texto y se queda solo con las listas activas de
 * contenido latino.
 */
fun escanearTextoLocal(nombreArchivo: String, contenido: String): Flow<EventoEscaneo> = channelFlow {
    val enlaces = extraerEnlaces(contenido).distinct()
    send(EventoEscaneo.Progreso(0, enlaces.size))

    // En paralelo (antes uno por uno: un archivo con cientos de enlaces tardaba una eternidad).
    val semaforo = Semaphore(30)
    val hechos = AtomicInteger(0)
    coroutineScope {
      enlaces.forEach { enlace ->
        launch {
            semaforo.withPermit {
                val credenciales = extraerCredencialesXtream(enlace)
                if (credenciales != null) {
                    val validada = withTimeoutOrNull(150_000L) { validarYEnriquecer(credenciales, nombreArchivo) }
                        ?: ListaValidada(credenciales, activa = false, esLatino = false, porcentajeLatino = 0.0, origenChat = nombreArchivo)
                    send(EventoEscaneo.Validada(validada))
                    if (validada.activa && validada.esLatino) {
                        send(EventoEscaneo.ListaEncontrada(validada))
                    }
                }
                send(EventoEscaneo.Progreso(hechos.incrementAndGet(), enlaces.size))
            }
        }
      }
    }
    send(EventoEscaneo.Finalizado)
}
