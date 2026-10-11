package com.example.geditorio.data

import com.example.geditorio.data.model.ReporteSalida

/**
 * Contrato de envio de reportes.
 *
 * EXISTE PARA SEPARAR LA PANTALLA DEL LUGAR DONDE SE GUARDA EL REPORTE.
 * La pantalla no sabe (ni debe saber) si el reporte va a Firebase, a una
 * API o se queda en memoria: solo entrega el modelo a este contrato.
 *
 * Hoy lo cumple ReportePruebaSender, que no guarda nada. Cuando exista
 * Firebase se implementa esta misma interfaz y se cambia una sola linea
 * en SalidaRepository, sin tocar ReportarFragment ni el formulario.
 */
interface ReporteSender {
    fun enviar(reporte: ReporteSalida)
}
