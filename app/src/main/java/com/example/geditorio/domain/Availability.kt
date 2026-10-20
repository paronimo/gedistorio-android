package com.example.geditorio.domain

/**
 * Reglas de disponibilidad (turnos, franjas horarias y horarios de la grilla).
 *
 * Puerto directo del modulo compartido de la app web, para que el conductor
 * vea exactamente los mismos turnos y rangos que administra la oficina.
 *
 * Rango de la manana: 06:00 a 12:00 inclusive. Rango de la tarde: 13:00 a 21:00
 * inclusive. Fuera de ambos rangos, no se asume turno.
 */

/** Hora en que empieza la manana. */
const val MORNING_STARTS_AT_HOUR = 6

/** Hora en que termina la manana (inclusive). */
const val MORNING_ENDS_AT_HOUR = 12

/** Hora en que empieza la tarde. */
const val AFTERNOON_STARTS_AT_HOUR = 13

/** Hora en que termina la tarde (inclusive). */
const val AFTERNOON_ENDS_AT_HOUR = 21

/** Los dos turnos posibles del dia. */
enum class Turn(val key: String, val label: String) {
    MORNING("morning", "mañana"),
    AFTERNOON("afternoon", "tarde");

    companion object {
        /** Turno a partir de su clave textual ("morning" / "afternoon"). */
        fun fromKey(key: String?): Turn? = entries.firstOrNull { it.key == key }
    }
}

/** Un dia de la semana configurable en la grilla. */
data class Weekday(val weekday: Int, val label: String, val short: String)

/** Turnos que se ofrecen en la grilla, en el orden en que se muestran. */
val CONFIGURABLE_WEEKDAYS: List<Weekday> = listOf(
    Weekday(1, "Lunes", "Lun"),
    Weekday(2, "Martes", "Mar"),
    Weekday(3, "Miércoles", "Mié"),
    Weekday(4, "Jueves", "Jue"),
    Weekday(5, "Viernes", "Vie"),
    Weekday(6, "Sábado", "Sáb"),
    Weekday(0, "Domingo", "Dom"),
)

/** Nombres de los dias de la semana, de lunes a domingo. */
val WEEKDAYS: List<String> = CONFIGURABLE_WEEKDAYS.map { it.label }

private val TIME_PATTERN = Regex("^(\\d{1,2}):(\\d{2})$")

/**
 * Minutos desde medianoche, para comparar contra los limites con exactitud:
 * a las 12:00 ya termino la manana, pero a las 12:30 sigue sin ser tarde.
 * Devuelve null si la hora no tiene el formato esperado o esta fuera de rango.
 */
private fun minutesOfDay(time: String?): Int? {
    val match = TIME_PATTERN.find(time?.trim().orEmpty()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    if (hour > 23 || minute > 59) return null
    return hour * 60 + minute
}

/**
 * Funcion centralizada: la hora de la salida decide el turno.
 *
 * Devuelve null cuando la hora esta fuera de ambos rangos (por ejemplo 12:30 o
 * las 22:00): no se asume turno y la interfaz debe avisar en vez de elegir por
 * el usuario.
 */
fun turnOfTime(time: String?): Turn? {
    val minutes = minutesOfDay(time) ?: return null
    if (minutes >= MORNING_STARTS_AT_HOUR * 60 && minutes <= MORNING_ENDS_AT_HOUR * 60) return Turn.MORNING
    if (minutes >= AFTERNOON_STARTS_AT_HOUR * 60 && minutes <= AFTERNOON_ENDS_AT_HOUR * 60) return Turn.AFTERNOON
    return null
}

/** Explicacion lista para mostrar cuando una hora no cae en ningun turno. */
fun turnRangeNote(): String = "06:00 a 12:00 · 13:00 a 21:00"

/** Un horario valido y con turno asignado. */
fun isTimeInTurnRange(time: String?): Boolean = turnOfTime(time) != null

/** Clave de un horario de la grilla: "weekday:turn". */
fun slotKey(weekday: Int, turn: Turn): String = "$weekday:${turn.key}"

/** Valida una clave de horario producida por [slotKey]. */
fun isValidSlotKey(key: String?): Boolean {
    val parts = key.orEmpty().split(":")
    if (parts.size != 2) return false
    val turn = Turn.fromKey(parts[1]) ?: return false
    return CONFIGURABLE_WEEKDAYS.any { it.weekday == parts[0].toIntOrNull() } && turn != null
}
