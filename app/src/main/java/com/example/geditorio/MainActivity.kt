package com.example.geditorio

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.geditorio.data.AuthRepository
import com.example.geditorio.data.cloud.ReintentoReportes
import com.example.geditorio.push.NotificacionesPush
import com.example.geditorio.ui.home.HomeFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity(), HomeFragment.CerrarSesionListener {

    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Navegacion principal: Inicio / Mis salidas / Reportar / Historial
        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setupWithNavController(navHost.navController)

        // Cerrar sesion: opcion visible en Inicio y, ademas, manteniendo
        // pulsado el icono de la barra inferior. La pantalla de Inicio avisa
        // por HomeFragment.CerrarSesionListener.
        bottomNav.setOnLongClickListener {
            confirmarCierreSesion()
            true
        }

        // Reportes que quedaron sin conexión: se reintentan al abrir la app y,
        // mientras esté abierta, cuando vuelve la red.
        ReintentoReportes.iniciar(this)

        // Notificaciones del cronograma (FCM): canal, permiso en Android 13+
        // y registro del celular en el backend con la cuenta iniciada, para
        // que el servidor identifique al destinatario sin adivinar nombres.
        NotificacionesPush.crearCanal(this)
        NotificacionesPush.pedirPermiso(this)
        NotificacionesPush.iniciarRegistro(this)
    }

    override fun onCerrarSesionSolicitado() {
        confirmarCierreSesion()
    }

    /** Pregunta antes de cerrar sesion y, al aceptar, vuelve al login. */
    private fun confirmarCierreSesion() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.cerrar_sesion_pregunta)
            .setNegativeButton(R.string.btn_cancelar, null)
            .setPositiveButton(R.string.cerrar_sesion) { _, _ -> cerrarSesion() }
            .show()
    }

    private fun cerrarSesion() {
        // Desvincula el token FCM de la cuenta antes de salir: este celular
        // deja de recibir los avisos de las salidas de esa persona.
        NotificacionesPush.desvincularAntesDeCerrarSesion(this, auth) {
            startActivity(NotificacionesPush.intentDeInicio(this))
            finish()
        }
    }
}
