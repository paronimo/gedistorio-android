package com.example.geditorio.ui.reportar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.geditorio.R
import com.example.geditorio.data.ManzanasRepository
import com.example.geditorio.data.ReporteError
import com.example.geditorio.data.SalidaRepository
import com.example.geditorio.data.SalidasException
import com.example.geditorio.data.model.ReporteSalida
import com.example.geditorio.data.model.Salida
import com.example.geditorio.domain.Manzana
import com.example.geditorio.domain.ManzanasGeoData
import com.example.geditorio.domain.SeleccionMapa
import com.example.geditorio.domain.seleccionATexto
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import com.example.geditorio.data.cloud.ColaReportesDisco
import com.example.geditorio.data.cloud.CuentaNube
import com.example.geditorio.data.cloud.FirestoreReportesNube
import com.example.geditorio.data.cloud.ReportePendiente
import com.example.geditorio.data.cloud.ResultadoEnvio
import com.example.geditorio.data.cloud.SincronizadorReportes

/**
 * Pantalla Reportar salida: formulario para indicar el resultado y las manzanas
 * trabajadas sobre el mapa interactivo.
 *
 * El mapa permite seleccionar manzanas de forma visual con Canvas (zoom, pan,
 * toques) y genera el texto estructurado para la columna "Territorio" del
 * reporte (por ejemplo: "T 4 : 7250 (Faltan: 7251, 7270)" o "T 4").
 *
 * Al enviar, valida el formulario, construye [ReporteSalida] y lo guarda
 * DIRECTO en Cloud Firestore (no depende del backend local). Solo se da por
 * enviado cuando Firestore confirma; sin conexion queda en la cola persistente
 * del celular y se reintenta solo, con el mismo identificador (sin duplicar).
 * La lista de salidas sigue consultandose al backend ([SalidaRepository]).
 */
class ReportarFragment : Fragment() {

    private val repository = SalidaRepository()
    private lateinit var sincronizador: SincronizadorReportes
    private lateinit var manzanasRepository: ManzanasRepository

    private var salida: Salida? = null
    private var vista: View? = null

    private var geoData: ManzanasGeoData = ManzanasGeoData(emptyList(), emptyList())
    private var seleccion: SeleccionMapa = SeleccionMapa()
    private val historial = ArrayDeque<SeleccionMapa>()

    private var envioEnCurso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La cola está en el almacenamiento interno: un reporte sin conexión
        // sobrevive a cerrar la app y a reiniciar el celular.
        sincronizador = SincronizadorReportes(
            cliente = FirestoreReportesNube(),
            cola = ColaReportesDisco(requireContext().applicationContext)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reportar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vista = view
        manzanasRepository = ManzanasRepository(requireContext())

        cargarSalida(view)
        configurarMapa(view)

        view.findViewById<MaterialButton>(R.id.btnEnviar).setOnClickListener { enviar(view) }
        view.findViewById<MaterialButton>(R.id.btnCancelar).setOnClickListener { cancelar() }
    }

    override fun onDestroyView() {
        vista = null
        super.onDestroyView()
    }

    /**
     * Carga la proxima salida del cronograma REAL del gestor. Mientras llega, o
     * si el servidor falla, se muestra el motivo y no se permite enviar: no se
     * usa una salida de ejemplo como si viniera del servidor.
     */
    private fun cargarSalida(view: View) {
        val titulo = view.findViewById<TextView>(R.id.txtSalidaTitulo)
        val detalle = view.findViewById<TextView>(R.id.txtSalidaDetalle)
        val boton = view.findViewById<MaterialButton>(R.id.btnEnviar)

        titulo.setText(R.string.reportar_cargando_salida)
        detalle.text = ""
        boton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val vistaActual = vista ?: return@launch
            val actual = try {
                repository.proximaSalida()
            } catch (e: SalidasException) {
                salida = null
                titulo.setText(R.string.reportar_salida_no_disponible)
                detalle.setText(mensajeDeErrorCarga(e.error))
                vistaActual.findViewById<MaterialButton>(R.id.btnEnviar).isEnabled = false
                return@launch
            }

            salida = actual
            if (actual == null) {
                titulo.setText(R.string.reportar_salida_no_disponible)
                detalle.setText(R.string.reportar_sin_salidas)
                vistaActual.findViewById<MaterialButton>(R.id.btnEnviar).isEnabled = false
                return@launch
            }

            titulo.text = "${actual.tipo.etiqueta} · ${actual.fechaTexto}"
            detalle.text = listOf(actual.horaTexto, actual.lugarDeEncuentro)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            vistaActual.findViewById<MaterialButton>(R.id.btnEnviar).isEnabled = true
        }
    }

    private fun mensajeDeErrorCarga(error: ReporteError): Int = when (error) {
        ReporteError.SIN_CONFIGURAR -> R.string.reporte_error_sin_configurar
        ReporteError.SIN_SESION, ReporteError.SIN_AUTORIZACION -> R.string.reporte_error_sesion
        ReporteError.SIN_CONEXION -> R.string.cronograma_error_conexion
        else -> R.string.cronograma_error_generico
    }

    private fun configurarMapa(view: View) {
        val mapa = view.findViewById<MapaManzanasView>(R.id.mapaManzanas)
        val txtVacio = view.findViewById<TextView>(R.id.txtMapaVacio)
        val btnZoomIn = view.findViewById<View>(R.id.btnZoomIn)
        val btnZoomOut = view.findViewById<View>(R.id.btnZoomOut)
        val btnZoomReset = view.findViewById<View>(R.id.btnZoomReset)
        val btnQuitarUltima = view.findViewById<View>(R.id.btnQuitarUltima)
        val btnQuitarTodo = view.findViewById<View>(R.id.btnQuitarTodo)

        geoData = manzanasRepository.cargar()

        if (geoData.manzanas.isEmpty()) {
            mapa.visibility = View.GONE
            txtVacio.visibility = View.VISIBLE
        } else {
            mapa.visibility = View.VISIBLE
            txtVacio.visibility = View.GONE
            mapa.setManzanas(geoData.manzanas)
            mapa.onManzanaTocada = { manzana -> onManzanaTocada(manzana) }
        }

        btnZoomIn?.setOnClickListener { mapa.acercar() }
        btnZoomOut?.setOnClickListener { mapa.alejar() }
        btnZoomReset?.setOnClickListener { mapa.reiniciarVista() }

        btnQuitarUltima?.setOnClickListener { quitarUltima() }
        btnQuitarTodo?.setOnClickListener { quitarTodo() }

        actualizarSeleccionUI()
    }

    /**
     * Cuadro de dialogo al tocar una manzana con exactamente tres opciones:
     *  - Marcar esta manzana (o Desmarcar si ya estaba)
     *  - Marcar todo el territorio (o Desmarcar si ya estaba completo)
     *  - Cancelar (cierra sin modificar la seleccion)
     */
    private fun onManzanaTocada(manzana: Manzana?) {
        if (manzana == null) return

        if (manzana.idManzana.isBlank() || manzana.territorioId.isBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.reportar_dialog_sin_identificador,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val estaMarcadaInd = manzana.idManzana in seleccion.manzanasDe(manzana.territorioId)
        val territorioCompleto = seleccion.territorioCompleto(manzana.territorioId)

        // Opciones exactas requeridas, sin variar segun el estado previo.
        val opcionManzana = getString(R.string.reportar_dialog_marcar_manzana)
        val opcionTerritorio = getString(R.string.reportar_dialog_marcar_territorio)

        val opciones = arrayOf(
            opcionManzana,
            opcionTerritorio,
            getString(R.string.reportar_dialog_cancelar)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.reportar_dialog_titulo, manzana.idManzana))
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> {
                        guardarHistorial()
                        seleccion = if (estaMarcadaInd) {
                            seleccion.sinManzana(manzana)
                        } else {
                            seleccion.conManzanaIndividual(manzana)
                        }
                        actualizarSeleccionUI()
                    }
                    1 -> {
                        guardarHistorial()
                        seleccion = if (territorioCompleto) {
                            seleccion.sinTerritorioCompleto(manzana.territorioId)
                        } else {
                            seleccion.conTerritorioCompleto(manzana.territorioId)
                        }
                        actualizarSeleccionUI()
                    }
                    2 -> {
                        // Cancelar: cierra sin modificar la seleccion previa
                    }
                }
            }
            .show()
    }

    private fun guardarHistorial() {
        historial.addLast(seleccion)
        if (historial.size > 20) {
            historial.removeFirst()
        }
    }

    private fun quitarUltima() {
        if (historial.isNotEmpty()) {
            seleccion = historial.removeLast()
            actualizarSeleccionUI()
        }
    }

    private fun quitarTodo() {
        if (!seleccion.vacia) {
            guardarHistorial()
            seleccion = seleccion.limpiar()
            actualizarSeleccionUI()
        }
    }

    private fun actualizarSeleccionUI() {
        val v = vista ?: return
        val mapa = v.findViewById<MapaManzanasView>(R.id.mapaManzanas)
        val txtSel = v.findViewById<TextView>(R.id.txtSeleccionManzanas)
        val txtTextoTerr = v.findViewById<TextView>(R.id.txtTextoTerritorios)

        val marcadas = geoData.manzanas.filter { seleccion.estaMarcada(it) }.toSet()
        mapa?.setSeleccionadas(marcadas)

        if (seleccion.vacia) {
            txtSel?.setText(R.string.reportar_sin_seleccion)
            txtTextoTerr?.text = "-"
        } else {
            val partes = mutableListOf<String>()
            seleccion.territoriosCompletos.sorted().forEach {
                partes.add("T $it (completo)")
            }
            seleccion.manzanasPorTerritorio.forEach { (t, ms) ->
                if (ms.isNotEmpty()) {
                    partes.add("T $t: ${ms.sorted().joinToString(", ")}")
                }
            }
            txtSel?.text = partes.joinToString(" · ")

            val textoFormateado = seleccionATexto(seleccion) { tId ->
                geoData.manzanasDe(tId)
            }
            txtTextoTerr?.text = textoFormateado
        }
    }

    private fun enviar(view: View) {
        if (envioEnCurso) return

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
                Toast.makeText(requireContext(), R.string.error_falta_resultado, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val observaciones = view.findViewById<TextInputEditText>(R.id.inputObservaciones)
            .text?.toString()?.trim().orEmpty()

        val textoTerritorio = seleccionATexto(seleccion) { tId ->
            geoData.manzanasDe(tId)
        }

        val reporte = ReporteSalida(
            salidaId = salidaActual.id,
            realizada = realizada,
            observaciones = observaciones,
            territorioTexto = textoTerritorio
        )

        // Identidad desde la sesión de Firebase (nunca desde el formulario).
        val identidad = CuentaNube.identidadActual().getOrElse {
            Toast.makeText(requireContext(), R.string.reporte_error_sesion, Toast.LENGTH_LONG).show()
            return
        }
        // El id del documento depende de la salida y del usuario: reintentar el
        // mismo reporte sobrescribe el MISMO documento, nunca crea otro.
        val pendiente = ReportePendiente(
            clave = FirestoreReportesNube.documentId(reporte.salidaId, identidad.uid),
            outingId = reporte.salidaId,
            salidaFecha = salidaActual.fecha,
            realizada = reporte.realizada,
            observaciones = reporte.observaciones,
            territorioTexto = reporte.territorioTexto,
            uid = identidad.uid,
            email = identidad.email
        )
        val boton = view.findViewById<MaterialButton>(R.id.btnEnviar)
        envioEnCurso = true
        boton.isEnabled = false
        // El envio corre fuera del hilo de la interfaz: la pantalla sigue
        // respondiendo aunque la nube tarde o no haya conexion.
        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = sincronizador.enviar(pendiente)
            envioEnCurso = false
            boton.isEnabled = true
            when (resultado) {
                is ResultadoEnvio.Guardado -> {
                    val mensaje = if (resultado.yaExistia) {
                        R.string.reporte_ya_guardado
                    } else {
                        R.string.reporte_enviado
                    }
                    Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
                }
                is ResultadoEnvio.Pendiente -> {
                    // Todavía no está en la nube: se avisa con claridad.
                    Toast.makeText(requireContext(), R.string.reporte_pendiente, Toast.LENGTH_LONG).show()
                }
                is ResultadoEnvio.Error -> {
                    val mensaje = mensajeDeError(resultado.motivo)
                    Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun mensajeDeError(error: ReporteError?): Int = when (error) {
        ReporteError.SIN_CONFIGURAR -> R.string.reporte_error_sin_configurar
        ReporteError.SIN_SESION, ReporteError.SIN_AUTORIZACION -> R.string.reporte_error_sesion
        ReporteError.SALIDA_NO_ENCONTRADA -> R.string.reporte_error_salida
        ReporteError.SIN_CONEXION -> R.string.reporte_error_conexion
        ReporteError.SERVIDOR -> R.string.reporte_error_servidor
        ReporteError.ENVIO_EN_CURSO -> R.string.reporte_envio_en_curso
        else -> R.string.reporte_error_generico
    }

    private fun cancelar() {
        vista?.findViewById<RadioGroup>(R.id.grupoResultado)?.clearCheck()
        vista?.findViewById<TextInputEditText>(R.id.inputObservaciones)?.setText("")
        seleccion = seleccion.limpiar()
        historial.clear()
        actualizarSeleccionUI()
        Toast.makeText(requireContext(), R.string.reporte_cancelado, Toast.LENGTH_SHORT).show()
    }
}
