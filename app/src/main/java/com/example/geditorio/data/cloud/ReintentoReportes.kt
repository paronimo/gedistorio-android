package com.example.geditorio.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reintenta los reportes pendientes cuando vuelve la conexión.
 *
 * Se registra al iniciar la app y escucha cambios de red del sistema: por eso
 * funciona aunque la pantalla de reportes esté cerrada. Al arrancar también
 * reintenta lo que quedó pendiente de sesiones anteriores.
 *
 * No usa AlarmManager ni un servicio en primer plano: reintentar al volver la
 * red y al abrir la app cubre el caso real sin pedir permisos adicionales. Si
 * el celular está encendido y sin red, el pendiente se conserva en disco.
 */
object ReintentoReportes {

    private const val TAG = "GeditorioReportes"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var registrado = false

    fun iniciar(context: Context) {
        val aplicacion = context.applicationContext
        val cola = ColaReportesDisco(aplicacion)
        val sincronizador = SincronizadorReportes(FirestoreReportesNube(), cola)

        scope.launch {
            // Intento inicial: lo pendiente de la sesión anterior.
            reintentar(sincronizador, "inicio")
        }

        if (registrado) return
        val conectividad = aplicacion.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            conectividad.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { reintentar(sincronizador, "red-recuperada") }
                }
            })
            registrado = true
        } catch (e: SecurityException) {
            // Sin permiso de estado de red: queda el reintento al abrir la app
            // y al enviar desde la pantalla.
            Log.w(TAG, "No se pudo escuchar la red; se reintenta al abrir la app", e)
        }
    }

    private suspend fun reintentar(sincronizador: SincronizadorReportes, origen: String) {
        if (sincronizador.pendientes() == 0) return
        val resumen = sincronizador.reintentarPendientes()
        Log.i(
            TAG,
            "[$origen] reportes pendientes: ${resumen.guardados} guardados, " +
                "${resumen.pendientes} siguen pendientes, ${resumen.fallidos} con error"
        )
    }
}
