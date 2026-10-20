package com.example.geditorio.data.model

/**
 * Modelo de un reporte de salida.
 *
 * Se envia al backend web (POST /api/reports) a traves de ReporteHttp,
 * autenticado con la sesion de Firebase. Por eso el campo "realizada" es un
 * Boolean y no un texto: el estado real de la salida se deduce de aca, sin
 * depender de como este guardado en el cronograma. [territorioTexto] es el
 * texto que genera el mapa de manzanas (por ejemplo "T 4 : 7250 (Faltan: 7251)").
 */
data class ReporteSalida(
    val salidaId: String,
    val realizada: Boolean,
    val observaciones: String,
    val territorioTexto: String = ""
)
