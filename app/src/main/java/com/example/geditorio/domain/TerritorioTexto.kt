package com.example.geditorio.domain

/**
 * Texto de territorios y manzanas.
 *
 * Regla acordada: se lista el territorio con las manzanas marcadas, y entre
 * parentesis las manzanas que faltan respecto del archivo cargado. Ejemplo:
 *
 *     T 4 : 7250 (Faltan: 7251, 7270)
 *
 * Aclaracion importante: "Faltan" se calcula solo con las manzanas presentes
 * en el GeoJSON cargado. Si ese archivo no es el registro completo, el texto
 * puede quedar incompleto. No se completa con datos inventados.
 */

/** Una manzana marcada, con el territorio al que pertenece. */
data class ManzanaMarcada(val territorioId: String, val idManzana: String)

/** Un territorio con las manzanas que el usuario marco en el. */
data class TerritorioSeleccionado(
    val territorioId: String,
    val manzanas: List<Manzana>,
)

/**
 * Agrupa las manzanas marcadas por territorio. El orden de territorios y de
 * manzanas dentro de cada uno es el que trae la lista de entrada.
 */
fun agruparPorTerritorio(manzanas: List<Manzana>): List<TerritorioSeleccionado> {
    val porTerritorio = linkedMapOf<String, MutableList<Manzana>>()
    for (manzana in manzanas) {
        porTerritorio.getOrPut(manzana.territorioId) { mutableListOf() }.add(manzana)
    }
    return porTerritorio.map { (territorioId, lista) ->
        TerritorioSeleccionado(territorioId, lista)
    }
}

/**
 * Arma el texto final. [seleccionadas] son todas las manzanas marcadas;
 * [todasDelTerritorio] permite saber cuales faltan en cada territorio.
 */
fun textoTerritorios(
    seleccionadas: List<Manzana>,
    todasDelTerritorio: (String) -> List<Manzana>,
): String {
    if (seleccionadas.isEmpty()) return ""

    return agruparPorTerritorio(seleccionadas).joinToString("\n") { territorio ->
        val idsMarcados = territorio.manzanas.map { it.idManzana }
        val faltantes = todasDelTerritorio(territorio.territorioId)
            .map { it.idManzana }
            .filterNot { it in idsMarcados }

        val base = "T ${territorio.territorioId} : ${idsMarcados.joinToString(", ")}"
        if (faltantes.isEmpty()) base
        else "$base (Faltan: ${faltantes.joinToString(", ")})"
    }
}
