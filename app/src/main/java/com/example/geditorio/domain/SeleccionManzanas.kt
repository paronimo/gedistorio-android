package com.example.geditorio.domain

/**
 * Seleccion de manzanas hecha por el usuario en el mapa.
 *
 * Regla importante: una seleccion puede ser "por manzana" o "por territorio
 * completo". Se guardan por separado porque significan cosas distintas y
 * porque el texto del reporte las trata distinto:
 *
 *  - Territorio completo  -> se escribe solo "T 4" (sin enumerar manzanas).
 *  - Manzana(s) suelta(s) -> se escriben con su numero y entre parentesis las
 *    manzanas del mismo territorio que faltan respecto del archivo cargado.
 *
 * "Faltan" se calcula UNICAMENTE con las manzanas presentes en el GeoJSON
 * cargado. Si ese archivo no es el registro completo y definitivo, el calculo
 * puede quedar incompleto. No se completa con datos inventados.
 */
data class SeleccionMapa(
    /** Territorios marcados como completos. */
    val territoriosCompletos: Set<String> = emptySet(),
    /** Manzanas marcadas de forma individual, por territorio. */
    val manzanasPorTerritorio: Map<String, Set<String>> = emptyMap(),
) {

    /** Manzanas sueltas de un territorio. */
    fun manzanasDe(territorioId: String): Set<String> =
        manzanasPorTerritorio[territorioId].orEmpty()

    /** True si ese territorio ya esta marcado entero. */
    fun territorioCompleto(territorioId: String): Boolean =
        territorioId in territoriosCompletos

    /** True si no hay nada seleccionado. */
    val vacia: Boolean
        get() = territoriosCompletos.isEmpty() && manzanasPorTerritorio.values.all { it.isEmpty() }

    /**
     * Marca una sola manzana. Si su territorio estaba marcado como completo,
     * se convierte a manzana suelta: el usuario esta desmarcando el resto.
     */
    fun conManzanaIndividual(manzana: Manzana): SeleccionMapa {
        val territorio = manzana.territorioId
        val restantes = territoriosCompletos - territorio
        val actuales = manzanasPorTerritorio[territorio].orEmpty() + manzana.idManzana
        return copy(
            territoriosCompletos = restantes,
            manzanasPorTerritorio = manzanasPorTerritorio + (territorio to actuales),
        )
    }

    /** Marca un territorio completo y descarta cualquier manzana suelta de el. */
    fun conTerritorioCompleto(territorioId: String): SeleccionMapa = copy(
        territoriosCompletos = territoriosCompletos + territorioId,
        manzanasPorTerritorio = manzanasPorTerritorio - territorioId,
    )

    /** Alterna una manzana suelta: la agrega si no estaba, la quita si estaba. */
    fun alternarManzanaIndividual(manzana: Manzana): SeleccionMapa {
        val territorio = manzana.territorioId
        // Si el territorio esta completo, alternar una manzana lo desarma.
        if (territorioCompleto(territorio)) {
            val sueltas = manzanasPorTerritorio[territorio].orEmpty()
            val nuevas = if (manzana.idManzana in sueltas) sueltas - manzana.idManzana
            else sueltas + manzana.idManzana
            return copy(
                territoriosCompletos = territoriosCompletos - territorio,
                manzanasPorTerritorio = manzanasPorTerritorio + (territorio to nuevas),
            )
        }

        val sueltas = manzanasPorTerritorio[territorio].orEmpty()
        val nuevas = if (manzana.idManzana in sueltas) sueltas - manzana.idManzana
        else sueltas + manzana.idManzana

        return copy(
            manzanasPorTerritorio = if (nuevas.isEmpty()) manzanasPorTerritorio - territorio
            else manzanasPorTerritorio + (territorio to nuevas),
        )
    }

    /** Quita una manzana suelta o desmarca un territorio completo. */
    fun sinManzana(manzana: Manzana): SeleccionMapa {
        val territorio = manzana.territorioId
        if (territorioCompleto(territorio)) {
            return copy(territoriosCompletos = territoriosCompletos - territorio)
        }
        val nuevas = manzanasPorTerritorio[territorio].orEmpty() - manzana.idManzana
        return copy(
            manzanasPorTerritorio = if (nuevas.isEmpty()) manzanasPorTerritorio - territorio
            else manzanasPorTerritorio + (territorio to nuevas),
        )
    }

    /** Quita un territorio de los marcados completos. */
    fun sinTerritorioCompleto(territorioId: String): SeleccionMapa = copy(
        territoriosCompletos = territoriosCompletos - territorioId
    )

    /** Desmarca todo. */
    fun limpiar(): SeleccionMapa = SeleccionMapa()

    /**
     * True si la manzana esta marcada, ya sea de forma individual o porque su
     * territorio entero esta marcado. Sirve para pintarla en el mapa.
     */
    fun estaMarcada(manzana: Manzana): Boolean =
        territorioCompleto(manzana.territorioId) || manzana.idManzana in manzanasDe(manzana.territorioId)

    /** Todas las manzanas sueltas como pares (territorio, manzana). */
    val manzanasIndividuales: List<ManzanaMarcada>
        get() = manzanasPorTerritorio.flatMap { (territorioId, ids) ->
            ids.map { ManzanaMarcada(territorioId, it) }
        }
}

/**
 * Genera el texto del reporte a partir de la seleccion.
 *
 * [manzanasDe] entrega las manzanas presentes en el archivo para un territorio,
 * y se usa tanto para enumerar las marcadas como para calcular las faltantes.
 *
 * Formato:
 *  - Territorio completo:            "T 4"
 *  - Una manzana:                    "T 4 : 7250 (Faltan: 7251, 7270)"
 *  - Varias manzanas del territorio: "T 4 : 7250, 7251 (Faltan: 7270)"
 *
 * Si hay selecciones en varios territorios, cada uno va en su propia linea.
 * Si un territorio no tiene manzanas cargadas y se marca completo, se escribe
 * solo "T n" igual, porque no hay datos para calcular faltantes.
 */
fun seleccionATexto(
    seleccion: SeleccionMapa,
    manzanasDe: (String) -> List<Manzana>,
): String {
    if (seleccion.vacia) return ""

    val lineas = mutableListOf<String>()

    // Territorios completos: se escriben solos, ordenados naturalmente.
    seleccion.territoriosCompletos
        .sortedBy { it.naturalKeyPublica() }
        .forEach { lineas.add("T $it") }

    // Manzanas sueltas: se agrupan por territorio.
    seleccion.manzanasPorTerritorio
        .filterValues { it.isNotEmpty() }
        .toSortedMap(compareBy { it.naturalKeyPublica() })
        .forEach { (territorioId, idsMarcados) ->
            val disponibles = manzanasDe(territorioId).map { it.idManzana }
            val ordenadas = idsMarcados.sortedBy { it.naturalKeyPublica() }
            val faltantes = disponibles
                .filterNot { it in idsMarcados }
                .sortedBy { it.naturalKeyPublica() }

            val base = "T $territorioId : ${ordenadas.joinToString(", ")}"
            lineas.add(
                if (faltantes.isEmpty()) base
                else "$base (Faltan: ${faltantes.joinToString(", ")})"
            )
        }

    return lineas.joinToString("\n")
}

/** Igual que la clave natural privada del parser, expuesta para este archivo. */
private fun String.naturalKeyPublica(): String =
    if (all { it.isDigit() }) "0" + padStart(12, '0') else "1$this"
