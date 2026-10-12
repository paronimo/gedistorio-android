package com.example.geditorio.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.geditorio.MainActivity
import com.example.geditorio.R
import com.example.geditorio.data.AuthError
import com.example.geditorio.data.AuthException
import com.example.geditorio.data.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio de sesion.
 *
 * Unica pantalla publica de la app. Solo permite entrar con una cuenta ya
 * existente (Email/Password): no registra usuarios nuevos, porque las cuentas
 * las administra por ahora el responsable del sistema desde la consola de
 * Firebase.
 */
class LoginActivity : AppCompatActivity() {

    private val auth = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.inputCorreo)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val correo = findViewById<EditText>(R.id.inputCorreo)
        val contrasena = findViewById<EditText>(R.id.inputContrasena)
        val boton = findViewById<MaterialButton>(R.id.btnIniciarSesion)
        val progreso = findViewById<CircularProgressIndicator>(R.id.progresoLogin)

        boton.setOnClickListener {
            val textoCorreo = correo.text.toString().trim()
            val textoContrasena = contrasena.text.toString()

            when {
                textoCorreo.isEmpty() -> mostrarError(getString(R.string.error_correo_vacio))
                textoContrasena.isEmpty() -> mostrarError(getString(R.string.error_contrasena_vacia))
                else -> {
                    progreso.visibility = View.VISIBLE
                    boton.isEnabled = false
                    lifecycleScope.launch {
                        try {
                            auth.iniciarSesion(textoCorreo, textoContrasena)
                            irAInicio()
                        } catch (e: AuthException) {
                            progreso.visibility = View.GONE
                            boton.isEnabled = true
                            mostrarError(getString(e.error.mensaje()))
                        }
                    }
                }
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        Snackbar.make(findViewById(android.R.id.content), mensaje, Snackbar.LENGTH_LONG).show()
    }

    private fun irAInicio() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/** Traduce cada [AuthError] al texto que se muestra en pantalla. */
private fun AuthError.mensaje(): Int = when (this) {
    AuthError.CREDENCIALES -> R.string.error_credenciales
    AuthError.SIN_CONEXION -> R.string.error_conexion
    AuthError.GENERICO -> R.string.error_generico
}
