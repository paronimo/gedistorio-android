package com.example.geditorio.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.geditorio.R
import com.example.geditorio.data.SalidaRepository

/**
 * Pantalla de inicio.
 *
 * Muestra los datos de la proxima salida. Por ahora provienen del
 * repositorio con DATOS DE EJEMPLO: cuando exista la fuente real, esta
 * pantalla no necesita cambios.
 */
class HomeFragment : Fragment() {

    private val repository = SalidaRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val salida = repository.proximaSalida()

        view.findViewById<TextView>(R.id.txtTipo).text = salida.tipo.etiqueta
        view.findViewById<TextView>(R.id.txtFecha).text = salida.fechaTexto

        view.mostrarFila(R.id.rowHora, R.string.etiqueta_hora, salida.horaTexto)
        view.mostrarFila(R.id.rowLugar, R.string.etiqueta_lugar, salida.lugarDeEncuentro)
        view.mostrarFila(R.id.rowConductor, R.string.etiqueta_conductor, salida.conductor)
        view.mostrarFila(
            R.id.rowTerritorio,
            R.string.etiqueta_territorio,
            salida.territoriosTexto
        )
        view.mostrarFila(R.id.rowEstado, R.string.etiqueta_estado, salida.estado.etiqueta)
    }

    private fun View.mostrarFila(idFila: Int, etiqueta: Int, valor: String) {
        val fila = findViewById<View>(idFila)
        fila.findViewById<TextView>(R.id.txtEtiqueta).setText(etiqueta)
        fila.findViewById<TextView>(R.id.txtDato).text = valor
    }
}
