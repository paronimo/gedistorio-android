package com.example.geditorio.domain

/**
 * Control de territorios.
 *
 * Los territorios siguen siendo manuales: la salida lleva un texto y el sistema
 * solo lo interpreta para poder controlarlo. Un territorio es cada parte entre
 * comas: "1, 70, 69, 2" son cuatro.
 *
 * Es un puerto del modulo compartido de la app web. No forma parte del PDF ni
 * impide guardar: solo sirve para advertir que territorios quedaron sin usar o
 * se repiten en el mes.
 */

/** Una salida vista solo por lo que respecta al control de territorios. */
data class TerritoryOuting(
    val id: String?,
    val territory: String?,
)

/** Un territorio del registro. */
data class Territory(
    val name: String,
    val active: Boolean = true,
)

private val WHITESPACE = Regex("\\s+")

/**
 * Divide un texto de territorio en sus partes, sin repeticiones y sin depender
 * de mayusculas: " 1 , 70 " -> ["1", "70"].
 */
fun splitTerritories(value: String?): List<String> {
    val seen = mutableSetOf<String>()
    val parts = mutableListOf<String>()
    for (raw in value.orEmpty().split(",")) {
        val part = raw.trim()
        if (part.isEmpty()) continue
        val key = territoryKey(part)
        if (!seen.add(key)) continue
        parts.add(part)
    }
    return parts
}

/** Clave de comparacion de un territorio, sin mayusculas ni espacios sobrantes. */
fun territoryKey(value: String): String = value.trim().lowercase()

/** Todos los territorios que aparecen en las salidas dadas, sin duplicados. */
fun usedTerritories(outings: List<TerritoryOuting> = emptyList()): List<String> {
    val seen = mutableSetOf<String>()
    val used = mutableListOf<String>()
    for (outing in outings) {
        for (part in splitTerritories(outing.territory)) {
            val key = territoryKey(part)
            if (!seen.add(key)) continue
            used.add(part)
        }
    }
    return used
}

/** Los territorios activos del registro que todavia no aparecen en el mes. */
fun unusedTerritories(
    territories: List<Territory> = emptyList(),
    outings: List<TerritoryOuting> = emptyList(),
): List<String> {
    val used = usedTerritories(outings).map { territoryKey(it) }.toSet()
    return territories
        .filter { it.active }
        .flatMap { splitTerritories(it.name) }
        .filter { territoryKey(it) !in used }
}

/**
 * Los territorios que la salida en edicion ya usa y que aparecen en OTRA salida
 * del mismo mes. Solo es una advertencia: nunca impide guardar.
 */
fun repeatedTerritories(
    territoryText: String?,
    outings: List<TerritoryOuting> = emptyList(),
    currentOutingId: String? = null,
): List<String> {
    val seen = mutableSetOf<String>()
    for (outing in outings) {
        if (outing.id == currentOutingId) continue
        for (part in splitTerritories(outing.territory)) seen.add(territoryKey(part))
    }
    // Se devuelven tal como los escribio el usuario, no normalizados.
    val own = mutableSetOf<String>()
    return splitTerritories(territoryText).filter { part ->
        val key = territoryKey(part)
        if (key !in seen || own.contains(key)) return@filter false
        own.add(key)
        true
    }
}

/** Utilidad interna: normaliza espacios repetidos. */
internal fun normalizeSpaces(value: String): String = WHITESPACE.replace(value.trim(), " ")
