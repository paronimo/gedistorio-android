package com.example.geditorio.data.mock

import com.example.geditorio.data.ReporteSender
import com.example.geditorio.data.model.ReporteSalida

/**
 * ENVIO DE PRUEBA. NO PERSISTE NADA.
 *
 * Solo deja registro en memoria durante la sesion, para poder verificar
 * que el formulario arma bien el modelo. No escribe en disco, no usa base
 * de datos y no manda nada a ningun servidor.
 *
 * Es temporal: cuando exista Firebase se implementa ReporteSender con la
 * fuente real y esta clase deja de usarse.
 */
object ReportePruebaSender : ReporteSender {

    /** Ultimo reporte "enviado", solo para inspeccionarlo en la sesion. */
    var ultimoReporte: ReporteSalida? = null
        private set

    override fun enviar(reporte: ReporteSalida) {
        ultimoReporte = reporte
    }
}
