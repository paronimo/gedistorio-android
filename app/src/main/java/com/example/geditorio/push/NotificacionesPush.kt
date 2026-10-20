package com.example.geditorio.push

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.geditorio.BuildConfig
import com.example.geditorio.R
import com.example.geditorio.MainActivity
import com.example.geditorio.data.AuthRepository
import com.example.geditorio.data.TokenProvider
import com.example.geditorio.ui.login.LoginActivity
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Notificaciones de las salidas programadas (Firebase Cloud Messaging).
 *
 * El backend decide CUÁNDO y A QUIÉN avisar usando el cronograma real; este
 * objeto solo se ocupa del celular:
 *  - crea el canal de notificaciones (Android 8+);
 *  - pide el permiso de notificaciones (Android 13+);
 *  - registra el token FCM en el backend con la sesión del conductor.
 *
 * El token va autenticado: el backend lo asocia a la cuenta del token de
 * Firebase y, por el correo de esa cuenta, al registro del conductor. La app
 * no puede decir "soy tal persona": no envía ningún nombre.
 */
object NotificacionesPush {

    const val CANAL_SALIDAS = "salidas"

    private const val TAG = "GeditorioPush"
    private const val PREFS = "geditorio_notificaciones"
    private const val CLAVE_TOKEN = "token_fcm"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Último error de registro, para diagnóstico (sin datos sensibles). */
    @Volatile
    var ultimoError: String? = null
        private set

    /** Crea el canal con el mismo id que usa el backend ("salidas"). */
    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val canal = NotificationChannel(
            CANAL_SALIDAS,
            context.getString(com.example.geditorio.R.string.canal_salidas_nombre),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(com.example.geditorio.R.string.canal_salidas_descripcion)
        }
        manager.createNotificationChannel(canal)
    }

    /**
     * Muestra el aviso cuando la app está abierta (FCM no lo hace en primer
     * plano). Usa el mismo canal e ícono que el aviso del sistema cuando la
     * app está cerrada, de modo que el conductor vea siempre lo mismo.
     */
    fun mostrarEnPrimerPlano(context: Context, titulo: String, cuerpo: String, salidaId: String) {
        if (!permisoConcedido(context)) return
        crearCanal(context)
        val notificacion = NotificationCompat.Builder(context, CANAL_SALIDAS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(ContextCompat.getColor(context, R.color.azul))
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify((salidaId.ifBlank { titulo }).hashCode(), notificacion)
        } catch (e: SecurityException) {
            // El usuario quitó el permiso entre la comprobación y el envío.
            ultimoError = e.message
        }
    }

    /** Android 13+ exige permiso para mostrar notificaciones. */
    fun permisoConcedido(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Pide el permiso una vez; si lo rechaza, se puede volver a pedir en Ajustes. */
    fun pedirPermiso(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permisoConcedido(activity)) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                CODIGO_PERMISO
            )
        }
    }

    const val CODIGO_PERMISO = 4101

    /**
     * Obtiene el token FCM de este celular y lo registra en el backend con la
     * sesión iniciada. Si no hay sesión o el backend no responde, no falla la
     * app: se vuelve a intentar en el próximo inicio (FCM entrega el token
     * también cuando el sistema lo renueva).
     */
    fun iniciarRegistro(context: Context, baseUrl: String = BuildConfig.BACKEND_URL) {
        scope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                guardarToken(context, token)
                registrarEnServidor(token, baseUrl)
            } catch (e: Exception) {
                ultimoError = e.message
                Log.w(TAG, "No se pudo registrar el dispositivo para notificaciones", e)
            }
        }
    }

    /** Registro que dispara FCM cuando crea o renueva el token (ver MessagingService). */
    fun registrarTokenNuevo(context: Context, token: String, baseUrl: String = BuildConfig.BACKEND_URL) {
        val anterior = tokenGuardado(context)
        guardarToken(context, token)
        scope.launch {
            try {
                val auth = AuthRepository()
                if (anterior != null && anterior != token && auth.haySesionIniciada()) {
                    // El token anterior ya no existe en FCM: se quita del backend.
                    runCatching { darDeBaja(anterior, auth, baseUrl) }
                }
                registrarEnServidor(token, baseUrl)
            } catch (e: Exception) {
                ultimoError = e.message
            }
        }
    }

    private suspend fun registrarEnServidor(token: String, baseUrl: String) {
        val auth = AuthRepository()
        val idToken = auth.idToken() ?: return // sin sesión: se registra al iniciar sesión
        withContext(Dispatchers.IO) {
            RegistroDispositivoHttp(baseUrl).registrar(token, idToken)
        }
        ultimoError = null
        Log.i(TAG, "Dispositivo registrado para notificaciones")
    }

    private suspend fun darDeBaja(token: String, auth: TokenProvider, baseUrl: String) {
        val idToken = auth.idToken() ?: return
        withContext(Dispatchers.IO) {
            RegistroDispositivoHttp(baseUrl).darDeBaja(token, idToken)
        }
    }

    /**
     * Antes de cerrar sesión: el backend deja de enviar los avisos a este
     * celular. Si no hay red, se cierra la sesión igual (el backend deja de
     * enviar en cuanto el token deja de ser válido para Firebase).
     */
    fun desvincularAntesDeCerrarSesion(
        context: Context,
        auth: AuthRepository,
        alTerminar: () -> Unit
    ) {
        scope.launch {
            runCatching {
                val token = tokenGuardado(context)
                if (!token.isNullOrBlank()) darDeBaja(token, auth, BuildConfig.BACKEND_URL)
            }
            auth.cerrarSesion()
            withContext(Dispatchers.Main) { alTerminar() }
        }
    }

    /** Pantalla inicial según haya sesión o no (después de cerrar sesión). */
    fun intentDeInicio(context: Context): Intent = Intent(context, LoginActivity::class.java)

    private fun tokenGuardado(context: Context?): String? =
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(CLAVE_TOKEN, null)

    private fun guardarToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CLAVE_TOKEN, token).apply()
    }
}
