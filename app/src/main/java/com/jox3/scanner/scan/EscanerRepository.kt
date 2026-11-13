package com.jox3.scanner.scan

import com.jox3.scanner.scan.data.ChatTelegram
import com.jox3.scanner.scan.data.extraerCredencialesXtream
import com.jox3.scanner.scan.data.extraerEnlaces
import com.jox3.scanner.scan.engine.ListaValidada
import com.jox3.scanner.scan.engine.validarYEnriquecer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Eventos que emite un escaneo en curso. */
sealed class EventoEscaneo {
    data class Progreso(val procesados: Int, val total: Int) : EventoEscaneo()
    data class ListaEncontrada(val lista: ListaValidada) : EventoEscaneo()
    object Finalizado : EventoEscaneo()
}

/**
 * Busca enlaces en los mensajes de los chats seleccionados y los valida uno
 * por uno. La parte de "leer mensajes de Telegram" es lo que reemplaza
 * TDLib más adelante; la validación (extracción de credenciales +
 * [validarYEnriquecer]) ya es el motor real, reutilizable sin cambios.
 */
interface EscanerRepository {
    fun escanear(chats: List<ChatTelegram>): Flow<EventoEscaneo>
}

/**
 * Simula mensajes de Telegram con enlaces de listas. Se incluye un enlace
 * real de prueba (el mismo que el usuario reportó durante el ajuste del
 * detector de contenido latino) para poder validar el motor completo
 * (extracción + verificación Xtream + detección latina) contra un panel de
 * verdad mientras la lectura real de Telegram no está integrada.
 */
class FakeEscanerRepository : EscanerRepository {

    private val mensajesSimulados = listOf(
        "📺 Nueva lista activa, pruébenla:\n" +
            "http://elmatematicoeseljotodejc.neospaxy.top:8080/get.php?username=T77zr3-v5KXB&password=T77zr3-v5KXB&type=m3u_plus",
        "Lista de respaldo por si la de arriba falla:\n" +
            "http://ejemplo-servidor-demo.invalid:8080/get.php?username=demo123&password=demo123&type=m3u_plus",
    )

    override fun escanear(chats: List<ChatTelegram>): Flow<EventoEscaneo> = flow {
        val enlaces = mensajesSimulados.flatMap { extraerEnlaces(it) }.distinct()
        val origen = chats.firstOrNull()?.titulo ?: "chat desconocido"

        emit(EventoEscaneo.Progreso(0, enlaces.size))
        enlaces.forEachIndexed { indice, enlace ->
            val credenciales = extraerCredencialesXtream(enlace)
            if (credenciales != null) {
                val validada = validarYEnriquecer(credenciales, origen)
                emit(EventoEscaneo.ListaEncontrada(validada))
            }
            emit(EventoEscaneo.Progreso(indice + 1, enlaces.size))
            delay(300)
        }
        emit(EventoEscaneo.Finalizado)
    }
}
