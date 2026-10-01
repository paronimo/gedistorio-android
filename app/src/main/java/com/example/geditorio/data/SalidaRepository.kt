package com.example.geditorio.data

import com.example.geditorio.data.mock.SalidaEjemploDataSource
import com.example.geditorio.data.model.Salida

/**
 * Punto unico de acceso a los datos de salidas.
 *
 * Hoy devuelve DATOS DE EJEMPLO. Cuando exista la fuente real, solo se
 * cambia el dataSource por la implementacion correspondiente y ni las
 * pantallas ni el resto del codigo necesitan modificarse.
 */
class SalidaRepository(
    private val dataSource: SalidaDataSource = SalidaEjemploDataSource
) {
    fun proximaSalida(): Salida = dataSource.obtenerProximaSalida()
}
