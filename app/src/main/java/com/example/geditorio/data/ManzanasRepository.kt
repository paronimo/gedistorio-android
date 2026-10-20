package com.example.geditorio.data

import android.content.Context
import android.util.Log
import com.example.geditorio.domain.Manzana
import com.example.geditorio.domain.ManzanasGeoData
import com.example.geditorio.domain.parseManzanasGeoJson
import org.json.JSONObject

/**
 * Carga las manzanas desde el GeoJSON empaquetado en assets.
 *
 * Es de solo lectura: no modifica el archivo original del repositorio, que
 * queda intacto en app/. Solo lee la copia en assets/manzanas.geojson.
 *
 * El archivo se lee una sola vez y se guarda en memoria. Si falta o esta mal
 * formado, se registra el error y se devuelve una lista vacia: la app no se
 * cae y la pantalla puede avisar que no hay manzanas cargadas.
 */
class ManzanasRepository(private val context: Context) {

    @Volatile
    private var cache: ManzanasGeoData? = null

    /** Manzanas y territorios leidos del GeoJSON. */
    fun cargar(): ManzanasGeoData = cache ?: synchronized(this) {
        cache ?: cargarDesdeAssets().also { cache = it }
    }

    /** Manzanas disponibles dentro de un territorio. */
    fun manzanasDeTerritorio(territorioId: String): List<Manzana> =
        cargar().manzanasDe(territorioId)

    /** Todos los territorios, con sus manzanas. */
    fun territorios() = cargar().territorios

    private fun cargarDesdeAssets(): ManzanasGeoData = try {
        val texto = context.assets.open(ASSET_MANZANAS).bufferedReader().use { it.readText() }
        parseManzanasGeoJson(texto)
    } catch (e: Exception) {
        Log.e(TAG, "No se pudo leer $ASSET_MANZANAS: ${e.message}")
        ManzanasGeoData(territorios = emptyList(), invalidos = emptyList())
    }

    /**
     * Identificador de territorio tal como aparece en el GeoJSON, sin el
     * sufijo de version ni ceros a la izquierda. Ej: "T-4-v2" -> "4".
     * Si no se puede interpretar, devuelve el texto original sin espacios.
     */
    fun territorioDesdeManzana(idManzana: String): String? {
        val crudo = idManzana.substringBefore('.').trim()
        val conDigitos = crudo.filter { it.isDigit() }
        return conDigitos.trimStart('0').ifEmpty { null }
    }

    /**
     * Primera manzana del territorio con ese numero, si existe en el archivo.
     * Sirve para mostrar el nombre de la manzana elegida, sin inventar datos.
     */
    fun manzanaPorNumero(numero: String): Manzana? {
        val buscado = numero.trimStart('0').ifEmpty { "0" }
        return cargar().manzanas.firstOrNull {
            it.idManzana.trimStart('0').ifEmpty { "0" } == buscado
        }
    }

    companion object {
        private const val TAG = "ManzanasRepository"
        const val ASSET_MANZANAS = "manzanas.geojson"

        /**
         * Cuenta las manzanas de un FeatureCollection sin construir modelos.
         * Sirve para comparar lo cargado con el total del archivo.
         */
        fun contarFeatures(texto: String): Int = try {
            JSONObject(texto).optJSONArray("features")?.length() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "GeoJSON invalido: ${e.message}")
            0
        }
    }
}
