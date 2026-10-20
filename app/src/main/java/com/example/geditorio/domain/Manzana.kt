package com.example.geditorio.domain

import org.json.JSONObject

/**
 * Modelo de manzanas y territorios a partir del GeoJSON de manzanas.
 *
 * IMPORTANTE: los datos provienen de un archivo GeoJSON que todavia NO esta
 * confirmado como registro completo y definitivo. Por eso "faltan" significa
 * "faltan respecto de este archivo", no "faltan en la realidad".
 *
 * El parser no inventa datos: si un poligono no trae "idManzana" o
 * "territorioId", se descarta y se cuenta como invalido.
 */

/** Un punto del contorno de una manzana. */
data class Punto(val x: Float, val y: Float)

/**
 * Una manzana: un poligono con su id y el territorio al que pertenece.
 *
 * [contorno] es el anillo exterior en el orden del archivo. [etiqueta] es la
 * posicion donde conviene dibujar el numero de manzana.
 */
data class Manzana(
    val idManzana: String,
    val territorioId: String,
    val contorno: List<Punto>,
) {
    /** Punto medio (promedio de los vertices) del contorno. */
    val centro: Punto by lazy {
        if (contorno.isEmpty()) Punto(0f, 0f)
        else Punto(
            contorno.map { it.x }.average().toFloat(),
            contorno.map { it.y }.average().toFloat(),
        )
    }
}

/** Todas las manzanas de un territorio, con su id normalizado. */
data class Territorio(
    val territorioId: String,
    val manzanas: List<Manzana>,
)

/** Resultado del parseo: lo util mas el detalle de lo que no se pudo leer. */
data class ManzanasGeoData(
    val territorios: List<Territorio>,
    val invalidos: List<String>,
) {
    /** Todas las manzanas, sin importar el territorio. */
    val manzanas: List<Manzana> get() = territorios.flatMap { it.manzanas }

    /** Manzanas de un territorio, por su id. */
    fun manzanasDe(territorioId: String): List<Manzana> =
        territorios.firstOrNull { it.territorioId == territorioId }?.manzanas.orEmpty()

    /** Busca una manzana por su id, en cualquier territorio. */
    fun manzanaPorId(idManzana: String): Manzana? =
        manzanas.firstOrNull { it.idManzana == idManzana }
}

/**
 * Lee un GeoJSON de manzanas. Acepta un FeatureCollection o un solo Feature.
 *
 * Cada Feature debe tener geometry.type = "Polygon" y, en properties, los
 * campos "idManzana" y "territorioId". Se usan solo esos dos identificadores;
 * no se leen ni se escriben otros campos.
 */
fun parseManzanasGeoJson(texto: String): ManzanasGeoData {
    val raiz = JSONObject(texto)
    val features = when (val tipo = raiz.optString("type")) {
        "FeatureCollection" -> raiz.optJSONArray("features")
        "Feature" -> null
        else -> error("GeoJSON con 'type' no soportado: $tipo")
    }

    val lista = when {
        features != null -> (0 until features.length()).mapNotNull { features.optJSONObject(it) }
        raiz.optString("type") == "Feature" -> listOf(raiz)
        else -> emptyList()
    }

    val porTerritorio = linkedMapOf<String, MutableList<Manzana>>()
    val invalidos = mutableListOf<String>()

    for ((indice, feature) in lista.withIndex()) {
        val properties = feature.optJSONObject("properties")
        val idManzana = properties?.optString("idManzana").orEmpty().trim()
        val territorioId = properties?.optString("territorioId").orEmpty().trim()

        if (idManzana.isEmpty() || territorioId.isEmpty()) {
            invalidos.add("Feature #${indice + 1}: sin idManzana o territorioId")
            continue
        }

        val contorno = leerAnilloExterior(feature.optJSONObject("geometry"))
        if (contorno.size < 3) {
            invalidos.add("Manzana $idManzana (T $territorioId): contorno invalido")
            continue
        }

        porTerritorio.getOrPut(territorioId) { mutableListOf() }
            .add(Manzana(idManzana = idManzana, territorioId = territorioId, contorno = contorno))
    }

    // Orden estable: territorios y manzanas por id, de forma natural.
    val territorios = porTerritorio
        .map { (territorioId, manzanas) ->
            Territorio(territorioId, manzanas.sortedBy { it.idManzana.naturalKey() })
        }
        .sortedBy { it.territorioId.naturalKey() }

    return ManzanasGeoData(territorios = territorios, invalidos = invalidos)
}

/** Primer anillo exterior de un poligono, en orden. */
private fun leerAnilloExterior(geometry: JSONObject?): List<Punto> {
    if (geometry == null || geometry.optString("type") != "Polygon") return emptyList()
    val anillo = geometry.optJSONArray("coordinates")?.optJSONArray(0) ?: return emptyList()
    return (0 until anillo.length()).mapNotNull { indice ->
        val punto = anillo.optJSONArray(indice) ?: return@mapNotNull null
        if (punto.length() < 2) return@mapNotNull null
        Punto(punto.optDouble(0).toFloat(), punto.optDouble(1).toFloat())
    }
}

/** Clave de orden natural: "2" antes que "10", y el texto alfabetico al final. */
private fun String.naturalKey(): String =
    if (all { it.isDigit() }) "0" + padStart(12, '0') else "1$this"
