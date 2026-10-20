package com.example.geditorio.data.cloud

/** Un reporte esperando guardarse en la nube. */
data class ReportePendiente(
    /** Identificador del reporte: el mismo entre reintentos para no duplicar. */
    val clave: String,
    val outingId: String,
    val salidaFecha: String,
    val realizada: Boolean,
    val observaciones: String,
    val territorioTexto: String,
    val uid: String,
    val email: String,
    val intentos: Int = 0,
    val creadoEn: Long = System.currentTimeMillis(),
    val ultimoError: String? = null
)

/**
 * Cola persistente de reportes pendientes.
 *
 * La implementación de la app guarda en almacenamiento interno; las pruebas
 * usan una en memoria. La interfaz permite verificar la lógica (retención sin
 * Internet, reintentos con la misma clave) sin Android.
 */
interface ColaReportes {
    fun listar(): List<ReportePendiente>
    fun guardar(reporte: ReportePendiente)
    fun quitar(clave: String)
    fun marcarFallo(clave: String, error: String?): ReportePendiente?
    fun existe(clave: String): Boolean
    fun tamano(): Int
}

class ColaEnMemoria : ColaReportes {
    private val pendientes = LinkedHashMap<String, ReportePendiente>()

    override fun listar(): List<ReportePendiente> = pendientes.values.toList()

    override fun guardar(reporte: ReportePendiente) {
        pendientes[reporte.clave] = reporte
    }

    override fun quitar(clave: String) {
        pendientes.remove(clave)
    }

    override fun marcarFallo(clave: String, error: String?): ReportePendiente? {
        val actual = pendientes[clave] ?: return null
        val actualizado = actual.copy(intentos = actual.intentos + 1, ultimoError = error)
        pendientes[clave] = actualizado
        return actualizado
    }

    override fun existe(clave: String): Boolean = pendientes.containsKey(clave)

    override fun tamano(): Int = pendientes.size
}
