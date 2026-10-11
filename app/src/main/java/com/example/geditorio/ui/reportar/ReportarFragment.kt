package com.example.geditorio.ui.reportar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.geditorio.R
import com.example.geditorio.data.SalidaRepository
import com.example.geditorio.data.model.ReporteSalida
import com.example.geditorio.data.model.Salida
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * Pantalla Reportar salida: formulario para indicar el resultado.
 *
 * Por ahora NO guarda el reporte en ningun lado ni lo manda a ningun
 * servidor. Al enviar, solo valida el formulario, construye el modelo
 * [ReporteSalida] y se lo entrega al repositorio, que hoy lo descarta.
 *
 * La pantalla no importa ninguna clase de Firebase ni de base de datos:
 * el destino del reporte se decide en SalidaRepository.
 */
class ReportarFragment : Fragment() {

    private val repository = SalidaRepository()

    private var salida: Salida? = null
    private var vista: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reportar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Se guarda la vista para poder leer el formulario al enviar.
        vista = view

        mostrarSalida(view)

        view.findViewById<MaterialButton>(R.id.btnEnviar).setOnClickListener { enviar() }
        view.findViewById<MaterialButton>(R.id.btnCancelar).setOnClickListener { cancelar() }
    }

    override fun onDestroyView() {
        // Se suelta la referencia para no quedarnos con la vista destruida.
        vista = null
        super.onDestroyView()
    }

    /**
     * Muestra que salida se esta reportando.
     *
     * Por ahora toma la proxima disponible. Cuando se navegue desde
     * "Mis salidas" se recibira el id por argumento y se buscara esa.
     */
    private fun mostrarSalida(view: View) {
        val actual = repository.proximaSalida()
        salida = actual

        view.findViewById<TextView>(R.id.txtSalidaTitulo).text =
            "${actual.tipo.etiqueta} · ${actual.fechaTexto}"
        view.findViewById<TextView>(R.id.txtSalidaDetalle).text =
            "${actual.horaTexto} · ${actual.lugarDeEncuentro}"
    }

    private fun enviar() {
        val view = vista ?: return
        val salidaActual = salida

        if (salidaActual == null) {
            Toast.makeText(requireContext(), R.string.reportar_salida_no_disponible, Toast.LENGTH_SHORT)
                .show()
            return
        }

        val realizada = when (view.findViewById<RadioGroup>(R.id.grupoResultado).checkedRadioButtonId) {
            R.id.radioRealizada -> true
            R.id.radioNoRealizada -> false
            else -> {
                // Sin resultado elegido: no se arma ni se envia nada.
                Toast.makeText(requireContext(), R.string.error_falta_resultado, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val observaciones = view.findViewById<TextInputEditText>(R.id.inputObservaciones)
            .text?.toString()?.trim().orEmpty()

        // El unico punto donde hoy se "envia". Cuando exista Firebase, el
        // repositorio lo guardara de verdad sin cambiar estas lineas.
        repository.enviarReporte(
            ReporteSalida(
                salidaId = salidaActual.id,
                realizada = realizada,
                observaciones = observaciones
            )
        )

        Toast.makeText(requireContext(), R.string.reporte_enviado_prueba, Toast.LENGTH_LONG).show()
    }

    private fun cancelar() {
        // Por ahora descarta el formulario y limpia los campos.
        vista?.findViewById<RadioGroup>(R.id.grupoResultado)?.clearCheck()
        vista?.findViewById<TextInputEditText>(R.id.inputObservaciones)?.setText("")
        Toast.makeText(requireContext(), R.string.reporte_cancelado, Toast.LENGTH_SHORT).show()
    }
}
