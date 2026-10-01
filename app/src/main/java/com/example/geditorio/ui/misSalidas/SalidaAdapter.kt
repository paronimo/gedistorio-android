package com.example.geditorio.ui.misSalidas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.geditorio.R
import com.example.geditorio.data.model.Salida

/**
 * Adapter de la lista de salidas.
 *
 * Solo recibe [Salida] ya ordenadas por el repositorio: no ordena ni
 * decide nada, para que la pantalla no tenga reglas propias de datos.
 */
class SalidaAdapter(
    private val onClick: (Salida) -> Unit
) : RecyclerView.Adapter<SalidaAdapter.SalidaViewHolder>() {

    private var salidas: List<Salida> = emptyList()

    fun actualizar(nuevas: List<Salida>) {
        salidas = nuevas
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SalidaViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_salida, parent, false)
        return SalidaViewHolder(vista, onClick)
    }

    override fun onBindViewHolder(holder: SalidaViewHolder, position: Int) {
        holder.enlazar(salidas[position])
    }

    override fun getItemCount(): Int = salidas.size

    class SalidaViewHolder(
        itemView: View,
        private val onClick: (Salida) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        fun enlazar(salida: Salida) {
            itemView.findViewById<TextView>(R.id.txtFecha).text = salida.fechaTexto
            itemView.findViewById<TextView>(R.id.txtHora).text = salida.horaTexto
            itemView.findViewById<TextView>(R.id.txtTipo).text = salida.tipo.etiqueta
            itemView.findViewById<TextView>(R.id.txtEstado).text = salida.estado.etiqueta
            itemView.findViewById<TextView>(R.id.txtLugar).text = salida.lugarDeEncuentro
            itemView.findViewById<TextView>(R.id.txtTerritorios).text =
                itemView.context.getString(
                    R.string.etiqueta_territorio_con_valor,
                    salida.territoriosTexto
                )

            itemView.setOnClickListener { onClick(salida) }
        }
    }
}
