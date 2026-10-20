package com.example.geditorio

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
<<<<<<< Updated upstream
=======
import com.example.geditorio.data.AuthRepository
import com.example.geditorio.ui.home.HomeFragment
import com.example.geditorio.ui.login.LoginActivity
>>>>>>> Stashed changes
import com.google.android.material.bottomnavigation.BottomNavigationView

<<<<<<< Updated upstream
class MainActivity : AppCompatActivity() {
=======
class MainActivity : AppCompatActivity(), HomeFragment.CerrarSesionListener {

    private val auth = AuthRepository()

>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
        findViewById<BottomNavigationView>(R.id.bottomNav)
            .setupWithNavController(navHost.navController)
=======
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setupWithNavController(navHost.navController)

        // Cerrar sesion: opcion visible en Inicio y, ademas, manteniendo
        // pulsado el icono de la barra inferior. La pantalla de Inicio avisa
        // por HomeFragment.CerrarSesionListener.
        bottomNav.setOnLongClickListener {
            confirmarCierreSesion()
            true
        }
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
        auth.cerrarSesion()
        irALogin()
    }

    /** Vuelve al login limpiando la pila para que no se pueda volver atras. */
    private fun irALogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
>>>>>>> Stashed changes
    }
}
