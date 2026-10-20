package com.example.geditorio.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.tasks.await

/**
 * Punto unico de acceso a Firebase Authentication.
 *
 * Solo permite INICIAR y CERRAR sesion con correo y contrasena. No expone
 * registro de cuentas: las cuentas las crea el responsable del sistema.
 * Las contrasenas nunca se guardan ni se registran en ningun lado; se
 * entregan directamente a Firebase y se descartan.
 */
class AuthRepository : TokenProvider {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** Usuario con sesion valida ahora mismo, o null si no hay sesion. */
    val usuarioActual: FirebaseUser? get() = auth.currentUser

    /** true si hay una sesion iniciada y vigente. */
    fun haySesionIniciada(): Boolean = auth.currentUser != null

    /**
     * Inicia sesion con correo y contrasena.
     *
     * Devuelve el usuario autenticado o lanza [AuthException] con un
     * mensaje comprensible para mostrar en pantalla. No crea cuentas nuevas.
     */
    suspend fun iniciarSesion(correo: String, contrasena: String): FirebaseUser {
        val tarea = auth.signInWithEmailAndPassword(correo, contrasena)
        try {
            return tarea.await().user
                ?: throw AuthException(AuthError.GENERICO)
        } catch (e: FirebaseNetworkException) {
            throw AuthException(AuthError.SIN_CONEXION, e)
        } catch (e: FirebaseAuthInvalidUserException) {
            // Correo inexistente o cuenta deshabilitada: mismo mensaje, para
            // no revelar si un correo esta o no registrado.
            throw AuthException(AuthError.CREDENCIALES, e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw AuthException(AuthError.CREDENCIALES, e)
        } catch (e: FirebaseAuthUserCollisionException) {
            // No deberia ocurrir al solo iniciar sesion.
            throw AuthException(AuthError.GENERICO, e)
        } catch (e: Exception) {
            throw AuthException(AuthError.GENERICO, e)
        }
    }

    /**
     * ID token de la sesion actual, para autenticar los pedidos al backend web.
     * Firebase lo renueva solo cuando esta por vencer; no se guarda en disco.
     * Devuelve null si no hay sesion.
     */
    override suspend fun idToken(): String? {
        val usuario = auth.currentUser ?: return null
        return usuario.getIdToken(false).await().token
    }

    /** Cierra la sesion en Firebase. */
    fun cerrarSesion() {
        auth.signOut()
    }
}

/** Tipo de error de autenticacion, para mapear a textos en la pantalla. */
enum class AuthError {
    /** Correo o contrasena incorrectos. */
    CREDENCIALES,

    /** Sin conexion a la red o fallo de red con Firebase. */
    SIN_CONEXION,

    /** Cualquier otro fallo inesperado. */
    GENERICO
}

/**
 * Error de autenticacion con un [AuthError] que la pantalla traduce a un
 * mensaje. La causa original se conserva para depuracion, sin datos sensibles.
 */
class AuthException(
    val error: AuthError,
    cause: Throwable? = null
) : Exception(cause?.message, cause)
