package com.example.geditorio.push

import com.example.geditorio.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recepción de las notificaciones del cronograma enviadas por el backend.
 *
 * La librería firebase-messaging implementa el servicio que las recibe aunque
 * la app no esté abierta. El backend envía "notification" + "data":
 *  - con la app cerrada o en segundo plano el sistema muestra el aviso solo;
 *  - con la app abierta (primer plano) el sistema NO lo muestra, así que aquí
 *    se publica en el mismo canal para que nunca se pierda.
 *
 * No se guarda contenido en disco ni se reenvía a terceros. onNewToken informa
 * el token para registrarlo en el backend con la cuenta que inició sesión.
 */
class GeditorioMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        NotificacionesPush.crearCanal(this)
        NotificacionesPush.registrarTokenNuevo(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        NotificacionesPush.crearCanal(this)
        val titulo = message.notification?.title
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.notificacion_titulo)
        val cuerpo = message.notification?.body ?: return
        // Misma clave (salidaId) que usa el backend: no se apila dos veces.
        NotificacionesPush.mostrarEnPrimerPlano(this, titulo, cuerpo, message.data["salidaId"].orEmpty())
    }
}
