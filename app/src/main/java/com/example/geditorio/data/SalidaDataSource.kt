package com.example.geditorio.data

import com.example.geditorio.data.model.Salida

/**
 * Contrato minimo que debe cumplir cualquier fuente de datos de salidas.
 * Hoy solo lo cumple el ejemplo; luego se implementara con la fuente real.
 */
interface SalidaDataSource {
    fun obtenerProximaSalida(): Salida
}
