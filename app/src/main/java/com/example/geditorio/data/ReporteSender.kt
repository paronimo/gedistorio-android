package com.example.geditorio.data

import com.example.geditorio.data.model.ReporteSalida
import com.example.geditorio.data.reporte.EnvioReporte

/**
 * Contrato de envio de reportes.
 *
 * EXISTE PARA SEPARAR LA PANTALLA DEL LUGAR DONDE SE GUARDA EL REPORTE.
 * La pantalla no sabe a que servidor va el reporte: solo entrega el modelo
 * a este contrato.
 *
 * La implementacion real es ReporteHttp, que envia el reporte al backend web
 * con la sesion de Firebase. Un reporte solo cuenta como enviado cuando el
 * backend confirma que lo guardo (EnvioReporte.guardado = true).
 *
 * [claveIdempotencia] identifica el intento concreto: si se reintenta el mismo
 * reporte, el backend devuelve el ya guardado en lugar de crear otro.
 */
interface ReporteSender {
    suspend fun enviar(reporte: ReporteSalida, claveIdempotencia: String): EnvioReporte
}
