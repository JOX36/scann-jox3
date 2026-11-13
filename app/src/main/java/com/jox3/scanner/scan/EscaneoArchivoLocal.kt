package com.jox3.scanner.scan

import com.jox3.scanner.scan.data.extraerCredencialesXtream
import com.jox3.scanner.scan.data.extraerEnlaces
import com.jox3.scanner.scan.engine.validarYEnriquecer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Escanea el contenido de texto de un archivo .txt elegido del dispositivo
 * (vía el selector de archivos del sistema), igual que el modo 5 del script
 * de Python original (`escanear_archivo_local_latino`): valida todos los
 * enlaces encontrados en el texto y se queda solo con las listas activas de
 * contenido latino.
 */
fun escanearTextoLocal(nombreArchivo: String, contenido: String): Flow<EventoEscaneo> = flow {
    val enlaces = extraerEnlaces(contenido).distinct()
    emit(EventoEscaneo.Progreso(0, enlaces.size))

    enlaces.forEachIndexed { indice, enlace ->
        val credenciales = extraerCredencialesXtream(enlace)
        if (credenciales != null) {
            val validada = validarYEnriquecer(credenciales, nombreArchivo)
            if (validada.activa && validada.esLatino) {
                emit(EventoEscaneo.ListaEncontrada(validada))
            }
        }
        emit(EventoEscaneo.Progreso(indice + 1, enlaces.size))
    }
    emit(EventoEscaneo.Finalizado)
}
