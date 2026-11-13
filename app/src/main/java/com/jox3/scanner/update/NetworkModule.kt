package com.jox3.scanner.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cliente Ktor compartido para las llamadas de red de la app. Se centraliza
 * aquí para no crear un HttpClient nuevo por cada feature (esta misma
 * instancia se reutilizará en la etapa de validación de listas Xtream).
 */
object NetworkModule {
    val cliente: HttpClient by lazy {
        HttpClient(Android) {
            // Sin esto, una llamada a un panel caído/colgado (muy común
            // entre decenas de enlaces reales: paneles vencidos que ni
            // siquiera rechazan la conexión, solo nunca responden) se queda
            // esperando para siempre — con la validación hecha una a una,
            // basta UN solo panel así para que el escaneo entero se quede
            // pegado en "0 de N enlaces validados" sin avanzar ni avisar
            // error. El script de Python ya usaba un timeout total de 10s
            // por request (`REQUEST_TIMEOUT`); acá es el equivalente.
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
        }
    }
}
