package com.example.geditorio.ui.detalle

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.geditorio.R
import com.example.geditorio.data.SalidaRepository
import com.example.geditorio.data.SalidasException
import com.example.geditorio.data.model.Salida
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Detalle completo de una salida.
 *
 * Recibe solo el id y busca la salida en el repositorio, de modo que
 * siempre muestra la version vigente del dato.
 */
class DetalleSalidaFragment : Fragment() {

    /** Debe coincidir con el nombre del argumento en nav_graph.xml. */
    private val ARG_SALIDA_ID = "salidaId"

    private val repository = SalidaRepository()
    private var vista: View? = null

    override fun onDestroyView() {
        vista = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_detalle_salida, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vista = view

        viewLifecycleOwner.lifecycleScope.launch {
            val actual = vista ?: return@launch
            val salida = try {
                repository.salidaPorId(requireArguments().getString(ARG_SALIDA_ID).orEmpty())
            } catch (e: SalidasException) {
                Toast.makeText(requireContext(), R.string.cronograma_error_conexion, Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
                return@launch
            }
            mostrar(actual, salida)
        }
    }

    private fun mostrar(view: View, salida: Salida?) {
        if (salida == null) {
            // La salida ya no existe: no se muestra una pantalla vacia.
            Toast.makeText(requireContext(), R.string.error_salida_no_encontrada, Toast.LENGTH_SHORT)
                .show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }

        view.findViewById<TextView>(R.id.txtTipo).text = salida.tipo.etiqueta
        view.findViewById<TextView>(R.id.txtEstado).text = salida.estado.etiqueta
        view.findViewById<TextView>(R.id.txtFecha).text = salida.fechaTexto
        view.findViewById<TextView>(R.id.txtHora).text = salida.horaTexto
        view.findViewById<TextView>(R.id.txtLugar).text =
            salida.lugarDeEncuentro.ifBlank { getString(R.string.sin_asignar) }
        view.findViewById<TextView>(R.id.txtConductor).text =
            salida.conductor.ifBlank { getString(R.string.sin_asignar) }
        view.findViewById<TextView>(R.id.txtTerritorios).text =
            if (salida.territorios.isEmpty()) getString(R.string.sin_territorios)
            else salida.territoriosTexto

        // El boton de mapa solo aparece si la salida tiene enlace.
        val boton = view.findViewById<MaterialButton>(R.id.btnMapa)
        val enlace = salida.enlaceGoogleMaps
        if (enlace.isNullOrBlank()) {
            boton.visibility = View.GONE
        } else {
            boton.visibility = View.VISIBLE
            boton.setOnClickListener { abrirMapa(enlace) }
        }
    }

    private fun abrirMapa(enlace: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(enlace))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_sin_app_mapa, Toast.LENGTH_SHORT).show()
        }
    }
}
