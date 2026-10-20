package com.example.geditorio.ui.misSalidas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.geditorio.R
import com.example.geditorio.data.ReporteError
import com.example.geditorio.data.SalidaRepository
import com.example.geditorio.data.SalidasException
import kotlinx.coroutines.launch

/**
 * Pantalla Mis salidas: lista las salidas asignadas al conductor,
 * ordenadas por fecha y hora.
 *
 * Solo pasa el id de la salida seleccionada al detalle. No manda el objeto
 * completo, para que el detalle no dependa de datos que ya podrian haber
 * cambiado.
 */
class MisSalidasFragment : Fragment() {

    private val repository = SalidaRepository()
    private var vista: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_mis_salidas, container, false)

    override fun onDestroyView() {
        vista = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vista = view

        val lista = view.findViewById<RecyclerView>(R.id.listaSalidas)

        val adapter = SalidaAdapter { salida ->
            // Se pasa solo el id, no el objeto Salida completo.
            findNavController().navigate(
                R.id.action_misSalidasFragment_to_detalleSalidaFragment,
                bundleOf("salidaId" to salida.id)
            )
        }

        lista.layoutManager = LinearLayoutManager(requireContext())
        lista.adapter = adapter

        view.findViewById<View>(R.id.btnReintentar).setOnClickListener { cargar(view, adapter) }
        cargar(view, adapter)
    }

    /** Lista el cronograma real; si falla, muestra el error con reintento. */
    private fun cargar(view: View, adapter: SalidaAdapter) {
        val bloqueError = view.findViewById<View>(R.id.bloqueError)
        view.findViewById<TextView>(R.id.txtVacio).visibility = View.GONE
        bloqueError.visibility = View.GONE
        view.findViewById<RecyclerView>(R.id.listaSalidas).visibility = View.VISIBLE
        view.findViewById<View>(R.id.btnReintentar).isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val actual = vista ?: return@launch
            val salidas = try {
                repository.misSalidas()
            } catch (e: SalidasException) {
                // No se finge una lista vacia ni se usan ejemplos: se informa.
                adapter.actualizar(emptyList())
                actual.findViewById<TextView>(R.id.txtErrorCronograma)
                    .setText(mensajeDeErrorCarga(e.error))
                actual.findViewById<RecyclerView>(R.id.listaSalidas).visibility = View.GONE
                actual.findViewById<View>(R.id.bloqueError).visibility = View.VISIBLE
                actual.findViewById<View>(R.id.btnReintentar).isEnabled = true
                return@launch
            }

            actual.findViewById<TextView>(R.id.txtVacio)
                .visibility = if (salidas.isEmpty()) View.VISIBLE else View.GONE
            adapter.actualizar(salidas)
        }
    }

    private fun mensajeDeErrorCarga(error: ReporteError): Int = when (error) {
        ReporteError.SIN_CONFIGURAR -> R.string.reporte_error_sin_configurar
        ReporteError.SIN_SESION, ReporteError.SIN_AUTORIZACION -> R.string.reporte_error_sesion
        ReporteError.SIN_CONEXION -> R.string.cronograma_error_conexion
        else -> R.string.cronograma_error_generico
    }
}
