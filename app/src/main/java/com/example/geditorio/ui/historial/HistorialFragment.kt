package com.example.geditorio.ui.historial

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.geditorio.R

/**
 * Pantalla Historial. Todavia sin logica real: solo deja
 * preparada la estructura para completarla mas adelante.
 */
class HistorialFragment : Fragment(R.layout.fragment_placeholder) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.txtTitulo).setText(R.string.nav_historial)
        view.findViewById<TextView>(R.id.txtDescripcion).setText(R.string.historial_descripcion)
    }
}
