package com.example.geditorio.data.mock

import com.example.geditorio.data.SalidaDataSource
import com.example.geditorio.data.model.EstadoSalida
import com.example.geditorio.data.model.Salida
import com.example.geditorio.data.model.TipoSalida

/**
 * FUENTE DE DATOS DE EJEMPLO.
 *
 * IMPORTANTE: esta clase es temporal. Solo alimenta la interfaz visual.
 * Cuando exista la fuente real (API / backend), se implementara
 * SalidaDataSource con esa fuente y esta dejara de usarse.
 *
 * La UI nunca lee estos valores directamente: siempre pasa por SalidaRepository.
 *
 * Las fechas van en ISO (yyyy-MM-dd) para que el orden cronologico sea
 * correcto; la pantalla las convierte con Salida.fechaTexto.
 */
object SalidaEjemploDataSource : SalidaDataSource {

    // A proposito NO estan ordenadas: el orden lo resuelve el repositorio.
    private val ejemplo = listOf(
        Salida(
            id = "ejemplo-003",
            fecha = "2026-10-03",
            hora = "09:00",
            tipo = TipoSalida.Grupo(2),
            lugarDeEncuentro = "Ejemplo: Plaza San Martin",
            enlaceGoogleMaps = "https://maps.google.com/?q=ejemplo-san-martin",
            conductor = "Ejemplo: Carla Mendez",
            territorios = listOf(45, 12),
            estado = EstadoSalida.CONFIRMADA
        ),
        Salida(
            id = "ejemplo-001",
            fecha = "2026-09-12",
            hora = "07:30",
            tipo = TipoSalida.Grupo(1),
            lugarDeEncuentro = "Ejemplo: Plaza Central",
            enlaceGoogleMaps = null,
            conductor = "Ejemplo: Carla Mendez",
            territorios = listOf(1, 70, 69, 2),
            estado = EstadoSalida.REALIZADA
        ),
        Salida(
            id = "ejemplo-004",
            fecha = "2026-09-26",
            hora = "16:00",
            tipo = TipoSalida.Congregacional,
            lugarDeEncuentro = "Ejemplo: esquina de las calles Ejemplo 1 y Ejemplo 2",
            enlaceGoogleMaps = null,
            conductor = "Ejemplo: Carla Mendez",
            territorios = listOf(80),
            estado = EstadoSalida.PENDIENTE
        ),
        Salida(
            id = "ejemplo-002",
            fecha = "2026-09-12",
            hora = "14:00",
            tipo = TipoSalida.Grupo(3),
            lugarDeEncuentro = "Ejemplo: casa de la familia Ejemplo",
            enlaceGoogleMaps = "https://maps.google.com/?q=ejemplo-casa",
            conductor = "Ejemplo: Carla Mendez",
            territorios = listOf(12),
            estado = EstadoSalida.CANCELADA
        ),
        Salida(
            id = "ejemplo-005",
            fecha = "2026-10-17",
            hora = "08:00",
            tipo = TipoSalida.Grupo(4),
            lugarDeEncuentro = "Ejemplo: casa de la familia Ejemplo 2",
            enlaceGoogleMaps = null,
            conductor = "Ejemplo: Carla Mendez",
            territorios = listOf(33, 34, 35),
            estado = EstadoSalida.PENDIENTE
        )
    )


    override fun obtenerSalidas(): List<Salida> = ejemplo
}
