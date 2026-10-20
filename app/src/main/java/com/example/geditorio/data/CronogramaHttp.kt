package com.example.geditorio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Consulta del cronograma real en el backend web.
 *
 * Usa la ruta protegida GET /api/schedule/:year/:month/outings, que devuelve
 * exactamente las mismas salidas que la web (id, date, time, type, territory,
 * driverName, placeName, placeMapsUrl). Se autentica con la sesion de
 * Firebase del conductor.
 *
 * No se usa GET /api/months/:year/:month/outings porque esa ruta es la de la
 * web (sin sesion) y no debe cambiar su comportamiento.
 */
class CronogramaHttp(
    private val baseUrl: String,
    private val timeoutMs: Int = 15_000,
    private val conexion: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection }
) {

    /** Salidas del mes tal como las devuelve el backend. */
    suspend fun salidasDelMes(anio: Int, mes: Int, token: String): List<JSONObject> =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) throw SalidasException(ReporteError.SIN_CONFIGURAR)
            if (token.isBlank()) throw SalidasException(ReporteError.SIN_SESION)

            val url = URL(baseUrl.trimEnd('/') + "/api/schedule/$anio/$mes/outings")
            var abierta: HttpURLConnection? = null

            try {
                val request = conexion(url).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                }
                abierta = request

                when (val codigo = request.responseCode) {
                    HttpURLConnection.HTTP_OK -> leerSalidas(request)
                    401, 403 -> throw SalidasException(ReporteError.SIN_AUTORIZACION)
                    else -> {
                        if (codigo in 400..499) throw SalidasException(ReporteError.SERVIDOR)
                        throw SalidasException(ReporteError.SERVIDOR)
                    }
                }
            } catch (e: SalidasException) {
                throw e
            } catch (e: IOException) {
                // Sin red, servidor apagado, DNS o timeout: no hay datos reales.
                throw SalidasException(ReporteError.SIN_CONEXION, e)
            } catch (e: Exception) {
                throw SalidasException(ReporteError.GENERICO, e)
            } finally {
                abierta?.disconnect()
            }
        }

    private fun leerSalidas(request: HttpURLConnection): List<JSONObject> {
        val texto = request.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arreglo = try {
            JSONArray(texto)
        } catch (e: Exception) {
            throw SalidasException(ReporteError.SERVIDOR, e)
        }

        return (0 until arreglo.length()).mapNotNull { arreglo.optJSONObject(it) }
    }
}
