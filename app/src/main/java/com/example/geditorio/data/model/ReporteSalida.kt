package com.example.geditorio.data.model

/**
 * Modelo de un reporte de salida.
 *
 * Se define junto al resto de los modelos, en data/model, y es la misma
 * estructura que se enviara a Firebase cuando exista. Por eso el campo
 * "realizada" es un Boolean y no un texto: el estado real de la salida
 * se deduce de aca, sin depender de como este guardado en el cronograma.
 */
data class ReporteSalida(
    val salidaId: String,
    val realizada: Boolean,
    val observaciones: String
)
