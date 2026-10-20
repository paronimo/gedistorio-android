package com.example.geditorio.data.mock

import com.example.geditorio.data.ReporteSender
import com.example.geditorio.data.model.ReporteSalida
import com.example.geditorio.data.reporte.EnvioReporte
import java.util.concurrent.atomic.AtomicInteger

/**
 * ENVIO DE PRUEBA. NO PERSISTE NADA: SIMULA UNA CONFIRMACION.
 *
 * Solo deja registro en memoria durante la sesion, para poder verificar
 * que la pantalla arma bien el modelo y cuenta bien los envios. No escribe
 * en disco, no usa base de datos y no manda nada a ningun servidor.
 *
 * La app ya envia al backend real con ReporteHttp; esta clase solo se usa
 * en pruebas del cliente.
 */
object ReportePruebaSender : ReporteSender {

    /** Ultimo reporte preparado, solo para inspeccionarlo en la sesion. */
    var ultimoReporte: ReporteSalida? = null
        private set

    /** Cantidad de envios efectivamente realizados (incluye repetidos). */
    val envios = AtomicInteger(0)

    override suspend fun enviar(reporte: ReporteSalida, claveIdempotencia: String): EnvioReporte {
        ultimoReporte = reporte
        envios.incrementAndGet()
        // No hay servidor: confirma en memoria solo para probar la pantalla.
        return EnvioReporte.guardado(reporte.salidaId, "simulado", duplicado = false)
    }
}
