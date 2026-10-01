package com.example.geditorio.data.model

/**
 * Modelo de una salida. Define SOLO la forma del dato,
 * sin saber de donde viene (mock, API o base de datos).
 */
data class Salida(
    val id: String,
    val fecha: String,
    val hora: String,
    val tipo: String,
    val lugarDeEncuentro: String,
    val conductor: String,
    val territorio: String
)
