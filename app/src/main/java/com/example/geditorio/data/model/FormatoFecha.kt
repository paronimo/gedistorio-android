package com.example.geditorio.data.model

import java.util.Calendar

/**
 * Conversion de fecha entre el formato que se guarda (ISO, ordenable) y el
 * texto que se muestra.
 *
 * Se separa del modelo para que [Salida] no dependa de java.text ni de
 * funciones de formato. Ademas, cuando exista la fuente real con su propio
 * formato, se cambia esta clase y el modelo sigue intacto.
 */
object FormatoFecha {

    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    private val DIAS = listOf(
        "Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado", "Domingo"
    )

    /**
     * "2026-09-12" -> "Viernes 12 de septiembre de 2026".
     * Si el valor no esta en ISO se devuelve tal cual, para no romper la
     * pantalla mientras los datos de ejemplo van migrando.
     */
    fun aTextoLargo(fechaIso: String): String {
        val partes = fechaIso.split("-")
        if (partes.size != 3) return fechaIso
        val anio = partes[0].toIntOrNull() ?: return fechaIso
        val mes = partes[1].toIntOrNull() ?: return fechaIso
        val dia = partes[2].toIntOrNull() ?: return fechaIso
        if (mes !in 1..12) return fechaIso

        val diaSemana = diaSemanaDe(anio, mes, dia)
        return "${DIAS[diaSemana]} $dia de ${MESES[mes - 1]} de $anio"
    }

    /**
     * Dia de la semana (0 = lunes) de una fecha, via Calendar para no
     * depender de una API nueva que no este disponible en minSdk 24.
     */
    private fun diaSemanaDe(anio: Int, mes: Int, dia: Int): Int {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(anio, mes - 1, dia)
        // Calendar: domingo = 1. Se rota para que lunes = 0.
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
}
