package com.example.geditorio.data

import com.example.geditorio.data.mock.SalidaEjemploDataSource
import com.example.geditorio.data.model.EstadoSalida
import com.example.geditorio.data.model.Salida

/**
 * Punto unico de acceso a los datos de salidas.
 *
 * Hoy devuelve DATOS DE EJEMPLO. Cuando exista la fuente real, solo se
 * cambia el dataSource por la implementacion correspondiente y ni las
 * pantallas ni el resto del codigo necesitan modificarse.
 */
class SalidaRepository(
    private val dataSource: SalidaDataSource = SalidaEjemploDataSource
) {
    /**
     * Proxima salida pendiente o confirmada, la mas cercana en el tiempo.
     * Las ya realizadas o canceladas no se consideran "proxima salida".
     */
    fun proximaSalida(): Salida = misSalidas()
        .firstOrNull { it.estado == EstadoSalida.PENDIENTE || it.estado == EstadoSalida.CONFIRMADA }
        ?: misSalidas().first()

    /** Salidas asignadas al conductor, ordenadas por fecha y hora. */
    fun misSalidas(): List<Salida> = dataSource.obtenerSalidas().sortedWith(
        compareBy({ it.claveFecha }, { it.claveHora })
    )

    fun salidaPorId(id: String): Salida? = dataSource.obtenerSalidas().firstOrNull { it.id == id }
}
