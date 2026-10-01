package com.example.geditorio.data

import com.example.geditorio.data.model.Salida

/**
 * Contrato minimo que debe cumplir cualquier fuente de datos de salidas.
 * Hoy solo lo cumple el ejemplo; luego se implementara con la fuente real.
 *
 * Solo expone la LISTA de salidas. Ni la proxima salida ni el orden los
 * decide la fuente: eso es responsabilidad del repositorio, para que
 * todas las pantallas los obtengan igual.
 */
interface SalidaDataSource {
    fun obtenerSalidas(): List<Salida>
}
