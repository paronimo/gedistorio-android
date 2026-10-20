package com.example.geditorio.data.cloud

import com.example.geditorio.data.ReporteError
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

/**
 * Escritura de un reporte directamente en Cloud Firestore.
 *
 * - Documento: reports/<outingId>__<uid>. El id es determinístico: reintentar
 *   el mismo reporte sobrescribe el MISMO documento, nunca crea otro.
 * - Dentro de una transacción se vuelve a leer la salida del catálogo
 *   `reporting_outings`. Si no existe, no se escribe nada.
 * - El uid y el correo se toman de la sesión, no de lo que diga la pantalla.
 * - Si el documento ya existe (otro diseño anterior o un reintento paralelo),
 *   se considera guardado: no se duplica.
 */
interface ClienteReportesNube {
    /** true si el documento ya existía antes de este intento. */
    suspend fun guardar(pendiente: ReportePendiente): ResultadoGuardado
}

data class ResultadoGuardado(val yaExistia: Boolean, val documentId: String)

class FirestoreReportesNube(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ClienteReportesNube {

    override suspend fun guardar(pendiente: ReportePendiente): ResultadoGuardado =
        withContext(Dispatchers.IO) {
            val identidad = CuentaNube.identidadActual()
                .getOrElse { throw CloudReportException(ReporteError.SIN_SESION, it) }
            if (identidad.uid != pendiente.uid) {
                // La sesión cambió desde que se guardó el pendiente: no se
                // publica un reporte de otra persona desde este celular.
                throw CloudReportException(ReporteError.SIN_AUTORIZACION)
            }
            CuentaNube.salidaAutorizada(identidad, pendiente.outingId, db)
                .getOrElse { throw it as? CloudReportException ?: CloudReportException(ReporteError.GENERICO, it) }

            val documentId = documentId(pendiente.outingId, identidad.uid)
            val referencia = db.collection(COLECCION).document(documentId)
            var yaExistia = false
            try {
                db.runTransaction { transaccion ->
                    val salida = transaccion.get(
                        db.collection(CuentaNube.COLECCION_SALIDAS).document(pendiente.outingId)
                    )
                    if (!salida.exists()) {
                        throw CloudReportException(ReporteError.SALIDA_NO_ENCONTRADA)
                    }
                    val fechaOficial = salida.getString("date")
                    if (!fechaOficial.isNullOrBlank() && fechaOficial != pendiente.salidaFecha) {
                        throw CloudReportException(ReporteError.SERVIDOR)
                    }
                    val existente = transaccion.get(referencia)
                    if (existente.exists()) {
                        yaExistia = true
                        return@runTransaction null
                    }
                    transaccion.set(referencia, datos(pendiente, salida.getString("date"), salida.getString("time"), salida.getString("type")))
                    null
                }.await()
            } catch (e: CloudReportException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                throw clasificar(e)
            } catch (e: Exception) {
                throw clasificar(e)
            }
            ResultadoGuardado(yaExistia = yaExistia, documentId = documentId)
        }

    private fun datos(
        pendiente: ReportePendiente,
        outingDate: String?,
        outingTime: String?,
        outingType: String?
    ): Map<String, Any> {
        val ahora = com.google.firebase.Timestamp.now()
        return mapOf(
            "outingId" to pendiente.outingId,
            "outingDate" to (outingDate ?: pendiente.salidaFecha),
            "outingTime" to (outingTime ?: ""),
            "outingType" to (outingType ?: ""),
            "realizada" to pendiente.realizada,
            "observaciones" to pendiente.observaciones,
            // El texto del territorio va tal cual lo generó el mapa.
            "territorioTexto" to pendiente.territorioTexto,
            "receivedBy" to pendiente.uid,
            "receivedByEmail" to pendiente.email,
            "receivedAt" to ahora,
            "receivedAtServer" to ahora,
            "source" to "cloud",
            "schema" to 1
        )
    }

    private fun clasificar(e: Exception): CloudReportException {
        val mensaje = e.message.orEmpty().lowercase()
        if (e is FirebaseFirestoreException) {
            return when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    CloudReportException(ReporteError.SIN_CONEXION, e)
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    CloudReportException(ReporteError.SIN_AUTORIZACION, e)
                FirebaseFirestoreException.Code.NOT_FOUND ->
                    CloudReportException(ReporteError.SALIDA_NO_ENCONTRADA, e)
                else -> CloudReportException(ReporteError.SERVIDOR, e)
            }
        }
        return if (mensaje.contains("network") || mensaje.contains("offline") || mensaje.contains("unable to resolve")) {
            CloudReportException(ReporteError.SIN_CONEXION, e)
        } else {
            CloudReportException(ReporteError.GENERICO, e)
        }
    }

    companion object {
        const val COLECCION = "reports"

        fun documentId(outingId: String, uid: String) = "${outingId}__$uid"
    }
}
