package com.jox3.scanner.scan.engine

import java.text.Normalizer

/**
 * Detector de contenido LATINO. Antes (heredado del script de Python) mezclaba
 * "está en español" con "es de Latinoamérica": palabras genéricas como
 * DEPORTES, NOTICIAS o CINE bastaban para pasar el umbral, así que un panel de
 * España (Movistar, LaLiga, Orange…) salía como "LATAM 23 %". Ahora:
 *
 * - **Señales fuertes** ([PALABRAS_LATAM]): países latinoamericanos, "LATINO",
 *   "LATAM", telenovelas y cadenas latinas conocidas. SOLO estas cuentan para
 *   el porcentaje LATAM.
 * - **Señales de España** ([PALABRAS_ESPANA]): prefijo "ES |", Movistar, LaLiga,
 *   Rakuten, Tivify… Sirven para marcar el panel como "España".
 * - Las palabras genéricas en español ya no cuentan.
 * - Se compara por PALABRA COMPLETA, sin tildes ("INTERNACIONAL" ya no cuenta
 *   como "NACIONAL", "CINEMAX" no cuenta como "CINE").
 */
private val PALABRAS_LATAM: List<String> = listOf(
    // Región / idioma
    "LATINO", "LATINOS", "LATINA", "LATINAS", "LATINOAMERICA", "LATAM", "HISPANOAMERICA",
    // Países
    "MEXICO", "COLOMBIA", "ARGENTINA", "CHILE", "PERU", "VENEZUELA", "ECUADOR", "BOLIVIA",
    "PARAGUAY", "URUGUAY", "PANAMA", "COSTA RICA", "GUATEMALA", "HONDURAS", "EL SALVADOR",
    "NICARAGUA", "REPUBLICA DOMINICANA", "CUBA", "PUERTO RICO", "BRASIL", "BRAZIL",
    // Contenido típicamente latino
    "NOVELAS", "TELENOVELAS", "TLNOVELAS", "CANTINFLAS",
    // Cadenas / canales latinoamericanos conocidos
    "CARACOL", "RCN", "TELEMUNDO", "UNIVISION", "AZTECA", "TELEVISA", "LAS ESTRELLAS",
    "WILLAX", "CHILEVISION", "TELEFE", "GLOBO", "SBT", "ESPN SUR", "FOX SPORTS MX",
    "HBO LATINO", "CINECANAL", "DE PELICULA", "WIN SPORTS", "SENAL COLOMBIA",
    "CANAL TRECE", "AMERICA TV", "ATV", "PANAMERICANA", "VENEVISION", "TELEVEN",
)

private val PALABRAS_ESPANA: List<String> = listOf(
    "ESPANA", "ES", "MOVISTAR", "ORANGE", "LALIGA", "RAKUTEN", "TIVIFY", "VODAFONE",
    "ANTENA 3", "TELECINCO", "LA SEXTA", "TVE", "AUTONOMICOS", "ZAPI", "AVATEL",
)

const val UMBRAL_CONTENIDO_LATINO = 0.12

/** Mayúsculas, sin tildes y con todo lo que no sea letra/número convertido en espacio, rodeado de espacios. */
private fun normalizar(texto: String): String {
    val sinTildes = Normalizer.normalize(texto.uppercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    return " " + sinTildes.replace(Regex("[^A-Z0-9]+"), " ").trim() + " "
}

private fun coincide(normalizado: String, palabras: List<String>): Boolean =
    palabras.any { normalizado.contains(" $it ") }

/** Resultado del análisis de una lista de textos (categorías o nombres de canal). */
data class AnalisisContenido(
    val esLatino: Boolean,
    /** Fracción de textos con señal LATAM fuerte. */
    val proporcionLatino: Double,
    val esEspana: Boolean,
    val proporcionEspana: Double,
)

fun analizarContenido(textos: List<String>): AnalisisContenido {
    if (textos.isEmpty()) return AnalisisContenido(false, 0.0, false, 0.0)
    val normalizados = textos.map(::normalizar)
    val latam = normalizados.count { coincide(it, PALABRAS_LATAM) }.toDouble() / textos.size
    val espana = normalizados.count { coincide(it, PALABRAS_ESPANA) }.toDouble() / textos.size
    val esLatino = latam >= UMBRAL_CONTENIDO_LATINO
    return AnalisisContenido(
        esLatino = esLatino,
        proporcionLatino = latam,
        esEspana = !esLatino && espana >= UMBRAL_CONTENIDO_LATINO,
        proporcionEspana = espana,
    )
}
