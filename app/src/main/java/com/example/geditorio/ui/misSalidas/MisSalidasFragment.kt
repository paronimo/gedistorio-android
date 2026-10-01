package com.example.geditorio.ui.misSalidas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.geditorio.R
import com.example.geditorio.data.SalidaRepository

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_mis_salidas, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lista = view.findViewById<RecyclerView>(R.id.listaSalidas)
        val textoVacio = view.findViewById<android.widget.TextView>(R.id.txtVacio)

        val adapter = SalidaAdapter { salida ->
            // Se pasa solo el id, no el objeto Salida completo.
            findNavController().navigate(
                R.id.action_misSalidasFragment_to_detalleSalidaFragment,
                bundleOf("salidaId" to salida.id)
            )
        }

        lista.layoutManager = LinearLayoutManager(requireContext())
        lista.adapter = adapter

        val salidas = repository.misSalidas()
        adapter.actualizar(salidas)
        textoVacio.visibility = if (salidas.isEmpty()) View.VISIBLE else View.GONE
    }
}
