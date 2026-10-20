package com.example.geditorio.data.cloud

import com.example.geditorio.data.ReporteError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

/**
 * Identidad del conductor para escribir en la nube SIN pasar por el backend.
 *
 * El UID y el correo salen de la sesión de Firebase Authentication del
 * celular (FirebaseAuth.currentUser), nunca del formulario. Además, la cuenta
 * debe estar autorizada en `reporting_accounts/<uid>` (la publica el backend
 * desde el gestor): si no lo está, no se escribe nada.
 *
 * Las reglas de Firestore vuelven a comprobar lo mismo del lado del servidor:
 * esta clase evita peticiones inútiles, no reemplaza a las reglas.
 */
object CuentaNube {

    data class Identidad(val uid: String, val email: String)

    /** Sesión actual o un error que la pantalla ya sabe traducir. */
    fun identidadActual(auth: FirebaseAuth = FirebaseAuth.getInstance()): Result<Identidad> {
        val usuario = auth.currentUser
            ?: return Result.failure(CloudReportException(ReporteError.SIN_SESION))
        val uid = usuario.uid
        val email = usuario.email?.trim()?.lowercase().orEmpty()
        if (uid.isBlank()) return Result.failure(CloudReportException(ReporteError.SIN_SESION))
        if (email.isBlank()) return Result.failure(CloudReportException(ReporteError.SIN_AUTORIZACION))
        return Result.success(Identidad(uid, email))
    }

    /**
     * Comprueba que la cuenta esté autorizada y que la salida exista en el
     * catálogo publicado. Con el backend apagado, esto sale de la caché local
     * de Firestore (el catálogo ya se descargó antes); si no hay caché, la
     * transacción de escritura fallará por las reglas y lo informamos como
     * error de conexión.
     */
    suspend fun salidaAutorizada(
        identidad: Identidad,
        outingId: String,
        db: FirebaseFirestore = FirebaseFirestore.getInstance()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cuenta = leerCuenta(db, identidad.uid)
            if (cuenta == null) {
                return@withContext Result.failure(CloudReportException(ReporteError.SIN_AUTORIZACION))
            }
            val salida = leerSalida(db, outingId)
            if (salida == null) {
                return@withContext Result.failure(CloudReportException(ReporteError.SALIDA_NO_ENCONTRADA))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(clasificar(e))
        }
    }

    private suspend fun leerCuenta(db: FirebaseFirestore, uid: String): DocumentSnapshot? {
        val referencia = db.collection(COLECCION_CUENTAS).document(uid)
        return try {
            referencia.get(Source.CACHE).await()
        } catch (e: Exception) {
            // Sin caché: se intenta el servidor (requiere Internet).
            referencia.get().await()
        }
    }

    private suspend fun leerSalida(db: FirebaseFirestore, outingId: String): DocumentSnapshot? {
        val referencia = db.collection(COLECCION_SALIDAS).document(outingId)
        return try {
            referencia.get(Source.CACHE).await()
        } catch (e: Exception) {
            referencia.get().await()
        }
    }

    private fun clasificar(e: Exception): CloudReportException {
        val mensaje = e.message.orEmpty()
        return when {
            e is com.google.firebase.firestore.FirebaseFirestoreException -> when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                    CloudReportException(ReporteError.SIN_CONEXION, e)
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    CloudReportException(ReporteError.SIN_AUTORIZACION, e)
                else -> CloudReportException(ReporteError.SERVIDOR, e)
            }
            e is java.io.IOException || mensaje.contains("network", true) ->
                CloudReportException(ReporteError.SIN_CONEXION, e)
            else -> CloudReportException(ReporteError.GENERICO, e)
        }
    }

    const val COLECCION_CUENTAS = "reporting_accounts"
    const val COLECCION_SALIDAS = "reporting_outings"
}

/** Error del envío a la nube con un motivo traducible en la pantalla. */
class CloudReportException(
    val error: ReporteError,
    cause: Throwable? = null
) : Exception(cause?.message, cause)
