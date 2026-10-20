package com.example.geditorio.data

import com.example.geditorio.data.model.EstadoSalida
import com.example.geditorio.data.model.Salida
import com.example.geditorio.data.model.TipoSalida
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Fallo al consultar el cronograma. La pantalla lo traduce a un mensaje; nunca
 * se reemplaza por salidas de ejemplo como si vinieran del servidor.
 */
class SalidasException(
    val error: ReporteError,
    cause: Throwable? = null
) : Exception(cause?.message, cause)

/**
 * Cronograma real del gestor web.
 *
 * Consulta el mes actual y el siguiente (el cronograma se arma por mes) y
 * convierte cada salida al modelo [Salida] conservando el id del backend, que
 * es el que valida POST /api/reports.
 */
class ServidorSalidaDataSource(
    private val cliente: CronogramaHttp,
    private val tokenProvider: TokenProvider,
    private val zona: ZoneId = ZoneId.systemDefault(),
    private val hoy: () -> LocalDate = { LocalDate.now(zona) }
) : SalidaDataSource {

    override suspend fun obtenerSalidas(): List<Salida> {
        val token = try {
            tokenProvider.idToken()
        } catch (e: Exception) {
            null
        } ?: throw SalidasException(ReporteError.SIN_SESION)

        val fechaHoy = hoy()
        val meses = listOf(YearMonth.from(fechaHoy), YearMonth.from(fechaHoy).plusMonths(1)).distinct()

        val respuestas = coroutineScope {
            meses.map { mes -> async { cliente.salidasDelMes(mes.year, mes.monthValue, token) } }
                .map { it.await() }
        }

        // Un mismo id no debe aparecer dos veces aunque el mes se repita.
        return respuestas
            .flatten()
            .mapNotNull { convertir(it) }
            .distinctBy { it.id }
    }

    /**
     * Convierte una salida del backend al modelo de la app.
     *
     * - El id es el del backend, sin cambios.
     * - "Grupo N" se convierte en TipoSalida.Grupo; cualquier otro tipo
     *   (congregacional o uno propio del gestor) conserva su nombre real.
     * - El cronograma no guarda un estado por salida y la app todavia no
     *   consulta los reportes: sin ese dato no se puede afirmar que una salida
     *   se realizo, asi que todas quedan pendientes.
     * - Un territorio vacio se muestra como "Sin asignar" en la pantalla.
     */
    private fun convertir(json: JSONObject): Salida? {
        val id = json.optString("id").trim()
        val fecha = json.optString("date").trim()
        val hora = json.optString("time").trim()
        val tipo = json.optString("type").trim()
        if (id.isEmpty() || fecha.isEmpty() || hora.isEmpty() || tipo.isEmpty()) return null

        val conductor = json.optString("driverName").trim().ifEmpty {
            json.optString("driver").trim()
        }
        val territorios = json.optString("territory")
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()

        return Salida(
            id = id,
            fecha = fecha,
            hora = hora,
            tipo = tipoDe(tipo),
            lugarDeEncuentro = json.optString("placeName").trim(),
            enlaceGoogleMaps = json.optString("placeMapsUrl").trim().ifEmpty { null },
            conductor = conductor,
            territorios = territorios,
            estado = EstadoSalida.PENDIENTE
        )
    }

    companion object {
        private val PATRON_GRUPO = Regex("^grupo\\s*(\\d+)$", RegexOption.IGNORE_CASE)

        /** "Grupo 3" -> Grupo(3); el resto conserva su nombre del gestor. */
        fun tipoDe(nombre: String): TipoSalida {
            val numero = PATRON_GRUPO.find(nombre)?.groupValues?.get(1)?.toIntOrNull()
            if (numero != null) return TipoSalida.Grupo(numero)
            return if (nombre.equals("Congregacional", ignoreCase = true)) {
                TipoSalida.Congregacional
            } else {
                TipoSalida.Otro(nombre)
            }
        }
    }
}
