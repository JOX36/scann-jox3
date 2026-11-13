package com.jox3.scanner.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
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
