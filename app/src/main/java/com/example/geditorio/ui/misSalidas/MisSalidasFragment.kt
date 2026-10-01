package com.example.geditorio.ui.misSalidas

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.geditorio.R

/**
 * Pantalla MisSalidas. Todavia sin logica real: solo deja
 * preparada la estructura para completarla mas adelante.
 */
class MisSalidasFragment : Fragment(R.layout.fragment_placeholder) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.txtTitulo).setText(R.string.nav_mis_salidas)
        view.findViewById<TextView>(R.id.txtDescripcion).setText(R.string.mis_salidas_descripcion)
    }
}
