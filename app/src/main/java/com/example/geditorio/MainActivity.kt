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
import com.example.geditorio.ui.login.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Barrera de seguridad: sin sesion valida no se entra a la pantalla privada.
        if (!auth.haySesionIniciada()) {
            irALogin()
            return
        }

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

        // Cerrar sesion: manten pulsado el icono de la barra inferior.
        bottomNav.setOnLongClickListener {
            confirmarCierreSesion()
            true
        }
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
        auth.cerrarSesion()
        irALogin()
    }

    /** Vuelve al login limpiando la pila para que no se pueda volver atras. */
    private fun irALogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}