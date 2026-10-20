package com.example.geditorio.domain

/**
 * Control mensual de casas y familias, similar al control de territorios.
 *
 * No asigna ni modifica nada: solo detecta que casas aparecen en las salidas del
 * mes. Al igual que con los territorios, no forma parte del PDF.
 *
 * Puerto del modulo compartido de la app web.
 */

/** Una casa del registro. */
data class House(
    val id: String,
    val name: String,
    val group: String? = null,
    val askAvailability: Boolean = false,
    val availabilityConfirmed: Map<String, Boolean> = emptyMap(),
)

/** Una salida, vista por lo que respecta al control de casas. */
data class HouseOuting(
    val id: String?,
    val type: String?,
    val placeId: String?,
    val date: String?,
    val time: String?,
)

private val GROUP_PREFIX = Regex("^grupo\\s*", RegexOption.IGNORE_CASE)
private val OUTING_GROUP = Regex("^grupo\\s*(\\d+)$", RegexOption.IGNORE_CASE)

/** El grupo de una casa se normaliza a su numero: "Grupo 1" -> "1". */
fun houseGroup(house: House?): String =
    GROUP_PREFIX.replace((house?.group ?: "").trim(), "").lowercase()

/**
 * El grupo de la salida: "Grupo 2" -> "2", "Congregacional" -> "".
 * Define para que grupos la casa era elegible.
 */
fun outingGroup(outing: HouseOuting?): String {
    val match = OUTING_GROUP.find((outing?.type ?: "").trim())
    return match?.groupValues?.get(1).orEmpty()
}

/**
 * Una casa solo es utilizable por los grupos que le corresponden: la suya, o
 * cualquiera si es una casa general (sin grupo). Asi una casa de Grupo 2 nunca
 * se ofrece como disponible para Grupo 1, 3 o 4.
 */
fun houseServesGroup(house: House, group: String?): Boolean {
    val own = houseGroup(house)
    if (own.isEmpty()) return true
    return own == group.orEmpty().trim().lowercase()
}

/**
 * Una casa utilizada en una salida de Grupo 2 es de Grupo 2: no se marca como
 * disponible para los demas grupos.
 */
fun usedHouseGroups(house: House, outings: List<HouseOuting> = emptyList()): List<String> {
    val used = outings.filter { it.placeId == house.id }
    val groups = used.map { outingGroup(it) }.filter { it.isNotEmpty() }.toSet()
    // Una casa general usada en una congregacional no pertenece a ningun grupo.
    if (groups.isEmpty()) return listOf("")
    return groups.toList()
}

/** Una casa que aparece en al menos una salida del mes. */
data class UsedHouse(val house: House, val group: String, val outing: HouseOuting)

/** Las casas que aparecen en al menos una salida del mes, con la salida que las uso. */
fun usedHouses(houses: List<House> = emptyList(), outings: List<HouseOuting> = emptyList()): List<UsedHouse> {
    val used = mutableListOf<UsedHouse>()
    for (house in houses) {
        val outing = outings.firstOrNull { it.placeId == house.id } ?: continue
        used.add(
            UsedHouse(
                house = house,
                // El grupo real de la casa manda: si no tiene grupo, la casa es
                // general y no se convierte en el grupo de la salida.
                group = houseGroup(house),
                outing = outing,
            )
        )
    }
    return used
}

/** Una casa sin salidas asociadas, junto a su grupo normalizado. */
data class UnusedHouse(val house: House, val group: String)

/** Las casas que no aparecen en ninguna salida, agrupadas por grupo. */
fun unusedHouses(houses: List<House> = emptyList(), outings: List<HouseOuting> = emptyList()): List<UnusedHouse> =
    houses
        .filter { house -> outings.none { it.placeId == house.id } }
        .map { UnusedHouse(it, houseGroup(it)) }

/**
 * Las casas de un grupo concreto que todavia no se usaron en ese grupo.
 * "General" agrupa las casas sin grupo.
 */
fun unusedHousesOfGroup(
    houses: List<House> = emptyList(),
    outings: List<HouseOuting> = emptyList(),
    group: String?,
): List<UnusedHouse> = unusedHouses(houses, outings).filter { it.group == group.orEmpty().lowercase() }
