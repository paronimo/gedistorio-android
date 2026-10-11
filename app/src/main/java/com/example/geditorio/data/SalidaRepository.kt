package com.example.geditorio.data

import com.example.geditorio.data.mock.ReportePruebaSender
import com.example.geditorio.data.mock.SalidaEjemploDataSource
import com.example.geditorio.data.model.EstadoSalida
import com.example.geditorio.data.model.ReporteSalida
import com.example.geditorio.data.model.Salida

/**
 * Punto unico de acceso a los datos de salidas y al envio de reportes.
 *
 * Hoy devuelve DATOS DE EJEMPLO y los reportes NO se guardan en ningun lado.
 * Cuando exista la fuente real, solo se cambian los dos parametros por sus
 * implementaciones correspondientes y ni las pantallas ni el resto del
 * codigo necesitan modificarse.
 */
class SalidaRepository(
    private val dataSource: SalidaDataSource = SalidaEjemploDataSource,
    private val reporteSender: ReporteSender = ReportePruebaSender
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

    /**
     * Envia un reporte de salida.
     *
     * La pantalla no sabe donde termina el reporte: solo se lo entrega al
     * repositorio. Hoy no se persiste nada.
     */
    fun enviarReporte(reporte: ReporteSalida) = reporteSender.enviar(reporte)
}
