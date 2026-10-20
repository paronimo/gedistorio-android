package com.example.geditorio.data.cloud

import com.example.geditorio.data.ReporteError

/** Resultado de un envío desde la pantalla de reportes. */
sealed class ResultadoEnvio {
    /** Confirmado por Firestore: el reporte está en la nube. */
    data class Guardado(val yaExistia: Boolean) : ResultadoEnvio()

    /** No hay conexión: queda guardado en el celular y se reintenta solo. */
    data object Pendiente : ResultadoEnvio()

    /** Error definitivo que no se debe reintentar en segundo plano. */
    data class Error(val motivo: ReporteError, val detalle: String? = null) : ResultadoEnvio()
}

/**
 * Sincronizador de reportes: primero intenta la nube; si falla por conexión,
 * guarda el reporte en la cola persistente y lo reintenta al volver la red.
 *
 * El usuario solo ve "enviado" cuando Firestore confirmó la escritura. Si queda
 * pendiente, se le informa que se enviará cuando vuelva la conexión.
 *
 * Reintentos: la clave del reporte (salida + uid) no cambia, y Firestore usa el
 * mismo id de documento, así que reintentar nunca crea duplicados.
 */
class SincronizadorReportes(
    private val cliente: ClienteReportesNube,
    private val cola: ColaReportes,
    private val ahora: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun enviar(pendiente: ReportePendiente): ResultadoEnvio {
        // Se guarda antes de enviar: si el proceso muere a mitad de camino, el
        // reporte no se pierde y el próximo intento usa la misma clave.
        cola.guardar(pendiente)
        return intentar(pendiente)
    }

    private suspend fun intentar(pendiente: ReportePendiente): ResultadoEnvio {
        return try {
            val resultado = cliente.guardar(pendiente)
            cola.quitar(pendiente.clave)
            ResultadoEnvio.Guardado(yaExistia = resultado.yaExistia)
        } catch (e: CloudReportException) {
            when (e.error) {
                ReporteError.SIN_CONEXION -> {
                    cola.marcarFallo(pendiente.clave, e.message)
                    ResultadoEnvio.Pendiente
                }
                ReporteError.SALIDA_NO_ENCONTRADA,
                ReporteError.SIN_AUTORIZACION,
                ReporteError.SIN_SESION,
                ReporteError.SIN_CONFIGURAR -> {
                    // No se reintenta solo: depende del usuario o del catálogo.
                    // Se descarta de la cola para no reintentarlo en bucle.
                    cola.quitar(pendiente.clave)
                    ResultadoEnvio.Error(e.error, e.message)
                }
                else -> {
                    // Servidor caído o error inesperado: se conserva y reintenta.
                    cola.marcarFallo(pendiente.clave, e.message)
                    ResultadoEnvio.Pendiente
                }
            }
        } catch (e: Exception) {
            cola.marcarFallo(pendiente.clave, e.message)
            ResultadoEnvio.Pendiente
        }
    }

    /**
     * Reintenta todos los pendientes (al recuperar la conexión y al abrir la
     * app). Devuelve cuántos quedaron guardados y cuántos siguen pendientes.
     * Recorre una copia de la cola: quitar elementos mientras se itera es seguro.
     */
    suspend fun reintentarPendientes(): ResumenReintento {
        var guardados = 0
        var pendientes = 0
        var fallidos = 0
        for (reporte in cola.listar()) {
            when (intentar(reporte)) {
                is ResultadoEnvio.Guardado -> guardados += 1
                is ResultadoEnvio.Pendiente -> pendientes += 1
                is ResultadoEnvio.Error -> fallidos += 1
            }
        }
        return ResumenReintento(guardados, pendientes, fallidos, ahora())
    }

    fun pendientes(): Int = cola.tamano()
}

data class ResumenReintento(
    val guardados: Int,
    val pendientes: Int,
    val fallidos: Int,
    val momento: Long
)
