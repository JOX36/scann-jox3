package com.jox3.scanner.scan.engine

/**
 * Puerto del detector de contenido latino del script de Python
 * (`es_contenido_latino` / `LATIN_KEYWORDS` / `UMBRAL_CONTENIDO_LATINO`),
 * con la misma historia de ajustes: ya no se usa la zona horaria del
 * servidor (un server en Europa puede tener contenido 100% latino), y el
 * umbral quedó bajo (12%, no 50%) porque los nombres de categoría de un
 * panel Xtream suelen mezclar géneros en inglés aunque el contenido sea
 * mayormente latino.
 */
private val PALABRAS_CLAVE_LATINAS: Set<String> = setOf(
    // Región / idioma
    "LATINO", "LATINA", "LATINOAMERICA", "LATAM", "HISPANO", "ESPAÑOL", "ESPANOL",
    // Países
    "MEXICO", "MÉXICO", "COLOMBIA", "ARGENTINA", "CHILE", "PERU", "PERÚ", "VENEZUELA",
    "ECUADOR", "BOLIVIA", "PARAGUAY", "URUGUAY", "PANAMA", "PANAMÁ", "COSTA RICA",
    "GUATEMALA", "HONDURAS", "EL SALVADOR", "NICARAGUA", "REPUBLICA DOMINICANA",
    "REPÚBLICA DOMINICANA", "CUBA", "PUERTO RICO", "BRASIL", "BRAZIL",
    // Géneros genéricos en español (frecuentes en categorías de paneles latinos)
    "DEPORTES", "NOTICIAS", "PELICULAS", "PELÍCULAS", "NOVELAS", "INFANTIL",
    "ANIMADOS", "ENTRETENIMIENTO", "MUSICA", "MÚSICA", "DOCUMENTALES", "VARIEDAD",
    "NACIONAL", "RELIGIOSO", "EVENTOS", "CINE",
    // Canales / cadenas latinoamericanas conocidas
    "CARACOL", "RCN", "TELEMUNDO", "UNIVISION", "UNIVISIÓN", "AZTECA", "TELEVISA",
    "WILLAX", "CHILEVISION", "CHILEVISIÓN", "TELEFE", "GLOBO", "SBT", "RECORD",
    "ESPN SUR", "FOX SPORTS MX", "HBO LATINO", "CINECANAL", "TLNOVELAS", "DE PELICULA",
)

const val UMBRAL_CONTENIDO_LATINO = 0.12

/**
 * Evalúa una lista de textos (nombres de categoría, o nombres de canal como
 * respaldo) y decide si el panel es mayormente contenido latino.
 *
 * Devuelve `(esLatino, proporcion)` — se guarda el porcentaje real (no solo
 * el booleano) para poder mostrarlo en los resultados, igual que
 * `latino_pct` en el script de Python.
 */
fun esContenidoLatino(textos: List<String>): Pair<Boolean, Double> {
    if (textos.isEmpty()) return false to 0.0

    val coincidencias = textos.count { texto ->
        val normalizado = texto.uppercase()
        PALABRAS_CLAVE_LATINAS.any { palabra -> normalizado.contains(palabra) }
    }

    val proporcion = coincidencias.toDouble() / textos.size
    return (proporcion >= UMBRAL_CONTENIDO_LATINO) to proporcion
}
