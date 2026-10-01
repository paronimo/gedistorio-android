package com.example.geditorio.data.model

/**
 * MODELO DE UNA SALIDA DEL CRONOGRAMA.
 *
 * Este archivo es la UNICA definicion del modelo: no se duplica en ningun
 * otro lugar. Solo describe QUE datos tiene una salida, no de donde vienen
 * (esa responsabilidad es de SalidaDataSource / SalidaRepository).
 *
 * Decisiones de diseno:
 * - "tipo" es un sealed class, no un String suelto, para que "Grupo 1" no se
 *   confunda con el texto "grupo1" ni con el numero 1. Al ser
 *   TipoSalida.Grupo parametrizado por numero, se pueden agregar Grupo 5,
 *   Grupo 6... SIN modificar este archivo.
 * - "territorios" es una lista de numeros, no un texto. Acepta uno o varios
 *   ("1, 70, 69, 2") y se formatea solo al mostrarlo.
 * - "lugarDeEncuentro" sigue siendo un UNICO concepto (casa, plaza, esquina,
 *   u otra ubicacion). No se parte en direccion/ciudad porque no se pidio.
 * - "enlaceGoogleMaps" es opcional: null cuando no hay enlace.
 * - "fecha" y "hora" se guardan en formato ORDENABLE: fecha en ISO
 *   (yyyy-MM-dd) y hora en 24h (HH:mm). Asi el orden cronologico se obtiene
 *   comparando strings, sin conversores ni logica repetida. Para mostrar se
 *   usan [fechaTexto] y [horaTexto], que hacen la conversion.
 * - "claveFecha" y "claveHora" exponen esos mismos valores para ordenar.
 */
data class Salida(
    val id: String,
    val fecha: String,
    val hora: String,
    val tipo: TipoSalida,
    val lugarDeEncuentro: String,
    val enlaceGoogleMaps: String? = null,
    val conductor: String,
    val territorios: List<Int> = emptyList(),
    val estado: EstadoSalida = EstadoSalida.PENDIENTE
) {
    /** Texto listo para mostrar con los territorios separados por coma. */
    val territoriosTexto: String
        get() = territorios.joinToString(", ")

    /** Claves de ordenacion cronologica (formato ISO). */
    val claveFecha: String get() = fecha
    val claveHora: String get() = hora

    /** Fecha en texto legible, por ejemplo "Viernes 12 de septiembre de 2026". */
    val fechaTexto: String
        get() = FormatoFecha.aTextoLargo(fecha)

    /** Hora en texto legible, por ejemplo "07:30". */
    val horaTexto: String
        get() = hora
}

/**
 * Tipo de salida. Preparado para crecer: los grupos se representan con
 * Grupo, asi que un "Grupo 5" ya es valido sin tocar este archivo.
 */
sealed class TipoSalida(val etiqueta: String) {

    /** Salida de ambito general. */
    data object Congregacional : TipoSalida("Congregacional")

    /** Salida de un grupo concreto. El numero permite nuevos grupos. */
    data class Grupo(val numero: Int) : TipoSalida("Grupo $numero")
}

/**
 * Estado de la salida. Es una enumeracion simple; se ajusta a medida que
 * se definan las reglas reales del cronograma.
 */
enum class EstadoSalida(val etiqueta: String) {
    PENDIENTE("Pendiente"),
    CONFIRMADA("Confirmada"),
    REALIZADA("Realizada"),
    CANCELADA("Cancelada")
}
