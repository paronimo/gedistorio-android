package com.example.geditorio.data.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Cola de reportes pendientes guardada en el almacenamiento interno privado de
 * la app (un JSON por archivo). Sin permiso de almacenamiento externo y sin
 * base de datos: sobrevive a cerrar la app y a reiniciar el celular.
 *
 * Escritura atómica: se escribe en un archivo temporal y se renombra, así un
 * corte de energía no deja un archivo corrupto.
 */
class ColaReportesDisco(context: Context) : ColaReportes {

    private val archivo = File(context.filesDir, "reportes_pendientes.json")
    private val cerrojo = Any()

    override fun listar(): List<ReportePendiente> = synchronized(cerrojo) { leer() }

    override fun guardar(reporte: ReportePendiente) = synchronized(cerrojo) {
        val actuales = leer().filterNot { it.clave == reporte.clave } + reporte
        escribir(actuales)
    }

    override fun quitar(clave: String) = synchronized(cerrojo) {
        escribir(leer().filterNot { it.clave == clave })
    }

    override fun marcarFallo(clave: String, error: String?): ReportePendiente? = synchronized(cerrojo) {
        val actuales = leer()
        val index = actuales.indexOfFirst { it.clave == clave }
        if (index < 0) return null
        val actualizado = actuales[index].copy(
            intentos = actuales[index].intentos + 1,
            ultimoError = error?.take(300)
        )
        val nuevos = actuales.toMutableList().also { it[index] = actualizado }
        escribir(nuevos)
        actualizado
    }

    override fun existe(clave: String): Boolean = synchronized(cerrojo) {
        leer().any { it.clave == clave }
    }

    override fun tamano(): Int = synchronized(cerrojo) { leer().size }

    private fun leer(): List<ReportePendiente> {
        if (!archivo.exists()) return emptyList()
        return try {
            val arreglo = JSONArray(archivo.readText(Charsets.UTF_8))
            (0 until arreglo.length()).mapNotNull { index ->
                val objeto = arreglo.optJSONObject(index) ?: return@mapNotNull null
                objeto.toReporte()
            }
        } catch (e: Exception) {
            // Un archivo ilegible no debe romper la app; los pendientes se
            // pierden solo si el archivo estaba corrupto, no por falta de red.
            emptyList()
        }
    }

    private fun escribir(reportes: List<ReportePendiente>) {
        val arreglo = JSONArray()
        reportes.forEach { arreglo.put(it.toJson()) }
        val temporal = File(archivo.parentFile, "${archivo.name}.tmp")
        try {
            temporal.writeText(arreglo.toString(), Charsets.UTF_8)
            if (!temporal.renameTo(archivo)) {
                archivo.writeText(arreglo.toString(), Charsets.UTF_8)
                temporal.delete()
            }
        } catch (e: IOException) {
            // Sin espacio: el reporte sigue en memoria de la pantalla y se
            // informa el error; no se marca como enviado.
            throw e
        }
    }

    private fun ReportePendiente.toJson() = JSONObject()
        .put("clave", clave)
        .put("outingId", outingId)
        .put("salidaFecha", salidaFecha)
        .put("realizada", realizada)
        .put("observaciones", observaciones)
        .put("territorioTexto", territorioTexto)
        .put("uid", uid)
        .put("email", email)
        .put("intentos", intentos)
        .put("creadoEn", creadoEn)
        .put("ultimoError", ultimoError ?: JSONObject.NULL)

    private fun JSONObject.toReporte() = ReportePendiente(
        clave = getString("clave"),
        outingId = getString("outingId"),
        salidaFecha = optString("salidaFecha", ""),
        realizada = optBoolean("realizada", false),
        observaciones = optString("observaciones", ""),
        territorioTexto = optString("territorioTexto", ""),
        uid = getString("uid"),
        email = getString("email"),
        intentos = optInt("intentos", 0),
        creadoEn = optLong("creadoEn", System.currentTimeMillis()),
        ultimoError = if (isNull("ultimoError")) null else optString("ultimoError")
    )
}
