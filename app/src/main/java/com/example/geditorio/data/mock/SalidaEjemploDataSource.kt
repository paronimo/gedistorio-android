package com.example.geditorio.data.mock

import com.example.geditorio.data.SalidaDataSource
import com.example.geditorio.data.model.Salida

/**
 * FUENTE DE DATOS DE EJEMPLO.
 *
 * IMPORTANTE: esta clase es temporal. Solo alimenta la interfaz visual
 * inicial. Cuando exista la fuente real (API / backend), se implementara
 * SalidaDataSource con esa fuente y esta dejara de usarse.
 *
 * La UI nunca lee estos valores directamente: siempre pasa por SalidaRepository.
 */
object SalidaEjemploDataSource : SalidaDataSource {

    private val ejemplo = listOf(
        Salida(
            id = "ejemplo-001",
            fecha = "Viernes 12 de septiembre de 2026",
            hora = "07:30",
            tipo = "Ronda matutina",
            lugarDeEncuentro = "Plaza Central",
            conductor = "Ejemplo: Carla Mendez",
            territorio = "Ejemplo: Centro"
        ),
        Salida(
            id = "ejemplo-002",
            fecha = "Martes 15 de septiembre de 2026",
            hora = "14:00",
            tipo = "Visita programada",
            lugarDeEncuentro = "Delegacion Norte",
            conductor = "Ejemplo: Diego Rojas",
            territorio = "Ejemplo: Norte"
        )
    )

    override fun obtenerProximaSalida(): Salida = ejemplo.first()
}
