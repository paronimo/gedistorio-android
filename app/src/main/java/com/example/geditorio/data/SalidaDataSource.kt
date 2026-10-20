package com.example.geditorio.data

import com.example.geditorio.data.model.Salida

/**
 * Contrato minimo que debe cumplir cualquier fuente de datos de salidas.
 * En la app lo cumple [ServidorSalidaDataSource] (cronograma real del gestor).
 *
 * Solo expone la LISTA de salidas. Ni la proxima salida ni el orden los
 * decide la fuente: eso es responsabilidad del repositorio, para que
 * todas las pantallas los obtengan igual.
 */
interface SalidaDataSource {
    /** Lance [SalidasException] si no se pueden obtener las salidas reales. */
    suspend fun obtenerSalidas(): List<Salida>
}
