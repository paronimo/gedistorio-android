package com.example.geditorio.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.geditorio.R
import com.example.geditorio.data.ReporteError
import com.example.geditorio.data.SalidaRepository
import com.example.geditorio.data.SalidasException
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio.
 *
 * Muestra los datos de la proxima salida del cronograma REAL del gestor.
 * Si el servidor no responde, muestra el error en lugar de una salida de
 * ejemplo.
 *
 * Tambien ofrece la opcion visible de cerrar sesion. El fragmento no cierra
 * sesion por si mismo: se lo pide a la pantalla que lo contiene, para que el
 * cierre y la limpieza de la pila sigan en un solo lugar.
 */
class HomeFragment : Fragment() {

    /** Quien contiene al fragmento y sabe cerrar sesion. */
    interface CerrarSesionListener {
        fun onCerrarSesionSolicitado()
    }

    private val repository = SalidaRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnCerrarSesion).setOnClickListener {
            (activity as? CerrarSesionListener)?.onCerrarSesionSolicitado()
        }

        view.findViewById<TextView>(R.id.txtTipo)
            .setText(R.string.reportar_cargando_salida)
        view.findViewById<TextView>(R.id.txtFecha).text = ""

        viewLifecycleOwner.lifecycleScope.launch {
            val salida = try {
                repository.proximaSalida()
            } catch (e: SalidasException) {
                mostrarError(view, mensajeDeErrorCarga(e.error))
                return@launch
            }

            if (salida == null) {
                mostrarError(view, R.string.reportar_sin_salidas)
                return@launch
            }

            view.findViewById<TextView>(R.id.txtTipo).text = salida.tipo.etiqueta
            view.findViewById<TextView>(R.id.txtFecha).text = salida.fechaTexto

            view.mostrarFila(R.id.rowHora, R.string.etiqueta_hora, salida.horaTexto)
            view.mostrarFila(
                R.id.rowLugar,
                R.string.etiqueta_lugar,
                salida.lugarDeEncuentro.ifBlank { getString(R.string.sin_asignar) }
            )
            view.mostrarFila(
                R.id.rowConductor,
                R.string.etiqueta_conductor,
                salida.conductor.ifBlank { getString(R.string.sin_asignar) }
            )
            view.mostrarFila(
                R.id.rowTerritorio,
                R.string.etiqueta_territorio,
                salida.territoriosTexto.ifBlank { getString(R.string.sin_asignar) }
            )
            view.mostrarFila(R.id.rowEstado, R.string.etiqueta_estado, salida.estado.etiqueta)
        }
    }

    private fun mostrarError(view: View, mensaje: Int) {
        view.findViewById<TextView>(R.id.txtTipo).setText(R.string.reportar_salida_no_disponible)
        view.findViewById<TextView>(R.id.txtFecha).text = ""
        view.mostrarFila(R.id.rowHora, R.string.etiqueta_hora, getString(mensaje))
    }

    private fun mensajeDeErrorCarga(error: ReporteError): Int = when (error) {
        ReporteError.SIN_CONFIGURAR -> R.string.reporte_error_sin_configurar
        ReporteError.SIN_SESION, ReporteError.SIN_AUTORIZACION -> R.string.reporte_error_sesion
        ReporteError.SIN_CONEXION -> R.string.cronograma_error_conexion
        else -> R.string.cronograma_error_generico
    }

    private fun View.mostrarFila(idFila: Int, etiqueta: Int, valor: String) {
        val fila = findViewById<View>(idFila)
        fila.findViewById<TextView>(R.id.txtEtiqueta).setText(etiqueta)
        fila.findViewById<TextView>(R.id.txtDato).text = valor
    }
}
